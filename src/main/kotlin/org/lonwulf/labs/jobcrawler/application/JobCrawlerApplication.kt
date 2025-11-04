package org.lonwulf.labs.jobcrawler.application

import org.lonwulf.labs.jobcrawler.domain.model.CrawlerConfig
import org.lonwulf.labs.jobcrawler.domain.repository.IJobServiceRepository
import org.lonwulf.labs.jobcrawler.utils.scheduleCrawler

class JobCrawlerApplication(
    private val config: CrawlerConfig,
    private val repository: IJobServiceRepository
) {
    suspend fun startService() {
        scheduleCrawler(service = repository, intervalHrs = config.intervalHours)
    }

    fun stopService() {
        repository.close()
    }
}
