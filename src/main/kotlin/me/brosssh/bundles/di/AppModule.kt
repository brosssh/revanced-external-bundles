package me.brosssh.bundles.di

import me.brosssh.bundles.clients.GithubClient
import me.brosssh.bundles.db.repositories.BundleRepository
import me.brosssh.bundles.db.repositories.PackageRepository
import me.brosssh.bundles.db.repositories.RefreshJobRepository
import me.brosssh.bundles.db.repositories.SourceRepository
import me.brosssh.bundles.services.GithubService
import org.koin.dsl.module
import me.brosssh.bundles.services.RefreshService

val appModule = module {
    single { BundleRepository() }
    single { SourceRepository() }
    single { RefreshJobRepository() }
    single { PackageRepository() }
    single { GithubService(get()) }
    single {
        RefreshService(
            githubService = get(),
            refreshJobRepository = get(),
            sourceRepository = get(),
            packageRepository = get()
        )
    }

    single { GithubClient(get()) }
}
