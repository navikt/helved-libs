package libs.jdbc

import libs.postgres.Postgres
import libs.postgres.Postgres.migrate
import libs.postgres.PostgresConfig
import libs.postgres.concurrency.CoroutineDatasource
import libs.utils.env
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.coroutines.CoroutineContext

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
        }.apply {
            migrate()
        }
    }

    val config by lazy {
        PostgresConfig(
            host = container.host,
            port = container.firstMappedPort.toString(),
            database = container.databaseName,
            username = container.username,
            password = container.password
        )
    }

    val context: CoroutineContext by lazy {
        CoroutineDatasource(datasource)
    }

    override fun close() {
        // GitHub Actions service containers or testcontainers is not reusable and must be closed
        if (isGHA()) {
            container.close()
        }
    }
}

private fun isGHA(): Boolean = env("GITHUB_ACTIONS", false)