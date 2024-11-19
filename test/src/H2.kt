package test

import jdbc.*
import logger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private val testLog = logger("test")

class H2(val migrationsDir: File): AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            testLog.info("Shutting down TestRunner")
            close()
        })
    }
    val config by lazy {
        jdbc.Config(
            host = "stub",
            port = "5432",
            database = "test_db",
            username = "sa",
            password = "",
            url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
    }

    val context = CoroutineDatasource(jdbc.init(config)).also {
        runBlocking {
            withContext(it) {
                Migrator(migrationsDir).migrate()
            }
        }
    }

    suspend fun truncate(vararg tableNames: String) =
        withContext(context) {
            // val tables = listOf(TaskDao.TABLE_NAME, TaskHistoryDao.TABLE_NAME)
            transaction {
                tableNames.forEach {
                    coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY FALSE").execute()
                    coroutineContext.connection.prepareStatement("TRUNCATE TABLE $it").execute()
                    coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY TRUE").execute()
                    testLog.info("table '$it' truncated.") 
                }
            }
        }

    override fun close() {}
}
