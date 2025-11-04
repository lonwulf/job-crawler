package org.lonwulf.labs.jobcrawler.utils


sealed class CrawlerResult {
    data class Success(val jobsFound: Int) : CrawlerResult()
    data class Failure(val error:String, val cause: Throwable?) : CrawlerResult()
}