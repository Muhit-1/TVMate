package com.tvmate.shared

import android.content.Context
import androidx.room.Room
import com.tvmate.shared.data.CatalogRepository
import com.tvmate.shared.data.Settings
import com.tvmate.shared.data.UserRepository
import com.tvmate.shared.data.db.TvMateDatabase
import com.tvmate.shared.data.net.IptvApi
import com.tvmate.shared.data.net.RadioBrowserClient
import com.tvmate.shared.data.net.buildRetrofit
import com.tvmate.shared.data.net.newHttpClient
import retrofit2.create

/** Hand-wired app singletons. No DI framework needed at this size. */
class TvMateGraph private constructor(context: Context) {
    private val app = context.applicationContext
    val httpClient = newHttpClient()
    val settings = Settings(app)
    private val db = Room.databaseBuilder(app, TvMateDatabase::class.java, "tvmate.db").build()
    val catalog = CatalogRepository(db, buildRetrofit("https://iptv-org.github.io/api/", httpClient).create<IptvApi>(), settings)
    val user = UserRepository(db.user())
    val radio = RadioBrowserClient(httpClient)

    companion object {
        @Volatile private var instance: TvMateGraph? = null
        fun get(context: Context): TvMateGraph =
            instance ?: synchronized(this) { instance ?: TvMateGraph(context).also { instance = it } }
    }
}
