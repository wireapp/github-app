package com.wire.github

import com.wire.github.config.projectModules
import com.wire.github.util.SignatureValidator
import com.wire.github.util.TemplateHandler
import com.wire.integrations.jvm.WireAppSdk
import com.wire.integrations.jvm.model.QualifiedId
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertEquals
import io.ktor.client.request.header
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class ApplicationTest {
    @BeforeTest
    fun setupKoin() {
        startKoin {
            modules(projectModules)
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
        const val DUMMY_PAYLOAD = "{\"action\": \"created\", \"deleted\": false}"
        const val DUMMY_SIGNATURE = "dummySignature"
        const val DUMMY_TEMPLATE = "dummyTemplate"
    }
}
