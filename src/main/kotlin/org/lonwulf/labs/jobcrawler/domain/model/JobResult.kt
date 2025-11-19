package org.lonwulf.labs.jobcrawler.domain.model

import org.lonwulf.labs.jobcrawler.utils.getEnvOrThrow

data class JobResult(
    val title: String,
    val urlLink: String,
    val company: String?,
    val snippet: String?,
    val datePosted: String?,
    val source: String?,
    val locationHint: String?
) {
    object ExperienceRegexCache {
        val experienceRegex = Regex(
            """\b(\d+)\s*(?:(?:-|–|—|to)\s*(\d+)\s*|\+|plus\s+)?(?:years?|yrs?)\b|""" +
                    """\b(?:minimum|min\.?|at\s+least)\s*(\d+)\s*(?:years?|yrs?)\b""",
            RegexOption.IGNORE_CASE
        )
    }

    fun matchesExperience(
        minYears: Int = 1,
        maxYears: Int = 6
    ): Boolean {
        val text = listOf(title, snippet, company)
            .joinToString(" ")
            .replace("[–—]".toRegex(), "-") // normalize en/em dashes to ASCII dash
            .replace("\\s+".toRegex(), " ") // collapse multiple spaces
            .trim()

        if (text.isEmpty()) return false

        val match = ExperienceRegexCache.experienceRegex.find(text) ?: return false

        val low = (match.groups[1]?.value ?: match.groups[3]?.value)?.toIntOrNull() ?: return false
        val high = match.groups[2]?.value?.toIntOrNull() ?: low

        return low in minYears..maxYears ||
                high in minYears..maxYears ||
                (low <= minYears && high >= maxYears)
    }


    object JobFilterCache {
        private val nonWordChars = Regex("[^\\w\\s]+")
        private val slashes = Regex("[/\\\\]")
        val envHybridRegexes = getEnvOrThrow("HYBRID_REGEXES").split(",").map(String::trim).map {
            Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE)
        }
        val envExcludeRegexes = getEnvOrThrow("EXCLUDE_REGEXES").split(",").map(String::trim).map {
            Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE)
        }

        val role = getEnvOrThrow("ROLE")

        val hasRoleRegex = Regex("\\b$role\\b", RegexOption.IGNORE_CASE)
        val iosRegex = Regex("\\bios\\b", RegexOption.IGNORE_CASE)

        fun normalize(text: String): String = text
            .replace(slashes, " ")
            .replace(nonWordChars, " ")
            .lowercase()
            .trim()
    }

    fun isRelevantJob(): Boolean {
        val cache = JobFilterCache
        val normalizedText = cache.normalize(
            listOf(title, snippet, company, locationHint).joinToString(" ")
        )

        if (cache.envExcludeRegexes.any { it.containsMatchIn(normalizedText) }) return false
        if (cache.envHybridRegexes.any { it.containsMatchIn(normalizedText) }) return true

        val hasRole = cache.hasRoleRegex.containsMatchIn(normalizedText)
        val hasIos = cache.iosRegex.containsMatchIn(normalizedText)

        return hasRole
    }
}