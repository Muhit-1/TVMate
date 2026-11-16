package com.tvmate.shared.data.net

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

val TvMateJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

const val USER_AGENT = "TVMate/0.1 (Android)"

/** The big iptv-org files are streamed so they are never held as one huge string. */
interface IptvApi {
    @Streaming @GET("channels.json") suspend fun channels(): ResponseBody
    @Streaming @GET("feeds.json") suspend fun feeds(): ResponseBody
    @Streaming @GET("streams.json") suspend fun streams(): ResponseBody
    @Streaming @GET("logos.json") suspend fun logos(): ResponseBody
    @Streaming @GET("blocklist.json") suspend fun blocklist(): ResponseBody
    @GET("countries.json") suspend fun countries(): List<CountryDto>
    @GET("categories.json") suspend fun categories(): List<CategoryDto>
    @GET("languages.json") suspend fun languages(): List<LanguageDto>
}

interface RadioBrowserApi {
    @GET("json/stats") suspend fun stats(): ResponseBody

    @GET("json/stations/search")
    suspend fun search(
        @Query("name") name: String = "",
        @Query("countrycode") countryCode: String = "",
        @Query("language") language: String = "",
        @Query("tag") tag: String = "",
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): List<StationDto>

    @GET("json/tags")
    suspend fun tags(
        @Query("order") order: String = "stationcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("limit") limit: Int = 60,
    ): List<RadioTagDto>

    @GET("json/countries")
    suspend fun countries(
        @Query("order") order: String = "stationcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true,
    ): List<RadioCountryDto>
}

fun newHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
    }
    .build()

fun buildRetrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(TvMateJson.asConverterFactory("application/json".toMediaType()))
    .build()
