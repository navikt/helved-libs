package task

import jdbc.transaction
import java.time.LocalDateTime
import java.util.*

typealias RetryStrategy = TaskDao.(attemptNumber: Int) -> LocalDateTime

object Tasks {
    suspend fun filterBy(
        status: List<Status>?,
        after: LocalDateTime?,
        kind: List<Kind>?,
        payload: String?,
        limit: Int? = null,
        offset: Int? = null,
        order: Order? = null,
    ): List<TaskDao> = transaction {
        TaskDao
            .select(limit, offset, order) {
                it.status = status
                it.createdAt = after?.let { SelectTime(Operator.GE, after) }
                it.kind = kind
                it.payload = payload
            }
    }

    suspend fun count(
        status: List<Status>?,
        after: LocalDateTime?,
        kind: List<Kind>?,
        payload: String?,
    ): Int = transaction {
        TaskDao.count {
            it.status = status
            it.createdAt = after?.let { SelectTime(Operator.GE, after) }
            it.kind = kind
            it.payload = payload
        }
    }

    suspend fun incomplete(): List<TaskDao> = transaction {
        TaskDao.select { it.status = Status.entries - Status.COMPLETE }
    }

    suspend fun forKind(kind: Kind): List<TaskDao> = transaction {
        TaskDao.select { it.kind = listOf(kind) }
    }

    suspend fun forId(id: UUID): TaskDao? = transaction {
        TaskDao.select { it.id = id }.firstOrNull()
    }

    suspend fun forStatus(status: Status): List<TaskDao> = transaction {
        TaskDao.select { it.status = listOf(status) }
    }

    suspend fun createdAfter(after: LocalDateTime): List<TaskDao> = transaction {
        TaskDao.select { it.createdAt = SelectTime(Operator.GE, after) }
    }

    suspend fun rerunAll(status: List<Status>, kind: List<Kind>) =
        transaction {
            TaskDao.rerunAll(status, kind)
        }

    suspend fun rerun(id: UUID) =
        transaction {
            val now = LocalDateTime.now()
            val task = TaskDao.select { it.id = id }.single()
            task
                .copy(
                    updatedAt = now,
                    scheduledFor = now,
                ).update()
            TaskHistoryDao(
                taskId = task.id,
                createdAt = task.createdAt,
                triggeredAt = task.updatedAt,
                triggeredBy = task.updatedAt,
                status = task.status,
                message = task.message,
            ).insert()
        }

    suspend fun update(
        id: UUID,
        status: Status,
        msg: String?,
        retryStrategy: RetryStrategy,
    ) = transaction {
        val task = TaskDao.select { it.id = id }.single()

        task.copy(
            status = status,
            updatedAt = LocalDateTime.now(),
            scheduledFor = task.retryStrategy(task.attempt),
            attempt = task.attempt + 1,
            message = msg,
        ).update()

        TaskHistoryDao(
            taskId = task.id,
            createdAt = task.createdAt,
            triggeredAt = task.updatedAt,
            triggeredBy = task.updatedAt,
            status = task.status,
            message = task.message,
        ).insert()
    }

    suspend fun complete(id: UUID) = transaction {
        val task = TaskDao.select { it.id = id }.single()

        task.copy(
            status = Status.COMPLETE,
            updatedAt = LocalDateTime.now(),
            attempt = task.attempt + 1,
        ).update()

        TaskHistoryDao(
            taskId = task.id,
            createdAt = task.createdAt,
            triggeredAt = task.updatedAt,
            triggeredBy = task.updatedAt,
            status = task.status,
            message = null,
        ).insert()
    }

    suspend fun <T> create(
        kind: Kind,
        payload: T,
        scheduledFor: LocalDateTime? = null,
        payloadMapper: (T) -> String
    ): UUID =
        transaction {
            val now = LocalDateTime.now()
            val taskId = UUID.randomUUID()
            TaskDao(
                id = taskId,
                kind = kind,
                payload = payloadMapper(payload),
                status = Status.IN_PROGRESS,
                attempt = 0,
                message = null,
                createdAt = now,
                updatedAt = now,
                scheduledFor = scheduledFor ?: now,
            ).insert()

            taskId
        }
}
