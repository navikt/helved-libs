package libs.task

import H2
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

class TasksTest {
    val oneSec: RetryStrategy = { LocalDateTime.now().plusSeconds(1) }

    @AfterEach
    fun clear() {
        runBlocking {
            H2.truncate()
        }
    }

    @Test
    fun `excludes completed`() = runTest(H2.context) {
        transaction {
            enTask(Status.COMPLETE).insert()
        }
        assertEquals(0, Tasks.incomplete().size)
    }

    @Test
    fun `includes in progress`() = runTest(H2.context) {
        transaction {
            enTask(Status.IN_PROGRESS).insert()
        }
        assertEquals(1, Tasks.incomplete().size)
    }

    @Test
    fun `includes manual`() = runTest(H2.context) {
        transaction {
            enTask(Status.MANUAL).insert()
        }
        assertEquals(1, Tasks.incomplete().size)
    }

    @Test
    fun `includes fail`() = runTest(H2.context) {
        transaction {
            enTask(Status.FAIL).insert()
        }
        assertEquals(1, Tasks.incomplete().size)
    }

    @Test
    fun `filter selected`() = runTest(H2.context) {
        transaction {
            Status.entries.forEach { status ->
                enTask(status).insert()
            }
        }

        Status.entries.forEach { status ->
            val tasks = Tasks.forStatus(status)
            assertEquals(1, tasks.size)
            assertEquals(status, tasks.single().status)
        }
    }

    @Test
    fun `includes after`() = runTest(H2.context) {
        transaction {
            enTask(createdAt = LocalDateTime.of(2024, 6, 14, 10, 45)).insert()
            enTask(createdAt = LocalDateTime.of(2024, 6, 15, 10, 45)).insert()
            enTask(createdAt = LocalDateTime.of(2024, 6, 16, 10, 45)).insert()
        }

        val actual = Tasks.createdAfter(LocalDateTime.of(2024, 6, 13, 10, 45))
        assertEquals(3, actual.size)
    }

    @Test
    fun `excludes before`() = runTest(H2.context) {
        transaction {
            enTask(createdAt = LocalDateTime.of(2024, 6, 14, 10, 45)).insert()
            enTask(createdAt = LocalDateTime.of(2024, 6, 15, 10, 45)).insert()
            enTask(createdAt = LocalDateTime.of(2024, 6, 16, 10, 45)).insert()
        }

        val actual = Tasks.createdAfter(LocalDateTime.of(2024, 6, 17, 10, 45))
            .filterNot { it.kind == Kind.Avstemming } // these are automatically created on startup

        assertEquals(0, actual.size)
    }

    @Test
    fun `includes limit`() = runTest(H2.context) {
        transaction {
            enTask(createdAt = LocalDateTime.of(2024, 6, 14, 10, 45)).insert()
            enTask(createdAt = LocalDateTime.of(2024, 6, 15, 10, 45)).insert()
            enTask(createdAt = LocalDateTime.of(2024, 6, 16, 10, 45)).insert()
        }

        val actual = Tasks.createdAfter(LocalDateTime.of(2024, 6, 15, 10, 45))

        assertEquals(2, actual.size)
    }

    @Test
    fun `attempt is increased`() = runTest(H2.context) {
        val task = transaction {
            enTask(Status.IN_PROGRESS).apply { insert() }
        }
        transaction {
            Tasks.update(task.id, Status.MANUAL, "Klarer ikke automatisk sende inn oppdrag", oneSec)
        }

        val actual = transaction { TaskDao.select { it.id = task.id } }.single()
        assertEquals(1, actual.attempt)
    }

    @Test
    fun `update_at is set to now`() = runTest(H2.context) {
        val task = transaction {
            enTask(Status.IN_PROGRESS).apply { insert() }
        }
        transaction {
            Tasks.update(task.id, Status.IN_PROGRESS, "Oppdrag var stengt. Forsøker igjen...", oneSec)
        }

        val actual = transaction { TaskDao.select { it.id = task.id } }.single()
        assertTrue(task.updatedAt.isBefore(actual.updatedAt))
        assertTrue(LocalDateTime.now().isAfter(actual.updatedAt))
    }

    @Test
    fun `scheduled for is set according to retry strategy`() = runTest(H2.context) {
        val task = transaction {
            enTask(Status.IN_PROGRESS).apply { insert() }
        }

        val expectedNextAttemptTime = task.oneSec()

        transaction {
            Tasks.update(task.id, Status.IN_PROGRESS, "Oppdrag var stengt. Forsøker igjen...", oneSec)
        }

        val updatedTask = transaction {
            TaskDao.select { it.id = task.id }
        }.firstOrNull()

        assertWithin(expectedNextAttemptTime, updatedTask?.scheduledFor!!, 1)
    }

    @Test
    fun `message is applied`() = runTest(H2.context) {
        val task = transaction {
            enTask(Status.IN_PROGRESS).apply { insert() }
        }
        transaction {
            Tasks.update(task.id, Status.FAIL, "Ugyldig id", oneSec)
        }

        val actual = transaction { TaskDao.select { it.id = task.id } }.single()
        assertEquals("Ugyldig id", actual.message)
    }

    private fun assertWithin(
        expected: LocalDateTime,
        actual: LocalDateTime,
        seconds: Long,
    ) = listOf(
        expected.truncatedTo(ChronoUnit.SECONDS),
        expected.truncatedTo(ChronoUnit.SECONDS).plusSeconds(seconds),
    ).contains(actual.truncatedTo(ChronoUnit.SECONDS))
}
