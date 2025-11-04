package org.lonwulf.labs.jobcrawler.data.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class SeenStore(dbPath: String = "data/seen_job_ads.db") : AutoCloseable {
    private var conn: Connection? = null
    private val seenUrls = mutableSetOf<String>()


    init {
        try {
            File(dbPath).parentFile.mkdirs()
            conn = DriverManager.getConnection("jdbc:sqlite:$dbPath").apply {
                autoCommit = true
            }
            createTableIfNotExists()
            loadSeenUrls()
            migrateFirstSeenColumnIfNeeded()
        } catch (e: Exception) {
            e.printStackTrace()
            throw IllegalStateException("Failed to initialize database at $dbPath", e)
        }
    }

    private fun createTableIfNotExists() {
        val sql = """
        CREATE TABLE IF NOT EXISTS seen_job_ads (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            url TEXT UNIQUE,
            title TEXT,
            company TEXT,
            date_posted TEXT,
            first_seen DATETIME DEFAULT (datetime('now'))
        )
        """.trimIndent()

        try {
            conn?.createStatement()?.use { statement -> statement.execute(sql) }
            conn?.createStatement()?.use {
                it.execute("CREATE INDEX IF NOT EXISTS idx_seen_job_ad_at ON seen_job_ads(first_seen)")
            }
        } catch (e: SQLException) {
            println("Error creating table: ${e.message}")
            throw e
        }
    }

    private fun loadSeenUrls() {
        try {
            conn?.createStatement()?.use { statement ->
                statement.executeQuery("SELECT url FROM seen_job_ads").use { resultSet ->
                    while (resultSet.next()) {
                        seenUrls.add(resultSet.getString("url"))
                    }
                }
            }
            println("Loaded ${seenUrls.size} seen URLs from database [x]")
        } catch (e: SQLException) {
            println("Error loading seen URLs: ${e.message}")
        }
    }

    @Synchronized
    fun isSeen(url: String): Boolean {
        return seenUrls.contains(url)
    }

    @Synchronized
    fun markSeen(url: String, title: String?, company: String?, datePosted: String?) {
        if (seenUrls.contains(url)) return
        val sql = """
            INSERT OR IGNORE INTO seen_job_ads (url, title, company, date_posted)
            VALUES (?,?,?,?)
        """.trimIndent()
        try {
            conn?.prepareStatement(sql)?.use { ps ->
                ps.apply {
                    setString(1, url)
                    setString(2, title)
                    setString(3, company)
                    setString(4, datePosted)
                    executeUpdate()
                }
            }
            seenUrls.add(url)
        } catch (e: SQLException) {
            println("Error marking URL as seen ($url): ${e.message}")
        }
    }

    @Synchronized
    fun cleanupOldEntries(daysToKeep: Int = 30) {
        val sql = """
            DELETE FROM seen_job_ads
            WHERE first_seen < datetime('now', '-$daysToKeep days')
        """.trimIndent()

        try {
            conn?.createStatement()?.use { statement ->
                val deleted = statement.executeUpdate(sql)
                if (deleted > 0) {
                    println("Cleaned up $deleted old entries from database")
                    seenUrls.clear()
                    loadSeenUrls()
                }
            }
        } catch (e: SQLException) {
            println("Error during cleanup: ${e.message}")
        }
    }

    fun getSeenCount(): Int {
        return try {
            conn?.createStatement().use { statement ->
                statement?.executeQuery("SELECT COUNT(*) as count FROM seen_job_ads").use { resultSet ->
                    if (resultSet?.next() == true) resultSet.getInt("count") else 0
                }
            }
        } catch (e: SQLException) {
            println("Error getting seen count: ${e.message}")
            0
        }
    }

    override fun close() {
        try {
            conn?.let {
                if (!it.isClosed) {
                    it.close()
                    println("Closed db connection [x]")
                }
            }
        } catch (e: SQLException) {
            println("Error closing database: ${e.message}")
        }
    }

    @Synchronized
    private fun migrateFirstSeenColumnIfNeeded() {
        try {
            var firstSeenType: String? = null
            conn?.createStatement()?.use { statement ->
                statement.executeQuery("PRAGMA table_info(seen_job_ads)").use { resultSet ->
                    while (resultSet.next()) {
                        if (resultSet.getString("name") == "first_seen") {
                            firstSeenType = resultSet.getString("type")?.uppercase()
                            break
                        }
                    }
                    resultSet.close()
                }
                statement.close()
            }
            if (firstSeenType == "DATETIME") {
                println("Schema check: 'first_seen' is already DATETIME. Migration skipped.")
                return
            }
            println("=".repeat(60) + "\n")
            println("Migrating 'first_seen' column to DATETIME...")

            val createTempTable = """
                CREATE TABLE IF NOT EXISTS seen_job_posts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT UNIQUE,
                title TEXT,
                company TEXT,
                first_seen DATETIME DEFAULT (datetime('now'))
                )
            """.trimIndent()
            val sqlCopyData = """
                INSERT INTO seen_job_posts (
                id,url,title,company,date_posted, first_seen)
                SELECT id, url, title, company, date_posted, COALESCE(first_seen, datetime('now'))
                FROM seen_job_ads
            """.trimIndent()
            conn?.createStatement()?.use { statement ->
                statement.execute("BEGIN TRANSACTION")
                statement.execute(createTempTable)
                statement.execute(sqlCopyData)
                statement.execute("DROP TABLE seen_job_ads")
                statement.execute("ALTER TABLE seen_job_posts RENAME TO seen_job_ads")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_seen_job_ad_at ON seen_job_ads(first_seen)")
                statement.execute("COMMIT")
            }
            println("[x] Migration completed successfully.")
        } catch (e: SQLException) {
            println("Error migrating column: ${e.message}")
            try {
                conn?.createStatement()?.execute("ROLLBACK")
            } catch (_: SQLException) {
            }
        }
    }
}