package com.tvmate.tv

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import android.media.AudioManager
import android.os.Build
import com.tvmate.shared.TvMateGraph
import com.tvmate.shared.data.MediaRef
import com.tvmate.shared.data.db.ChannelEntity
import com.tvmate.shared.data.net.StationDto
import com.tvmate.shared.link.Command
import com.tvmate.shared.link.Link
import com.tvmate.shared.link.StreamSpec
import com.tvmate.shared.link.TvState
import com.tvmate.shared.link.TvStatus
import com.tvmate.shared.player.PlayItem
import com.tvmate.shared.player.PlaybackState
import com.tvmate.shared.player.StreamPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.SecureRandom

enum class TvScreen { Home, Player }

data class NowPlaying(
    val kind: MediaRef.Kind,
    val id: String,
    val name: String,
    val meta: String?,
    val logoUrl: String?,
)

/**
 * Everything the TV app needs while it runs: the player, what is playing, the phone link server and
 * the pairing code. Lives as long as the app process, so a phone can keep controlling playback.
 */
class TvHost(private val app: Application) {
    val graph = TvMateGraph.get(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val player = StreamPlayer(app, graph.httpClient)
    val tvName: String = android.provider.Settings.Global.getString(app.contentResolver, "device_name") ?: Build.MODEL

    val screen = MutableStateFlow(TvScreen.Home)
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val pairingCode = MutableStateFlow(newCode())
    /** true once the user chose to skip the first-run pairing screen */
    val pairingSkipped = MutableStateFlow(false)
    val pairedPhones: StateFlow<Int> = graph.settings.phoneTokens.map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, -1)

    /** Set by the activity so phone D-pad keys reach the TV UI. */
    @Volatile var keyInjector: ((Int) -> Unit)? = null

    private val volumeTick = MutableStateFlow(0)

    val state: StateFlow<TvState> = combine(player.state, player.playWhenReady, nowPlaying, volumeTick) { p, ready, now, _ ->
        val (status, index, total, message) = when (p) {
            PlaybackState.Idle -> Quad(TvStatus.IDLE, 0, 0, null)
            is PlaybackState.Buffering -> Quad(TvStatus.BUFFERING, p.index, p.total, null)
            is PlaybackState.Playing -> Quad(if (ready) TvStatus.PLAYING else TvStatus.PAUSED, p.index, p.total, null)
            is PlaybackState.Retrying -> Quad(TvStatus.RETRYING, p.failedIndex + 1, p.total, "Stream ${p.failedIndex + 1} stopped, trying another…")
            is PlaybackState.Failed -> Quad(TvStatus.FAILED, 0, 0, "All streams failed (${p.reason})")
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        TvState(
            tvName = tvName,
            status = status,
            kind = now?.kind?.key,
            itemId = now?.id,
            title = now?.name,
            meta = now?.meta,
            logoUrl = now?.logoUrl,
            streamIndex = index,
            streamCount = total,
            volumePercent = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max,
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
            message = message,
        )
    }.stateIn(scope, SharingStarted.Eagerly, TvState(tvName))

    private data class Quad(val a: TvStatus, val b: Int, val c: Int, val d: String?)

    private val server = LinkServer(this, graph.settings)
    private val nsd = NsdRegistrar(app)

    fun start() {
        scope.launch { graph.catalog.refreshIfStale() }
        val port = server.start()
        if (port != null) nsd.register(tvName, port)
    }

    // ---- pairing ----

    private var failedAttempts = 0

    /** Returns true when [code] matches. The code is single-use and changes after 5 wrong tries. */
    fun checkCode(code: String): Boolean {
        val ok = code.equals(pairingCode.value, ignoreCase = true)
        if (ok) {
            failedAttempts = 0
            pairingCode.value = newCode()
        } else if (++failedAttempts >= 5) {
            failedAttempts = 0
            pairingCode.value = newCode()
        }
        return ok
    }

    // ---- playback (always on the main thread) ----

    fun playChannel(c: ChannelEntity) {
        scope.launch {
            val streams = graph.catalog.streams(c.id).map { PlayItem(it.url, it.referrer, it.userAgent, it.quality) }
            startPlayback(NowPlaying(MediaRef.Kind.TV, c.id, c.name, c.country, c.logoUrl), streams)
        }
    }

    fun playStation(s: StationDto) {
        startPlayback(
            NowPlaying(MediaRef.Kind.RADIO, s.stationuuid, s.name.trim(), listOf(s.country.ifBlank { s.countrycode }, s.codec).filter { it.isNotBlank() }.joinToString(" · "), s.favicon.ifBlank { null }),
            listOf(PlayItem(s.playUrl)),
        )
    }

    fun playRef(ref: MediaRef) {
        scope.launch {
            if (ref.kind == MediaRef.Kind.TV) {
                val streams = graph.catalog.streams(ref.id).map { PlayItem(it.url, it.referrer, it.userAgent, it.quality) }
                startPlayback(NowPlaying(ref.kind, ref.id, ref.name, ref.subtitle, ref.logoUrl), streams)
            } else {
                startPlayback(NowPlaying(ref.kind, ref.id, ref.name, ref.subtitle, ref.logoUrl), listOf(PlayItem(ref.streamUrl.orEmpty())))
            }
        }
    }

    private fun startPlayback(now: NowPlaying, items: List<PlayItem>) {
        nowPlaying.value = now
        player.play(now.name, items)
        screen.value = TvScreen.Player
        runCatching { ContextCompat.startForegroundService(app, Intent(app, TvMediaService::class.java)) }
            .onFailure { Log.w("TvMateHost", "media service not started: ${it.message}") }
    }

    fun leavePlayer() {
        if (nowPlaying.value?.kind == MediaRef.Kind.TV) stopPlayback()
        screen.value = TvScreen.Home
    }

    fun stopPlayback() {
        player.stop()
        nowPlaying.value = null
        screen.value = TvScreen.Home
    }

    fun togglePause() = player.setPlaying(!player.player.playWhenReady)

    /** Entry point for everything the phone sends. */
    fun handle(cmd: Command) {
        scope.launch {
            when (cmd) {
                is Command.PlayTv -> startPlayback(
                    NowPlaying(MediaRef.Kind.TV, cmd.channelId, cmd.name, cmd.meta, cmd.logoUrl),
                    cmd.streams.map { it.toItem() },
                )
                is Command.PlayRadio -> startPlayback(
                    NowPlaying(MediaRef.Kind.RADIO, cmd.stationId, cmd.name, cmd.meta, cmd.logoUrl),
                    listOf(cmd.stream.toItem()),
                )
                is Command.PlayPause -> player.setPlaying(cmd.play)
                Command.Stop -> stopPlayback()
                is Command.SelectStream -> player.playStream(cmd.index)
                is Command.VolumeStep -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (cmd.delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI,
                )
                is Command.VolumeSet -> {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (cmd.percent.coerceIn(0, 100) * max / 100), AudioManager.FLAG_SHOW_UI)
                }
                Command.MuteToggle -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                is Command.Key -> keyInjector?.invoke(cmd.code)
            }
            volumeTick.value++
        }
    }

    private fun StreamSpec.toItem() = PlayItem(url, referrer, userAgent, label)

    companion object {
        private val random = SecureRandom()
        fun newCode(): String = buildString {
            repeat(Link.CODE_LENGTH) { append(Link.CODE_ALPHABET[random.nextInt(Link.CODE_ALPHABET.length)]) }
        }
    }
}
