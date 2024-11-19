package jdbc

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DatasourceTest {
    private val datasource = jdbc.init(
        jdbc.Config(
            host = "stub",
            port = "5432",
            database = "datasource_db",
            username = "sa",
            password = "",
            url = "jdbc:h2:mem:datasource_db;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
    )

    @Test
    fun `can be in context`() = runTest(jdbc.context) {
        val actual = coroutineContext.datasource
        assertEquals(datasource, actual)
    }

    @Test
    fun `fails without context`() {
        val err = assertThrows<IllegalStateException> {
            runBlocking {
                coroutineContext.datasource
            }
        }
        assertEquals("Datasource not in context", err.message)
    }
}
