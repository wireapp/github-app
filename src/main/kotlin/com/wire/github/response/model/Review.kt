package com.wire.github.response.model

import kotlinx.serialization.Serializable

@Serializable
data class Review(
    val body: String? = null,
    val user: User? = null,
    val state: String
) {
    val emoji: String
        get() = when (state) {
            "approved" -> "✅"
            "changes_requested" -> "🔄"
            else -> "📝"
        }
}
