package com.wire.github

import java.io.File

internal object TestFixtures {
    private const val FIXTURES_DIRECTORY = "src/test/fixtures"

    fun event(name: String): String = read("events/$name.json")

    fun message(name: String): String = read("messages/$name.txt").trim()

    private fun read(path: String): String = File("$FIXTURES_DIRECTORY/$path").readText()
}
