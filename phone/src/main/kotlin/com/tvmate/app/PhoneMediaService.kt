package com.tvmate.app

import android.app.PendingIntent
import android.content.Intent
import com.tvmate.shared.link.Command
import com.tvmate.shared.link.TvStatus
import com.tvmate.shared.media.MediaSnapshot
import com.tvmate.shared.media.MirrorMediaService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Lock-screen and notification controls for whatever the TV is playing. */
class PhoneMediaService : MirrorMediaService() {
    private val host get() = (application as TvMatePhoneApp).host

    override fun snapshots(): Flow<MediaSnapshot?> = combine(host.link.tvState, host.link.status) { tv, link ->
        val title = tv?.title
        if (link != LinkStatus.Connected || tv == null || title == null || tv.status == TvStatus.IDLE) null
        else MediaSnapshot(title, "Playing on ${tv.tvName}", tv.status != TvStatus.PAUSED)
    }

    override fun onPlayPause(play: Boolean) { host.link.send(Command.PlayPause(play)) }
    override fun onStop() { host.link.send(Command.Stop) }

    override fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
