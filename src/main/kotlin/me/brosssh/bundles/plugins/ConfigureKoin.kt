package me.brosssh.bundles.plugins

import io.ktor.server.application.*
import me.brosssh.bundles.di.appModule
import me.brosssh.bundles.di.httpClientModule
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {

    install(Koin) {
        slf4jLogger()
        modules(
            httpClientModule,
            appModule
        )
    }
}
