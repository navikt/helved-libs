package libs.task

import libs.postgres.concurrency.transaction
import java.util.*

object TaskHistory {
    suspend fun history(id: UUID): List<TaskHistoryDao> = transaction {
        TaskHistoryDao.select(id)
    }
}
