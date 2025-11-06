package org.lonwulf.labs.jobcrawler.utils

import org.lonwulf.labs.jobcrawler.domain.model.CrawlerConfig

object ConfigLoader {

    fun loadFromEnvironment(): CrawlerConfig {
        val apiKey = getEnvOrThrow("API_KEY")

        val cx = getEnvOrThrow("CX")

        val intervalHours = System.getenv("INTERVAL_HOURS")
            ?.takeIf { it.isNotBlank() }?.toLongOrNull()
            ?: 6

        val envTitles = getEnvOrThrow("TITLES").split(",").map { it.trim() }
        val envLocations = getEnvOrThrow("LOCATIONS").split(",").map { it.trim() }

        //TODO delete titles and location hardcoded vals
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
            "in Kenya onsite", "in Kenya hybrid",
            "Worldwide",
//        "Worldwide remote",
            "Remote",
//        "Anywhere remote",
            "in EMEA"
        )

        return CrawlerConfig(
            apiKey = apiKey,
            cxId = cx,
            titles = envTitles ,
            locations = envLocations ,
            intervalHours = intervalHours
        )
    }
}

fun getEnvOrThrow(key: String): String =
    System.getenv(key)
        ?.takeIf { it.isNotBlank() }
        ?: error("❌ Environment variable $key is missing or empty.")