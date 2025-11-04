package org.lonwulf.labs.jobcrawler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.koin.dsl.koinApplication
import org.lonwulf.labs.jobcrawler.application.JobCrawlerApplication
import org.lonwulf.labs.jobcrawler.di.appModule
import kotlin.system.exitProcess


fun main() = runBlocking {
    println("====Starting scheduler at ${Clock.System.now()}")
    val koinApp = koinApplication {
        modules(appModule)
    }
    val koin = koinApp.koin
    val application = koin.get<JobCrawlerApplication>()
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down gracefully...")
        runBlocking {
            application.stopService()
        }
        koinApp.close()
    })
    try {
        coroutineScope {
            launch {
                application.startService()
            }.join()
        }
    } catch (ce: CancellationException) {
        println("Scheduler cancelled via coroutine context : ${ce.message}")
    } catch (e: Exception) {
        println("Fatal error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    } finally {
        application.stopService()
        koinApp.close()
        println("Scheduler stopped cleanly at ${Clock.System.now()}")
    }
}