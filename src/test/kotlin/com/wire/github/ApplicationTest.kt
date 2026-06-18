package com.wire.github

import com.wire.github.github.GitHubWebhookManager
import com.wire.github.util.SignatureValidator
import com.wire.github.util.TemplateHandler
import com.wire.sdk.WireAppSdk
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.service.WireApplicationManager
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import io.mockk.every
import io.mockk.mockk
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertIs

class ApplicationTest {
    @BeforeTest
    fun setupKoin() {
        val mockRedisClient = mockk<RedisClient>()
        val mockRedisCommands = mockk<RedisCommands<String, String>>()
        val mockRedisConnection = mockk<StatefulRedisConnection<String, String>>()
        val mockWireAppSdk = mockk<WireAppSdk>(relaxed = true)
        val mockGitHubWebhookManager = mockk<GitHubWebhookManager>(relaxed = true)

        // Configure the Redis connection mock to return the sync commands
        every { mockRedisConnection.sync() } returns mockRedisCommands

        startKoin {
            modules(
                module {
                    single { SignatureValidator() }
                    single { TemplateHandler() }
                    single { mockRedisClient }
                    single<StatefulRedisConnection<String, String>> { mockRedisConnection }
                    single { mockWireAppSdk }
                    single { mockGitHubWebhookManager }
                }
            )
        }
    }

    @AfterTest
    fun stopKoinAfter() {
        stopKoin()
    }

    @Test
    fun `given service is available, when GET health, then should return HTTP Status 200`() =
        testApplication {
            application {
                module()
            }
            val response = client.get("/health")

            assertEquals(
                HttpStatusCode.OK,
                response.status,
                response.bodyAsText()
            )
        }

