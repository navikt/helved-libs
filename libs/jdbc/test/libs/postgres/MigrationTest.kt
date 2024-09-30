package libs.postgres

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.CoroutineDatasource
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals

class MigrationTest {
    private val ctx = CoroutineDatasource(Postgres.initialize(h2))

    @AfterEach
    fun cleanup() {
        runBlocking {
            withContext(ctx) {
                transaction {
                    coroutineContext.connection.prepareStatement("DROP TABLE IF EXISTS migrations, test_table, test_table2")
                        .execute()
                }
            }
        }
    }

    @Test
    fun `location is not a dir`() = runTest {
        val err = assertThrows<MigrationException> {
            Migrator(File("test/migrations/valid/1.sql"), ctx)
        }
        assertEquals(MigrationError.NO_DIR.msg, err.message)
    }

    @Test
    fun `location contains no files`() = runTest {
        val err = assertThrows<MigrationException> {
            Migrator(File("test/migrations/empty"), ctx)
        }
        assertEquals(MigrationError.NO_FILES.msg, err.message)
    }

    @Test
    fun `can create migrations table`() = runTest {
        Migrator(File("test/migrations/valid"), ctx)
        Migrator(File("test/migrations/valid"), ctx)
    }

    @Test
    fun `can migrate scripts`() = runTest(ctx) {
        Migrator(File("test/migrations/valid"), ctx).migrate()
    }

    @Test
    fun `migrations are idempotent`() = runTest(ctx) {
        val migrator = Migrator(File("test/migrations/valid"), ctx)
        migrator.migrate()
        migrator.migrate()
    }

    @Test
    fun `sequence is corrupted`() = runTest(ctx) {
        val migrator = Migrator(File("test/migrations/wrong_seq"), ctx)
        val err = assertThrows<MigrationException> {
            migrator.migrate()
        }
        assertEquals(MigrationError.VERSION_SEQ.msg, err.message)
    }

    @Test
    fun `checksum is corrupted`() = runTest(ctx) {
        Migrator(File("test/migrations/valid"), ctx).migrate()

        val err = assertThrows<MigrationException> {
            Migrator(File("test/migrations/wrong_checksum"), ctx).migrate()
        }
        assertEquals(MigrationError.CHECKSUM.msg, err.message)
    }
}

private val utsjekk = JdbcConfig(
    host = "localhost",
    port = "32768",
    database = "test",
    username = "test",
    password = "test",
)

private val h2 = JdbcConfig(
    host = "stub",
    port = "5432",
    database = "test_db",
    username = "sa",
    password = "",
    url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
    driver = "org.h2.Driver",
)
