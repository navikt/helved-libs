package libs.http

class HttpClientFactoryTest {

//    @Test
//    fun `defaults to log level info`() {
//        val client = HttpClientFactory.new()
//        val plugins: Attributes = client.attributes[AttributeKey("ApplicationPluginRegistry")]
//        val logging: Logging = plugins[AttributeKey("ClientLogging")]
//        assertEquals(LogLevel.INFO, logging.level)
//    }

//    @Test
//    fun `defaults with log, json, retry and timeout`() {
//        val client = HttpClientFactory.new()
//        val plugins: Attributes = client.attributes[AttributeKey("ApplicationPluginRegistry")]
//        assertTrue(plugins.contains(AttributeKey<Any>("ClientLogging")))
//        assertTrue(plugins.contains(AttributeKey<Any>("ContentNegotiation")))
//        assertTrue(plugins.contains(AttributeKey<Any>("RetryFeature")))
//        assertTrue(plugins.contains(AttributeKey<Any>("TimeoutPlugin")))
//    }
//
//    @Test
//    fun `can change log level`() {
//        val client = HttpClientFactory.new(LogLevel.ALL)
//        val plugins: Attributes = client.attributes[AttributeKey("ApplicationPluginRegistry")]
//        val logging: Logging = plugins[AttributeKey("ClientLogging")]
//        assertEquals(LogLevel.ALL, logging.level)
//    }
//
//    @Test
//    fun `can exclude optionals`() {
//        val client = HttpClientFactory.new(
//            json = null,
//            retries = null,
//            requestTimeoutMs = null,
//        )
//
//        val plugins: Attributes = client.attributes[AttributeKey("ApplicationPluginRegistry")]
//        assertTrue(plugins.contains(AttributeKey<Any>("ClientLogging")))
//        assertFalse(plugins.contains(AttributeKey<Any>("ContentNegotiation")))
//        assertFalse(plugins.contains(AttributeKey<Any>("RetryFeature")))
//        assertFalse(plugins.contains(AttributeKey<Any>("TimeoutPlugin")))
//    }
}