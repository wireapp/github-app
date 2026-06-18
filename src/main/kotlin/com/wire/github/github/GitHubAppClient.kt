package com.wire.github.github

import com.wire.github.util.ENV_VAR_GITHUB_CLIENT_ID
import com.wire.github.util.ENV_VAR_GITHUB_PRIVATE_KEY
import com.wire.github.util.KtxSerializer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Suppress("TooManyFunctions")
class GitHubAppClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) {
    fun ensureRepositoryWebhook(
        repository: GitHubRepository,
        webhookUrl: String,
        webhookSecret: String
    ): Long {
        val token = createInstallationAccessToken(repository)
        val existingHook = findRepositoryWebhook(
            repository = repository,
            webhookUrl = webhookUrl,
            token = token
        )

        return existingHook ?: createRepositoryWebhook(
            repository = repository,
            webhookUrl = webhookUrl,
            webhookSecret = webhookSecret,
            token = token
        )
    }

    fun deleteRepositoryWebhook(
        repository: GitHubRepository,
        webhookId: Long
    ) {
        val token = createInstallationAccessToken(repository)
        val response = send(
            request = requestBuilder(
                "/repos/${repository.owner}/${repository.name}/hooks/$webhookId"
            ).DELETE()
                .withBearer(token)
                .build()
        )

        require(response.statusCode() in SUCCESS_STATUS_CODES) {
            "GitHub failed to delete webhook for ${repository.fullName}: " +
                "${response.statusCode()} ${response.body()}"
        }
    }

    private fun findRepositoryWebhook(
        repository: GitHubRepository,
        webhookUrl: String,
        token: String
    ): Long? {
        val response = send(
            request = requestBuilder(
                "/repos/${repository.owner}/${repository.name}/hooks?per_page=$MAX_HOOKS_PAGE_SIZE"
            ).GET()
                .withBearer(token)
                .build()
        )

        require(response.statusCode() in SUCCESS_STATUS_CODES) {
            "GitHub failed to list webhooks for ${repository.fullName}: " +
                "${response.statusCode()} ${response.body()}"
        }

        return KtxSerializer.json
            .parseToJsonElement(response.body())
            .jsonArray
            .firstNotNullOfOrNull { hook ->
                hook.jsonObject
                    .takeIf { it.webhookUrl() == webhookUrl }
                    ?.get("id")
                    ?.jsonPrimitive
                    ?.longOrNull
            }
    }

    private fun createRepositoryWebhook(
        repository: GitHubRepository,
        webhookUrl: String,
        webhookSecret: String,
        token: String
    ): Long {
        val body = buildJsonObject {
            put("name", WEBHOOK_NAME)
            put("active", true)
            put("events", pullRequestEvents())
            putJsonObject("config") {
                put("url", webhookUrl)
                put("content_type", WEBHOOK_CONTENT_TYPE)
                put("secret", webhookSecret)
                put("insecure_ssl", WEBHOOK_VERIFY_SSL)
            }
        }.toString()

        val response = send(
            request = requestBuilder("/repos/${repository.owner}/${repository.name}/hooks")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .withBearer(token)
                .header("Content-Type", "application/json")
                .build()
        )

        require(response.statusCode() in SUCCESS_STATUS_CODES) {
            "GitHub failed to create webhook for ${repository.fullName}: " +
                "${response.statusCode()} ${response.body()}"
        }

        return KtxSerializer.json
            .parseToJsonElement(response.body())
            .jsonObject["id"]
            ?.jsonPrimitive
            ?.long
            ?: error("GitHub webhook creation response did not include an id")
    }

    private fun createInstallationAccessToken(repository: GitHubRepository): String {
        val installationId = getRepositoryInstallationId(repository)
        val response = send(
            request = requestBuilder("/app/installations/$installationId/access_tokens")
                .POST(HttpRequest.BodyPublishers.noBody())
                .withBearer(generateJwt())
                .build()
        )

        require(response.statusCode() in SUCCESS_STATUS_CODES) {
            "GitHub failed to create installation token for ${repository.fullName}: " +
                "${response.statusCode()} ${response.body()}"
        }

        return KtxSerializer.json
            .parseToJsonElement(response.body())
            .jsonObject["token"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: error("GitHub installation token response did not include a token")
    }

    private fun getRepositoryInstallationId(repository: GitHubRepository): Long {
        val response = send(
            request = requestBuilder("/repos/${repository.owner}/${repository.name}/installation")
                .GET()
                .withBearer(generateJwt())
                .build()
        )

        require(response.statusCode() in SUCCESS_STATUS_CODES) {
            "GitHub failed to resolve app installation for ${repository.fullName}: " +
                "${response.statusCode()} ${response.body()}"
        }

        return KtxSerializer.json
            .parseToJsonElement(response.body())
            .jsonObject["id"]
            ?.jsonPrimitive
            ?.long
            ?: error("GitHub repository installation response did not include an id")
    }

    private fun generateJwt(): String {
        val now = Instant.now().epochSecond
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val expiresAt = now + JWT_EXPIRATION_SECONDS
        val issuedAt = now - JWT_CLOCK_DRIFT_SECONDS
        val payload = base64Url(
            """{"iat":$issuedAt,"exp":$expiresAt,"iss":"${githubClientId()}"}"""
                .toByteArray()
        )
        val headerAndPayload = "$header.$payload"
        val signature = Signature
            .getInstance("SHA256withRSA")
            .apply { initSign(githubPrivateKey()) }
            .run {
                update(headerAndPayload.toByteArray())
                base64Url(sign())
            }

        return "$headerAndPayload.$signature"
    }

    private fun requestBuilder(path: String): HttpRequest.Builder =
        HttpRequest
            .newBuilder(URI.create("$GITHUB_API_URL$path"))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", USER_AGENT)

    private fun HttpRequest.Builder.withBearer(token: String): HttpRequest.Builder =
        header("Authorization", "Bearer $token")

    private fun send(request: HttpRequest): HttpResponse<String> =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())

