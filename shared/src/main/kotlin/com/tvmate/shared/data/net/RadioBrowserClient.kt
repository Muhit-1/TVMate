package com.tvmate.shared.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.create
import java.io.IOException

/**
 * Radio Browser has no single host. We look up the mirror list, then use the first one that answers.
 * If a call fails, we forget the chosen mirror and pick again once.
 */
class RadioBrowserClient(private val client: OkHttpClient) {
    private val lock = Mutex()
    private var api: RadioBrowserApi? = null

    private val fallbackHosts = listOf(
        "de1.api.radio-browser.info",
        "de2.api.radio-browser.info",
        "fi1.api.radio-browser.info",
        "at1.api.radio-browser.info",
    )

    private suspend fun mirrors(): List<String> = withContext(Dispatchers.IO) {
        val listed = runCatching {
            val req = Request.Builder().url("https://all.api.radio-browser.info/json/servers").build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                TvMateJson.decodeFromString<List<RadioServerDto>>(body).map { it.name }.filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
        (listed.shuffled() + fallbackHosts).distinct()
    }

    private suspend fun connect(): RadioBrowserApi = lock.withLock {
        api?.let { return it }
        for (host in mirrors()) {
            val candidate = buildRetrofit("https://$host/", client).create<RadioBrowserApi>()
            val ok = runCatching { candidate.stats().close() }.isSuccess
            if (ok) {
                api = candidate
                return candidate
            }
        }
        throw IOException("No Radio Browser server reachable")
    }

    private suspend fun <T> call(block: suspend (RadioBrowserApi) -> T): T {
        return try {
            block(connect())
        } catch (e: IOException) {
            lock.withLock { api = null }
            block(connect())
        }
    }

    suspend fun search(
        name: String = "",
        countryCode: String = "",
        language: String = "",
        tag: String = "",
        limit: Int = 50,
        offset: Int = 0,
    ) = call { it.search(name = name, countryCode = countryCode, language = language, tag = tag, limit = limit, offset = offset) }
        .filter { it.playUrl.isNotBlank() }

    suspend fun tags() = call { it.tags() }
    suspend fun countries() = call { it.countries() }
}
