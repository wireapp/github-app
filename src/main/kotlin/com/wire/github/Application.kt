package com.wire.github

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

// TODO: Add proper ENV variables
private const val SERVER_PORT: Int = 8080
private const val SERVER_HOST: String = "0.0.0.0"

fun main() {
    embeddedServer(
        factory = Netty,
        port = SERVER_PORT,
        host = SERVER_HOST,
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureRouting()
}
