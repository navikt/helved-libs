package libs.postgres.concurreny

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.JdbcConfig
import libs.postgres.Migrator
import libs.postgres.Postgres
import libs.postgres.concurrency.CoroutineTransaction
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.util.*

class TransactionTest {
    init {
        Postgres.initialize(
            JdbcConfig(
                host = "stub",
                port = "5432",
                database = "transaction_db",
                username = "sa",
                password = "",
                url = "jdbc:h2:mem:transaction_db;MODE=PostgreSQL",
                driver = "org.h2.Driver",
            )
        )
    }

    @BeforeEach
    fun setup() {
        runBlocking {
            withContext(Postgres.context) {
                Migrator(File("test/migrations/valid"), Postgres.context).migrate()
            }
        }
    }

    @AfterEach
    fun cleanup() {
        runBlocking {
            withContext(Postgres.context) {
                transaction {
                    coroutineContext.connection.prepareStatement("DROP TABLE IF EXISTS migrations, test_table, test_table2")
                        .execute()
                }
            }
        }
    }

    @Test
    fun `can be in context`() = runTest(Postgres.context) {
        transaction {
            assertEquals(0, AsyncDao.count())
        }
    }

    @Test
    fun `fails without context`() = runTest(Postgres.context) {
        val err = assertThrows<IllegalStateException> {
            runBlocking {
                AsyncDao.count()
            }
        }
        assertEquals(err.message, "Connection not in context")

    }

    @Test
    fun rollback() = runTest(Postgres.context) {
        transaction {
            val err = assertThrows<IllegalStateException> {
                AsyncDao(UUID.randomUUID(), "two").insertAndThrow()
            }
            kotlin.test.assertEquals(err.message, "wops")
        }
    }

    @Test
    fun `can be nested with rollbacks`() = runTest(Postgres.context) {
        transaction {
            assertEquals(0, AsyncDao.count())
        }

        runCatching {
            transaction {
                transaction {
                    AsyncDao(UUID.randomUUID(), "one").insert()
                }
                transaction {
                    AsyncDao(UUID.randomUUID(), "two").insertAndThrow()
                }
            }
        }

        transaction {
            assertEquals(0, AsyncDao.count())
        }
    }

    @Test
    fun `can be nested with commits`() = runTest(Postgres.context) {
        runCatching {
            transaction {
                assertEquals(0, AsyncDao.count())
            }

            transaction {
                transaction {
                    AsyncDao(UUID.randomUUID(), "one").insert()
                }
                transaction {
                    AsyncDao(UUID.randomUUID(), "two").insert()
                }
            }
        }

        transaction {
            assertEquals(2, AsyncDao.count())
        }
    }

    @Test
    fun `can be completed`() {
        val transaction = CoroutineTransaction()
        assertFalse(transaction.completed)
        transaction.complete()
        assertTrue(transaction.completed)
    }
}
