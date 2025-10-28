package com.wire.github

import com.wire.github.response.model.GitHubResponse
import com.wire.github.util.KtxSerializer
import com.wire.github.util.SignatureValidator
import com.wire.github.util.TemplateHandler
import com.wire.sdk.WireAppSdk
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.UUID
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.context.GlobalContext

@Suppress("LongMethod")
@OptIn(ExperimentalSerializationApi::class)
fun Application.configureRouting() {
    install(plugin = ContentNegotiation) {
        json(KtxSerializer.json)
    }

    val wireAppSdk = GlobalContext.get().get<WireAppSdk>()
    val signatureValidator = GlobalContext.get().get<SignatureValidator>()
    val templateHandler = GlobalContext.get().get<TemplateHandler>()

    routing {
        trace {
            application.log.info(it.buildText())
        }

        get("/health") {
            call.response.status(value = HttpStatusCode.OK)
        }

        post("/{$PARAM_CONVERSATION_ID}/{$PARAM_CONVERSATION_DOMAIN}") {
            // Headers
            val event = call.request.headers["X-GitHub-Event"]
            val signature = call.request.headers["X-Hub-Signature"]
            val delivery = call.request.headers["X-GitHub-Delivery"]

            requireNotNull(event)
            requireNotNull(signature)
            requireNotNull(delivery)

            // Path Parameter
            val conversationId = call.parameters[PARAM_CONVERSATION_ID]
                ?: return@post call.respondText(
                    status = HttpStatusCode.BadRequest,
                    text = "Missing $PARAM_CONVERSATION_ID"
                )

            val conversationDomain = call.parameters[PARAM_CONVERSATION_DOMAIN]
                ?: return@post call.respondText(
                    status = HttpStatusCode.BadRequest,
                    text = "Missing $PARAM_CONVERSATION_DOMAIN"
                )

            // Payload
            val payload = call.receiveText()

            // Validation of received signature
            val isSignatureValid = signatureValidator.isValid(
                conversationId = conversationId,
                conversationDomain = conversationDomain,
                signature = signature,
                payload = payload
            )
            if (!isSignatureValid) {
                return@post call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = "Invalid Signature for Conversation"
                )
            }

            val response = KtxSerializer.json.decodeFromString<GitHubResponse>(payload)

            // Handle event response and send message
            val messageTemplate = templateHandler.handleEvent(
                event = event,
                response = response
            )

            messageTemplate?.let { message ->
                wireAppSdk.getApplicationManager().sendMessage(
                    message = WireMessage.Text.create(
                        conversationId = QualifiedId(
                            id = UUID.fromString(conversationId),
                            domain = conversationDomain
                        ),
                        text = message
                    )
                )
            }

            return@post call.response.status(HttpStatusCode.OK)
        }
    }
}

private const val PARAM_CONVERSATION_ID = "conversationId"
private const val PARAM_CONVERSATION_DOMAIN = "conversationDomain"
