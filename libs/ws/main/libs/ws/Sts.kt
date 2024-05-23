package libs.ws

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import libs.http.HttpClientFactory
import libs.utils.secureLog
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

interface Sts {
    suspend fun samlToken(): SamlToken
}

data class StsConfig(
    val host: URL,
    val user: String,
    val pass: String,
)

typealias ProxyAuthProvider = suspend () -> String

class StsClient(
    private val config: StsConfig,
    private val http: HttpClient = HttpClientFactory.basic(LogLevel.ALL),
    private val jackson: ObjectMapper = jacksonObjectMapper(),
    private val proxyAuth: ProxyAuthProvider? = null,
) : Sts {
    override suspend fun samlToken(): SamlToken {
        // todo: cache valid tokens

        val response = http.get("${config.host}/rest/v1/sts/samltoken") {
            basicAuth(config.user, config.pass)
            proxyAuth?.let { it -> header("X-Proxy-Authorization", it()) }
        }

        val samlToken = response.tryInto {
            val accessToken = it["access_token"]
                ?.takeIf(JsonNode::isTextual)?.asText()
                ?: stsError(it)

            val tokenType = it["issued_token_type"]
                ?.takeIf(JsonNode::isTextual)?.asText()
                ?: stsError(it)

            val expiresIn = it["expires_in"]
                ?.takeIf(JsonNode::isNumber)?.asLong()
                ?: stsError(it)

            if (tokenType != "urn:ietf:params:oauth:token-type:saml2") {
                stsError(it)
            }

            secureLog.info("Raw token: $accessToken")

            val decoded = String(Base64.getDecoder().decode(accessToken)).replace("&#13;\n", "")

            // todo: temporary test with explicit spaceing after commas
            val manipulated = decoded.replaceBetweenXmlTag("X509IssuerName", "CN=B27 Issuing CA Intern, DC=preprod, DC=local")
            secureLog.info("Base64 decoded token: $manipulated ")

            SamlToken(
                token = manipulated,
                expirationTime = LocalDateTime.now().plusSeconds(expiresIn)
            )
        }

        return samlToken
    }

    private suspend fun <T : Any> HttpResponse.tryInto(from: (JsonNode) -> T): T {
        when (status) {
            HttpStatusCode.OK -> {
                val body = bodyAsText()
                val json = jackson.readTree(body)
                return from(json)
            }

            else -> error("Unexpected status code: $status when calling $request.url")
        }
    }

}

/**
 * Replaces the content between the XML tags with the given replacement.
 * @example <tag>original</tag> -> <tag>replacement</tag>
 */
fun String.replaceBetweenXmlTag(tag: String, replacement: String): String {
    return replace(
        regex = Regex("(?<=<$tag>).*(?=</$tag>)"),
        replacement = replacement
    )
}

class StsException(msg: String) : RuntimeException(msg)

fun stsError(node: JsonNode): Nothing {
    throw StsException(
        """
            Error from STS: ${node.path("title").asText()}
            Details: ${node.path("detail").takeIf(JsonNode::isTextual) ?: node}
        """.trimIndent()
    )
}

data class SamlToken(
    val token: String,
    val expirationTime: LocalDateTime,
) {

    val expired: Boolean get() = expirationTime <= LocalDateTime.now().plus(EXP_LEEWAY)

    companion object {
        private val EXP_LEEWAY = Duration.ofSeconds(10)
    }
}
