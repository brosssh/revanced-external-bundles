package me.brosssh.bundles.di

import me.brosssh.bundles.Config
import me.brosssh.bundles.domain.services.BundleService
import me.brosssh.bundles.domain.services.RefreshJobStatusService
import me.brosssh.bundles.domain.services.jobs.RefreshAllJobService
import me.brosssh.bundles.domain.services.jobs.RefreshBundlesJobService
import me.brosssh.bundles.domain.services.jobs.RefreshPatchesJobService
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
    single { PatchPackageRepository() }

    single {
        GithubClient(
            client = get(),
            githubToken = Config.githubPatToken
        )
    }

    single {
        RefreshBundlesJobService(
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }

    single {
        RefreshPatchesJobService(
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }

    single {
        RefreshAllJobService(
            get(),
            get(),
            get()
        )
    }

    single { BundleService(get()) }
    single { RefreshJobStatusService(get()) }

}
