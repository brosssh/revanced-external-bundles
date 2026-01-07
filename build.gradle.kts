import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    id("io.ktor.plugin") version libs.versions.ktor.get()
    application
    `maven-publish`
    signing
}

group = "me.brosssh"

tasks {
    // Used by gradle-semantic-release-plugin.
    // Tracking: https://github.com/KengoTODA/gradle-semantic-release-plugin/issues/435.
    publish {
        dependsOn(shadowJar)
    }

    shadowJar {
        manifest {
            attributes(
                "Implementation-Version" to project.version.toString()
            )
        }
        // Needed for Jetty to work.
        mergeServiceFiles()
    }
}

ktor {
    fatJar {
        archiveFileName.set("${project.name}-${project.version}.jar")
    }
}

repositories {
    mavenCentral()
    google()
    maven {
        // A repository must be specified for some reason. "registry" is a dummy.
        url = uri("https://maven.pkg.github.com/brosssh/registry")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    maven {
        // A repository must be specified for some reason. "registry" is a dummy.
        url = uri("https://maven.pkg.github.com/revanced/registry")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation(libs.ktor.core)
    implementation(libs.ktor.netty)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.kotlinx.json)
    implementation(libs.ktor.call.logging)
    implementation(libs.ktor.openapi)
    implementation(libs.ktor.swagger)
    implementation(libs.ktor.auth)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.kotlinx.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("app.brosssh:revanced-patcher:1.3.0-dev.1")
    implementation("com.android.tools.build:apksig:8.1.1")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    implementation("io.github.smiley4:ktor-swagger-ui:5.4.0")
    implementation("io.github.smiley4:ktor-openapi:5.4.0")

    implementation(libs.hikari.cp)
    implementation(libs.postgresql)

    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)

    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("me.brosssh.bundles.ApplicationKt")
}

// The maven-publish plugin is necessary to make signing work.
publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("revanced-external-bundles-publication") {
            from(components["java"])
        }
    }
}

signing {
    useGpgCmd()

    sign(publishing.publications["revanced-external-bundles-publication"])
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}
