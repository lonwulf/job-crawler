package org.lonwulf.labs.jobcrawler.crawler

import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lonwulf.labs.jobcrawler.data.response.SearchDTO
import org.lonwulf.labs.jobcrawler.domain.model.JobResult
import org.lonwulf.labs.jobcrawler.utils.DateUtils
import java.net.URLEncoder
import kotlin.math.min

class GoogleSearchClient(
    private val apiKey: String,
    private val cx: String,
    private val resultsPerRequest: Int = 10,
    private val delayMsBetweenRequests: Long = 500,
    private val client: OkHttpClient,
    private val adapter: JsonAdapter<SearchDTO>
) {
    private val baseUrl = "https://www.googleapis.com/customsearch/v1"

    suspend fun searchJobs(query: String, maxResults: Int = 15, maxPages: Int = 2): List<JobResult> = withContext(
        Dispatchers.IO
    ) {
        val results = mutableListOf<JobResult>()
        val pages = min(maxPages, (maxResults + resultsPerRequest - 1) / resultsPerRequest)
        var start = 1
        repeat(pages) { page ->
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl?q=$encodedQuery&cx=$cx&key=$apiKey&num=$resultsPerRequest&start=$start"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
//                    start += resultsPerRequest
//                    delay(delayMsBetweenRequests)
//                    continue
                    throw HttpException(response.code, "HTTP ${response.code}: ${response.message}")

                }

                val body = response.body?.string() ?: ""
                val parsed = try {
                    adapter.fromJson(body)
                } catch (e: Exception) {
                    null
                }
                val items = parsed?.items ?: emptyList()
                for (item in items) {
                    if (results.size >= maxResults) break
                    val link = item.link
                    val title = item.title
                    val snippet = item.snippet ?: ""
                    val metaTags = item.pageMap?.metaTags?.firstOrNull()
                    val dateIso = metaTags?.get("article:published_time")
                        ?: metaTags?.get("date")
                        ?: metaTags?.get("datePublished")
                        ?: metaTags?.get("publishdate")
                        ?: DateUtils.parseRelativeDateToIso(snippet)
                        ?: DateUtils.parseIsoLikeFromString(snippet)
                    val company = extractCompany(snippet)
                    results += JobResult(
                        title = title,
                        urlLink = link,
                        company = company,
                        snippet = snippet,
                        datePosted = dateIso,
                        source = "google-cse",
                        locationHint = null
                    )
                }
            }
            if (results.size >= maxResults) return@withContext results
            start += resultsPerRequest
            if (page < pages - 1) delay(delayMsBetweenRequests)
        }
        results
    }

    private fun extractCompany(snippet: String?): String? {
        if (snippet.isNullOrBlank()) return null
        val lower = snippet.lowercase()
        // Try "at Company" pattern
        Regex("""\bat\s+([A-Za-z0-9&\-\.\s]+)""").find(lower)?.groups?.get(1)?.value?.let { match ->
            return cleanCompanyName(match)
        }

        // Try "by Company" pattern
        Regex("""\bby\s+([A-Za-z0-9&\-\.\s]+)""").find(lower)?.groups?.get(1)?.value?.let { match ->
            return cleanCompanyName(match)
        }
        return null
    }

    /**
     * Clean and format company name
     */
    private fun cleanCompanyName(companyName: String): String {
        return companyName.trim()
            .split(".", "—", " - ")[0]
            .trim()
            .replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase() else c.toString()
            }
    }

    class HttpException(val code: Int, message: String) : Exception(message)

}