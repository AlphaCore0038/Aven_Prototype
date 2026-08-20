package com.aven.ai.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Single shared HTTP client for all remote AI calls. */
object HttpClientFactory {

    fun create(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
}