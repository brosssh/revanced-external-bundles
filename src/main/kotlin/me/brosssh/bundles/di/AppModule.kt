package me.brosssh.bundles.di

import me.brosssh.bundles.Config
import me.brosssh.bundles.clients.GithubClient
import me.brosssh.bundles.db.repositories.*
import me.brosssh.bundles.services.BundleService
import me.brosssh.bundles.services.GithubService
import me.brosssh.bundles.services.RefreshService
import org.koin.dsl.module

val appModule = module {

    single { BundleRepository() }
    single { SourceRepository() }
    single { PatchRepository() }
    single { RefreshJobRepository() }
    single { PackageRepository() }

    single {
        GithubClient(
            client = get(),
            githubToken = Config.githubRepoToken
        )
    }

    single { GithubService(get()) }

    single {
        RefreshService(
            githubService = get(),
            refreshJobRepository = get(),
            sourceRepository = get(),
            bundleRepository = get(),
            patchRepository = get(),
            packageRepository = get()
        )
    }

    single {
        BundleService(get())
    }
}
