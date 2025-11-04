package org.lonwulf.labs.jobcrawler.domain.model

data class CrawlerConfig(
    val apiKey: String,
    val cxId: String,
    val titles: List<String>,
    val locations: List<String>,
    val resultsPerQueryTotal: Int = 15,
    val resultsPerRequest: Int = 10,
    val maxRequestsPerRun: Int = 60,
    val delayMs: Long = 1000L,
    val maxAgeDays: Long = 30L,
    val dataDir:String = "data",
    val intervalHours: Long = 6L
)
