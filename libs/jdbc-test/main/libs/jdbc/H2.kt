package libs.jdbc

import kotlinx.coroutines.withContext
import libs.postgres.JdbcConfig
import libs.postgres.Postgres
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.utils.appLog

object H2 {
    val config by lazy {
        JdbcConfig(
            host = "stub",
            port = "5432",
            database = "test_db",
            username = "sa",
            password = "",
            url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
    }

    suspend fun clear(table: String) = withContext(Postgres.context) {
        transaction {
            coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY FALSE").execute()
            coroutineContext.connection.prepareStatement("TRUNCATE TABLE $table").execute()
            coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY TRUE").execute()
        }.also {
            appLog.info("table '$table' trunctated.")
        }
    }
}