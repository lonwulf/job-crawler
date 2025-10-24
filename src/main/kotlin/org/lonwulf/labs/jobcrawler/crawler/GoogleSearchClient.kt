package org.lonwulf.labs.jobcrawler.crawler

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.lonwulf.labs.jobcrawler.data.response.SearchDTO
import org.lonwulf.labs.jobcrawler.utils.DateUtils
import java.net.URLEncoder
import kotlin.math.min

class GoogleSearchClient(
    private val apiKey: String,
    private val cx: String,
    private val resultsPerRequest: Int = 10,
    private val delayMsBetweenRequests: Long = 500
) {

    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(SearchDTO::class.java)
    private val baseUrl = "https://www.googleapis.com/customsearch/v1"

    fun searchJobs(query: String, maxResults: Int = 15, maxPages: Int = 2): List<JobResult> {
        val results = mutableListOf<JobResult>()
        val pages = min(maxPages, (maxResults + resultsPerRequest - 1) / resultsPerRequest)
        var start = 1
        for (p in 0 until pages) {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl?q=$encodedQuery&cx=$cx&key=$apiKey&num=$resultsPerRequest&start=$start"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    start += resultsPerRequest
                    Thread.sleep(delayMsBetweenRequests)
                    continue
                }

                val body = response.body?.string() ?: ""
                val parsed = try {
                    adapter.fromJson(body)
                } catch (e: Exception) {
                    null
                }
                val items = parsed?.items ?: emptyList()
                for (item in items) {
                    val link = item.link ?: continue
                    val title = item.title ?: ""
                    val snippet = item.snippet ?: ""
                    val dateIso = item.pageMap?.metaTags?.firstOrNull()?.get("article:published_time")
                        ?: item.pageMap?.metaTags?.firstOrNull()?.get("date")
                        ?: DateUtils.parseRelativeDateToIso(snippet)
                        ?: DateUtils.parseIsoLikeFromString(snippet)
                    val company = extractCompany(snippet)
                    results.add(
                        JobResult(
                            title = title,
                            urlLink = link,
                            company = company,
                            snippet = snippet,
                            datePosted = dateIso,
                            source = "google-cse",
                            locationHint = null
                        )
                    )
                    if (results.size >= maxResults) break
                }
            }
            start += resultsPerRequest
            Thread.sleep(delayMsBetweenRequests)
            if (results.size >= maxResults) break
        }
        return results
    }

    private fun extractCompany(snippet: String?): String? {
        if (snippet.isNullOrBlank()) return null
        val lower = snippet.lowercase()
        Regex("""\bat\s+([A-Za-z0-9&\-\.\s]+)""").find(lower)?.groups?.get(1)?.value?.let {
            return it.trim().split(".", "—", " - ")[0].trim()
                .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
        }
        Regex("""\bby\s+([A-Za-z0-9&\-\.\s]+)""").find(lower)?.groups?.get(1)?.value?.let {
            return it.trim().split(".", "—", " - ")[0].trim()
                .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
        }
        return null
    }

}