    @Test
    fun `given received event, when pull_request is created, then validations are passing`() {
        val signatureValidator = mockk<SignatureValidator>()
        every {
            signatureValidator.generateHmacSha256(
                data = DUMMY_PAYLOAD,
                secret = DUMMY_WEBHOOK_SECRET
            )
        } returns DUMMY_SIGNATURE
        every {
            signatureValidator.isValid(
                signature = "sha256=$DUMMY_SIGNATURE",
                payload = DUMMY_PAYLOAD,
                secret = DUMMY_WEBHOOK_SECRET
            )
        } returns true

        val wireAppSdk = mockk<WireAppSdk>()
        every {
            wireAppSdk.getApplicationManager().sendMessage(
                message = any()
            )
        } returns UUID.randomUUID()

        val templateHandler = mockk<TemplateHandler>()
        every {
            templateHandler.handleEvent(
                event = any(),
                response = any()
            )
        } returns DUMMY_TEMPLATE

        val gitHubWebhookManager = mockk<GitHubWebhookManager>()
        every {
            gitHubWebhookManager.conversationsForRepository(REPOSITORY_FULL_NAME)
        } returns listOf(CONVERSATION_ID)
        justRun { gitHubWebhookManager.markRepositoryActive(REPOSITORY_FULL_NAME) }

        loadKoinModules(
            module {
                single { signatureValidator }
                single { wireAppSdk }
                single { templateHandler }
                single { gitHubWebhookManager }
            }
        )

        testApplication {
            application {
                configureRouting()
            }

            val signature = String.format(
                "sha256=%s",
                signatureValidator.generateHmacSha256(
                    data = DUMMY_PAYLOAD,
                    secret = DUMMY_WEBHOOK_SECRET
                )
            )

            // Test the real route with mocked service
            val response = client.post("/github/webhook") {
                contentType(ContentType.Application.Json)

                header("X-GitHub-Event", DUMMY_EVENT)
                header("X-Hub-Signature-256", signature)
                header("X-GitHub-Delivery", "delivery")

                setBody(DUMMY_PAYLOAD)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            verify(exactly = 1) {
                wireAppSdk.getApplicationManager().sendMessage(
                    message = any()
                )
            }
        }
    }

    @Test
    fun `given workflow run has no stored message, then sends text message and stores id`() {
        val signatureValidator = mockk<SignatureValidator>()
        every {
            signatureValidator.isValid(
                signature = any(),
                payload = WORKFLOW_RUN_PAYLOAD,
                secret = DUMMY_WEBHOOK_SECRET
            )
        } returns true

        val sentMessageId = UUID.randomUUID()
        val applicationManager = mockk<WireApplicationManager>()
        val sentMessage = io.mockk.slot<WireMessage>()
        every {
            applicationManager.sendMessage(
                message = capture(sentMessage)
            )
        } returns sentMessageId

        val wireAppSdk = mockk<WireAppSdk>()
        every { wireAppSdk.getApplicationManager() } returns applicationManager

        val templateHandler = mockk<TemplateHandler>()
        every {
            templateHandler.handleEvent(
                event = EVENT_WORKFLOW_RUN,
                response = any()
            )
        } returns DUMMY_TEMPLATE

        val gitHubWebhookManager = mockk<GitHubWebhookManager>()
        every {
            gitHubWebhookManager.conversationsForRepository(REPOSITORY_FULL_NAME)
        } returns listOf(CONVERSATION_ID)
        justRun { gitHubWebhookManager.markRepositoryActive(REPOSITORY_FULL_NAME) }
        every {
            gitHubWebhookManager.workflowRunMessageId(
                fullName = REPOSITORY_FULL_NAME,
                workflowRunId = WORKFLOW_RUN_ID,
                conversationId = CONVERSATION_ID
            )
        } returns null
        justRun {
            gitHubWebhookManager.rememberWorkflowRunMessageId(
                fullName = REPOSITORY_FULL_NAME,
                workflowRunId = WORKFLOW_RUN_ID,
                conversationId = CONVERSATION_ID,
                messageId = sentMessageId
            )
        }

        loadKoinModules(
            module {
                single { signatureValidator }
                single { wireAppSdk }
                single { templateHandler }
                single { gitHubWebhookManager }
            }
        )

        testApplication {
            application {
                configureRouting()
            }

            val response = client.post("/github/webhook") {
                contentType(ContentType.Application.Json)

                header("X-GitHub-Event", EVENT_WORKFLOW_RUN)
                header("X-Hub-Signature-256", "sha256=$DUMMY_SIGNATURE")
                header("X-GitHub-Delivery", "delivery")

                setBody(WORKFLOW_RUN_PAYLOAD)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertIs<WireMessage.Text>(sentMessage.captured)
            verify(exactly = 1) {
                gitHubWebhookManager.rememberWorkflowRunMessageId(
                    fullName = REPOSITORY_FULL_NAME,
                    workflowRunId = WORKFLOW_RUN_ID,
                    conversationId = CONVERSATION_ID,
                    messageId = sentMessageId
                )
            }
        }
    }

    @Test
    fun `given workflow run has stored message, then edits message and stores new id`() {
        val signatureValidator = mockk<SignatureValidator>()
        every {
            signatureValidator.isValid(
                signature = any(),
                payload = WORKFLOW_RUN_PAYLOAD,
                secret = DUMMY_WEBHOOK_SECRET
            )
        } returns true

        val storedMessageId = UUID.randomUUID()
        val editedMessageId = UUID.randomUUID()
        val applicationManager = mockk<WireApplicationManager>()
        val sentMessage = io.mockk.slot<WireMessage>()
        every {
            applicationManager.sendMessage(
                message = capture(sentMessage)
            )
        } returns editedMessageId

        val wireAppSdk = mockk<WireAppSdk>()
        every { wireAppSdk.getApplicationManager() } returns applicationManager

        val templateHandler = mockk<TemplateHandler>()
        every {
            templateHandler.handleEvent(
                event = EVENT_WORKFLOW_RUN,
                response = any()
            )
        } returns DUMMY_TEMPLATE

        val gitHubWebhookManager = mockk<GitHubWebhookManager>()
        every {
            gitHubWebhookManager.conversationsForRepository(REPOSITORY_FULL_NAME)
        } returns listOf(CONVERSATION_ID)
        justRun { gitHubWebhookManager.markRepositoryActive(REPOSITORY_FULL_NAME) }
        every {
            gitHubWebhookManager.workflowRunMessageId(
                fullName = REPOSITORY_FULL_NAME,
                workflowRunId = WORKFLOW_RUN_ID,
                conversationId = CONVERSATION_ID
            )
        } returns storedMessageId
        justRun {
            gitHubWebhookManager.rememberWorkflowRunMessageId(
                fullName = REPOSITORY_FULL_NAME,
                workflowRunId = WORKFLOW_RUN_ID,
                conversationId = CONVERSATION_ID,
                messageId = editedMessageId
            )
        }

        loadKoinModules(
            module {
                single { signatureValidator }
                single { wireAppSdk }
                single { templateHandler }
                single { gitHubWebhookManager }
            }
        )

        testApplication {
            application {
                configureRouting()
            }

            val response = client.post("/github/webhook") {
                contentType(ContentType.Application.Json)

                header("X-GitHub-Event", EVENT_WORKFLOW_RUN)
                header("X-Hub-Signature-256", "sha256=$DUMMY_SIGNATURE")
                header("X-GitHub-Delivery", "delivery")

                setBody(WORKFLOW_RUN_PAYLOAD)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val editedMessage = assertIs<WireMessage.TextEdited>(sentMessage.captured)
            assertEquals(storedMessageId, editedMessage.replacingMessageId)
            verify(exactly = 1) {
                gitHubWebhookManager.rememberWorkflowRunMessageId(
                    fullName = REPOSITORY_FULL_NAME,
                    workflowRunId = WORKFLOW_RUN_ID,
                    conversationId = CONVERSATION_ID,
                    messageId = editedMessageId
                )
            }
        }
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = "conv_domain"
        )
        const val DUMMY_EVENT = "pull_request"
        const val EVENT_WORKFLOW_RUN = "workflow_run"
        const val DUMMY_SIGNATURE = "dummySignature"
        const val DUMMY_TEMPLATE = "dummyTemplate"
        const val DUMMY_WEBHOOK_SECRET = "dummyWebhookSecret"
        const val REPOSITORY_FULL_NAME = "dummy_repository_full_name"
        const val WORKFLOW_RUN_ID = 1234L
        val DUMMY_PAYLOAD = """
            {
                "action": "created",
                "sender": {
                    "avatar_url": "dummy_url",
                    "login": "dummy_login"
                },
                "repository": {
                    "full_name": "$REPOSITORY_FULL_NAME",
                    "name": "repository_name"
                },
                "deleted": false
            }
        """.trimIndent()
        val WORKFLOW_RUN_PAYLOAD = """
            {
                "action": "in_progress",
                "workflow_run": {
                    "id": $WORKFLOW_RUN_ID,
                    "name": "Build",
                    "html_url": "https://github.com/wireapp/github-app/actions/runs/$WORKFLOW_RUN_ID",
                    "head_branch": "main",
                    "head_sha": "1234567890abcdef",
                    "status": "in_progress",
                    "conclusion": null
                },
                "sender": {
                    "avatar_url": "dummy_url",
                    "login": "dummy_login"
                },
                "repository": {
                    "full_name": "$REPOSITORY_FULL_NAME",
                    "name": "repository_name"
                }
            }
        """.trimIndent()
    }
}
