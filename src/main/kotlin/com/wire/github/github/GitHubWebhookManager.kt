package com.wire.github.github

import com.wire.github.util.ENV_VAR_GITHUB_REPO_INACTIVITY_SECONDS
import com.wire.github.util.ENV_VAR_GITHUB_WEBHOOK_SECRET
import com.wire.github.util.ENV_VAR_HOST
import com.wire.github.util.KtxSerializer
import com.wire.github.util.toStorageKey
import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.StatefulRedisConnection
import java.time.Clock
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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

    data class RegistrationResult(
        val newlySubscribed: Boolean,
        val webhookId: Long
    )

    data class CommitCiSummary(
        val messageId: UUID?,
        val workflows: List<CommitCiWorkflow>
    )

    @Serializable
    data class CommitCiWorkflow(
        val runId: Long,
        val name: String,
        val status: String?,
        val conclusion: String?,
        val htmlUrl: String?
    )

    fun ensureWebhookForConversation(
        repository: GitHubRepository,
        conversationId: QualifiedId
    ): RegistrationResult {
        storage.sadd(KNOWN_REPOSITORIES_KEY, repository.fullName)

        val newlySubscribed =
            storage.sadd(
                conversationsKey(repository.fullName),
                conversationId.toStorageKey()
            ) == 1L

        markRepositoryActive(repository.fullName)

        val existingWebhookId = storage.get(webhookIdKey(repository.fullName))?.toLongOrNull()
        if (existingWebhookId != null) {
            return RegistrationResult(
                newlySubscribed = newlySubscribed,
                webhookId = existingWebhookId
            )
        }

        val webhookId = gitHubAppClient.ensureRepositoryWebhook(
            repository = repository,
            webhookUrl = webhookUrl(),
            webhookSecret = githubWebhookSecret()
        )

        storage.set(webhookIdKey(repository.fullName), webhookId.toString())

        return RegistrationResult(
            newlySubscribed = newlySubscribed,
            webhookId = webhookId
        )
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

    fun updateCommitCiWorkflow(
        fullName: String,
        headSha: String,
        conversationId: QualifiedId,
        workflow: CommitCiWorkflow
    ): CommitCiSummary {
        val workflowKey = commitCiWorkflowKey(fullName, headSha, conversationId, workflow.runId)

        storage.set(workflowKey, KtxSerializer.json.encodeToString(workflow))
        storage.sadd(
            commitCiWorkflowIdsKey(fullName, headSha, conversationId),
            workflow.runId.toString()
        )
        rememberCommitCiKey(fullName, workflowKey)
        rememberCommitCiKey(
            fullName,
            commitCiWorkflowIdsKey(fullName, headSha, conversationId)
        )

        return CommitCiSummary(
            messageId = commitCiMessageId(fullName, headSha, conversationId),
            workflows = commitCiWorkflows(fullName, headSha, conversationId)
        )
    }

    fun rememberCommitCiMessageId(
        fullName: String,
        headSha: String,
        conversationId: QualifiedId,
        messageId: UUID
    ) {
        val key = commitCiMessageIdKey(fullName, headSha, conversationId)
        storage.set(key, messageId.toString())
        rememberCommitCiKey(fullName, key)
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
        storage.smembers(commitCiKeysKey(fullName)).forEach { key ->
            storage.del(key)
        }
        storage.del(commitCiKeysKey(fullName))
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

    private fun commitCiMessageId(
        fullName: String,
        headSha: String,
        conversationId: QualifiedId
    ): UUID? =
        storage
            .get(commitCiMessageIdKey(fullName, headSha, conversationId))
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun commitCiWorkflows(
        fullName: String,
        headSha: String,
        conversationId: QualifiedId
    ): List<CommitCiWorkflow> {
        val workflows = storage
            .smembers(commitCiWorkflowIdsKey(fullName, headSha, conversationId))
            .mapNotNull { workflowRunId ->
                val runId = workflowRunId.toLongOrNull() ?: return@mapNotNull null
                val workflowKey = commitCiWorkflowKey(fullName, headSha, conversationId, runId)
                val workflow = storage.get(workflowKey) ?: return@mapNotNull null

                runCatching {
                    KtxSerializer.json.decodeFromString<CommitCiWorkflow>(workflow)
                }.getOrNull()
            }

        return workflows.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun rememberCommitCiKey(
        fullName: String,
        key: String
    ) {
        storage.sadd(commitCiKeysKey(fullName), key)
    }

    private fun commitCiMessageIdKey(
        fullName: String,
        headSha: String,
        conversationId: QualifiedId
    ): String =
        "$REPOSITORY_KEY_PREFIX:$fullName:commit_ci:$headSha:message:" +
            conversationId.toStorageKey()

    private fun commitCiWorkflowIdsKey(
        fullName: String,
        headSha: String,
        conversationId: QualifiedId
    ): String =
        "$REPOSITORY_KEY_PREFIX:$fullName:commit_ci:$headSha:workflows:" +
            conversationId.toStorageKey()

    private fun commitCiWorkflowKey(
        fullName: String,
        headSha: String,
        conversationId: QualifiedId,
        workflowRunId: Long
    ): String =
        "$REPOSITORY_KEY_PREFIX:$fullName:commit_ci:$headSha:workflow:$workflowRunId:" +
            conversationId.toStorageKey()

    private fun commitCiKeysKey(fullName: String): String =
        "$REPOSITORY_KEY_PREFIX:$fullName:commit_ci_keys"

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
