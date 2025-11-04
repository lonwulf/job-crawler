package org.lonwulf.labs.jobcrawler.utils

import org.lonwulf.labs.jobcrawler.domain.model.CrawlerConfig

object ConfigLoader {

    fun loadFromEnvironment(): CrawlerConfig {
        val apiKey = getEnvOrThrow("API_KEY")

        val cx = getEnvOrThrow("CX")

        val intervalHours = System.getenv("INTERVAL_HOURS")
            ?.takeIf { it.isNotBlank() }?.toLongOrNull()
            ?: 6

        val titles = listOf(
            "Android Engineer",
            "Android Developer",
            "Senior Android Engineer",
            "Senior Android Developer",
            //        "Android software developer",
            //        "Senior Android Software developer",
//        "Software engineer - Android", "Software developer - Android"
        )

        val locations = listOf(
            "Kenya onsite", "Kenya hybrid",
            "Worldwide",
//        "Worldwide remote",
            "Remote",
//        "Anywhere remote",
            "EMEA"
        )

        return CrawlerConfig(
            apiKey = apiKey,
            cxId = cx,
            titles = titles,
            locations = locations,
            intervalHours = intervalHours
        )
    }

    private fun getEnvOrThrow(key:String):String =
        System.getenv(key)
            ?.takeIf { it.isNotBlank() }
            ?: error("❌ Environment variable $key is missing or empty.")
}