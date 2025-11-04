package org.lonwulf.labs.jobcrawler.domain.repository

import org.lonwulf.labs.jobcrawler.utils.CrawlerResult

interface IJobServiceRepository {
    suspend fun runCrawl(): CrawlerResult
    fun performPeriodicCleanup()
    fun close()
}