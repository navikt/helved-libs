package libs.postgres.concurreny

import libs.postgres.concurrency.connection
import libs.postgres.map
import java.util.*
import kotlin.coroutines.coroutineContext

internal data class AsyncDao(
    val id: UUID,
    val data: String,
) {
    suspend fun insert() {
        coroutineContext.connection
            .prepareStatement("INSERT INTO TEST_TABLE (id, data) values (?, ?)").use {
                it.setObject(1, id)
                it.setObject(2, data)
                it.executeUpdate()
            }
    }

    suspend fun insertAndThrow() {
        insert()
        error("wops")
    }

    companion object {
        suspend fun count(): Int = coroutineContext.connection
            .prepareStatement("SELECT count(*) FROM TEST_TABLE").use { stmt ->
                stmt.executeQuery()
                    .map { it.getInt(1) }
                    .singleOrNull() ?: 0
            }
    }
}
