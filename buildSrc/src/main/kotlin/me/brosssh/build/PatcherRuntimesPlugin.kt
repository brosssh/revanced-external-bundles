package me.brosssh.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService

@Suppress("unused")
class PatcherRuntimesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("java") {
            val java = project.extensions.getByType(JavaPluginExtension::class.java)
            val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            val manifest = project.layout.buildDirectory.file("generated/patcher-runtimes/manifest.json")
            val generateManifest = project.tasks.register(
                "generatePatcherRuntimeManifest",
                JavaExec::class.java
            ) {
                group = "build"
                description = "Validate patcher-runtimes.toml and generate its normalized build manifest"
                dependsOn(project.tasks.named("classes"))
                javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
                classpath = sourceSets.named("main").get().runtimeClasspath
                mainClass.set(
                    "me.brosssh.bundles.workers.config.PatcherRuntimeBuildManifestGenerator"
                )
                argumentProviders.add {
                    listOf(manifest.get().asFile.absolutePath)
                }
                outputs.file(manifest)
            }
            val prepareRuntimes = project.tasks.register(
                "preparePatcherRuntimes",
                PreparePatcherRuntimes::class.java
            ) {
                group = "build"
                description = "Resolve patcher libraries into isolated per-runtime classpaths"
                dependsOn(generateManifest)
                manifestFile.set(manifest)
                outputDirectory.set(project.layout.buildDirectory.dir("patcher-runtimes"))
            }

            project.tasks.matching {
                it.name == "run" || it.name == "shadowJar" || it.name == "startShadowScript"
            }.configureEach {
                dependsOn(prepareRuntimes)
            }
            project.tasks.withType(Test::class.java).configureEach {
                dependsOn(prepareRuntimes)
            }
        }
    }
}
