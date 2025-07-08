/**
 * This file holds and handles environment variables for both:
 * - GitHub App
 * - Wire App SDK
 *
 * Although not all environment variables for Wire App SDK are here because some are
 * already being handled by the SDK itself.
 */
package com.wire.github.util

import java.util.UUID

/**
 * Host Port to be used when setting up Ktor server.
 */
val ENV_VAR_PORT: Int = System
    .getenv()
    .getOrDefault(
        "GHAPP_SERVER_PORT",
        "8083"
    ).toInt()

/**
 * Host URL to be used to contact via webhook.
 * Must contain the protocol (`HTTP` or `HTTPS`) ???
 * Note: This is not the Host URL for the Ktor server in the docker container.
 */
val ENV_VAR_HOST: String = System
    .getenv()
    .getOrDefault(
        "GHAPP_API_HOST",
        "http://0.0.0.0"
    )

/**
 * Redis Host URL
 * In case it needs a password, must be included in this same environment variable.
 * Examples:
 * - "redis://host"
 * - "redis://[:password@]host"
 */
val ENV_VAR_REDIS_HOST: String = System
    .getenv()
    .getOrDefault(
        "GHAPP_REDIS_HOST",
        "redis://localhost"
    )

/**
 * Redis Port Number
 * To used when connecting and also exposed via docker-compose.
 */
val ENV_VAR_REDIS_PORT: String = System
    .getenv()
    .getOrDefault(
        "GHAPP_REDIS_PORT",
        "6379"
    )

/**
 * Application ID received when Onboarding the App.
 */
val ENV_VAR_APPLICATION_ID: UUID = UUID.fromString(
    System
        .getenv()
        .getOrDefault(
            "WIRE_SDK_APP_ID",
            UUID.randomUUID().toString()
        )
)

/**
 * API Token received when Onboarding the App.
 */
val ENV_VAR_API_TOKEN: String = System
    .getenv()
    .getOrDefault(
        "WIRE_SDK_API_TOKEN",
        "dummyApiToken"
    )

/**
 * API Host to be used as a backend contact point for the SDK.
 */
val ENV_VAR_API_HOST: String = System
    .getenv()
    .getOrDefault(
        "WIRE_SDK_API_HOST",
        "https://nginz-https.chala.wire.link"
    )

/**
 * Cryptography storage password
 * Used when setting up the user and client database.
 * If lost or forgotten, there is no future access to the database.
 * Must be exactly 32 characters
 */
val ENV_VAR_CRYPTOGRAPHY_STORAGE_PASSWORD: String = System
    .getenv("WIRE_SDK_CRYPTOGRAPHY_STORAGE_PASSWORD")
