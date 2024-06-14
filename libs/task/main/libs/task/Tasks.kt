package libs.task

import libs.postgres.concurrency.transaction
import java.time.LocalDateTime

object Tasks {

    suspend fun incomplete(): List<TaskDto> =
        transaction {
            TaskDao.findBy(Status.entries - Status.COMPLETE)
                .map(TaskDto::from)
        }

    suspend fun forStatus(status: Status): List<TaskDto> =
        transaction {
            TaskDao.findBy(status)
                .map(TaskDto::from)
        }

    suspend fun createdAfter(after: LocalDateTime): List<TaskDto> =
        transaction {
            TaskDao.findCreatedAfter(after)
                .map(TaskDto::from)
        }
}
