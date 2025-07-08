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
    ): String? =
        try {
            val template = compileTemplate(
                event = event,
                action = response.action
            )

            populateTemplate(
                mustache = template,
                model = response
            )
        } catch (exception: MustacheNotFoundException) {
            logger.error("MustacheNotFoundException: $exception")
            null
        }

    private fun compileTemplate(
        event: String,
        action: String?
    ): Mustache {
        val path = action?.let {
            String.format(
                Locale.getDefault(),
                "$TEMPLATE_DIRECTORY/%s/%s.%s.template",
                LANGUAGE_ENGLISH,
                event,
                it
            )
        } ?: String.format(
            Locale.getDefault(),
            "$TEMPLATE_DIRECTORY/%s/%s.template",
            LANGUAGE_ENGLISH,
            event
        )

        return mustacheFactory.compile(path)
    }

    private fun populateTemplate(
        mustache: Mustache,
        model: GitHubResponse
    ): String? =
        StringWriter().apply {
            mustache.execute(PrintWriter(this), model).flush()
        }.toString()

    private companion object {
        const val LANGUAGE_ENGLISH = "en"
        const val TEMPLATE_DIRECTORY = "templates"
    }
}
