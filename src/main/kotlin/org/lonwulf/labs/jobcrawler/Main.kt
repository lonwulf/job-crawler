package org.lonwulf.labs.jobcrawler

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.lonwulf.labs.jobcrawler.crawler.GoogleSearchClient
import org.lonwulf.labs.jobcrawler.crawler.JobResult
import org.lonwulf.labs.jobcrawler.data.db.SeenStore
import org.lonwulf.labs.jobcrawler.utils.CSVWriter
import org.lonwulf.labs.jobcrawler.utils.DateUtils
import org.lonwulf.labs.jobcrawler.utils.scheduleCrawler
import kotlin.math.ceil

fun runCrawler() = runBlocking {
    val apiKey = System.getenv("API_KEY")
        ?.takeIf { it.isNotBlank() }
        ?: error("❌ Environment variable API_KEY is missing or empty.")

    val cx = System.getenv("CX")
        ?.takeIf { it.isNotBlank() }
        ?: error("❌ Environment variable CX is missing or empty.")


    val titles = listOf(
        "Android Engineer",
//        "Android Developer" , "Android software developer",
        "Senior Android Engineer"
        //        , "Senior Android Developer", "Senior Android Software developer",
//        "Software engineer - Android", "Software developer - Android"
    )

    val locations = listOf(
        "Kenya onsite", "Kenya hybrid",
//        "Worldwide", "Worldwide remote",
        "Remote",
//        "Anywhere remote",
        "EMEA"
    )

    val resultsPerQueryTotal = 15
    val resultsPerRequest = 10
    val maxRequestsPerRun = 60
    val delayMs = 1000L
    val maxAgeDays = 30L

    val searchClient = GoogleSearchClient(apiKey, cx, resultsPerRequest, delayMs)
    val store = SeenStore("data/seen_job_ads.db")
    val allResults = mutableListOf<JobResult>()

    // compute estimated requests & cap
    val perPairRequests = ceil(resultsPerRequest.toDouble() / resultsPerRequest.toDouble()).toInt()
    val estimated = titles.size * locations.size * perPairRequests
    val allowedRequests = minOf(maxRequestsPerRun, estimated)
    var requestsMade = 0

    println("Estimated requests: $estimated | allowed this run: $allowedRequests")

    coroutineScope {
        for (title in titles) {
            for (location in locations) {
                if (requestsMade >= allowedRequests) break
                val query = "$title $location"

                launch {
                    val maxPages = ceil(resultsPerQueryTotal.toDouble() / resultsPerRequest.toDouble()).toInt()
                    println("➡--> Searching '$query' (up to $maxPages pages)")

                    val results = searchClient.searchJobs(query, maxResults = resultsPerQueryTotal, maxPages = maxPages)

                    synchronized(allResults) {
                        requestsMade += maxPages
                        for (result in results) {
                            if (store.isSeen(result.urlLink)) continue
                            val isRecent = result.datePosted == null || DateUtils.isWithinDays(result.datePosted, maxAgeDays)
                            val isRelevant = result.isRelevantAndroidJob()
                            val experienceMatch = result.matchesExperience(1, 6) || result.snippet?.contains("experience", true) != false

                            if (!isRecent || !isRelevant || !experienceMatch) continue

                            store.markSeen(result.urlLink, result.title, result.company, result.datePosted)
                            allResults.add(result)
                            println("[x] New job: ${result.title} (${result.company ?: "Unknown"})")
                        }
                    }
                }
            }
        }
    }

    if (allResults.isEmpty()) {
        println("${Clock.System.now()}: No new matching jobs found this run.")
    } else {
        val sortedResults = allResults.sortedBy { it.datePosted ?: "" }
        CSVWriter.appendJobs(sortedResults, "data/")
        println("${Clock.System.now()}: Wrote ${allResults.size} new jobs to CSV(s).")
    }

    println("Job crawling completed successfully [x]")
}

fun main() = runBlocking {
    println("🕒 Starting scheduler at ${Clock.System.now()}")
    val intervalHours = System.getenv("INTERVAL_HOURS")
        ?.takeIf { it.isNotBlank() }
        ?: error("❌ Environment variable INTERVAL_HOURS is missing or empty.")
    scheduleCrawler(intervalHours.toLong()) // run every 6 hours
    delay(Long.MAX_VALUE) // keep alive
}