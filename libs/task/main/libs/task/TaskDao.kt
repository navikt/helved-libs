@file:Suppress("NAME_SHADOWING")

package libs.task

import libs.postgres.concurrency.connection
import libs.postgres.map
import libs.utils.appLog
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

data class TaskDao(
    val id: UUID = UUID.randomUUID(),
    val payload: String,
    val status: Status,
    val attempt: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val scheduledFor: LocalDateTime,
    val message: String?,
) {
    suspend fun insert() {
        val sql = """
            INSERT INTO task (id, payload, status, attempt, created_at, updated_at, scheduled_for, message) 
            VALUES (?,?,?,?,?,?,?,?)
        """
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, id)
            stmt.setString(2, payload)
            stmt.setString(3, status.name)
            stmt.setObject(4, attempt)
            stmt.setTimestamp(5, Timestamp.valueOf(createdAt))
            stmt.setTimestamp(6, Timestamp.valueOf(updatedAt))
            stmt.setTimestamp(7, Timestamp.valueOf(scheduledFor))
            stmt.setString(8, message)
            stmt.executeUpdate()
        }.also {
            appLog.debug(sql)
        }
    }

    suspend fun update(status: Status) {
        val sql = "UPDATE task SET status = ?, attempt = ?, updated_at = ? WHERE id = ?"
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, status.name)
            stmt.setObject(2, attempt + 1)
            stmt.setObject(3, Timestamp.valueOf(updatedAt))
            stmt.setObject(4, id)
            stmt.executeUpdate()
        }.also {
            appLog.debug(sql)
        }
    }

    suspend fun update(status: Status, message: String) {
        val sql = "UPDATE task SET status = ?, attempt = ?, updated_at = ?, message = ? WHERE id = ?"
        coroutineContext.connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, status.name)
            stmt.setObject(2, attempt + 1)
            stmt.setObject(3, Timestamp.valueOf(updatedAt))
            stmt.setString(4, message)
            stmt.setObject(5, id)
            stmt.executeUpdate()
        }.also {
            appLog.debug(sql)
        }
    }

    companion object {
        suspend fun findUpdatedBefore(status: Status, updated_at: LocalDateTime): List<TaskDao> {
            val sql = """
               SELECT * FROM task
               WHERE status = ? AND updated_at < ?
            """
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status.name)
                stmt.setObject(2, Timestamp.valueOf(updated_at))
                stmt.executeQuery().map(::from)
            }.also {
                appLog.debug(sql)
            }
        }

        suspend fun findBy(status: Status): List<TaskDao> {
            val sql = """
               SELECT * FROM task
               WHERE status = ?
            """
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status.name)
                stmt.executeQuery().map(::from)
            }.also {
                appLog.debug(sql)
            }
        }

        suspend fun countBy(status: Status): Long {
            val sql = """
                SELECT count(*) FROM task
                WHERE status = ?
            """
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status.name)
                stmt.executeQuery().map { it.getLong(1) }.single()
            }.also {
                appLog.debug(sql)
            }
        }

        suspend fun findBy(status: List<Status>): List<TaskDao> {
            val status = status.joinToString(", ") { "'$it'" }
            val sql = """
                SELECT * FROM task
                WHERE status IN ($status)
            """
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().map(::from)
            }.also {
                appLog.debug(sql)
            }
        }

        suspend fun findCreatedAfter(created_at: LocalDateTime): List<TaskDao> {
            val sql = """
               SELECT * FROM task
               WHERE created_at >= ?
            """
            return coroutineContext.connection.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, Timestamp.valueOf(created_at))
                stmt.executeQuery().map(::from)
            }.also {
                appLog.debug(sql)
            }
        }
    }
}

fun TaskDao.Companion.from(rs: ResultSet) = TaskDao(
    id = UUID.fromString(rs.getString("id")),
    payload = rs.getString("payload"),
    status = Status.valueOf(rs.getString("status")),
    attempt = rs.getInt("attempt"),
    createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
    updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
    scheduledFor = rs.getTimestamp("scheduled_for").toLocalDateTime(),
    message = rs.getString("message")
)
