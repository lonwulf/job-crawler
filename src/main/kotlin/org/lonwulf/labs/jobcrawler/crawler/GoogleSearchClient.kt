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

class HttpException(val code: Int, message: String) : Exception(message)

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
        for (page in 0 until pages) {
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

                val body = response.body?.string() ?: throw IllegalStateException("Empty response body")
                val parsed = try {
                    adapter.fromJson(body)
                } catch (e: Exception) {
                    null
                }
                val items = parsed?.items ?: return@withContext emptyList()
                for (item in items) {
                    if (results.size >= maxResults) break
                    val link = item.link
                    val title = item.title
                    if (link == null || title == null) continue
                    val snippet = item.snippet ?: ""
                    val metaTags = item.pageMap?.metaTags?.firstOrNull()
                    val dateIso = metaTags?.get("article:published_time")
                        ?: metaTags?.get("date")
                        ?: metaTags?.get("datePublished")
                        ?: metaTags?.get("publishdate")
                        ?: DateUtils.parseRelativeDateToIso(snippet)
                        ?: DateUtils.parseIsoLikeFromString(snippet)
                    val company = extractCompany(item)
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
        return@withContext results
    }

    private fun extractCompany(item: SearchDTO.Item): String? {
        // Strategy 1: Try displayLink first (most reliable for company identification)
        extractFromDisplayLink(item.displayLink)?.let { return it }

        // Strategy 2: Try htmlTitle (often contains company name in structured format)
        extractFromHtmlTitle(item.htmlTitle)?.let { return it }

        // Strategy 3: Try snippet (least reliable, but sometimes contains company context)
        extractFromSnippet(item.snippet)?.let { return it }

        return null
    }

    private fun extractFromDisplayLink(displayLink: String?): String? {
        if (displayLink.isNullOrBlank()) return null

        // Extract company from domain patterns
        val patterns = listOf(
            // corporate.company.com -> company
            Regex("""corporate\.([a-zA-Z0-9\-]+)\."""),
            // jobs.company.com -> company
            Regex("""jobs\.([a-zA-Z0-9\-]+)\."""),
            // careers.company.com -> company
            Regex("""careers\.([a-zA-Z0-9\-]+)\."""),
            // company.com -> company (fallback)
            Regex("""(?:www\.)?([a-zA-Z0-9\-]+)\.(com|org|io|co|net)""")
        )

        for (pattern in patterns) {
            pattern.find(displayLink)?.groups?.get(1)?.value?.let { match ->
                val cleaned = cleanCompanyName(match)
                if (isValidCompany(cleaned)) {
                    return cleaned
                }
            }
        }

        return null
    }

    private fun extractFromHtmlTitle(htmlTitle: String?): String? {
        if (htmlTitle.isNullOrBlank()) return null

        // Remove HTML tags and extract company
        val cleanTitle = htmlTitle.replace(Regex("""<[^>]*>"""), "")

        val patterns = listOf(
            // "Job Title | Company" pattern
            Regex("""\|\s*([A-Z][A-Za-z0-9\-&\.\s]+)$"""),
            // "Company Careers" or "Company Jobs"
            Regex("""^([A-Z][A-Za-z0-9\-&\.\s]+?)\s+(?:Careers|Jobs|Career)"""),
            // "at Company" in title
            Regex("""\bat\s+([A-Z][A-Za-z0-9\-&\.\s]{2,30})(?:\s|$)"""),
            // Company name followed by special characters
            Regex("""([A-Z][A-Za-z0-9\-&]+)\s*[·•\-–—]""")
        )

        for (pattern in patterns) {
            pattern.find(cleanTitle)?.groups?.get(1)?.value?.let { match ->
                val cleaned = cleanCompanyName(match)
                if (isValidCompany(cleaned)) {
                    return cleaned
                }
            }
        }

        return null
    }

    private fun extractFromSnippet(snippet: String?): String? {
        if (snippet.isNullOrBlank()) return null

        // Remove HTML tags from snippet
        val cleanSnippet = snippet.replace(Regex("""<[^>]*>"""), "")

        val patterns = listOf(
            // "at Company" with context boundaries
            Regex("""\bat\s+([A-Z][A-Za-z0-9\-&\.\s]{2,30})(?=\s|\.|,|;|$|and|in|for)"""),
            // Company name at start of meaningful phrase
            Regex("""^([A-Z][A-Za-z0-9\-&\.\s]{2,30})\.\s*(?:On-site|Remote|Hybrid|Contractor)"""),
            // "from Company" pattern
            Regex("""\bfrom\s+([A-Z][A-Za-z0-9\-&\.\s]{2,30})(?=\s|\.|,|;|$)"""),
            // Hiring context
            Regex("""hiring\s+(?:at\s+)?([A-Z][A-Za-z0-9\-&\.\s]{2,20})""")
        )

        for (pattern in patterns) {
            pattern.find(cleanSnippet)?.groups?.get(1)?.value?.let { match ->
                val cleaned = cleanCompanyName(match)
                if (isValidCompany(cleaned)) {
                    return cleaned
                }
            }
        }

        return null
    }

    private fun isValidCompany(company: String): Boolean {
        if (company.length !in 2..40) return false
        if (!company.any { it.isLetter() }) return false
        // Basic check - not all uppercase acronyms (unless it's 2-4 chars)
        if (company.all { it.isUpperCase() } && company.length > 4) return false
        return true
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
}