    private fun githubClientId(): String =
        requireNotNull(ENV_VAR_GITHUB_CLIENT_ID) {
            "GHAPP_GITHUB_CLIENT_ID must be set to provision GitHub repository webhooks"
        }

    private fun githubPrivateKey(): RSAPrivateKey =
        requireNotNull(ENV_VAR_GITHUB_PRIVATE_KEY) {
            "GHAPP_GITHUB_PRIVATE_KEY must be set to provision GitHub repository webhooks"
        }.toRsaPrivateKey()

    private fun String.toRsaPrivateKey(): RSAPrivateKey {
        val normalized = replace("\\n", "\n")
        val keyBytes = normalized
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString(separator = "")
            .let { Base64.getDecoder().decode(it) }
            .let { decodedKey ->
                if (normalized.contains("BEGIN RSA PRIVATE KEY")) {
                    wrapPkcs1PrivateKey(decodedKey)
                } else {
                    decodedKey
                }
            }

        return KeyFactory
            .getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes)) as RSAPrivateKey
    }

    private fun wrapPkcs1PrivateKey(pkcs1: ByteArray): ByteArray =
        derSequence(
            derIntegerZero(),
            derSequence(
                derObjectIdentifier(RSA_ENCRYPTION_OBJECT_IDENTIFIER),
                derNull()
            ),
            derOctetString(pkcs1)
        )

    private fun derSequence(vararg values: ByteArray): ByteArray =
        derValue(tag = DER_SEQUENCE_TAG, value = values.flatMap { it.asIterable() }.toByteArray())

    private fun derIntegerZero(): ByteArray =
        derValue(tag = DER_INTEGER_TAG, value = byteArrayOf(0))

    private fun derObjectIdentifier(value: ByteArray): ByteArray =
        derValue(tag = DER_OBJECT_IDENTIFIER_TAG, value = value)

    private fun derNull(): ByteArray = derValue(tag = DER_NULL_TAG, value = byteArrayOf())

    private fun derOctetString(value: ByteArray): ByteArray =
        derValue(tag = DER_OCTET_STRING_TAG, value = value)

    private fun derValue(
        tag: Byte,
        value: ByteArray
    ): ByteArray = byteArrayOf(tag) + derLength(value.size) + value

    private fun derLength(length: Int): ByteArray =
        when {
            length < DER_SHORT_FORM_LENGTH_LIMIT -> byteArrayOf(length.toByte())
            else -> {
                val lengthBytes = generateSequence(length) { it shr BITS_PER_BYTE }
                    .takeWhile { it > 0 }
                    .map { it.toByte() }
                    .toList()
                    .asReversed()
                    .toByteArray()
                byteArrayOf((DER_LONG_FORM_LENGTH_MASK or lengthBytes.size).toByte()) + lengthBytes
            }
        }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun JsonObject.webhookUrl(): String? =
        this["config"]
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull

    private fun pullRequestEvents(): JsonArray =
        buildJsonArray {
            add(JsonPrimitive("pull_request"))
            add(JsonPrimitive("pull_request_review"))
            add(JsonPrimitive("pull_request_review_comment"))
        }

    private companion object {
        const val GITHUB_API_URL = "https://api.github.com"
        const val GITHUB_API_VERSION = "2022-11-28"
        const val USER_AGENT = "wire-github-app"
        const val WEBHOOK_NAME = "web"
        const val WEBHOOK_CONTENT_TYPE = "json"
        const val WEBHOOK_VERIFY_SSL = "0"
        const val MAX_HOOKS_PAGE_SIZE = 100
        const val JWT_CLOCK_DRIFT_SECONDS = 60L
        const val JWT_EXPIRATION_SECONDS = 540L
        const val DER_SHORT_FORM_LENGTH_LIMIT = 128
        const val BITS_PER_BYTE = 8
        const val DER_LONG_FORM_LENGTH_MASK = 0x80
        const val DER_SEQUENCE_TAG = 0x30.toByte()
        const val DER_INTEGER_TAG = 0x02.toByte()
        const val DER_OBJECT_IDENTIFIER_TAG = 0x06.toByte()
        const val DER_NULL_TAG = 0x05.toByte()
        const val DER_OCTET_STRING_TAG = 0x04.toByte()

        val SUCCESS_STATUS_CODES = 200..299

        val RSA_ENCRYPTION_OBJECT_IDENTIFIER = byteArrayOf(
            0x2a,
            0x86.toByte(),
            0x48,
            0x86.toByte(),
            0xf7.toByte(),
            0x0d,
            0x01,
            0x01,
            0x01
        )
    }
}
