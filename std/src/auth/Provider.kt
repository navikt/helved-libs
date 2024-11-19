package auth

import env
import logger
import async.*
import java.net.URL
import java.time.Instant

open class Config(
    open val clientId: String = env("AZURE_APP_CLIENT_ID"),
    open val jwks: URL = env("AZURE_OPENID_CONFIG_JWKS_URI"),
    open val issuer: String = env("AZURE_OPENID_CONFIG_ISSUER")
)

sealed interface Issuer {
    object AzureAd: Issuer
    object TokenX: Issuer
    object Maskinporten: Issuer
    object IdPorten: Issuer
}

interface Provider <T: Token>{
    suspend fun getAccessToken(tokenUrl: URL, key: String, body: () -> String): T 
}

data class AzureConfig(
    val tokenEndpoint: URL = env("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    override val jwks: URL = env("AZURE_OPENID_CONFIG_JWKS_URI"),
    override val issuer: String = env("AZURE_OPENID_CONFIG_ISSUER"),
    override val clientId: String = env("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = env("AZURE_APP_CLIENT_SECRET")
) : Config(clientId, jwks, issuer)

class AzureProvider(
    private val config: AzureConfig = AzureConfig(),
    private val client: Provider<AzureToken>,
) {
    suspend fun getClientCredentialsToken(scope: String): AzureToken =
        client.getAccessToken(config.tokenEndpoint, scope) {
            """
                client_id=${config.clientId}
                &client_secret=${config.clientSecret}
                &scope=$scope
                &grant_type=client_credentials
            """.asUrlPart()
        }

    suspend fun getOnBehalfOfToken(access_token: String, scope: String): AzureToken =
        client.getAccessToken(config.tokenEndpoint, scope) {
            """
                client_id=${config.clientId}
                &client_secret=${config.clientSecret}
                &assertion=$access_token
                &scope=$scope
                &grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
                &requested_token_use=on_behalf_of
            """.asUrlPart()
        }

    suspend fun getUsernamePasswordToken(scope: String, username: String, password: String): AzureToken =
        client.getAccessToken(config.tokenEndpoint, username) {
            """
                client_id=${config.clientId}
                &client_secret=${config.clientSecret}
                &scope=$scope
                &username=$username
                &password=$password
                &grant_type=password
            """.asUrlPart()
        }
}


data class AzureToken(
    val expires_in: Long,
    val access_token: String
) : Token {
    private val expiry: Instant = Instant.now().plusSeconds(expires_in - LEEWAY_SEC)

    override fun isExpired(): Boolean = Instant.now().isAfter(expiry)

    override fun toString(): String = """
        Expiry: $expires_in 
        Token:  $access_token
    """.trimIndent()
}

private val authLog = logger("auth")
private fun String.asUrlPart() = trimIndent().replace("\n", "")
private const val LEEWAY_SEC = 60

