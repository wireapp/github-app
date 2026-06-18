package com.wire.github

import com.wire.github.github.GitHubWebhookManager
import com.wire.github.util.SignatureValidator
import com.wire.github.util.TemplateHandler
import com.wire.sdk.WireAppSdk
import com.wire.sdk.model.QualifiedId
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.client.request.header
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

            assertEquals(HttpStatusCode.OK, response.status)
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

    private companion object {
        val CONVERSATION_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = "conv_domain"
        )
        const val DUMMY_EVENT = "pull_request"
        const val DUMMY_SIGNATURE = "dummySignature"
        const val DUMMY_TEMPLATE = "dummyTemplate"
        const val DUMMY_WEBHOOK_SECRET = "dummyWebhookSecret"
        const val REPOSITORY_FULL_NAME = "dummy_repository_full_name"
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
    }
}
