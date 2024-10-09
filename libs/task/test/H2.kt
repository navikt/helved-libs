import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.postgres.JdbcConfig
import libs.postgres.Postgres
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.task.TaskDao
import libs.task.TaskHistoryDao
import libs.utils.Resource
import libs.utils.appLog
import kotlin.coroutines.CoroutineContext

object H2 : AutoCloseable {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            appLog.info("Shutting down TestRunner")
            close()
        })
    }

    private val config by lazy {
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
    private val jdbc = Postgres.initialize(config)
    val context = CoroutineDatasource(jdbc).also {
        runBlocking {
            migrate(it)
        }
    }

    private suspend fun migrate(context: CoroutineContext) =
        withContext(context) {
            transaction {
                val sql = Resource.read("/1_task_v2.sql")
                coroutineContext.connection.prepareStatement(sql).execute()
                appLog.debug(sql)
            }
        }

    override fun close() = runBlocking { truncate() }

    suspend fun truncate() =
        withContext(context) {
            val tables = listOf(TaskDao.TABLE_NAME, TaskHistoryDao.TABLE_NAME)
            transaction {
                tables.forEach {
                    coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY FALSE").execute()
                    coroutineContext.connection.prepareStatement("TRUNCATE TABLE $it").execute()
                    coroutineContext.connection.prepareStatement("SET REFERENTIAL_INTEGRITY TRUE").execute()
                }
            }.also { tables.forEach { appLog.info("table '$it' truncated.") } }
        }
}