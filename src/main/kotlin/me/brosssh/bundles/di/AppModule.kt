package me.brosssh.bundles.di

import me.brosssh.bundles.Config
import me.brosssh.bundles.domain.services.BundleService
import me.brosssh.bundles.domain.services.RefreshJobStatusService
import me.brosssh.bundles.domain.services.jobs.RefreshAllJobService
import me.brosssh.bundles.domain.services.jobs.RefreshBundlesJobService
import me.brosssh.bundles.domain.services.jobs.RefreshPatchesJobService
import me.brosssh.bundles.integrations.GitHostType
import me.brosssh.bundles.integrations.HostResolver
import me.brosssh.bundles.integrations.common.GitHostCredentials
import me.brosssh.bundles.integrations.gitea.GiteaHostClientFactory
import me.brosssh.bundles.integrations.github.GithubClientFactory
import me.brosssh.bundles.integrations.gitlab.GitlabHostClientFactory
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
        GitHostCredentials.fromEnv(Config.gitHostsPat)
    }

    single {
        HostResolver(
            factories = mapOf(
                GitHostType.GITHUB to GithubClientFactory(get(), get()),
                GitHostType.GITLAB to GitlabHostClientFactory(get(), get()),
                GitHostType.GITEA to GiteaHostClientFactory(get(), get())
            ),
            authorities = HostResolver.fromEnv(Config.gitHosts)
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
