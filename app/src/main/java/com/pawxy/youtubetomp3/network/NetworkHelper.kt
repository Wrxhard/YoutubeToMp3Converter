package com.pawxy.youtubetomp3.network

import com.pawxy.youtubetomp3.model.Video
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object NetworkHelper {

    // Perform OkHttp request
    suspend fun doNetworkCall(
        fileUrl: String,
    ): Video? = suspendCoroutine { continuation ->
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS) // Increase the read timeout as needed
            .build()
        val request = Request.Builder()
            .url(fileUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    continuation.resumeWithException(IOException("Failed to download file: $response"))
                    return
                }

                val responseBody: ResponseBody? = response.body
                responseBody?.let {
                    val video = Video(it.byteStream(), it.contentLength())
                    continuation.resume(video)
                } ?: continuation.resume(null)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }
}
