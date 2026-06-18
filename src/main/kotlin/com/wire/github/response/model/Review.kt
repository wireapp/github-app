package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Review(
    val body: String? = null,
    val user: User,
    val state: String,
    @SerialName("html_url")
    val htmlUrl: String
) {
    val approved: Boolean get() = state.equals("approved", ignoreCase = true)
    val changesRequested: Boolean get() = state.equals("changes_requested", ignoreCase = true)
    val commented: Boolean get() = state.equals("commented", ignoreCase = true)
}
