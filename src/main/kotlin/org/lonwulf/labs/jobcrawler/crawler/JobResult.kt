package org.lonwulf.labs.jobcrawler.crawler

data class JobResult(
    val title: String,
    val urlLink: String,
    val company: String?,
    val snippet: String?,
    val datePosted: String?,
    val source:String?,
    val locationHint:String?
){
    fun matchesExperience(minYears:Int =1, maxYears:Int =6):Boolean{
        val text = listOf(title, snippet, company).joinToString(" ").lowercase()
        if(text.isBlank()) return false

        val regexes = listOf(
            Regex("""\b([1-6])\s*(?:\+|plus)?\s*(?:-|\u2013|\u2014)?\s*([1-6])?\s*years?\b"""),
            Regex("""\b(?:minimum|min\.|at least)\s*([1-6])\s*years?\b"""),
            Regex("""\b([1-6])\s*\+?\s*years?\b""")
        )
        for (r in regexes) {
            val m = r.find(text)
            if (m != null) {
                val g1 = m.groups[1]?.value?.toIntOrNull()
                val g2 = m.groups[2]?.value?.toIntOrNull()
                if(g1 != null){
                    val low = g1
                    val high = g2 ?: g1
                    if (high >= minYears && low <= maxYears) return true
                }
            }
        }
        return false
    }

    fun isRelevantAndroidJob(): Boolean {
        val text = listOf(title, snippet, company, locationHint)
            .joinToString(" ")
            .lowercase()

        val includeKeywords = listOf("android", "kotlin", "jetpack", "compose", "mobile", "play store")
        val excludeKeywords = listOf("ios", "flutter", "web", "intern", "qa", "test automation")

        val score = includeKeywords.count { it in text } - excludeKeywords.count { it in text }
        return score >= 1 // must have at least one relevant keyword and not many irrelevant ones
    }
}