package me.brosssh.bundles.di

import me.brosssh.bundles.clients.GithubClient
import me.brosssh.bundles.db.repositories.BundleRepository
import me.brosssh.bundles.db.repositories.PackageRepository
import me.brosssh.bundles.db.repositories.PatchRepository
import me.brosssh.bundles.db.repositories.RefreshJobRepository
import me.brosssh.bundles.db.repositories.SourceRepository
import me.brosssh.bundles.services.BundleService
import me.brosssh.bundles.services.GithubService
import org.koin.dsl.module
import me.brosssh.bundles.services.RefreshService
import org.koin.core.parameter.parametersOf

val appModule = module {

    single { BundleRepository() }
    single { SourceRepository() }
    single { PatchRepository() }
    single { RefreshJobRepository() }
    single { PackageRepository() }

    factory { (token: String) -> GithubClient(get(), token) }

    factory { (token: String) -> GithubService(get { parametersOf(token) }) }

    single {
        RefreshService(
            githubServiceFactory = { token -> get<GithubService> { parametersOf(token) } },
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
