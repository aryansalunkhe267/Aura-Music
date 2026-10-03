package com.example.network

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val reqBuilder = okhttp3.Request.Builder().url(url)
        headers.forEach { (name, values) ->
            values.forEach { value ->
                reqBuilder.addHeader(name, value)
            }
        }

        val body = dataToSend?.toRequestBody(null)
        reqBuilder.method(httpMethod, body)

        val call = client.newCall(reqBuilder.build())
        val okResponse = call.execute()

        val responseBody = okResponse.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()
        okResponse.headers.names().forEach { name ->
            responseHeaders[name] = okResponse.headers.values(name)
        }

        return Response(
            okResponse.code,
            okResponse.message,
            responseHeaders,
            responseBody,
            okResponse.request.url.toString()
        )
    }
}
