package libs.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import libs.postgres.concurrency.CoroutineDatasource
import java.sql.ResultSet
import javax.sql.DataSource

object Postgres {
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

fun <T : Any> ResultSet.asyncMap(block: (ResultSet) -> T): Sequence<T> =
    sequence {
        while (next()) yield(block(this@asyncMap))
    }

fun <T : Any> ResultSet.map(block: (ResultSet) -> T): List<T> =
    sequence {
        while (next()) yield(block(this@map))
    }.toList()
