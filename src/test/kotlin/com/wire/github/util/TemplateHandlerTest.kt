package com.wire.github.util

import com.wire.github.TestFixtures
import com.wire.github.response.model.Commit
import com.wire.github.response.model.GitHubResponse
import com.wire.github.response.model.PullRequest
import com.wire.github.response.model.Repository
import com.wire.github.response.model.Review
import com.wire.github.response.model.User
import com.wire.github.response.model.WorkflowJob
import com.wire.github.response.model.WorkflowRun
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TemplateHandlerTest {
    private val templateHandler = TemplateHandler()

    @Test
    fun `does not render a submitted pull request review without a body`() {
        val message = templateHandler.handleEvent(
            event = "pull_request_review",
            response = reviewResponse(body = null)
        )

        assertNull(message)
    }

    @Test
    fun `does not render a submitted pull request review with a blank body`() {
        val message = templateHandler.handleEvent(
            event = "pull_request_review",
            response = reviewResponse(body = "")
        )

        assertNull(message)
    }

    @Test
    fun `renders a submitted pull request review with a body`() {
        val message = templateHandler.handleEvent(
            event = "pull_request_review",
            response = reviewResponse(body = "Looks good")
        )

        assertContains(message.orEmpty(), "Looks good")
        assertContains(message.orEmpty(), "✅")
    }

    @Test
    fun `renders a note emoji for a commented pull request review`() {
        val message = templateHandler.handleEvent(
            event = "pull_request_review",
            response = reviewResponse(body = "A comment", state = "commented")
        )

        assertContains(message.orEmpty(), "📝")
    }

    @Test
    fun `renders a change emoji for a pull request review with requested changes`() {
        val message = templateHandler.handleEvent(
            event = "pull_request_review",
            response = reviewResponse(body = "Please update this", state = "changes_requested")
        )

        assertContains(message.orEmpty(), "🔄")
    }

    @Test
    fun `does not render a push with no commits`() {
        val message = templateHandler.handleEvent(
            event = "push",
            response = pushResponse(commits = emptyList())
        )

        assertNull(message)
    }

    @Test
    fun `renders a push with commits`() {
        val message = templateHandler.handleEvent(
            event = "push",
            response = pushResponse(commits = listOf(Commit(message = "Add feature")))
        )

        assertContains(message.orEmpty(), "Add feature")
    }

    @Test
    fun `renders a completed workflow run`() {
        val message = renderFixture(event = "workflow_run")

        assertEquals(messageFixture("workflow_run.completed"), message?.trim())
    }

    @Test
    fun `renders a completed workflow job`() {
        val message = renderFixture(event = "workflow_job")

        assertEquals(messageFixture("workflow_job.completed"), message?.trim())
    }

    @Test
    fun `does not render a workflow run for an unsupported action`() {
        val response = KtxSerializer.json.decodeFromString<GitHubResponse>(
            TestFixtures
                .event("workflow_run.completed")
                .replace("\"action\": \"completed\"", "\"action\": \"in_progress\"")
        )

        val message = templateHandler.handleEvent(
            event = "workflow_run",
            response = response
        )

        assertNull(message)
    }

    @Test
    fun `does not render a workflow job for an unsupported action`() {
        val response = KtxSerializer.json.decodeFromString<GitHubResponse>(
            TestFixtures
                .event("workflow_job.completed")
                .replace("\"action\": \"completed\"", "\"action\": \"in_progress\"")
        )

        val message = templateHandler.handleEvent(
            event = "workflow_job",
            response = response
        )

        assertNull(message)
    }

    @Test
    fun `maps workflow run conclusions to emojis`() {
        mapOf(
            "success" to ("✅" to "succeeded"),
            "failure" to ("❌" to "failed"),
            "cancelled" to ("🚫" to "cancelled"),
            "timed_out" to ("⏱️" to "timed out"),
            "action_required" to ("⚠️" to "requires action"),
            "skipped" to ("⏭️" to "skipped"),
            "stale" to ("💤" to "stale"),
            "neutral" to ("ℹ️" to "completed")
        ).forEach { (conclusion, presentation) ->
            assertEquals(presentation.first, workflowRun(conclusion).emoji)
            assertEquals(presentation.second, workflowRun(conclusion).conclusionText)
        }
    }

    @Test
    fun `maps workflow job conclusions to emojis`() {
        mapOf(
            "success" to ("✅" to "succeeded"),
            "failure" to ("❌" to "failed"),
            "cancelled" to ("🚫" to "cancelled"),
            "timed_out" to ("⏱️" to "timed out"),
            "action_required" to ("⚠️" to "requires action"),
            "skipped" to ("⏭️" to "skipped"),
            "stale" to ("💤" to "stale"),
            "neutral" to ("ℹ️" to "completed")
        ).forEach { (conclusion, presentation) ->
            assertEquals(presentation.first, workflowJob(conclusion).emoji)
            assertEquals(presentation.second, workflowJob(conclusion).conclusionText)
        }
    }

    @Test
    fun `renders a completed workflow run without optional fields`() {
        val message = renderFixture(
            event = "workflow_run",
            fixtureName = "workflow_run.completed.minimal"
        )

        assertEquals(TestFixtures.message("workflow_run.completed.minimal"), message?.trim())
    }

    @Test
    fun `renders a completed workflow job without optional fields`() {
        val message = renderFixture(
            event = "workflow_job",
            fixtureName = "workflow_job.completed.minimal"
        )

        assertEquals(TestFixtures.message("workflow_job.completed.minimal"), message?.trim())
    }

    private fun renderFixture(
        event: String,
        fixtureName: String = "$event.completed"
    ): String? {
        val response = KtxSerializer.json.decodeFromString<GitHubResponse>(
            TestFixtures.event(fixtureName)
        )

        return templateHandler.handleEvent(
            event = event,
            response = response
        )
    }

    private fun messageFixture(name: String): String = TestFixtures.message(name)

    private fun reviewResponse(
        body: String?,
        state: String = "approved"
    ) = GitHubResponse(
        action = "submitted",
        pullRequest = PullRequest(
            htmlUrl = "https://github.com/wire/example/pull/1",
            title = "Example pull request",
            user = user,
            number = 1
        ),
        review = Review(
            body = body,
            user = user,
            state = state
        ),
        sender = user,
        repository = Repository(
            fullName = "wire/example",
            name = "example"
        )
    )

    private fun pushResponse(commits: List<Commit>) =
        GitHubResponse(
            commits = commits,
            sender = user,
            compare = "https://github.com/wire/example/compare/main",
            repository = Repository(
                fullName = "wire/example",
                name = "example"
            )
        )

    private fun workflowRun(conclusion: String?) =
        WorkflowRun(
            name = "Build and test",
            conclusion = conclusion,
            event = "push",
            actor = user,
            headBranch = "main",
            htmlUrl = "https://github.com/wire/example/actions/runs/1",
            runNumber = 1
        )

    private fun workflowJob(conclusion: String?) =
        WorkflowJob(
            name = "unit-tests",
            conclusion = conclusion,
            htmlUrl = "https://github.com/wire/example/actions/runs/1/job/1"
        )

    private companion object {
        val user = User(
            avatarUrl = "https://github.com/wire.png",
            login = "wire"
        )
    }
}
