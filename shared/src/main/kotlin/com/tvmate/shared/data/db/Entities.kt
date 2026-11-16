package com.tvmate.shared.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels", indices = [Index("country"), Index("name")])
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val country: String?,
    /** comma separated category ids */
    val categories: String,
    /** comma separated ISO 639-3 language codes, from the channel's main feed */
    val languages: String,
    val isNsfw: Boolean,
    val logoUrl: String?,
    /** true when every stream of the channel is labelled Geo-blocked */
    val geoBlocked: Boolean,
    /** true when every stream of the channel is labelled Not 24/7 */
    val not247: Boolean,
    val streamCount: Int,
)

@Entity(tableName = "streams", indices = [Index("channelId")])
data class StreamEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val channelId: String,
    val url: String,
    val title: String?,
    val quality: String?,
    val referrer: String?,
    val userAgent: String?,
    val geoBlocked: Boolean,
    val not247: Boolean,
    /** lower is better: tried first */
    val rank: Int,
)

@Entity(tableName = "countries") data class CountryEntity(@PrimaryKey val code: String, val name: String)
@Entity(tableName = "categories") data class CategoryEntity(@PrimaryKey val id: String, val name: String)
@Entity(tableName = "languages") data class LanguageEntity(@PrimaryKey val code: String, val name: String)

/** kind is "tv" or "radio". key is "kind:itemId". */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val key: String,
    val kind: String,
    val itemId: String,
    val name: String,
    val subtitle: String?,
    val logoUrl: String?,
    /** radio only: station stream url (TV streams are looked up by channel) */
    val streamUrl: String?,
    val addedAt: Long,
)

@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val key: String,
    val kind: String,
    val itemId: String,
    val name: String,
    val subtitle: String?,
    val logoUrl: String?,
    val streamUrl: String?,
    val playedAt: Long,
)

/** Channels the user chose to hide. */
@Entity(tableName = "hidden_channels")
data class HiddenChannelEntity(@PrimaryKey val channelId: String)
