package libs.jdbc

import libs.postgres.JdbcConfig

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
}