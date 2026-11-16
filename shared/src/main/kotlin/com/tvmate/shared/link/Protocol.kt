package com.tvmate.shared.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phone ↔ TV link. The TV app runs a small HTTP + WebSocket server on the local network and advertises
 * it over mDNS (NSD). The phone pairs once with a code shown on the TV, then talks over a WebSocket.
 *
 *   POST /pair   {code, deviceName}  ->  {token, tvName}
 *   WS   /ws?token=...               ->  phone sends [Command], TV sends [TvState]
 */
object Link {
    const val SERVICE_TYPE = "_tvmate._tcp."
    const val DEFAULT_PORT = 8765
    const val CODE_LENGTH = 6
    /** No 0/O/1/I so the code is easy to read from across the room. */
    const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type"; encodeDefaults = true }
}

@Serializable data class PairRequest(val code: String, val deviceName: String)
@Serializable data class PairResponse(val token: String, val tvName: String)

@Serializable
data class StreamSpec(
    val url: String,
    val referrer: String? = null,
    val userAgent: String? = null,
    val label: String? = null,
)

@Serializable
sealed interface Command {
    @Serializable @SerialName("play_tv")
    data class PlayTv(
        val channelId: String,
        val name: String,
        val meta: String? = null,
        val logoUrl: String? = null,
        val streams: List<StreamSpec>,
    ) : Command

    @Serializable @SerialName("play_radio")
    data class PlayRadio(
        val stationId: String,
        val name: String,
        val meta: String? = null,
        val logoUrl: String? = null,
        val stream: StreamSpec,
    ) : Command

    @Serializable @SerialName("play_pause") data class PlayPause(val play: Boolean) : Command
    @Serializable @SerialName("stop") data object Stop : Command
    @Serializable @SerialName("select_stream") data class SelectStream(val index: Int) : Command
    @Serializable @SerialName("volume_step") data class VolumeStep(val delta: Int) : Command
    @Serializable @SerialName("volume_set") data class VolumeSet(val percent: Int) : Command
    @Serializable @SerialName("mute_toggle") data object MuteToggle : Command
    /** Android KeyEvent code, delivered to the TVMate TV app (D-pad, OK, Back). */
    @Serializable @SerialName("key") data class Key(val code: Int) : Command
}

enum class TvStatus { IDLE, BUFFERING, PLAYING, PAUSED, RETRYING, FAILED }

@Serializable
data class TvState(
    val tvName: String,
    val status: TvStatus = TvStatus.IDLE,
    /** "tv" or "radio"; null when nothing is loaded */
    val kind: String? = null,
    val itemId: String? = null,
    val title: String? = null,
    val meta: String? = null,
    val logoUrl: String? = null,
    val streamIndex: Int = 0,
    val streamCount: Int = 0,
    val volumePercent: Int = 0,
    val muted: Boolean = false,
    val message: String? = null,
)
