package jdbc

import kotlinx.coroutines.withContext
import kotlin.contracts.contract
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import logger
import secureLog
import java.sql.Connection
import java.sql.SQLException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import javax.sql.DataSource
import kotlin.coroutines.AbstractCoroutineContextElement

private val jdbcLog = logger("jdbc")

/**
 * Create or use current transaction on the coroutine context.
 * This requires a connection registered on the coroutine context.
 * 
 * @param block - the code block to execute
 * @return T - an arbitrary type, can be Unit
 */
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> transaction(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    val existingTransaction = coroutineContext[CoroutineTransaction]

    return when {
        existingTransaction == null -> {
            withConnection {
                runTransactionally {
                    block()
                }
            }
        }

        !existingTransaction.completed -> {
            withContext(coroutineContext) {
                block()
            }
        }

        else -> error("Nested transactions not supported")
    }
}

@PublishedApi
@OptIn(ExperimentalContracts::class)
internal suspend inline fun <T> runTransactionally(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    coroutineContext.connection.runWithManuelCommit {
        val transaction = CoroutineTransaction()

        try {
            val result = withContext(transaction) {
                block()
            }

            commit()
            return result
        } catch (e: Throwable) {
            rollback()
            throw e
        } finally {
            transaction.complete()
        }
    }
}

@PublishedApi
@OptIn(ExperimentalContracts::class)
internal inline fun <T> Connection.runWithManuelCommit(block: Connection.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val before = autoCommit
    return try {
        autoCommit = false
        run(block)
    } finally {
        autoCommit = before
    }
}

/**
 * Create or reuse the database connection registered on the coroutine context. 
 * This requires a datasource registered on the coroutine context.
 * 
 * @param block - the code block to execute
 * @return T - an arbitrary type, can be Unit
 */
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> withConnection(crossinline block: suspend CoroutineScope.() -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return if (coroutineContext.hasOpenConnection()) {
        withContext(coroutineContext) {
            block()
        }
    } else {
        val connection = coroutineContext.datasource.connection

        try {
            withContext(CoroutineConnection(connection)) {
                block()
            }
        } finally {
            connection.closeCatching()
        }
    }
}

@PublishedApi
internal fun CoroutineContext.hasOpenConnection(): Boolean {
    val con = get(CoroutineConnection)?.connection
    return con != null && !con.isClosedCatching()
}

@PublishedApi
internal fun Connection.closeCatching() {
    try {
        close()
    } catch (e: SQLException) {
        jdbcLog.warn("Failed to close database connection")
        secureLog.warn("Failed to close database connection", e)
    }
}

@PublishedApi
internal fun Connection.isClosedCatching(): Boolean {
    return try {
        isClosed
    } catch (e: SQLException) {
        jdbcLog.warn("Connection isClosedCatching check failed, already closed?")
        secureLog.warn("Connection isClosedCatching check failed, already closed?", e)
        true
    }
}

/**
 * Gets the connection from the coroutine context if registered.
 */
val CoroutineContext.connection: Connection
    get() = get(CoroutineConnection)
        ?.connection
        ?: error("Connection not in context")

/**
 * Gets the datasource from the coroutine context if registered.
 */
val CoroutineContext.datasource: DataSource
    get() = get(CoroutineDatasource)
        ?.datasource
        ?: error("Datasource not in context")

class CoroutineDatasource(
    val datasource: DataSource,
) : AbstractCoroutineContextElement(CoroutineDatasource) {
    companion object Key : CoroutineContext.Key<CoroutineDatasource>

    override fun toString() = "CoroutineDataSource($datasource)"
}

class CoroutineConnection(
    val connection: Connection
) : AbstractCoroutineContextElement(CoroutineConnection) {

    companion object Key : CoroutineContext.Key<CoroutineConnection>

    override fun toString(): String = "CoroutineConnection($connection)"
}

@PublishedApi
internal class CoroutineTransaction : AbstractCoroutineContextElement(CoroutineTransaction) {
    companion object Key : CoroutineContext.Key<CoroutineTransaction>

    var completed: Boolean = false
        private set

    fun complete() {
        completed = true
    }

    override fun toString(): String = "CoroutineTransaction(completed=$completed)"
}

