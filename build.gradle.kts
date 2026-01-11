plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
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

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
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
    maven {
        // A repository must be specified for some reason. "registry" is a dummy.
        url = uri("https://maven.pkg.github.com/morpheapp/registry")
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
    implementation(libs.ktor.auth)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.kotlinx.json)

    implementation(libs.ktor.swagger.ui)
    implementation(libs.ktor.openapi)

    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)

    implementation(libs.hikari.cp)
    implementation(libs.postgresql)

    implementation(libs.dotenv)
    implementation(libs.brosssh.patcher)
    implementation(libs.morphe.patcher)
    implementation(libs.logback)
    implementation(libs.apksig)

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
