package org.lonwulf.labs.jobcrawler.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.lonwulf.labs.jobcrawler.application.JobCrawlerApplication
import org.lonwulf.labs.jobcrawler.crawler.GoogleSearchClient
import org.lonwulf.labs.jobcrawler.data.db.SeenStore
import org.lonwulf.labs.jobcrawler.data.repository.JobServiceRepositoryImpl
import org.lonwulf.labs.jobcrawler.data.response.SearchDTO
import org.lonwulf.labs.jobcrawler.domain.model.CrawlerConfig
import org.lonwulf.labs.jobcrawler.domain.repository.IJobServiceRepository
import org.lonwulf.labs.jobcrawler.utils.ConfigLoader
import org.lonwulf.labs.jobcrawler.utils.getEnvOrThrow
import java.util.concurrent.TimeUnit

val appModule = module {
    single { ConfigLoader.loadFromEnvironment() }
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                println("HTTP Response: ${response.code} (${response.message})")
                response
            }.build()
    }
    single {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    single {
        get<Moshi>().adapter(SearchDTO::class.java)
    }

    single {
        val config = get<CrawlerConfig>()
        GoogleSearchClient(
            apiKey = config.apiKey,
            cx = config.cxId,
            resultsPerRequest = config.resultsPerRequest,
            delayMsBetweenRequests = config.delayMs,
            client = get(),
            adapter = get()
        )
    }
    single {
        val config = get<CrawlerConfig>()
        val dbPath = getEnvOrThrow("EXPORT_PATH") ?: config.dataDir
        SeenStore("${dbPath}/seen_job_ads.db")
    }
    single<IJobServiceRepository> {
        JobServiceRepositoryImpl(config = get(), store = get(), searchClient = get())
    }
    single { JobCrawlerApplication(get(), get()) }
}