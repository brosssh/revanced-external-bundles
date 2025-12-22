package me.brosssh.bundles.di

import me.brosssh.bundles.Config
import me.brosssh.bundles.domain.services.BundleService
import me.brosssh.bundles.domain.services.refresh.RefreshBundlesService
import me.brosssh.bundles.domain.services.refresh.RefreshPatchesService
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.repositories.*
import org.koin.dsl.module

val appModule = module {

    single { BundleRepository() }
    single { SourceRepository() }
    single { SourceMetadataRepository() }
    single { PatchRepository() }
    single { RefreshJobRepository() }
    single { PackageRepository() }

    single {
        GithubClient(
            client = get(),
            githubToken = Config.githubRepoToken
        )
    }

    single {
        RefreshBundlesService(
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }

    single {
        RefreshPatchesService(
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }

    single {
        BundleService(get())
    }
}
