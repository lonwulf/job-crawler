package org.lonwulf.labs.jobcrawler.utils

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.lonwulf.labs.jobcrawler.crawler.JobResult
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CSVWriter {
    private const val MASTER_FILE_NAME = "master_results.csv"

    private fun ensureHeader(file: File) {
        if (!file.exists()) {
            file.parentFile?.mkdirs() // ensure directory exists
            FileWriter(file, false).use { fw ->
                CSVPrinter(
                    fw,
                    CSVFormat.DEFAULT.builder()
                        .setHeader("title", "company", "link", "date_posted", "snippet", "source").build()
                ).use { it.flush() }
            }
        }
    }

    /**
     * Appends job results to both a daily file (YYYY-MM-DD_results.csv)
     * and a master file (master_results.csv) inside the given directory.
     *
     * @param jobs list of job results to append
     * @param dirPath target directory (default = current working dir)
     */
    fun appendJobs(jobs: List<JobResult>, dirPath: String = "") {
        if (jobs.isEmpty()) return

        val directory = File(dirPath)
        if (!directory.exists()) directory.mkdirs()

        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val dailyFile = File(directory, "${today}_results.csv")
        val masterFile = File(directory, MASTER_FILE_NAME)

        ensureHeader(dailyFile)
        ensureHeader(masterFile)

        fun writeJobs(file: File) {
            FileWriter(file, true).use { fw ->
                CSVPrinter(fw, CSVFormat.DEFAULT).use { printer ->
                    for (j in jobs) {
                        printer.printRecord(
                            j.title,
                            j.company ?: "",
                            j.urlLink,
                            j.datePosted ?: "",
                            j.snippet ?: "",
                            j.source ?: ""
                        )
                    }
                }
            }
        }

        writeJobs(dailyFile)
        writeJobs(masterFile)
    }
}