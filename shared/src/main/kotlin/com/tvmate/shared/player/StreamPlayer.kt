@file:OptIn(UnstableApi::class)

package com.tvmate.shared.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.tvmate.shared.data.net.USER_AGENT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/** One playable stream, with the headers some streams need. */
data class PlayItem(
    val url: String,
    val referrer: String? = null,
    val userAgent: String? = null,
    val label: String? = null,
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Buffering(val title: String, val index: Int, val total: Int) : PlaybackState
    data class Playing(val title: String, val index: Int, val total: Int) : PlaybackState
    /** The current stream failed and the next one is being tried. */
    data class Retrying(val title: String, val failedIndex: Int, val total: Int) : PlaybackState
    /** Every stream failed. */
    data class Failed(val title: String, val reason: String) : PlaybackState
}

/**
 * Wraps ExoPlayer. Plays a list of streams for one channel or station: if one fails it moves to the
 * next, and only reports [PlaybackState.Failed] when all of them have failed.
 */
class StreamPlayer(context: Context, private val client: OkHttpClient) {
    // Smaller buffers than the defaults: live streams gain nothing from 50 s of buffer and low-RAM TVs suffer.
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
        .setTargetBufferBytes(12 * 1024 * 1024)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).setLoadControl(loadControl).build()

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state

    private val _playWhenReady = MutableStateFlow(true)
    /** false while the user has paused */
    val playWhenReady: StateFlow<Boolean> = _playWhenReady

    private var title = ""
    private var queue: List<PlayItem> = emptyList()
    private var index = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (queue.isEmpty()) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> if (_state.value !is PlaybackState.Retrying) {
                        _state.value = PlaybackState.Buffering(title, index, queue.size)
                    }
                    Player.STATE_READY -> _state.value = PlaybackState.Playing(title, index, queue.size)
                    else -> Unit
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playWhenReady.value = playWhenReady
            }

            override fun onPlayerError(error: PlaybackException) {
                if (index + 1 < queue.size) {
                    _state.value = PlaybackState.Retrying(title, index, queue.size)
                    index += 1
                    start()
                } else {
                    _state.value = PlaybackState.Failed(title, error.errorCodeName)
                }
            }
        })
    }

    fun play(title: String, streams: List<PlayItem>) {
        if (streams.isEmpty()) {
            _state.value = PlaybackState.Failed(title, "No streams")
            return
        }
        this.title = title
        queue = streams
        index = 0
        start()
    }

    /** Jump to a specific stream of the current list, e.g. when the user picks "Stream 2". */
    fun playStream(i: Int) {
        if (i !in queue.indices) return
        index = i
        start()
    }

    private fun start() {
        val item = queue[index]
        _state.value = PlaybackState.Buffering(title, index, queue.size)
        val headers = buildMap { item.referrer?.let { put("Referer", it) } }
        val factory = OkHttpDataSource.Factory(client)
            .setUserAgent(item.userAgent ?: USER_AGENT)
            .setDefaultRequestProperties(headers)
        player.setMediaSource(DefaultMediaSourceFactory(factory).createMediaSource(MediaItem.fromUri(item.url)))
        player.prepare()
        player.playWhenReady = true
    }

    fun setPlaying(play: Boolean) {
        player.playWhenReady = play
    }

    fun stop() {
        player.stop()
        queue = emptyList()
        _state.value = PlaybackState.Idle
    }

    fun release() {
        player.release()
    }
}
