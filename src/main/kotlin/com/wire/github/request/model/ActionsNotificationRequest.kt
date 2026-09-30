package com.wire.github.request.model

import kotlinx.serialization.Serializable

@Serializable
data class ActionsNotificationRequest(
    val text: String
)
