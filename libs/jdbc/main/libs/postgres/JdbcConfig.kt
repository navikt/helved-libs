package libs.postgres

import libs.utils.env
import java.io.File

data class JdbcConfig(
    val host: String = env("DB_HOST"),
    val port: String = env("DB_PORT"),
    val database: String = env("DB_DATABASE"),
    val username: String = env("DB_USERNAME"),
    val password: String = env("DB_PASSWORD"),
    val url: String = "jdbc:postgresql://$host:$port/$database",
    val driver: String = "org.postgresql.Driver",
    val migrations: File = File("main/migrations")
)
