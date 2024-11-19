package ktor

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import logger
import async.*
import auth.*
import secureLog

private val log = logger("auth")

class AzureProvider(
    private val name: Issuer = Issuer.AzureAd,
    private val http: HttpClient = createClient(),
    private val cache: Cache<String, AzureToken> = TokenCache()
): Provider<AzureToken> {
    override suspend fun getAccessToken(
        tokenUrl: URL,
        key: String,
        body: () -> String,
    ): AzureToken {
        return when (val token = cache.get(key)) {
            null -> update(tokenUrl, key, body())
            else -> token
        }
    }

    private suspend fun update(tokenUrl: URL, key: String, body: String): AzureToken {
        val res = http.post(tokenUrl) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }

        val token = res.tryInto<AzureToken>()
        cache.add(key, token)
        return token
    }

    private suspend inline fun <reified T> HttpResponse.tryInto(): T {
        when (status.value) {
            in 200..299 -> return body<T>()
            else -> {
                log.error("Failed to get token from provider: $name")
                secureLog.error(
                    """
                    Got HTTP ${status.value} when issuing token from provider: ${request.url}
                    Status: ${status.value}
                    Body: ${bodyAsText()}
                    """.trimIndent(),
                )

                error("Failed to get token from provider: ${request.url}")
            }
        }
    }
}

fun JWTAuthenticationProvider.Config.configure(
    config: Config,
    customValidation: (JWTCredential) -> Boolean = { true },
) {
    val validator = TokenValidator(config, this)
    validator.configure(customValidation)
}

/**
 * Usage:
 * ```
 *  install(Authentication) {
 *      jwt(TokenProvider.AZURE) {
 *          configure(AzureConfig()) { jwt ->
 *              jwt.getClaim("pid", String::class) != null
 *          }
 *      }
 *  }
 *  ```
 */
class TokenValidator(
    private val config: Config,
    private val auth: JWTAuthenticationProvider.Config,
) {
    private val jwkProvider: JwkProvider = JwkProviderBuilder(config.jwks)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    internal fun configure(customValidation: (JWTCredential) -> Boolean = { true }) {
        auth.apply {
            verifier(jwkProvider, config.issuer)
            challenge { _, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Ugyldig token for realm $realm")
            }
            validate { cred ->
                val now = Date()

                if (config.clientId !in cred.audience) {
                    secureLog.warn("Validering av token feilet (clientId var ikke i audience: ${cred.audience}")
                    return@validate null
                }

                if (cred.expiresAt?.before(now) == true) {
                    secureLog.warn("Validering av token feilet (expired at: ${cred.expiresAt})")
                    return@validate null
                }

                if (cred.notBefore?.after(now) == true) {
                    secureLog.warn("Validering av token feilet (not valid yet, valid from: ${cred.notBefore})")
                    return@validate null
                }

                if (cred.issuedAt?.after(cred.expiresAt ?: return@validate null) == true) {
                    secureLog.warn("Validering av token feilet (issued after expiration: ${cred.issuedAt} )")
                    return@validate null
                }

                if (!customValidation(cred)) {
                    secureLog.warn("Validering av token feilet (Custom validering feilet)")
                    return@validate null
                }

                JWTPrincipal(cred.payload)
            }
        }
    }
}
