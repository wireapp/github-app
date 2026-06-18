package com.wire.github.github

import com.wire.github.util.ENV_VAR_GITHUB_REPO_INACTIVITY_SECONDS
import com.wire.github.util.ENV_VAR_GITHUB_WEBHOOK_SECRET
import com.wire.github.util.ENV_VAR_HOST
import com.wire.github.util.toStorageKey
import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.StatefulRedisConnection
import java.time.Clock
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class GitHubWebhookManager(
    private val gitHubAppClient: GitHubAppClient = GitHubAppClient(),
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val redisConnection = GlobalContext.get().get<StatefulRedisConnection<String, String>>()
    private val storage = redisConnection.sync()
    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "github-webhook-cleanup").apply { isDaemon = true }
    }

    init {
        cleanupExecutor.scheduleWithFixedDelay(
            ::cleanupInactiveRepositoriesSafely,
            cleanupIntervalSeconds(),
            cleanupIntervalSeconds(),
            TimeUnit.SECONDS
        )
    }

    fun ensureWebhookForConversation(
        repository: GitHubRepository,
        conversationId: QualifiedId
    ): Long {
        storage.sadd(KNOWN_REPOSITORIES_KEY, repository.fullName)
        storage.sadd(conversationsKey(repository.fullName), conversationId.toStorageKey())
        markRepositoryActive(repository.fullName)

        return gitHubAppClient
            .ensureRepositoryWebhook(
                repository = repository,
                webhookUrl = webhookUrl(),
                webhookSecret = githubWebhookSecret()
            ).also { webhookId ->
                storage.set(webhookIdKey(repository.fullName), webhookId.toString())
            }
    }

    fun conversationsForRepository(fullName: String): List<QualifiedId> =
        storage
            .smembers(conversationsKey(fullName))
            .mapNotNull { storageKey ->
                val (id, domain) = storageKey.split("@").takeIf { it.size == STORAGE_KEY_PARTS }
                    ?: return@mapNotNull null

                runCatching {
                    QualifiedId(
                        id = UUID.fromString(id),
                        domain = domain
                    )
                }.getOrNull()
            }

    fun markRepositoryActive(fullName: String) {
        storage.set(lastActivityKey(fullName), clock.millis().toString())
    }

    fun cleanupInactiveRepositories() {
        val cutoff = clock.millis() - ENV_VAR_GITHUB_REPO_INACTIVITY_SECONDS * MILLIS_PER_SECOND
        storage.smembers(KNOWN_REPOSITORIES_KEY).forEach { fullName ->
            val lastActivity = storage.get(lastActivityKey(fullName))?.toLongOrNull() ?: 0L
            if (lastActivity <= cutoff) {
                removeRepositoryWebhook(fullName)
            }
        }
    }

    private fun removeRepositoryWebhook(fullName: String) {
        val repository = GitHubRepository.fromFullName(fullName)
        val webhookId = storage.get(webhookIdKey(fullName))?.toLongOrNull()

        if (repository != null && webhookId != null) {
            gitHubAppClient.deleteRepositoryWebhook(
                repository = repository,
                webhookId = webhookId
            )
        }

        storage.srem(KNOWN_REPOSITORIES_KEY, fullName)
        storage.del(webhookIdKey(fullName))
        storage.del(conversationsKey(fullName))
        storage.del(lastActivityKey(fullName))
        logger.info("Removed inactive GitHub webhook subscription for repository: $fullName")
    }

    private fun cleanupInactiveRepositoriesSafely() {
        runCatching { cleanupInactiveRepositories() }
            .onFailure { exception ->
                logger.warn("GitHub webhook cleanup failed", exception)
            }
    }

    private fun webhookUrl(): String = "${ENV_VAR_HOST.trimEnd('/')}/$GITHUB_WEBHOOK_PATH"

    private fun githubWebhookSecret(): String =
        requireNotNull(ENV_VAR_GITHUB_WEBHOOK_SECRET) {
            "GHAPP_GITHUB_WEBHOOK_SECRET must be set to provision GitHub repository webhooks"
        }

    private fun cleanupIntervalSeconds(): Long =
        (ENV_VAR_GITHUB_REPO_INACTIVITY_SECONDS / CLEANUP_INTERVAL_DIVISOR)
            .coerceIn(MIN_CLEANUP_INTERVAL_SECONDS, MAX_CLEANUP_INTERVAL_SECONDS)

    private fun webhookIdKey(fullName: String): String =
        "$REPOSITORY_KEY_PREFIX:$fullName:webhook_id"

    private fun conversationsKey(fullName: String): String =
        "$REPOSITORY_KEY_PREFIX:$fullName:conversations"

    private fun lastActivityKey(fullName: String): String =
        "$REPOSITORY_KEY_PREFIX:$fullName:last_activity"

    companion object {
        const val GITHUB_WEBHOOK_PATH = "github/webhook"

        private const val KNOWN_REPOSITORIES_KEY = "github:repositories"
        private const val REPOSITORY_KEY_PREFIX = "github:repository"
        private const val STORAGE_KEY_PARTS = 2
        private const val MILLIS_PER_SECOND = 1000L
        private const val CLEANUP_INTERVAL_DIVISOR = 2
        private const val MIN_CLEANUP_INTERVAL_SECONDS = 60L
        private const val MAX_CLEANUP_INTERVAL_SECONDS = 3600L
    }
}
