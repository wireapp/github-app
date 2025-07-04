package com.wire.github

import com.wire.github.config.projectModules
import com.wire.github.util.ENV_VAR_PORT
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(projectModules)
    }

    embeddedServer(
        factory = Netty,
        port = ENV_VAR_PORT,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureRouting()
}
