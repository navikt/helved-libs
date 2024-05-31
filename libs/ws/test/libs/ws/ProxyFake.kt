package libs.ws

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.*

class ProxyFake : AutoCloseable {
    private val server = embeddedServer(Netty, port = 0, module = Application::proxy).apply { start() }

    val config
        get() = StsConfig(
            host = URI.create("http://localhost:${server.port}").toURL(),
            user = "test",
            pass = "test"
        )

    fun respondWith(token: GandalfToken) {
        response = token
    }

    fun expectRequest(block: (ApplicationCall) -> Boolean) {
        request = block
    }

    fun reset() {
        request = { true }
        response = GandalfToken()
    }

    override fun close() = server.stop(0, 0)
}

val NettyApplicationEngine.port: Int
    get() = runBlocking {
        resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

private var response: GandalfToken = GandalfToken()
private var request: (ApplicationCall) -> Boolean = { true }


private fun Application.proxy() {
    install(ContentNegotiation) {
        jackson()
    }

    routing {
        get("/rest/v1/sts/samltoken") {
            if (request(call)) {
                call.respond(response)
            } else {
                error("unexpected proxy request: $call")
            }
        }
    }
}

data class GandalfToken(
    val access_token: String = "very secure".let { Base64.getEncoder().encodeToString(it.toByteArray()) },
    val issued_token_type: String = "urn:ietf:params:oauth:token-type:saml2",
    val token_type: String = "Bearer",
    val expires_in: Long = 3600,
)
