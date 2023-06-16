package com.pawxy.youtubetomp3.network
import com.pawxy.youtubetomp3.model.Video
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException

object NetworkHelper {

    //perform Okhttp request
    fun doNetworkCall(fileUrl: String): Video? {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(fileUrl)
            .build()

        val response: Response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Failed to download file: $response")
        }

        val responseBody: ResponseBody? = response.body
        responseBody?.let {
            return Video(it.byteStream(),it.contentLength())
        }
        return null
    }
}
