package com.wire.github

import com.wire.github.github.GitHubWebhookManager
import com.wire.github.response.model.GitHubResponse
import com.wire.github.response.model.WorkflowRun
import com.wire.sdk.WireAppSdk
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.service.WireApplicationManager
import java.util.Locale
import java.util.UUID

internal fun WireAppSdk.sendGitHubMessage(
    gitHubWebhookManager: GitHubWebhookManager,
    response: GitHubResponse,
    text: String
) {
    gitHubWebhookManager
        .conversationsForRepository(response.repository.fullName)
        .forEach { conversationId ->
            sendGitHubMessage(
                conversationId = conversationId,
                text = text
            )
        }
}

private fun WireAppSdk.sendGitHubMessage(
    conversationId: QualifiedId,
    text: String
) {
    getApplicationManager().sendTextMessage(
        conversationId = conversationId,
        text = text
    )
}

internal fun WireAppSdk.sendCommitCiMessage(
    gitHubWebhookManager: GitHubWebhookManager,
    response: GitHubResponse,
    workflowRun: WorkflowRun?
) {
    val headSha = workflowRun?.headSha ?: return
    val workflowRunId = workflowRun.id ?: return
    val workflow = GitHubWebhookManager.CommitCiWorkflow(
        runId = workflowRunId,
        name = workflowRun.name ?: "Workflow $workflowRunId",
        status = workflowRun.status,
        conclusion = workflowRun.conclusion,
        htmlUrl = workflowRun.htmlUrl
    )

    gitHubWebhookManager
        .conversationsForRepository(response.repository.fullName)
        .forEach { conversationId ->
            sendCommitCiMessage(
                gitHubWebhookManager = gitHubWebhookManager,
                context = CommitCiMessageContext(
                    response = response,
                    workflowRun = workflowRun,
                    workflow = workflow,
                    headSha = headSha,
                    conversationId = conversationId
                )
            )
        }
}

private fun WireAppSdk.sendCommitCiMessage(
    gitHubWebhookManager: GitHubWebhookManager,
    context: CommitCiMessageContext
): UUID {
    val summary = gitHubWebhookManager.updateCommitCiWorkflow(
        fullName = context.response.repository.fullName,
        headSha = context.headSha,
        conversationId = context.conversationId,
        workflow = context.workflow
    )
    val text = CommitCiMessageFormatter.toMessage(
        summary = summary,
        context = context
    )

    val messageId = getApplicationManager().sendTextMessage(
        replacingMessageId = summary.messageId,
        conversationId = context.conversationId,
        text = text
    )
    gitHubWebhookManager.rememberCommitCiMessageId(
        fullName = context.response.repository.fullName,
        headSha = context.headSha,
        conversationId = context.conversationId,
        messageId = messageId
    )

    return messageId
}

private fun WireApplicationManager.sendTextMessage(
    replacingMessageId: UUID? = null,
    conversationId: QualifiedId,
    text: String
): UUID {
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

private data class CommitCiMessageContext(
    val response: GitHubResponse,
    val workflowRun: WorkflowRun,
    val workflow: GitHubWebhookManager.CommitCiWorkflow,
    val headSha: String,
    val conversationId: QualifiedId
)

private object CommitCiMessageFormatter {
    fun toMessage(
        summary: GitHubWebhookManager.CommitCiSummary,
        context: CommitCiMessageContext
    ): String =
        buildString {
            val icon = headlineIcon(summary)
            val status = headlineStatus(summary)
            val headline = "$icon **Check suite status:** $status"

            appendLine(headline)
            appendLine()
            appendLine("📦 **Repository:** ${context.response.repository.fullName}")
            context.workflowRun.headBranch?.let { appendLine("🌿 **Branch:** $it") }
            appendLine("🔖 **Commit:** `${context.headSha.take(COMMIT_SHA_DISPLAY_LENGTH)}`")
            appendLine()
            summary.workflows.forEach { workflow ->
                appendLine(
                    "${workflow.icon()} **${workflow.name}:** " +
                        "${workflow.displayStatus()}${workflow.link()}"
                )
            }
        }.trim()

    private fun headlineIcon(summary: GitHubWebhookManager.CommitCiSummary): String =
        when {
            summary.workflows.any { it.failed() } -> "❌"
            summary.workflows.isNotEmpty() && summary.workflows.all { it.successful() } -> "✅"
            summary.workflows.all { it.completed() } -> "⚪"
            else -> "⏳"
        }

    private fun headlineStatus(summary: GitHubWebhookManager.CommitCiSummary): String =
        when {
            summary.workflows.any { it.failed() } -> "Failed"
            summary.workflows.isNotEmpty() && summary.workflows.all { it.successful() } -> "Passed"
            summary.workflows.all { it.completed() } -> "Completed"
            else -> "Running"
        }

    private fun GitHubWebhookManager.CommitCiWorkflow.displayStatus(): String =
        (conclusion ?: status ?: "unknown").humanizeStatus()

    private fun GitHubWebhookManager.CommitCiWorkflow.icon(): String =
        when {
            failed() -> "❌"
            successful() -> "✅"
            completed() -> "⚪"
            else -> "⏳"
        }

    private fun GitHubWebhookManager.CommitCiWorkflow.link(): String =
        htmlUrl?.let { " - $it" } ?: ""

    private fun GitHubWebhookManager.CommitCiWorkflow.successful(): Boolean =
        conclusion == WORKFLOW_CONCLUSION_SUCCESS

    private fun GitHubWebhookManager.CommitCiWorkflow.failed(): Boolean =
        conclusion in WORKFLOW_FAILURE_CONCLUSIONS

    private fun GitHubWebhookManager.CommitCiWorkflow.completed(): Boolean =
        status == WORKFLOW_STATUS_COMPLETED || conclusion != null

    private fun String.humanizeStatus(): String =
        split("_")
            .joinToString(" ") { word ->
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }
}

private const val COMMIT_SHA_DISPLAY_LENGTH = 12
private const val WORKFLOW_CONCLUSION_SUCCESS = "success"
private const val WORKFLOW_STATUS_COMPLETED = "completed"

private val WORKFLOW_FAILURE_CONCLUSIONS = setOf(
    "failure",
    "error",
    "timed_out",
    "action_required",
    "startup_failure"
)
