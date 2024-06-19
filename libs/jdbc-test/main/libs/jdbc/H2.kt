package libs.jdbc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import libs.postgres.Postgres
import libs.postgres.Postgres.migrate
import libs.postgres.PostgresConfig
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.utils.appLog
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext

abstract class H2 {
    val h2: CoroutineContext by lazy {
        CoroutineDatasource(datasource)
    }

    val config by lazy {
        PostgresConfig(
            host = "stub",
            port = "5432",
            database = "test_db",
            username = "sa",
            password = "",
            url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
    }

    private val datasource: DataSource = Postgres.initialize(config).apply { migrate() }
    private val scope = CoroutineScope(h2)

    suspend fun clear(table: String) =
        scope.async {
            transaction {
                coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY FALSE").execute()
                coroutineContext.connection.prepareStatement("TRUNCATE TABLE $table").execute()
                coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY TRUE").execute()
            }
        }.await().also {
            appLog.info("table '$table' trunctated.")
        }
}