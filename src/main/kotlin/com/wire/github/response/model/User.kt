package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("login")
    val login: String? = null
)
