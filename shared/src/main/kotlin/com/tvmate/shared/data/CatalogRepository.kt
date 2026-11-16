package com.tvmate.shared.data

import androidx.room.withTransaction
import com.tvmate.shared.data.db.CategoryEntity
import com.tvmate.shared.data.db.ChannelEntity
import com.tvmate.shared.data.db.CountryEntity
import com.tvmate.shared.data.db.HiddenChannelEntity
import com.tvmate.shared.data.db.LanguageEntity
import com.tvmate.shared.data.db.StreamEntity
import com.tvmate.shared.data.db.TvMateDatabase
import com.tvmate.shared.data.net.BlocklistDto
import com.tvmate.shared.data.net.ChannelDto
import com.tvmate.shared.data.net.FeedDto
import com.tvmate.shared.data.net.IptvApi
import com.tvmate.shared.data.net.LogoDto
import com.tvmate.shared.data.net.StreamDto
import com.tvmate.shared.data.net.TvMateJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import okhttp3.ResponseBody

sealed interface RefreshResult {
    data object Fresh : RefreshResult
    data class Updated(val channels: Int, val streams: Int) : RefreshResult
    data class Failed(val message: String) : RefreshResult
}

data class ChannelQuery(
    val text: String = "",
    val country: String = "",
    val category: String = "",
    val language: String = "",
    val filters: ChannelFilters = ChannelFilters(),
    val limit: Int = 300,
)

/** iptv-org catalog: download, clean up, cache in Room, and query. */
class CatalogRepository(
    private val db: TvMateDatabase,
    private val api: IptvApi,
    private val settings: Settings,
) {
    private val dao = db.catalog()

    fun channels(q: ChannelQuery): Flow<List<ChannelEntity>> = dao.channels(
        query = q.text.trim(),
        country = q.country,
        category = q.category,
        language = q.language,
        showGeo = q.filters.showGeoBlocked,
        showNot247 = q.filters.showNot247,
        showNsfw = q.filters.showNsfw,
        limit = q.limit,
    )

    fun channelCount() = dao.channelCount()
    fun countries() = dao.countries()
    fun categories() = dao.categories()
    fun languages() = dao.languages()
    fun hiddenCount() = dao.hiddenCount()

    suspend fun channel(id: String) = dao.channel(id)
    suspend fun streams(channelId: String) = dao.streams(channelId)
    suspend fun hide(channelId: String) = dao.hide(HiddenChannelEntity(channelId))
    suspend fun unhide(channelId: String) = dao.unhide(channelId)
    suspend fun unhideAll() = dao.unhideAll()

    /** Refreshes once a day. Pass [force] to refresh now. Never throws. */
    suspend fun refreshIfStale(force: Boolean = false, now: Long = System.currentTimeMillis()): RefreshResult {
        val last = settings.lastRefresh()
        if (!force && now - last < DAY_MS && dao.channelCountNow() > 0) return RefreshResult.Fresh
        return try {
            val result = download()
            settings.markRefreshed(now)
            result
        } catch (e: Exception) {
            RefreshResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private suspend fun download(): RefreshResult.Updated = withContext(Dispatchers.IO) {
        val channelDtos: List<ChannelDto> = api.channels().decodeList()
        val feeds: List<FeedDto> = api.feeds().decodeList()
        val streamDtos: List<StreamDto> = api.streams().decodeList()
        val logos: List<LogoDto> = runCatching { api.logos().decodeList<LogoDto>() }.getOrDefault(emptyList())
        val blocked: Set<String> = runCatching { api.blocklist().decodeList<BlocklistDto>() }
            .getOrDefault(emptyList()).map { it.channel }.toSet()
        val countries = runCatching { api.countries() }.getOrDefault(emptyList())
        val categories = runCatching { api.categories() }.getOrDefault(emptyList())
        val languages = runCatching { api.languages() }.getOrDefault(emptyList())

        // Hide closed and blocklisted channels.
        val open = channelDtos.filter { it.closed == null && it.id !in blocked }
        val openIds = open.mapTo(HashSet()) { it.id }

        val languagesByChannel: Map<String, String> = feeds.groupBy { it.channel }.mapValues { (_, list) ->
            (list.firstOrNull { it.isMain } ?: list.first()).languages.joinToString(",")
        }
        val logoByChannel: Map<String, String> = logos.groupBy { it.channel }.mapValues { (_, list) ->
            list.maxBy { it.width }.url
        }

        val streamsByChannel: Map<String, List<StreamEntity>> = streamDtos
            .asSequence()
            .filter { it.channel != null && it.channel in openIds }
            .map { it.toEntity() }
            .distinctBy { it.channelId + it.url }
            .groupBy { it.channelId }
            .mapValues { (_, list) -> list.sortedBy { it.rank } }

        val channelEntities = open.mapNotNull { c ->
            val s = streamsByChannel[c.id] ?: return@mapNotNull null
            ChannelEntity(
                id = c.id,
                name = c.name,
                country = c.country,
                categories = c.categories.joinToString(","),
                languages = languagesByChannel[c.id].orEmpty(),
                isNsfw = c.isNsfw || "xxx" in c.categories,
                logoUrl = logoByChannel[c.id],
                geoBlocked = s.all { it.geoBlocked },
                not247 = s.all { it.not247 },
                streamCount = s.size,
            )
        }

        db.withTransaction {
            dao.clearAll()
            dao.insertCountries(countries.map { CountryEntity(it.code, it.name) })
            dao.insertCategories(categories.map { CategoryEntity(it.id, it.name) })
            dao.insertLanguages(languages.map { LanguageEntity(it.code, it.name) })
            channelEntities.chunked(CHUNK).forEach { dao.insertChannels(it) }
            channelEntities.chunked(CHUNK).forEach { chunk ->
                dao.insertStreams(chunk.flatMap { streamsByChannel.getValue(it.id) })
            }
        }
        RefreshResult.Updated(channelEntities.size, channelEntities.sumOf { it.streamCount })
    }

    private fun StreamDto.toEntity(): StreamEntity {
        val geo = labels.any { it.equals("Geo-blocked", ignoreCase = true) }
        val not247 = labels.any { it.equals("Not 24/7", ignoreCase = true) }
        val q = quality?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        return StreamEntity(
            channelId = channel!!,
            url = url,
            title = title,
            quality = quality,
            referrer = referrer,
            userAgent = userAgent,
            geoBlocked = geo,
            not247 = not247,
            rank = (if (geo) 100_000 else 0) + (if (not247) 10_000 else 0) + (9_999 - q.coerceIn(0, 9_999)),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified T> ResponseBody.decodeList(): List<T> =
        use { TvMateJson.decodeFromStream<List<T>>(it.byteStream()) }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val CHUNK = 2_000
    }
}
