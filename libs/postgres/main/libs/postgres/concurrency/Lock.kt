package libs.postgres.concurrency

suspend fun lock(on: String): Boolean = transaction {
    val query = "SELECT PG_ADVISORY_LOCK(${on.hashCode()})"
    coroutineContext.connection.prepareStatement(query).use { stmt ->
        stmt.execute()
    }
}

suspend fun tryLock(on: String): Boolean = transaction {
    val query = "SELECT PG_TRY_ADVISORY_LOCK(${on.hashCode()})"
    coroutineContext.connection.prepareStatement(query).use { stmt ->
        stmt.execute()
    }
}

suspend fun unlock(on: String): Boolean = transaction {
    val query = "SELECT PG_ADVISORY_UNLOCK(${on.hashCode()})"
    coroutineContext.connection.prepareStatement(query).use { stmt ->
        stmt.execute()
    }
}
