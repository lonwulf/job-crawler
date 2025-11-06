package org.lonwulf.labs.jobcrawler.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.lonwulf.labs.jobcrawler.crawler.GoogleSearchClient
import org.lonwulf.labs.jobcrawler.data.db.SeenStore
import org.lonwulf.labs.jobcrawler.domain.model.CrawlerConfig
import org.lonwulf.labs.jobcrawler.domain.model.JobResult
import org.lonwulf.labs.jobcrawler.domain.repository.IJobServiceRepository
import org.lonwulf.labs.jobcrawler.utils.CSVWriter
import org.lonwulf.labs.jobcrawler.utils.CrawlerResult
import org.lonwulf.labs.jobcrawler.utils.DateUtils
import kotlin.math.ceil

class JobServiceRepositoryImpl(
    private val config: CrawlerConfig,
    private val store: SeenStore,
    private val searchClient: GoogleSearchClient
) : IJobServiceRepository {

    private var lastCleanupDate = Clock.System.now()


    override suspend fun runCrawl(): CrawlerResult = withContext(Dispatchers.IO) {
        try {
            performPeriodicCleanup()
            val allResults = mutableListOf<JobResult>()
            val perPairRequests =
                ceil(config.resultsPerQueryTotal.toDouble() / config.resultsPerRequest.toDouble()).toInt()
            val estimated = config.titles.size * config.locations.size * perPairRequests
            val allowedRequests = minOf(config.maxRequestsPerRun, estimated)
            println("Estimated requests: $estimated | allowed this run: $allowedRequests")

            val requestCounter = RequestCounter(allowedRequests)
            coroutineScope {
                for (title in config.titles) {
                    for (location in config.locations) {
                        if (!requestCounter.canMakeRequests()) break

                        launch(Dispatchers.IO + CoroutineExceptionHandler { _, exception ->
                            println("Error processing '$title $location': ${exception.message}")
                        }) {
                            processQuery(
                                title = title,
                                location = location,
                                allResults = allResults,
                                requestCounter = requestCounter
                            )
                        }
                    }
                }
            }
            saveResults(allResults)
            CrawlerResult.Success(allResults.size)
        } catch (ce: CancellationException) {
            CrawlerResult.Failure(ce.message ?: "Cancelled unexpectedly", ce)
        } catch (ex: Exception) {
            CrawlerResult.Failure(ex.message ?: "Unknown error", ex)
        }
    }

    override fun performPeriodicCleanup() {
        val now = Clock.System.now()
        val daysSinceLastCleanup = (now - lastCleanupDate).inWholeDays
        if (daysSinceLastCleanup >= 1) {
            println("Performing daily db cleanup")
            store.cleanupOldEntries(daysToKeep = 30)
            lastCleanupDate = now
        }
    }

    private suspend fun processQuery(
        title: String,
        location: String,
        allResults: MutableList<JobResult>,
        requestCounter: RequestCounter
    ) {
        val query = "$title $location"
        val maxPages = ceil(config.resultsPerQueryTotal.toDouble() / config.resultsPerRequest.toDouble()).toInt()
        println("---> Searching '$query' (up to $maxPages pages)")

        val results =
            searchClient.searchJobs(query = query, maxResults = config.resultsPerQueryTotal, maxPages = maxPages)

        synchronized<Unit>(allResults) {
            requestCounter.increment(maxPages)
            processResults(results, allResults)
        }
    }

    private fun processResults(results: List<JobResult>, allResults: MutableList<JobResult>) {
        for (result in results) {
            try {
                if (store.isSeen(result.urlLink)) continue
                val isRecent = result.datePosted == null || DateUtils.isWithinDays(result.datePosted, config.maxAgeDays)
                val isRelevant = result.isRelevantJob()
                val experienceMatch =
                    result.matchesExperience(1, 6) || result.snippet?.contains("experience", true) != false
                if (!isRecent || !isRelevant || !experienceMatch) continue

                store.markSeen(result.urlLink, result.title, result.company, result.datePosted)
                allResults.add(result)
                println("[x] Title: ${result.title} Company: ${result.company ?: "Unknown"}")
            } catch (e: Exception) {
                println("Error processing result ${result.urlLink}: ${e.message}")
            }
        }
    }

    private fun saveResults(results: List<JobResult>) {
        if (results.isEmpty()) {
            println("${Clock.System.now()}: No new matching jobs found on this run")
        } else {
            val sortedResults = results.sortedBy { it.datePosted ?: "" }
            CSVWriter.appendJobs(sortedResults, "${config.dataDir}/")
            println("${Clock.System.now()}: Wrote ${results.size} new jobs to CSV(s).")
        }

        val totalSeen = store.getSeenCount()
        println("Total jobs in database: $totalSeen")
        println("Job crawling completed successfully")
    }

    override fun close() {
        store.close()
    }

    inner class RequestCounter(private val maxRequests: Int) {
        @Volatile
        private var requestsMade = 0

        fun canMakeRequests(): Boolean = requestsMade < maxRequests

        @Synchronized
        fun increment(count: Int) {
            requestsMade += count
        }
    }
}