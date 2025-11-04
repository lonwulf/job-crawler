package org.lonwulf.labs.jobcrawler.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.lonwulf.labs.jobcrawler.domain.repository.IJobServiceRepository
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

suspend fun scheduleCrawler(
    service: IJobServiceRepository, intervalHrs: Long = 6, scope: CoroutineScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob()
    )
) {
    var consecutiveFailures = 0
    val maxConsecutiveFailures = 3
    val backoffMinutes = 10L
    scope.launch {
        while (isActive) {
            val startTime = Clock.System.now()
            println("\n" + "=".repeat(60))
            println("Starting crawl run at $startTime")
            println("=".repeat(60))

            try {
                val result = service.runCrawl()
                when (result) {
                    is CrawlerResult.Success -> {
                        consecutiveFailures = 0
                        println("Crawl completed successfully. Found ${result.jobsFound} new jobs.")
                    }

                    is CrawlerResult.Failure -> {
                        consecutiveFailures++
                        println("Crawl failed: ${result.error}")
                        if (consecutiveFailures >= maxConsecutiveFailures) {
                            println("Too many consecutive failures ($consecutiveFailures). Applying backoff...")
                            delay(backoffMinutes.minutes)
                            consecutiveFailures = 0
                        }
                    }
                }

            } catch (ce: CancellationException) {
                println("Crawler cancelled. Shutting down...")
                throw ce
            } catch (e: Exception) {
                consecutiveFailures++
                println("❌ Unexpected error during crawl: ${e.message}")
                e.printStackTrace()

                if (consecutiveFailures >= maxConsecutiveFailures) {
                    println("❌ Too many consecutive failures. Applying backoff...")
                    delay(backoffMinutes.minutes)
                    consecutiveFailures = 0
                }
            }
            val endTime = Clock.System.now()
            val duration = endTime - startTime
            println("\nRun statistics:")
            println("   - Duration: $duration")
            println("   - Consecutive failures: $consecutiveFailures")
            println("Next run in $intervalHrs hours")
            println("=".repeat(60) + "\n")

            delay(intervalHrs.hours)
        }
    }.join()
}