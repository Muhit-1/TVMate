package com.tvmate.shared.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tvmate.shared.link.Link
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tvmate_settings")

data class ChannelFilters(
    val showGeoBlocked: Boolean = false,
    val showNot247: Boolean = true,
    /** "Hide adult channels" is on by default, so showing them is opt-in. */
    val showNsfw: Boolean = false,
)

/** The TV a phone is paired with. */
data class PairedTv(val name: String, val host: String, val port: Int, val token: String)

class Settings(private val context: Context) {
    private object Keys {
        val showGeo = booleanPreferencesKey("show_geo_blocked")
        val showNot247 = booleanPreferencesKey("show_not_247")
        val showNsfw = booleanPreferencesKey("show_nsfw")
        val lastRefresh = longPreferencesKey("catalog_last_refresh")
        val tvName = stringPreferencesKey("paired_tv_name")
        val tvHost = stringPreferencesKey("paired_tv_host")
        val tvPort = intPreferencesKey("paired_tv_port")
        val tvToken = stringPreferencesKey("paired_tv_token")
        val phoneTokens = stringSetPreferencesKey("phone_tokens")
        val atvPaired = booleanPreferencesKey("atv_remote_paired")
    }

    val filters: Flow<ChannelFilters> = context.dataStore.data.map { p ->
        ChannelFilters(
            showGeoBlocked = p[Keys.showGeo] ?: false,
            showNot247 = p[Keys.showNot247] ?: true,
            showNsfw = p[Keys.showNsfw] ?: false,
        )
    }

    suspend fun setShowGeoBlocked(value: Boolean) = edit { it[Keys.showGeo] = value }
    suspend fun setShowNot247(value: Boolean) = edit { it[Keys.showNot247] = value }
    suspend fun setShowNsfw(value: Boolean) = edit { it[Keys.showNsfw] = value }

    // ---- phone side: the TV this phone is paired with ----
    val pairedTv: Flow<PairedTv?> = context.dataStore.data.map { p ->
        val host = p[Keys.tvHost]
        val token = p[Keys.tvToken]
        if (host == null || token == null) null
        else PairedTv(p[Keys.tvName] ?: "TV", host, p[Keys.tvPort] ?: Link.DEFAULT_PORT, token)
    }

    suspend fun savePairedTv(tv: PairedTv) = edit {
        it[Keys.tvName] = tv.name; it[Keys.tvHost] = tv.host; it[Keys.tvPort] = tv.port; it[Keys.tvToken] = tv.token
    }

    suspend fun forgetPairedTv() = edit {
        it.remove(Keys.tvName); it.remove(Keys.tvHost); it.remove(Keys.tvPort); it.remove(Keys.tvToken); it.remove(Keys.atvPaired)
    }

    /** Whether this phone completed Android TV Remote v2 pairing with the paired TV. */
    val atvPaired: Flow<Boolean> = context.dataStore.data.map { it[Keys.atvPaired] ?: false }
    suspend fun setAtvPaired(value: Boolean) = edit { it[Keys.atvPaired] = value }

    // ---- TV side: phones allowed to control this TV ----
    val phoneTokens: Flow<Set<String>> = context.dataStore.data.map { it[Keys.phoneTokens] ?: emptySet() }
    suspend fun addPhoneToken(token: String) = edit { it[Keys.phoneTokens] = (it[Keys.phoneTokens] ?: emptySet()) + token }
    suspend fun clearPhoneTokens() = edit { it.remove(Keys.phoneTokens) }

    suspend fun lastRefresh(): Long = context.dataStore.data.first()[Keys.lastRefresh] ?: 0L
    suspend fun markRefreshed(timeMillis: Long) = edit { it[Keys.lastRefresh] = timeMillis }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
