package libs.task

import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class TaskDaoTest : H2() {

    private val task = enTask(Status.UNPROCESSED)

    @Test
    fun `can update status`() = runTest(h2) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(status = Status.COMPLETE).update()
        }

        val actual = transaction { TaskDao.select(id = task.id) }
        assertEquals(Status.COMPLETE, actual.single().status)
    }

    @Test
    fun `can update attempt`() = runTest(h2) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(attempt = task.attempt + 4).update()
        }

        val actual = transaction { TaskDao.select(id = task.id) }
        assertEquals(4, actual.single().attempt)
    }

    @Test
    fun `can update update_at`() = runTest(h2) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(updatedAt = LocalDateTime.of(2024, 5, 17, 23, 59)).update()
        }

        val actual = transaction { TaskDao.select(id = task.id) }
        assertEquals(LocalDateTime.of(2024, 5, 17, 23, 59), actual.single().updatedAt)
    }

    @Test
    fun `can update message`() = runTest(h2) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(message = "hello there").update()
        }

        val actual = transaction { TaskDao.select(id = task.id) }
        assertEquals("hello there", actual.single().message)
    }

    @Test
    fun `can update scheduled_for`() = runTest(h2) {
        transaction {
            task.insert()
        }

        transaction {
            task.copy(scheduledFor = LocalDateTime.of(2024, 5, 17, 23, 59)).update()
        }

        val actual = transaction { TaskDao.select(id = task.id) }
        assertEquals(LocalDateTime.of(2024, 5, 17, 23, 59), actual.single().scheduledFor)
    }

    @Test
    fun `can update status attempt updated_at`() = runTest(h2) {
        val now = LocalDateTime.now()
        val id = UUID.randomUUID()

        transaction {
            TaskDao(
                id = id,
                payload = "some payload",
                status = Status.UNPROCESSED,
                attempt = 0,
                createdAt = now,
                updatedAt = now,
                scheduledFor = now,
                message = null,
            ).insert()
        }

        val before = transaction { TaskDao.select(id = id) }.single()
        assertEquals(Status.UNPROCESSED, before.status)
        assertEquals(now, before.updatedAt)
        assertEquals(0, before.attempt)
        assertEquals(null, before.message)

        transaction {
            before.copy(
                status = Status.FAIL,
                updatedAt = now.plusMinutes(1),
                attempt = task.attempt + 1,
                message = "Invalid payload"
            ).update()
        }

        val actual = transaction { TaskDao.select(id = id) }.single()
        assertEquals(Status.FAIL, actual.status)
        assertEquals(now.plusMinutes(1), actual.updatedAt)
        assertEquals(1, actual.attempt)
        assertEquals("Invalid payload", actual.message)
    }
}