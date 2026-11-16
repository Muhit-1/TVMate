package com.tvmate.tv

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache

class TvApp : Application() {
    lateinit var host: TvHost
        private set

    override fun onCreate() {
        super.onCreate()
        // TVs can have as little as 2 GB of RAM: keep the logo cache small.
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).memoryCache { MemoryCache.Builder().maxSizePercent(ctx, 0.06).build() }.build()
        }
        host = TvHost(this).also { it.start() }
    }
}
