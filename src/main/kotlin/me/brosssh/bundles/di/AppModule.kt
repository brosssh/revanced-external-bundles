package me.brosssh.bundles.di

import java.nio.file.Path

import me.brosssh.bundles.Config
import me.brosssh.bundles.db.SourceManifestSync
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
import me.brosssh.bundles.workers.PatchWorkerManager
import me.brosssh.bundles.workers.config.PatchWorkerPoolSettings
import me.brosssh.bundles.workers.config.PatcherRuntimeConfigLoader
import org.koin.dsl.module

val appModule = module {

    single { BundleRepository() }
    single { SourceRepository() }
    single { SourceMetadataRepository() }
    single { PatchRepository() }
    single { RefreshJobRepository() }
    single { PackageRepository() }
    single { PatchPackageRepository() }

    single(createdAtStart = true) {
        PatchWorkerManager(
            config = PatcherRuntimeConfigLoader.loadBundled(),
            runtimeRoot = Path.of(Config.patcherRuntimeDir),
            poolSettings = PatchWorkerPoolSettings(
                maxProcesses = Config.patcherWorkerMaxProcesses,
                maxPerRuntime = Config.patcherWorkerMaxPerRuntime,
                idleTimeoutSeconds = Config.patcherWorkerIdleSeconds
            )
        )
    }

    single {
        GitHostCredentials.fromEnv(
            Config.gitHostsPat,
            Config.legacyGithubPatToken
        )
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
            get(),
            get(),
            refreshConcurrency = Config.patcherRefreshConcurrency
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
    single { SourceManifestSync(get()) }

}
