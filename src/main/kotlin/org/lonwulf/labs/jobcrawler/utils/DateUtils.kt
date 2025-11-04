package org.lonwulf.labs.jobcrawler.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.seconds

object DateUtils {

    fun parseIsoLikeFromString(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val isoRegex = Pattern.compile("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""")
        val matcher = isoRegex.matcher(text)
        if (matcher.find()) {
            return try {
                Instant.parse(matcher.group())?.toString()
            } catch (e: Exception) {
                null
            }
        }
        // date like "Oct 12, 2025"
        val readableDate =
            Regex("""(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{1,2},\s*\d{4}""")

        val matchedReadableDate = readableDate.find(text)
        if (matchedReadableDate != null) {
            return try {
                val dateTime = LocalDate.parse(matchedReadableDate.value, DateTimeFormatter.ofPattern("MMMM d, yyyy"))
                dateTime.atStartOfDay(ZoneOffset.UTC).toInstant().toString()
            } catch (ex: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Parse relative phrases like "3 days ago", "1 month ago" and convert to ISO instant
     */
    fun parseRelativeDateToIso(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val lowerTxt = text.lowercase()
        val relativeTxt = Regex("""(\d+)\s+(hour|day|week|month|minute)s?\s+ago""")
        val matches = relativeTxt.find(lowerTxt)
        if (matches != null) {
            val amount = matches.groups[1]?.value?.toLongOrNull() ?: return null
            val unit = matches.groups[2]?.value ?: return null
            val now = Clock.System.now()
            val instant = when (unit) {
                "minute" -> now.minus((amount * 60).seconds)
                "hour" -> now.minus((amount * 3600).seconds)
                "day" -> now.minus((amount * 86400).seconds)
                "week" -> now.minus((amount * 86400 * 7).seconds)
                "month" -> now.minus((amount * 86400 * 30).seconds)
                else -> now
            }
            return instant.toString()
        }
        if (lowerTxt.contains("yesterday")) {
            return Clock.System.now().minus(86400.seconds).toString()
        }
        return null
    }

    /**
     * Check if ISO Instant string is within maxDays (e.g., 30)
     */
    fun isWithinDays(isoInstant: String?, maxDays: Long = 30): Boolean {
        if (isoInstant == null) return false
        return try {
            val posted = Instant.parse(isoInstant)
            val now = Clock.System.now()
            val diff = now - posted
            diff.inWholeDays <= maxDays
        } catch (e: Exception) {
            false
        }
    }

    fun isRecent(dateText: String?): Boolean {
        if (dateText == null) return false
        val indicators = listOf("hour", "day", "week", "month")
        return indicators.any { indicator -> dateText.contains(indicator, ignoreCase = true) }
    }
}