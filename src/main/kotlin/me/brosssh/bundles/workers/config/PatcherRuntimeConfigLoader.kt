package me.brosssh.bundles.workers.config

internal object PatcherRuntimeConfigLoader {
    private const val RESOURCE_NAME = "patcher-runtimes.toml"

    fun loadBundled(): PatcherRuntimeConfig {
        val input = PatcherRuntimeConfig::class.java.classLoader.getResourceAsStream(RESOURCE_NAME)
            ?: error("Bundled $RESOURCE_NAME was not found")
        return PatcherRuntimeConfigParser.parse(input)
    }
}
