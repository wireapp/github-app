package com.wire.github

import com.wire.github.metrics.UsageMetrics
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
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import io.ktor.client.request.header
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.koin.core.context.GlobalContext
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
                    single { PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }
                    single { UsageMetrics(registry = get<PrometheusMeterRegistry>()) }
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
    fun `given app is added to a conversation, when GET metrics, then counter is exposed`() =
        testApplication {
            application {
                module()
            }
            GlobalContext.get().get<UsageMetrics>().onAppAddedToConversation()

            val response = client.get("/metrics")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(
                response.bodyAsText().contains("githubapp_added_to_conversation_total 1.0")
            )
        }

    @Test
    fun `given help command is received, when GET metrics, then counter is exposed`() =
        testApplication {
            application {
                module()
            }
            val usageMetrics = GlobalContext.get().get<UsageMetrics>()
            usageMetrics.onHelpCommand()
            usageMetrics.onHelpCommand()

            val response = client.get("/metrics")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(
                response.bodyAsText().contains("githubapp_help_commands_total 2.0")
            )
        }

    @Test
    fun `given a delivery is rendered, when GET metrics, then funnel counters are set`() {
        val signatureValidator = mockk<SignatureValidator>()
        every { signatureValidator.isValid(any(), any(), any(), any()) } returns true

        val wireAppSdk = mockk<WireAppSdk>()
        every {
            wireAppSdk.getApplicationManager().sendMessage(message = any())
        } returns UUID.randomUUID()

        val templateHandler = mockk<TemplateHandler>()
        every {
            templateHandler.handleEvent(event = any(), response = any())
        } returns DUMMY_TEMPLATE

        loadKoinModules(
            module {
                single { signatureValidator }
                single { wireAppSdk }
                single { templateHandler }
            }
        )

        testApplication {
            application {
                module()
            }

            client.post("/${CONVERSATION_ID.id}/${CONVERSATION_ID.domain}") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", DUMMY_EVENT)
                header("X-Hub-Signature", "sha1=$DUMMY_SIGNATURE")
                header("X-GitHub-Delivery", "delivery")
                setBody(DUMMY_PAYLOAD)
            }

            val metrics = client.get("/metrics").bodyAsText()

            assertTrue(
                metrics.contains(
                    """githubapp_webhook_events_received_total{event="$DUMMY_EVENT"} 1.0"""
                ),
                "received counter missing in:\n$metrics"
            )
            assertTrue(
                metrics.contains(
                    """githubapp_notifications_sent_total{event="$DUMMY_EVENT"} 1.0"""
                ),
                "sent counter missing in:\n$metrics"
            )
        }
    }

    @Test
    fun `given no template for the event, when GET metrics, then unsupported counter is exposed`() {
        val signatureValidator = mockk<SignatureValidator>()
        every { signatureValidator.isValid(any(), any(), any(), any()) } returns true

        val wireAppSdk = mockk<WireAppSdk>(relaxed = true)

        val templateHandler = mockk<TemplateHandler>()
        every {
            templateHandler.handleEvent(event = any(), response = any())
        } returns null

        loadKoinModules(
            module {
                single { signatureValidator }
                single { wireAppSdk }
                single { templateHandler }
            }
        )

        testApplication {
            application {
                module()
            }

            val response = client.post("/${CONVERSATION_ID.id}/${CONVERSATION_ID.domain}") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", DUMMY_EVENT)
                header("X-Hub-Signature", "sha1=$DUMMY_SIGNATURE")
                header("X-GitHub-Delivery", "delivery")
                setBody(DUMMY_PAYLOAD)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            verify(exactly = 0) {
                wireAppSdk.getApplicationManager().sendMessage(message = any())
            }

            val metrics = client.get("/metrics").bodyAsText()

            assertTrue(
                metrics.contains(
                    """githubapp_unsupported_events_total{action="created",event="$DUMMY_EVENT"} 1.0"""
                ),
                "unsupported counter missing in:\n$metrics"
            )
            assertTrue(
                metrics.contains("githubapp_notifications_sent_total") == false,
                "nothing should have been sent, but sent counter exists in:\n$metrics"
            )
        }
    }

    @Test
    fun `given received event, when pull_request is created, then validations are passing`() {
        val signatureValidator = mockk<SignatureValidator>()
        every {
            signatureValidator.generateHmacSha1(
                data = DUMMY_PAYLOAD,
                secret = CONVERSATION_ID.id.toString()
            )
        } returns DUMMY_SIGNATURE
        every {
            signatureValidator.isValid(
                conversationId = CONVERSATION_ID.id.toString(),
                conversationDomain = CONVERSATION_ID.domain,
                signature = "sha1=$DUMMY_SIGNATURE",
                payload = DUMMY_PAYLOAD
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

        loadKoinModules(
            module {
                single { signatureValidator }
                single { wireAppSdk }
                single { templateHandler }
            }
        )

        testApplication {
            application {
                configureRouting()
            }

            val signature = String.format(
                "sha1=%s",
                signatureValidator.generateHmacSha1(
                    data = DUMMY_PAYLOAD,
                    secret = CONVERSATION_ID.id.toString()
                )
            )

            // Test the real route with mocked service
            val response = client.post("/${CONVERSATION_ID.id}/${CONVERSATION_ID.domain}") {
                contentType(ContentType.Application.Json)

                header("X-GitHub-Event", DUMMY_EVENT)
                header("X-Hub-Signature", signature)
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
        val DUMMY_PAYLOAD = """
            {
                "action": "created",
                "sender": {
                    "avatar_url": "dummy_url",
                    "login": "dummy_login"
                },
                "repository": {
                    "full_name": "dummy_repository_full_name",
                    "name": "repository_name"
                },
                "deleted": false
            }
        """.trimIndent()
    }
}
