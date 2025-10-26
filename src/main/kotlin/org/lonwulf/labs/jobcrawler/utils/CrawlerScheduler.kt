package org.lonwulf.labs.jobcrawler.utils

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lonwulf.labs.jobcrawler.runCrawler
import kotlin.time.Duration.Companion.hours

fun scheduleCrawler(intervalHrs: Long = 6) = GlobalScope.launch {
    while (true) {
        try {
            runCrawler()
        } catch (e: Exception) {
            println("⚠️ Error during crawl: ${e.message}")
        }
        println("⏳ Next run in $intervalHrs hours...")
        delay(intervalHrs.hours)
    }
}