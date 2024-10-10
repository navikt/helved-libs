package libs.postgres.concurreny

import kotlinx.coroutines.test.runTest
import libs.postgres.JdbcConfig
import libs.postgres.Jdbc
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConnectionTest {
    init {
        Jdbc.initialize(
            JdbcConfig(
                host = "stub",
                port = "5432",
                database = "connection_db",
                username = "sa",
                password = "",
                url = "jdbc:h2:mem:connection_db;MODE=PostgreSQL",
                driver = "org.h2.Driver",
            )
        )
    }

    @Test
    fun `can be in context`() = runTest(Jdbc.context) {
        transaction {
            assertNotNull(coroutineContext.connection)
        }
    }

    @Test
    fun `fails without context`() = runTest(Jdbc.context) {
        val err = assertThrows<IllegalStateException> {
            coroutineContext.connection
        }
        assertEquals("Connection not in context", err.message)
    }
}