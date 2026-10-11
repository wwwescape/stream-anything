package com.wwwescape.streamanything.data.streams

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A fetched page: where it ended up after redirects, and its text. */
data class HttpPage(val url: HttpUrl, val code: Int, val body: String)

/** The one HTTP client shared by the resolvers and the player, so page fetches and media
 * requests look like the same browser to the host — some hosts tie their generated links to
 * the client that requested the page. */
object StreamHttp {

    /** A regular mobile Chrome UA: several hosts serve a stripped or blocked page to anything
     * that doesn't look like a browser. */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            // Plain HTTP only to home-network addresses, checked on every hop (redirects too).
            .addNetworkInterceptor(HomeNetwork.cleartextGuard)
            .build()
    }

    /** Fetches [url] as text. A 404/410 means the video is gone; any other failure status is
     * reported as unreadable rather than retried. */
    suspend fun fetchPage(url: HttpUrl, referer: String? = null): String =
        get(url, headers = referer?.let { mapOf("Referer" to it) }.orEmpty()).body

    suspend fun get(url: HttpUrl, headers: Map<String, String> = emptyMap()): HttpPage =
        execute(url, headers, body = null)

    suspend fun postForm(url: HttpUrl, form: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpPage =
        execute(url, headers, FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build())

    suspend fun postJson(url: HttpUrl, json: String, headers: Map<String, String> = emptyMap()): HttpPage =
        execute(url, headers, json.toRequestBody(JSON))

    fun url(value: String): HttpUrl = value.toHttpUrl()

    private suspend fun execute(url: HttpUrl, headers: Map<String, String>, body: RequestBody?): HttpPage =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .apply {
                    headers.forEach { (name, value) -> header(name, value) }
                    if (body != null) post(body)
                }
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw StreamError.Network(e)
            }
            response.use {
                when {
                    it.code == 404 || it.code == 410 -> throw StreamError.VideoUnavailable()
                    !it.isSuccessful -> throw StreamError.Unreadable("HTTP ${it.code}")
                    else -> try {
                        HttpPage(it.request.url, it.code, it.body?.string().orEmpty())
                    } catch (e: IOException) {
                        throw StreamError.Network(e)
                    }
                }
            }
        }
}

/** `https://host/` — the Referer most hosts expect on their media requests. */
fun HttpUrl.rootUrl(): String = "${scheme}://${host}/"

/** `https://host` — for Origin headers. */
fun HttpUrl.origin(): String = "${scheme}://${host}"
