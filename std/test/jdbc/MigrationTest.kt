package jdbc

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationTest {
    private val ctx = CoroutineDatasource(jdbc.init(h2))

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
            Migrator(File("test/migrations/valid/1.sql"))
        }

        val expected = MigrationError.NO_DIR.msg
        val actual = requireNotNull(err.message)

        assertTrue(
            actual.contains(expected),
            """
               excpected: $expected
               actual:    $actual
            """.trimMargin()
        )
    }

    @Test
    fun `allow no files`() = runTest(ctx) {
        Migrator(File("test/migrations/empty"))
    }

    @Test
    fun `can create migrations table`() = runTest {
        Migrator(File("test/migrations/valid"))
        Migrator(File("test/migrations/valid"))
    }

    @Test
    fun `can migrate scripts`() = runTest(ctx) {
        Migrator(File("test/migrations/valid")).migrate()
    }

    @Test
    fun `migrations are idempotent`() = runTest(ctx) {
        val migrator = Migrator(File("test/migrations/valid"))
        migrator.migrate()
        migrator.migrate()
    }

    @Test
    fun `sequence is corrupted`() = runTest(ctx) {
        val migrator = Migrator(File("test/migrations/wrong_seq"))
        val err = assertThrows<MigrationException> {
            migrator.migrate()
        }
        assertEquals("A version was not incremented by 1: order: 1, 3", err.message)
    }

    @Test
    fun `checksum is corrupted`() = runTest(ctx) {
        Migrator(File("test/migrations/valid")).migrate()

        val err = assertThrows<MigrationException> {
            Migrator(File("test/migrations/wrong_checksum")).migrate()
        }
        assertEquals(MigrationError.CHECKSUM.msg, err.message)
    }

    @Test
    fun `can read utf8 filenames`() = runTest(ctx) {
        Migrator(File("test/migrations/utf8")).migrate()
    }
}

private val h2 = jdbc.Config(
    host = "stub",
    port = "5432",
    database = "test_db",
    username = "sa",
    password = "",
    url = "jdbc:h2:mem:test_db;MODE=PostgreSQL",
    driver = "org.h2.Driver",
)
