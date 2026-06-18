package com.wire.github.util

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheNotFoundException
import com.wire.github.response.model.GitHubResponse
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale
import org.slf4j.LoggerFactory

class TemplateHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val mustacheFactory = DefaultMustacheFactory()

    fun handleEvent(
        event: String,
        response: GitHubResponse
    ): String? {
        val path = templatePath(
            event = event,
            response = response
        ) ?: return null

        return try {
            val template = compileTemplate(
                path = path
            )

            populateTemplate(
                mustache = template,
                model = response
            )?.trim()?.takeIf { it.isNotBlank() }
        } catch (exception: MustacheNotFoundException) {
            logger.error("MustacheNotFoundException: $exception")
            null
        }
    }

    private fun templatePath(
        event: String,
        response: GitHubResponse
    ): String? =
        when (event) {
            EVENT_WORKFLOW_RUN ->
                response.workflowRun
                    ?.takeIf { it.id != null }
                    ?.let { eventTemplatePath(event) }
            else -> response.action?.let {
                actionTemplatePath(
                    event = event,
                    action = it
                )
            } ?: eventTemplatePath(event)
        }

    private fun actionTemplatePath(
        event: String,
        action: String
    ): String =
        String.format(
            Locale.getDefault(),
            "$TEMPLATE_DIRECTORY/%s/%s.%s.template",
            LANGUAGE_ENGLISH,
            event,
            action
        )

    private fun eventTemplatePath(event: String): String =
        String.format(
            Locale.getDefault(),
            "$TEMPLATE_DIRECTORY/%s/%s.template",
            LANGUAGE_ENGLISH,
            event
        )

    private fun compileTemplate(path: String): Mustache = mustacheFactory.compile(path)

    private fun populateTemplate(
        mustache: Mustache,
        model: GitHubResponse
    ): String? =
        StringWriter()
            .apply {
                mustache.execute(PrintWriter(this), model).flush()
            }.toString()

    private companion object {
        const val LANGUAGE_ENGLISH = "en"
        const val TEMPLATE_DIRECTORY = "templates"
        const val EVENT_WORKFLOW_RUN = "workflow_run"
    }
}
