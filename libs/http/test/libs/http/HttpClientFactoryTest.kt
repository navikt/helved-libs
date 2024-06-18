package libs.http

import io.ktor.client.plugins.logging.*
import io.ktor.util.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HttpClientFactoryTest {

    @Test
    fun `defaults to log level info`() {
        val client = HttpClientFactory.new()
        val plugins: Attributes = client.attributes[AttributeKey("ApplicationPluginRegistry")]
        val logging: Logging = plugins[AttributeKey("ClientLogging")]
        assertEquals(LogLevel.INFO, logging.level)
    }

    @Test
    fun `defaults with log, json, retry and timeout`() {
        val client = HttpClientFactory.new()
        val plugins: Attributes = client.attributes[AttributeKey("ApplicationPluginRegistry")]
        assertTrue(plugins.contains(AttributeKey<Nothing>("ClientLogging")))
        assertTrue(plugins.contains(AttributeKey<Nothing>("ContentNegotiation")))
        assertTrue(plugins.contains(AttributeKey<Nothing>("RetryFeature")))
        assertTrue(plugins.contains(AttributeKey<Nothing>("TimeoutPlugin")))
    }

    @Test
    fun `can change log level`() {
        val client = HttpClientFactory.new(LogLevel.ALL)
        val plugins: Attributes = client.attributes[AttributeKey("ApplicationPluginRegistry")]
        val logging: Logging = plugins[AttributeKey("ClientLogging")]
        assertEquals(LogLevel.ALL, logging.level)
    }

    @Test
    fun `can exclude optionals`() {
        val client = HttpClientFactory.new(
            json = null,
            retries = null,
            requestTimeoutMs = null,
        )

        val plugins: Attributes = client.attributes[AttributeKey("ApplicationPluginRegistry")]
        assertTrue(plugins.contains(AttributeKey<Nothing>("ClientLogging")))
        assertFalse(plugins.contains(AttributeKey<Nothing>("ContentNegotiation")))
        assertFalse(plugins.contains(AttributeKey<Nothing>("RetryFeature")))
        assertFalse(plugins.contains(AttributeKey<Nothing>("TimeoutPlugin")))
    }
}