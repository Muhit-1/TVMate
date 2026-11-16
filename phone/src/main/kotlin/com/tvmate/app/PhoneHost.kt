package com.tvmate.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.tvmate.app.atv.AtvIdentity
import com.tvmate.app.atv.AtvRemote
import com.tvmate.shared.TvMateGraph
import com.tvmate.shared.link.TvStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-level state that must outlive the screen: the connection to the TV, the system-remote
 * connection, and the notification that mirrors what the TV is playing.
 */
class PhoneHost(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val graph = TvMateGraph.get(app)
    val link = TvLink(app, scope, graph.httpClient, graph.settings)
    val atvIdentity = AtvIdentity()
    val atv = AtvRemote(atvIdentity, scope)
    val atvPaired: StateFlow<Boolean> = graph.settings.atvPaired.stateIn(scope, SharingStarted.Eagerly, false)

    fun start() {
        // Keep the system-remote connection open whenever a TV is paired and the remote is paired.
        scope.launch {
            combine(link.paired, atvPaired) { tv, ok -> if (ok) tv?.host else null }.distinctUntilChanged().collectLatest { host ->
                if (host != null) atv.connect(host) else atv.disconnect()
            }
        }
        // Show lock-screen / notification controls while the TV is playing something we started.
        scope.launch {
            link.tvState.map { it?.title != null && it.status != TvStatus.IDLE }.distinctUntilChanged().collect { playing ->
                if (playing) startMediaService()
            }
        }
    }

    private fun startMediaService() {
        // Android only lets an app start a foreground service while it is visible. Playback is normally
        // started from the app, so this works; if not, there is simply no notification.
        runCatching { ContextCompat.startForegroundService(app, Intent(app, PhoneMediaService::class.java)) }
            .onFailure { Log.w("TvMatePhone", "media notification not started: ${it.message}") }
    }
}

class TvMatePhoneApp : Application() {
    lateinit var host: PhoneHost
        private set

    override fun onCreate() {
        super.onCreate()
        // Logos are small; keep the image cache modest so older phones don't struggle.
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).memoryCache { MemoryCache.Builder().maxSizePercent(ctx, 0.08).build() }.build()
        }
        host = PhoneHost(this).also { it.start() }
    }
}
