package libs.ws

import io.ktor.server.request.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SoapTest {
    companion object {
        private val proxy = ProxyFake()

        @AfterAll
        @JvmStatic
        fun close() = proxy.close()
    }

    @AfterEach
    fun reset() = proxy.reset()

    @Test
    fun `soap fake response can be configured`() {
        val sts = StsClient(proxy.config.sts)
        val soap = SoapClient(proxy.config, sts)

        proxy.soap.response = "sweet"

        val res = runBlocking {
            soap.call("nice", "<xml>hello</xml>")
        }

        assertEquals("sweet", res)
    }

    @Test
    fun `can use proxy-auth`() {
        val sts = StsClient(proxy.config.sts)
        val soap = SoapClient(proxy.config, sts, proxyAuth = suspend {
            "token for proxy"
        })

        proxy.soap.request = {
            it.request.header("X-Proxy-Authorization") == "token for proxy"
        }

        assertDoesNotThrow {
            runBlocking {
                soap.call("action", "yo")
            }
        }
    }
}