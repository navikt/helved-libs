package libs.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import libs.postgres.concurrency.CoroutineDatasource
import libs.utils.env
import java.io.File
import java.sql.ResultSet
import javax.sql.DataSource

object Jdbc {
    lateinit var context: CoroutineDatasource

    fun initialize(
        config: JdbcConfig,
        hikariConfig: HikariConfig.() -> Unit = {},
    ): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                username = config.username
                password = config.password
                jdbcUrl = config.url
                driverClassName = config.driver
                minimumIdle = 1
                maximumPoolSize = 8
            }.apply(hikariConfig)
        ).also {
            context = CoroutineDatasource(it)
        }
}

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

fun <T : Any> ResultSet.map(block: (ResultSet) -> T): List<T> =
    sequence {
        while (next()) yield(block(this@map))
    }.toList()
