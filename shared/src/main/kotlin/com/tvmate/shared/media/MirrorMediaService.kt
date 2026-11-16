package com.tvmate.shared.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.composables.icons.lucide.R as Lucide
import com.tvmate.shared.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the notification shows. */
data class MediaSnapshot(val title: String, val subtitle: String?, val playing: Boolean)

/**
 * A foreground service that shows a media notification (with play/pause and stop) and registers a media
 * session, so lock-screen controls and hardware media keys work. It doesn't play anything itself: it
 * mirrors a [MediaSnapshot] flow and reports button presses back through [onPlayPause] and [onStop].
 *
 * It stops by itself as soon as the snapshot becomes null (nothing playing any more).
 */
abstract class MirrorMediaService : Service() {
    protected abstract fun snapshots(): Flow<MediaSnapshot?>
    protected abstract fun onPlayPause(play: Boolean)
    protected abstract fun onStop()
    /** Where tapping the notification goes. */
    protected abstract fun contentIntent(): PendingIntent?
    protected open val channelName: String get() = "Now playing"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private var collector: Job? = null
    private var last: MediaSnapshot? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "TVMate").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = onPlayPause(true)
                override fun onPause() = onPlayPause(false)
                override fun onStop() = this@MirrorMediaService.onStop()
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> last?.let { onPlayPause(!it.playing) }
            ACTION_STOP -> onStop()
        }
        // A foreground service must call startForeground right away, even before we know what is playing.
        if (!enterForeground(build(last ?: MediaSnapshot("TVMate", null, true)))) { stopSelf(); return START_NOT_STICKY }
        if (collector == null) {
            collector = scope.launch {
                // Give the first state update a moment to arrive before deciding nothing is playing.
                launch { delay(GRACE_MS); if (last == null) finish() }
                snapshots().collect { snap ->
                    if (snap == null) { if (last != null) finish() } else { last = snap; refresh(snap) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun refresh(snap: MediaSnapshot) {
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, snap.title).putString(MediaMetadata.METADATA_KEY_ARTIST, snap.subtitle.orEmpty()).build())
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (snap.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(snap))
    }

    private fun build(snap: MediaSnapshot): Notification {
        fun action(code: Int, drawable: Int, label: String, act: String) = Notification.Action.Builder(
            Icon.createWithResource(this, drawable), label,
            PendingIntent.getService(this, code, Intent(this, this@MirrorMediaService.javaClass).setAction(act), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tvmate)
            .setContentTitle(snap.title)
            .setContentText(snap.subtitle)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                if (snap.playing) action(1, Lucide.drawable.lucide_ic_pause, "Pause", ACTION_PLAY_PAUSE)
                else action(1, Lucide.drawable.lucide_ic_play, "Play", ACTION_PLAY_PAUSE),
            )
            .addAction(action(2, Lucide.drawable.lucide_ic_x, "Stop", ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun enterForeground(n: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, n)
        true
    } catch (e: Exception) {
        Log.w("TvMateMedia", "can't start foreground: ${e.message}")
        false
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "tvmate_now_playing"
        const val NOTIFICATION_ID = 4711
        const val ACTION_PLAY_PAUSE = "com.tvmate.action.PLAY_PAUSE"
        const val ACTION_STOP = "com.tvmate.action.STOP"
        const val GRACE_MS = 4_000L
    }
}
