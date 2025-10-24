package org.lonwulf.labs.jobcrawler.data.db

import kotlinx.datetime.Clock
import java.io.File
import java.sql.DriverManager

class SeenStore(dbPath: String = "data/seen_job_ads.db") {
    private val conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")


    init {
        File(dbPath).parentFile.mkdirs()
        conn.createStatement().use { statement ->
            statement.execute(
                """
        CREATE TABLE IF NOT EXISTS seen_job_ads (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            url TEXT UNIQUE,
            title TEXT,
            company TEXT,
            date_posted TEXT,
            first_seen TEXT
        )
        """.trimIndent()
            )
        }
    }

    fun isSeen(url: String): Boolean {
        val preparedStatement = conn.prepareStatement("SELECT 1 FROM seen_job_ads WHERE url=? LIMIT 1")
        preparedStatement.setString(1, url)
        val resultStatement = preparedStatement.executeQuery()
        val seen = resultStatement.next()
        resultStatement.close()
        preparedStatement.close()
        return seen
    }

    fun markSeen(url: String, title: String?, company: String?, datePosted: String?) {
        val ps = conn.prepareStatement(
            "INSERT OR IGNORE INTO seen_job_ads (url, title, company, date_posted, first_seen) VALUES (?,?,?,?,?)"
        )
        ps.apply {
            setString(1, url)
            setString(2, title)
            setString(3, company)
            setString(4, datePosted)
            setString(5, Clock.System.now().toString())
            executeUpdate()
            close()
        }
    }
}