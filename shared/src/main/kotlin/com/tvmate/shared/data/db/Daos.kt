package com.tvmate.shared.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query(
        """
        SELECT * FROM channels
        WHERE streamCount > 0
          AND id NOT IN (SELECT channelId FROM hidden_channels)
          AND (:showNsfw = 1 OR isNsfw = 0)
          AND (:showGeo = 1 OR geoBlocked = 0)
          AND (:showNot247 = 1 OR not247 = 0)
          AND (:query = '' OR name LIKE '%' || :query || '%')
          AND (:country = '' OR country = :country)
          AND (:category = '' OR (',' || categories || ',') LIKE '%,' || :category || ',%')
          AND (:language = '' OR (',' || languages || ',') LIKE '%,' || :language || ',%')
        ORDER BY CASE WHEN name GLOB '[A-Za-z0-9]*' THEN 0 ELSE 1 END, name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    fun channels(
        query: String,
        country: String,
        category: String,
        language: String,
        showGeo: Boolean,
        showNot247: Boolean,
        showNsfw: Boolean,
        limit: Int,
    ): Flow<List<ChannelEntity>>

    @Query("SELECT COUNT(*) FROM channels") fun channelCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM channels") suspend fun channelCountNow(): Int
    @Query("SELECT * FROM channels WHERE id = :id") suspend fun channel(id: String): ChannelEntity?
    @Query("SELECT * FROM streams WHERE channelId = :channelId ORDER BY rank") suspend fun streams(channelId: String): List<StreamEntity>

    @Query("SELECT * FROM countries ORDER BY name") fun countries(): Flow<List<CountryEntity>>
    @Query("SELECT * FROM categories ORDER BY name") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM languages ORDER BY name") fun languages(): Flow<List<LanguageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChannels(items: List<ChannelEntity>)
    @Insert suspend fun insertStreams(items: List<StreamEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCountries(items: List<CountryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLanguages(items: List<LanguageEntity>)

    @Query("DELETE FROM channels") suspend fun clearChannels()
    @Query("DELETE FROM streams") suspend fun clearStreams()
    @Query("DELETE FROM countries") suspend fun clearCountries()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM languages") suspend fun clearLanguages()

    @Transaction
    suspend fun clearAll() {
        clearChannels(); clearStreams(); clearCountries(); clearCategories(); clearLanguages()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun hide(item: HiddenChannelEntity)
    @Query("DELETE FROM hidden_channels WHERE channelId = :channelId") suspend fun unhide(channelId: String)
    @Query("DELETE FROM hidden_channels") suspend fun unhideAll()
    @Query("SELECT COUNT(*) FROM hidden_channels") fun hiddenCount(): Flow<Int>
}

@Dao
interface UserDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC") fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE `key` = :key)") fun isFavorite(key: String): Flow<Boolean>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(item: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE `key` = :key") suspend fun removeFavorite(key: String)

    @Query("SELECT * FROM recents ORDER BY playedAt DESC LIMIT 30") fun recents(): Flow<List<RecentEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addRecent(item: RecentEntity)
    @Query("DELETE FROM recents WHERE `key` NOT IN (SELECT `key` FROM recents ORDER BY playedAt DESC LIMIT 30)")
    suspend fun trimRecents()
}

@Database(
    entities = [
        ChannelEntity::class, StreamEntity::class, CountryEntity::class, CategoryEntity::class,
        LanguageEntity::class, FavoriteEntity::class, RecentEntity::class, HiddenChannelEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TvMateDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun user(): UserDao
}
