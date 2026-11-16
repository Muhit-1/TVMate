package com.tvmate.tv

import android.app.PendingIntent
import android.content.Intent
import com.tvmate.shared.link.Command
import com.tvmate.shared.link.TvStatus
import com.tvmate.shared.media.MediaSnapshot
import com.tvmate.shared.media.MirrorMediaService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Keeps the TV app alive while something plays (so radio keeps going when you leave the app on a
 * low-memory TV) and lets the remote's media keys control it.
 */
class TvMediaService : MirrorMediaService() {
    private val host get() = (application as TvApp).host

    override fun snapshots(): Flow<MediaSnapshot?> = host.state.map { s ->
        val title = s.title
        if (title == null || s.status == TvStatus.IDLE) null
        else MediaSnapshot(title, if (s.kind == "radio") "Radio" else "Live TV", s.status != TvStatus.PAUSED)
    }

    override fun onPlayPause(play: Boolean) = host.handle(Command.PlayPause(play))
    override fun onStop() = host.stopPlayback()

    override fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
