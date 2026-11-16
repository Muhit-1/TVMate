package com.tvmate.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.tvmate.shared.data.PairedTv
import com.tvmate.shared.data.Settings
import com.tvmate.shared.link.Command
import com.tvmate.shared.link.Link
import com.tvmate.shared.link.PairRequest
import com.tvmate.shared.link.PairResponse
import com.tvmate.shared.link.TvState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.Inet4Address
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "TvMateLink"

data class FoundTv(val name: String, val host: String, val port: Int)

sealed interface LinkStatus {
    data object NotPaired : LinkStatus
    data object Connecting : LinkStatus
    data object Connected : LinkStatus
    data class Offline(val reason: String) : LinkStatus
}

/** Finds TVs, pairs once with a code, then keeps a WebSocket open to the paired TV. */
class TvLink(
    context: Context,
    private val scope: CoroutineScope,
    httpClient: OkHttpClient,
    private val settings: Settings,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val client = httpClient.newBuilder().readTimeout(0, TimeUnit.SECONDS).pingInterval(15, TimeUnit.SECONDS).build()

    val found = MutableStateFlow<List<FoundTv>>(emptyList())
    val status = MutableStateFlow<LinkStatus>(LinkStatus.NotPaired)
    val tvState = MutableStateFlow<TvState?>(null)
    val paired: StateFlow<PairedTv?> = settings.pairedTv.stateIn(scope, SharingStarted.Eagerly, null)

    private var socket: WebSocket? = null

    init {
        scope.launch { paired.collectLatest { p -> if (p == null) idle() else keepConnected(p) } }
        // The TV's IP can change; if we see it again at a new address, follow it.
        scope.launch {
            found.collectLatest { list ->
                val p = paired.value ?: return@collectLatest
                list.firstOrNull { it.name == p.name && (it.host != p.host || it.port != p.port) }
                    ?.let { settings.savePairedTv(p.copy(host = it.host, port = it.port)) }
            }
        }
    }

    private fun idle() {
        status.value = LinkStatus.NotPaired
        tvState.value = null
    }

    // ---------------------------------------------------------------- connection

    private suspend fun keepConnected(p: PairedTv) {
        while (currentCoroutineContext().isActive) {
            status.value = LinkStatus.Connecting
            val closed = CompletableDeferred<String?>()
            val request = Request.Builder().url("http://${p.host}:${p.port}/ws?token=${p.token}").build()
            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    status.value = LinkStatus.Connected
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { Link.json.decodeFromString(TvState.serializer(), text) }.onSuccess { tvState.value = it }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    // 1008: the TV no longer knows this phone (it was reset or "forgot all phones").
                    if (code == 1008) scope.launch { settings.forgetPairedTv() }
                    webSocket.close(1000, null)
                    closed.complete("closed")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete("closed") }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(t.message ?: "no answer") }
            })
            socket = ws
            try {
                val reason = closed.await()
                status.value = LinkStatus.Offline(reason ?: "no answer")
                tvState.value = null
            } finally {
                ws.cancel()
                if (socket === ws) socket = null
            }
            startDiscovery()
            delay(5_000)
        }
    }

    fun send(command: Command): Boolean {
        val ws = socket ?: return false
        return ws.send(Link.json.encodeToString(Command.serializer(), command))
    }

    // ---------------------------------------------------------------- pairing

    suspend fun pair(tv: FoundTv, code: String): Result<PairedTv> = withContext(Dispatchers.IO) {
        runCatching {
            val body = Link.json.encodeToString(PairRequest.serializer(), PairRequest(code.trim(), Build.MODEL))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("http://${tv.host}:${tv.port}/pair").post(body).build()
            val result = try {
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.code == 403 -> throw PairException("That code isn’t right. Check the code on your TV and try again.")
                        !resp.isSuccessful -> throw PairException("The TV answered with an error (${resp.code}).")
                        else -> Link.json.decodeFromString(PairResponse.serializer(), resp.body.string())
                    }
                }
            } catch (e: IOException) {
                throw PairException("Couldn’t reach the TV. Make sure it’s on, running TVMate, and on the same Wi-Fi.")
            }
            PairedTv(result.tvName, tv.host, tv.port, result.token)
        }.onSuccess { settings.savePairedTv(it) }
    }

    suspend fun forget() {
        socket?.cancel()
        settings.forgetPairedTv()
    }

    class PairException(message: String) : Exception(message)

    // ---------------------------------------------------------------- discovery

    private var discovery: NsdManager.DiscoveryListener? = null
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var resolver: kotlinx.coroutines.Job? = null

    fun startDiscovery() {
        if (discovery != null) return
        resolver = scope.launch(Dispatchers.IO) {
            for (info in resolveQueue) resolve(info)?.let { tv -> found.value = (found.value.filterNot { it.host == tv.host } + tv) }
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "discovery failed $errorCode"); discovery = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { resolveQueue.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) { found.value = found.value.filterNot { it.name == info.attributeName() } }
        }
        discovery = l
        runCatching { nsd.discoverServices("_tvmate._tcp", NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { Log.w(TAG, "discovery not started", it); discovery = null }
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        resolver?.cancel()
    }

    /** For networks where discovery doesn't work: add a TV by IP address. */
    fun addManual(ip: String) {
        val clean = ip.trim()
        if (clean.isEmpty()) return
        found.value = found.value.filterNot { it.host == clean } + FoundTv("TV at $clean", clean, Link.DEFAULT_PORT)
    }

    private fun NsdServiceInfo.attributeName(): String =
        attributes["name"]?.toString(Charsets.UTF_8) ?: serviceName.removePrefix("TVMate ")

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): FoundTv? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val addr = if (Build.VERSION.SDK_INT >= 34) {
                    serviceInfo.hostAddresses.let { list -> list.firstOrNull { it is Inet4Address } ?: list.firstOrNull() }
                } else serviceInfo.host
                val tv = addr?.hostAddress?.let { FoundTv(serviceInfo.attributeName(), it, serviceInfo.port) }
                if (cont.isActive) cont.resume(tv)
            }
        })
    }
}
