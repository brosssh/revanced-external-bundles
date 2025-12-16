package me.brosssh.bundles.di

import me.brosssh.bundles.clients.GithubClient
import me.brosssh.bundles.db.queries.BundleRepository
import me.brosssh.bundles.db.queries.RefreshJobRepository
import me.brosssh.bundles.db.queries.SourceRepository
import me.brosssh.bundles.services.GithubService
import org.koin.dsl.module
import me.brosssh.bundles.services.RefreshService

val appModule = module {
    single { BundleRepository() }
    single { SourceRepository() }
    single { RefreshJobRepository() }
    single { GithubService(get()) }
    single {
        RefreshService(
            githubService = get(),
            refreshJobRepository = get(),
            sourceRepository = get()
        )
    }

    single { GithubClient(get()) }
}
