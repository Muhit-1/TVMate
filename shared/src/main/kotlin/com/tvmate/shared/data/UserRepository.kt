package com.tvmate.shared.data

import com.tvmate.shared.data.db.FavoriteEntity
import com.tvmate.shared.data.db.RecentEntity
import com.tvmate.shared.data.db.UserDao
import kotlinx.coroutines.flow.Flow

/** What the user can play: a TV channel or a radio station. */
data class MediaRef(
    val kind: Kind,
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val logoUrl: String? = null,
    /** radio: the station's stream url. TV: null (streams are looked up by channel id). */
    val streamUrl: String? = null,
) {
    enum class Kind(val key: String) { TV("tv"), RADIO("radio") }
    val key: String get() = "${kind.key}:$id"
}

class UserRepository(private val dao: UserDao) {
    fun favorites(): Flow<List<FavoriteEntity>> = dao.favorites()
    fun recents(): Flow<List<RecentEntity>> = dao.recents()
    fun isFavorite(ref: MediaRef): Flow<Boolean> = dao.isFavorite(ref.key)

    suspend fun setFavorite(ref: MediaRef, favorite: Boolean) {
        if (favorite) {
            dao.addFavorite(
                FavoriteEntity(ref.key, ref.kind.key, ref.id, ref.name, ref.subtitle, ref.logoUrl, ref.streamUrl, System.currentTimeMillis()),
            )
        } else {
            dao.removeFavorite(ref.key)
        }
    }

    suspend fun markPlayed(ref: MediaRef) {
        dao.addRecent(RecentEntity(ref.key, ref.kind.key, ref.id, ref.name, ref.subtitle, ref.logoUrl, ref.streamUrl, System.currentTimeMillis()))
        dao.trimRecents()
    }
}
