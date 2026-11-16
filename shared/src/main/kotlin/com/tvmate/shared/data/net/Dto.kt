package com.tvmate.shared.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- iptv-org API (https://iptv-org.github.io/api/) ----

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val country: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    val closed: String? = null,
)

@Serializable
data class FeedDto(
    val channel: String,
    val id: String,
    @SerialName("is_main") val isMain: Boolean = false,
    val languages: List<String> = emptyList(),
)

@Serializable
data class StreamDto(
    val channel: String? = null,
    val feed: String? = null,
    val title: String? = null,
    val url: String,
    val quality: String? = null,
    val labels: List<String> = emptyList(),
    @SerialName("user_agent") val userAgent: String? = null,
    val referrer: String? = null,
)

@Serializable
data class LogoDto(
    val channel: String,
    val url: String,
    val width: Int = 0,
)

@Serializable
data class BlocklistDto(val channel: String)

@Serializable
data class CountryDto(val code: String, val name: String)

@Serializable
data class CategoryDto(val id: String, val name: String)

@Serializable
data class LanguageDto(val code: String, val name: String)

// ---- Radio Browser (https://api.radio-browser.info/) ----

@Serializable
data class StationDto(
    val stationuuid: String,
    val name: String = "",
    val url: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val countrycode: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val votes: Int = 0,
) {
    val playUrl: String get() = urlResolved.ifBlank { url }
}

@Serializable
data class RadioServerDto(val name: String = "")

@Serializable
data class RadioTagDto(val name: String, val stationcount: Int = 0)

@Serializable
data class RadioCountryDto(val name: String, val iso_3166_1: String = "", val stationcount: Int = 0)
