package com.example.network

import com.example.network.api.JioSaavnApiService
import com.example.network.api.LrcLibApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Centralized networking client providing configured Retrofit services
 * for JioSaavn Unofficial API and LRCLIB Synced Lyrics API.
 */
object NetworkClient {

    private const val JIOSAAVN_BASE_URL = "https://saavn.dev/api/"
    private const val LRCLIB_BASE_URL = "https://lrclib.net/api/"

    // Strict User-Agent header required by LRCLIB to prevent rate-limit bans
    const val LRCLIB_USER_AGENT = "AuraMusic v1.0 (https://github.com/aryansalunkhe267/AuraMusic)"
    const val APP_USER_AGENT = "AuraMusic/1.0 (Android; Offline-Online Hybrid Music Player)"

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val commonOkHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", APP_USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            })
            .build()
    }

    private val lrcLibOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", LRCLIB_USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            })
            .build()
    }

    val jioSaavnApi: JioSaavnApiService by lazy {
        Retrofit.Builder()
            .baseUrl(JIOSAAVN_BASE_URL)
            .client(commonOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JioSaavnApiService::class.java)
    }

    val lrcLibApi: LrcLibApiService by lazy {
        Retrofit.Builder()
            .baseUrl(LRCLIB_BASE_URL)
            .client(lrcLibOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LrcLibApiService::class.java)
    }
}
