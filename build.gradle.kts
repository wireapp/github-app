import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "com.wire"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

application {
    mainClass = "com.wire.github.ApplicationKt"
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.logging)

    // Serialization
    implementation(libs.ktor.server.serialization)
    implementation(libs.kotlinx.serialization.json)

    // Wire SDK
    implementation(libs.wire.sdk)

    // Dependency Injection
    implementation(libs.koin.ktor)

    // Storage
    implementation(libs.redis)

    // Template
    implementation(libs.mustache)

    // Logging
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    // Test
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters {
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.JSON)
        reporter(ReporterType.HTML)
    }
    filter {
        exclude { element ->
            element.file.path.contains("generated/")
        }
    }
}

detekt {
    toolVersion = libs.versions.detekt.version.get()
    config.setFrom(file("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
    buildUponDefaultConfig = true
    source.setFrom("src/main/kotlin")
}

tasks {
    shadowJar {
        archiveFileName.set("github-app.jar")
        manifest {
            attributes["Main-Class"] = "com.wire.github.ApplicationKt"
        }
    }

    /**
     * Dummy environment variables for tests
     */
    test {
        environment("GHAPP_API_HOST", "http://0.0.0.0")
        environment("GHAPP_SERVER_PORT", "8083")
        environment("GHAPP_REDIS_HOST", "redis://localhost")
        environment("GHAPP_REDIS_PORT", "6379")
        environment("WIRE_SDK_API_HOST", "https://nginz-https.chala.wire.link")
        environment("WIRE_SDK_API_TOKEN", "myApiToken")
        environment("WIRE_SDK_APP_ID", "f562e146-dec2-4d85-93c7-7132746b5cca")
        environment("WIRE_SDK_CRYPTOGRAPHY_STORAGE_PASSWORD", "myDummyPasswordmyDummyPassword01")
    }
}
