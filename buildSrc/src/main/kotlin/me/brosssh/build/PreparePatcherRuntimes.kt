package me.brosssh.build

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class PreparePatcherRuntimes : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val document = JsonSlurper().parse(manifestFile.get().asFile) as? Map<*, *>
            ?: error("Patcher runtime build manifest must be a JSON object")
        require((document["formatVersion"] as? Number)?.toInt() == 1) {
            "Unsupported patcher runtime build manifest format"
        }
        val runtimes = document["runtimes"] as? List<*>
            ?: error("Patcher runtime build manifest must contain a runtimes array")

        val stagingRoot = temporaryDir.resolve("runtimes").toPath()
        project.delete(stagingRoot.toFile())
        Files.createDirectories(stagingRoot)
        val seenDirectories = mutableSetOf<String>()

        runtimes.forEachIndexed { index, value ->
            val runtime = value as? Map<*, *>
                ?: error("Patcher runtime build manifest entry $index must be an object")
            val coordinate = runtime["coordinate"] as? String
                ?: error("Patcher runtime build manifest entry $index must contain a coordinate")
            val directory = runtime["directory"] as? String
                ?: error("Patcher runtime build manifest entry $index must contain a directory")
            require(seenDirectories.add(directory)) {
                "Duplicate patcher runtime output directory '$directory'"
            }

            val target = stagingRoot.resolve(directory).normalize()
            require(target.startsWith(stagingRoot) && target != stagingRoot) {
                "Invalid patcher runtime output directory '$directory'"
            }
            val configuration = project.configurations.detachedConfiguration(
                project.dependencies.create(coordinate)
            ).apply {
                isTransitive = true
                resolutionStrategy.eachDependency {
                    if (requested.group == "app.revanced" && requested.name == "multidexlib2") {
                        // Patcher 11.0.4 requests 2.5.3-a3836654, which is no longer published.
                        // Use the published, binary-compatible revision for every runtime classpath.
                        useVersion("3.0.3.r3")
                    }
                }
            }
            project.copy {
                from(configuration)
                into(target.toFile())
            }
        }

        project.sync {
            from(stagingRoot.toFile())
            into(outputDirectory)
        }
    }
}
