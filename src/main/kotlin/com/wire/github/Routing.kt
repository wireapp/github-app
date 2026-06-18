package com.wire.github

import com.wire.github.github.GitHubWebhookManager
import com.wire.github.response.model.GitHubResponse
import com.wire.github.util.ENV_VAR_GITHUB_WEBHOOK_SECRET
import com.wire.github.util.KtxSerializer
import com.wire.github.util.SignatureValidator
import com.wire.github.util.TemplateHandler
import com.wire.sdk.WireAppSdk
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.service.WireApplicationManager
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
import java.util.UUID
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

            // Validation of received signature
            val isSignatureValid = signatureValidator.isValid(
                signature = signature,
                payload = payload,
                secret = requireNotNull(ENV_VAR_GITHUB_WEBHOOK_SECRET) {
                    "GHAPP_GITHUB_WEBHOOK_SECRET must be set to validate GitHub webhooks"
                }
            )
            if (!isSignatureValid) {
                return@post call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = "Invalid GitHub webhook signature"
                )
            }

            val response = KtxSerializer.json.decodeFromString<GitHubResponse>(payload)
            gitHubWebhookManager.markRepositoryActive(response.repository.fullName)

            // Handle event response and send message
            val messageTemplate = templateHandler.handleEvent(
                event = event,
                response = response
            )

            messageTemplate?.let { message ->
                wireAppSdk.sendGitHubMessage(
                    gitHubWebhookManager = gitHubWebhookManager,
                    event = event,
                    response = response,
                    text = message
                )
            }

            return@post call.response.status(HttpStatusCode.OK)
        }
    }
}

private fun WireAppSdk.sendGitHubMessage(
    gitHubWebhookManager: GitHubWebhookManager,
    event: String,
    response: GitHubResponse,
    text: String
) {
    gitHubWebhookManager
        .conversationsForRepository(response.repository.fullName)
        .forEach { conversationId ->
            sendGitHubMessage(
                gitHubWebhookManager = gitHubWebhookManager,
                event = event,
                response = response,
                conversationId = conversationId,
                text = text
            )
        }
}

private fun WireAppSdk.sendGitHubMessage(
    gitHubWebhookManager: GitHubWebhookManager,
    event: String,
    response: GitHubResponse,
    conversationId: QualifiedId,
    text: String
) {
    val workflowRunId = response.workflowRun?.id
    val messageId = if (event == EVENT_WORKFLOW_RUN && workflowRunId != null) {
        getApplicationManager().sendWorkflowRunMessage(
            gitHubWebhookManager = gitHubWebhookManager,
            repositoryFullName = response.repository.fullName,
            workflowRunId = workflowRunId,
            conversationId = conversationId,
            text = text
        )
    } else {
        getApplicationManager().sendTextMessage(
            conversationId = conversationId,
            text = text
        )
    }

    if (event == EVENT_WORKFLOW_RUN && workflowRunId != null) {
        gitHubWebhookManager.rememberWorkflowRunMessageId(
            fullName = response.repository.fullName,
            workflowRunId = workflowRunId,
            conversationId = conversationId,
            messageId = messageId
        )
    }
}

private fun WireApplicationManager.sendWorkflowRunMessage(
    gitHubWebhookManager: GitHubWebhookManager,
    repositoryFullName: String,
    workflowRunId: Long,
    conversationId: QualifiedId,
    text: String
): UUID {
    val replacingMessageId = gitHubWebhookManager.workflowRunMessageId(
        fullName = repositoryFullName,
        workflowRunId = workflowRunId,
        conversationId = conversationId
    )

    val message = replacingMessageId
        ?.let {
            WireMessage.TextEdited.create(
                replacingMessageId = it,
                conversationId = conversationId,
                text = text
            )
        }
        ?: WireMessage.Text.create(
            conversationId = conversationId,
            text = text
        )

    return sendMessage(message = message)
}

private fun WireApplicationManager.sendTextMessage(
    conversationId: QualifiedId,
    text: String
): UUID =
    sendMessage(
        message = WireMessage.Text.create(
            conversationId = conversationId,
            text = text
        )
    )

private const val EVENT_WORKFLOW_RUN = "workflow_run"
