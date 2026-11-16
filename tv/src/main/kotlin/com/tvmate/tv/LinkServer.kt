package com.tvmate.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.tvmate.shared.data.Settings
import com.tvmate.shared.link.Command
import com.tvmate.shared.link.Link
import com.tvmate.shared.link.PairRequest
import com.tvmate.shared.link.PairResponse
import com.tvmate.shared.link.TvState
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

private const val TAG = "TvMateLink"

/** HTTP + WebSocket server the phone app talks to. */
class LinkServer(private val host: TvHost, private val settings: Settings) {
    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    /** Starts on the first free port from [Link.DEFAULT_PORT]. Returns the port, or null if none worked. */
    fun start(): Int? {
        for (port in Link.DEFAULT_PORT until Link.DEFAULT_PORT + 10) {
            try {
                val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    install(WebSockets) { pingPeriod = 15.seconds }
                    routing {
                        post("/pair") {
                            val req = runCatching { Link.json.decodeFromString(PairRequest.serializer(), call.receiveText()) }.getOrNull()
                            if (req == null || !host.checkCode(req.code)) {
                                call.respondText("wrong code", status = HttpStatusCode.Forbidden)
                            } else {
                                val token = newToken()
                                settings.addPhoneToken(token)
                                val body = Link.json.encodeToString(PairResponse.serializer(), PairResponse(token, host.tvName))
                                call.respondText(body, ContentType.Application.Json)
                            }
                        }
                        webSocket("/ws") {
                            val token = call.request.queryParameters["token"]
                            if (token == null || token !in settings.phoneTokens.first()) {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                                return@webSocket
                            }
                            val sender = launch {
                                host.state.collect { send(Frame.Text(Link.json.encodeToString(TvState.serializer(), it))) }
                            }
                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        runCatching { Link.json.decodeFromString(Command.serializer(), frame.readText()) }
                                            .onSuccess { host.handle(it) }
                                            .onFailure { Log.w(TAG, "bad command", it) }
                                    }
                                }
                            } finally {
                                sender.cancel()
                            }
                        }
                    }
                }
                server.start(wait = false)
                engine = server
                Log.i(TAG, "listening on $port")
                return port
            } catch (e: Exception) {
                Log.w(TAG, "port $port unavailable: ${e.message}")
            }
        }
        return null
    }

    private fun newToken(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/** Advertises the TV on the local network so phones can find it. */
class NsdRegistrar(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun register(tvName: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "TVMate $tvName"
            serviceType = "_tvmate._tcp"
            this.port = port
            setAttribute("name", tvName)
            setAttribute("v", "1")
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { Log.i(TAG, "advertising as ${info.serviceName}") }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { Log.w(TAG, "advertise failed $errorCode") }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        listener = l
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
    }
}
