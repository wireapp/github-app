package com.wire.github

import com.wire.github.github.GitHubWebhookManager
import com.wire.github.response.model.GitHubResponse
import com.wire.github.util.ENV_VAR_GITHUB_WEBHOOK_SECRET
import com.wire.github.util.KtxSerializer
import com.wire.github.util.SignatureValidator
import com.wire.github.util.TemplateHandler
import com.wire.sdk.WireAppSdk
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureRouting() {
    install(plugin = ContentNegotiation) {
        json(KtxSerializer.json)
    }

    val wireAppSdk = GlobalContext.get().get<WireAppSdk>()
    val signatureValidator = GlobalContext.get().get<SignatureValidator>()
    val templateHandler = GlobalContext.get().get<TemplateHandler>()
    val gitHubWebhookManager = GlobalContext.get().get<GitHubWebhookManager>()

    routing {
        trace {
            application.log.info(it.buildText())
        }

        get("/health") {
            call.response.status(value = HttpStatusCode.OK)
        }

        post("/${GitHubWebhookManager.GITHUB_WEBHOOK_PATH}") {
            // Headers
            val event = call.request.headers["X-GitHub-Event"]
            val signature = call.request.headers["X-Hub-Signature-256"]
            val delivery = call.request.headers["X-GitHub-Delivery"]

            requireNotNull(event)
            requireNotNull(signature)
            requireNotNull(delivery)

            // Payload
            val payload = call.receiveText()

            if (!signatureValidator.isValidGithubWebhookSignature(signature, payload)) {
                return@post call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = "Invalid GitHub webhook signature"
                )
            }

            val response = KtxSerializer.json.decodeFromString<GitHubResponse>(payload)
            gitHubWebhookManager.markRepositoryActive(response.repository.fullName)

            if (event == EVENT_WORKFLOW_RUN) {
                wireAppSdk.sendCommitCiMessage(
                    gitHubWebhookManager = gitHubWebhookManager,
                    response = response,
                    workflowRun = response.workflowRun
                )
            } else {
                // Handle event response and send message
                val messageTemplate = templateHandler.handleEvent(
                    event = event,
                    response = response
                )

                messageTemplate?.let { message ->
                    wireAppSdk.sendGitHubMessage(
                        gitHubWebhookManager = gitHubWebhookManager,
                        response = response,
                        text = message
                    )
                }
            }

            return@post call.response.status(HttpStatusCode.OK)
        }
    }
}

private fun SignatureValidator.isValidGithubWebhookSignature(
    signature: String,
    payload: String
): Boolean =
    isValid(
        signature = signature,
        payload = payload,
        secret = requireNotNull(ENV_VAR_GITHUB_WEBHOOK_SECRET) {
            "GHAPP_GITHUB_WEBHOOK_SECRET must be set to validate GitHub webhooks"
        }
    )

private const val EVENT_WORKFLOW_RUN = "workflow_run"
