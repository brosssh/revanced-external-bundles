plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("patcherRuntimes") {
            id = "me.brosssh.patcher-runtimes"
            implementationClass = "me.brosssh.build.PatcherRuntimesPlugin"
        }
    }
}
