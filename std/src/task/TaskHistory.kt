package task

import jdbc.transaction
import java.util.*

object TaskHistory {
    suspend fun history(id: UUID): List<TaskHistoryDao> = transaction {
        TaskHistoryDao.select(id)
    }
}
