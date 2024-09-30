package libs.jdbc

import libs.postgres.JdbcConfig
import libs.postgres.Migrator
import libs.postgres.Postgres
import libs.utils.env
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

class PostgresContainer(appname: String) : AutoCloseable {
    private val container = PostgreSQLContainer("postgres:15").apply {
        if (!isGHA()) {
            withLabel("service", appname)
            withReuse(true)
            withNetwork(null)
            withCreateContainerCmdModifier { cmd ->
                cmd.withName("$appname-pg")
                cmd.hostConfig?.apply {
                    withMemory(512 * 1024 * 1024)
                    withMemorySwap(1024 * 1024 * 1024)
                }
            }
        }
        start()
    }

    private val datasource by lazy {
        Postgres.initialize(config) {
            initializationFailTimeout = 5_000
            idleTimeout = 10_000
            connectionTimeout = 5_000
            maxLifetime = 900_000
        }
    }


    val config by lazy {
        JdbcConfig(
            host = container.host,
            port = container.firstMappedPort.toString(),
            database = container.databaseName,
            username = container.username,
            password = container.password
        )
    }

    suspend fun migrate() = Migrator(config.migrations, Postgres.context).migrate()

    //    fun <T> transaction(block: (Connection) -> T): T = datasource.transaction(block)
    fun <T> withDatasource(block: (DataSource) -> T): T = block(datasource)

    override fun close() {
        // GitHub Actions service containers or testcontainers is not reusable and must be closed
        if (isGHA()) {
            container.close()
        }
    }
}

private fun isGHA(): Boolean = env("GITHUB_ACTIONS", false)