package com.wire.github.util

object FixtureHelper {
    fun loadFixture(name: String): String {
        return object {}.javaClass
            .getResource("/fixtures/events/$name")
            ?.readText()
            ?: throw IllegalArgumentException("Fixture $name not found.")
    }
}
