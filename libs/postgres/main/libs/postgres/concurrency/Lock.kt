package libs.postgres.concurrency

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> withLock(owner: String, action: () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    lock(owner)
    return try {
        action()
    } finally {
        unlock(owner)
    }
}