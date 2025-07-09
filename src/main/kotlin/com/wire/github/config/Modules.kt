package com.wire.github.config

import com.wire.github.EventsHandler
import com.wire.github.util.ENV_VAR_API_HOST
import com.wire.github.util.ENV_VAR_API_TOKEN
import com.wire.github.util.ENV_VAR_APPLICATION_ID
import com.wire.github.util.ENV_VAR_CRYPTOGRAPHY_STORAGE_PASSWORD
import com.wire.github.util.ENV_VAR_REDIS_HOST
import com.wire.github.util.ENV_VAR_REDIS_PORT
import com.wire.github.util.SignatureValidator
import com.wire.github.util.TemplateHandler
import com.wire.integrations.jvm.WireAppSdk
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.dsl.module

val projectModules = module {
    single(createdAtStart = true) {
        wireAppSdk().apply {
            startListening()
        }
    }
    single { SignatureValidator() }
    single { TemplateHandler() }
    single { RedisClient.create("$ENV_VAR_REDIS_HOST:$ENV_VAR_REDIS_PORT") }
    single<StatefulRedisConnection<String, String>> { get<RedisClient>().connect() }
}

private fun wireAppSdk(): WireAppSdk =
    WireAppSdk(
        applicationId = ENV_VAR_APPLICATION_ID,
        apiToken = ENV_VAR_API_TOKEN,
        apiHost = ENV_VAR_API_HOST,
        cryptographyStoragePassword = ENV_VAR_CRYPTOGRAPHY_STORAGE_PASSWORD,
        wireEventsHandler = EventsHandler()
    )
