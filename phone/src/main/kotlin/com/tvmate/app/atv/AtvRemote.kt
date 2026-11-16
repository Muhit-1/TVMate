package com.tvmate.app.atv

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException

private const val TAG = "TvMateAtv"

sealed interface AtvState {
    data object Off : AtvState
    data object Connecting : AtvState
    /** Connected and ready for keys. */
    data object Ready : AtvState
    data class Failed(val reason: String) : AtvState
}

/** Android TV key codes (android.view.KeyEvent values, same numbers the protocol uses). */
object AtvKey {
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val MEDIA_PLAY_PAUSE = 85
    const val VOLUME_MUTE = 164
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
}

data class AtvVolume(val level: Int, val max: Int, val muted: Boolean)

/**
 * Keeps a connection to the TV's remote service (port 6466) and sends key presses. Reconnects by
 * itself. Must be paired first (see [AtvPairing]); the TV drops unknown phones right after the handshake.
 */
class AtvRemote(private val identity: AtvIdentity, private val scope: CoroutineScope) {
    val state = MutableStateFlow<AtvState>(AtvState.Off)
    /** Whether the TV is awake (from the TV's own start/stop notices). */
    val tvOn = MutableStateFlow<Boolean?>(null)
    val volume = MutableStateFlow<AtvVolume?>(null)

    private var job: Job? = null
    private val outbox = Channel<ByteArray>(Channel.UNLIMITED)

    fun connect(host: String) {
        disconnect()
        job = scope.launch(Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
                state.value = AtvState.Connecting
                try {
                    runSession(host)
                } catch (e: Exception) {
                    if (!currentCoroutineContext().isActive) break
                    Log.w(TAG, "remote session ended: ${e.message}")
                    state.value = AtvState.Failed(e.message ?: "disconnected")
                }
                tvOn.value = null
                delay(5_000)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        state.value = AtvState.Off
        tvOn.value = null
        volume.value = null
    }

    fun sendKey(code: Int): Boolean {
        if (state.value != AtvState.Ready) return false
        // key_inject { key_code, direction = SHORT }
        return outbox.trySend(PbWriter().message(10) { int(1, code); int(2, DIRECTION_SHORT) }.toFrame()).isSuccess
    }

    private suspend fun runSession(host: String) {
        val socket = identity.openSocket(host, PORT, 8_000)
        // The TV pings every ~5 s when idle; 16 s of silence means the connection is dead.
        socket.soTimeout = 16_000
        socket.use {
            val input = it.inputStream
            val output = it.outputStream
            val writer = scope.launch(Dispatchers.IO) {
                for (frame in outbox) { output.write(frame); output.flush() }
            }
            try {
                while (currentCoroutineContext().isActive) {
                    val frame = try { PbReader.readFrame(input) } catch (e: SocketTimeoutException) { throw java.io.IOException("TV stopped responding") }
                        ?: throw java.io.IOException("TV closed the connection")
                    handle(PbReader.parse(frame))
                }
            } finally {
                writer.cancel()
                // Drop anything queued for the dead connection.
                while (outbox.tryReceive().isSuccess) { /* discard */ }
            }
        }
    }

    private fun reply(build: PbWriter.() -> Unit) { outbox.trySend(PbWriter().apply(build).toFrame()) }

    private fun handle(fields: List<PbField>) {
        for (f in fields) when (f.number) {
            1 -> { // remote_configure: the TV tells us what it supports; we answer with what we use.
                val supported = PbReader.parse(f.bytes).firstOrNull { it.number == 1 }?.long ?: 0L
                val active = supported and FEATURES
                reply {
                    message(1) {
                        int(1, active)
                        message(2) { int(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
                    }
                }
            }
            2 -> { // remote_set_active
                reply { message(2) { int(1, FEATURES) } }
                state.value = AtvState.Ready
            }
            8 -> { // ping request -> ping response
                val v = PbReader.parse(f.bytes).firstOrNull { it.number == 1 }?.long ?: 0L
                reply { message(9) { int(1, v) } }
            }
            40 -> tvOn.value = (PbReader.parse(f.bytes).firstOrNull { it.number == 1 }?.long ?: 0L) != 0L
            50 -> { // volume info
                val inner = PbReader.parse(f.bytes)
                volume.value = AtvVolume(
                    level = inner.firstOrNull { it.number == 7 }?.long?.toInt() ?: 0,
                    max = inner.firstOrNull { it.number == 6 }?.long?.toInt() ?: 100,
                    muted = (inner.firstOrNull { it.number == 8 }?.long ?: 0L) != 0L,
                )
            }
            3 -> Log.w(TAG, "TV reported an error")
        }
        if (state.value == AtvState.Connecting && fields.any { it.number == 40 }) state.value = AtvState.Ready
    }

    companion object {
        const val PORT = 6466
        private const val DIRECTION_SHORT = 3
        /** PING | KEY | POWER | VOLUME | APP_LINK */
        private const val FEATURES = 1L or 2L or 32L or 64L or 512L
    }
}
