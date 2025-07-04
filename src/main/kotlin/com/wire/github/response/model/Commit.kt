package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Commit(
    @SerialName("message")
    val message: String? = null
)
