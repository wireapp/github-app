package com.wire.github.config

import com.wire.github.EventsHandler
import com.wire.github.util.ENV_VAR_API_HOST
import com.wire.github.util.ENV_VAR_API_TOKEN
import com.wire.github.util.ENV_VAR_APPLICATION_ID
import com.wire.github.util.ENV_VAR_CRYPTOGRAPHY_STORAGE_KEY
import com.wire.github.util.ENV_VAR_REDIS_HOST
import com.wire.github.util.ENV_VAR_REDIS_PORT
import com.wire.github.util.SignatureValidator
import com.wire.github.util.TemplateHandler
import com.wire.sdk.WireAppSdk
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.koin.dsl.module

private const val SDK_INIT_TIMEOUT_SECONDS = 30L

val projectModules = module {
    single(createdAtStart = true) { initWireAppSdk() }
    single { SignatureValidator() }
    single { TemplateHandler() }
    single { RedisClient.create("$ENV_VAR_REDIS_HOST:$ENV_VAR_REDIS_PORT") }
    single<StatefulRedisConnection<String, String>> { get<RedisClient>().connect() }
}

/**
 * Creates the Wire SDK and starts listening, bounded by [SDK_INIT_TIMEOUT_SECONDS].
 *
 * `startListening()` is a blocking call that establishes the backend connection and
 * Proteus client; if it stalls (e.g. unreachable backend), the app would otherwise hang
 * during Koin startup and never bind its HTTP port. The init runs on a daemon thread so a
 * stuck initialization cannot keep the JVM alive once we fail fast on timeout.
 */
private fun initWireAppSdk(): WireAppSdk {
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wire-sdk-init").apply { isDaemon = true }
    }
    return try {
        executor
            .submit(Callable { wireAppSdk().apply { startListening() } })
            .get(SDK_INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (exception: TimeoutException) {
        throw IllegalStateException(
            "Wire SDK initialization did not complete within $SDK_INIT_TIMEOUT_SECONDS seconds",
            exception
        )
    } finally {
        executor.shutdownNow()
    }
}

private fun wireAppSdk(): WireAppSdk =
    WireAppSdk(
        applicationId = ENV_VAR_APPLICATION_ID,
        apiToken = ENV_VAR_API_TOKEN,
        apiHost = ENV_VAR_API_HOST,
        cryptographyStorageKey = ENV_VAR_CRYPTOGRAPHY_STORAGE_KEY,
        wireEventsHandler = EventsHandler()
    )
