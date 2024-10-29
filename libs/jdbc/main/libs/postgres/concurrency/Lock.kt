package libs.postgres.concurrency

import libs.utils.logger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private val jdbcLog = logger("jdbc")

suspend fun tryLock(on: String): Boolean = transaction {
    val query = "SELECT PG_TRY_ADVISORY_LOCK(${on.hashCode()})"
    coroutineContext.connection.prepareStatement(query).use { stmt ->
        stmt.execute().also {
            jdbcLog.debug("Locked $on")
        }
    }
}

suspend fun unlock(on: String): Boolean = transaction {
    val query = "SELECT PG_ADVISORY_UNLOCK(${on.hashCode()})"
    coroutineContext.connection.prepareStatement(query).use { stmt ->
        stmt.execute().also {
            jdbcLog.debug("Unlocked $on")
        }
    }
}

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> withLock(owner: String, action: () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    tryLock(owner)
    return try {
        action()
    } finally {
        unlock(owner)
    }
}