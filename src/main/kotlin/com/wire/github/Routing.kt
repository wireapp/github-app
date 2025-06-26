package com.wire.github

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // TODO: Configure correct routes
        //  /{userId/conversationId}/
        //  /status
        get("/") {
            call.respondText("Hello World!")
        }
    }
}
