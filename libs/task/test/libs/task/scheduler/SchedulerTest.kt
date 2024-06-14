package libs.task.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.postgres.map
import libs.task.H2
import libs.task.Status
import libs.task.TaskDao
import libs.utils.appLog
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import kotlin.system.measureTimeMillis

class SchedulerTest : H2() {

    @Test
    fun `UNPROCESSED tasks is set to PROCESSING by scheduler`() = runTest(h2) {
        appLog.info("initially ${count(Status.UNPROCESSED)} UNPROCESSED tasks")

        produceWhile {
            count(Status.UNPROCESSED) < 10
        }

        consumeWhile {
            count(Status.UNPROCESSED) > 1
        }
    }

    private suspend fun consumeWhile(predicate: suspend () -> Boolean) {
        Scheduler(h2).use {
            val timed = measureTimeMillis {
                while (predicate()) continue
            }

            val unprocCount = count(Status.UNPROCESSED)
            val procCount = count(Status.PROCESSING)

            appLog.info("Changed $unprocCount UNPROCESSED -> $procCount PROCESSING in $timed ms")
        }
    }

    private suspend fun produceWhile(predicate: suspend () -> Boolean) {
        val producer = scope.launch {
            for (task in infiniteTasks) {
                transaction {
                    task.insert()
                }
            }
        }

        val timed = measureTimeMillis {
            while (predicate()) continue
        }

        producer.cancelAndJoin()

        appLog.info("saved ${count(Status.UNPROCESSED)} UNPROCESSED tasks in $timed ms")
    }
}

private suspend fun count(status: Status): Int =
    transaction {
        coroutineContext.connection
            .prepareStatement("SELECT count(*) FROM task WHERE status = ?").use { stmt ->
                stmt.setString(1, status.name)
                stmt.executeQuery()
                    .map { it.getInt(1) }
                    .singleOrNull() ?: 0
            }
    }

@OptIn(ExperimentalCoroutinesApi::class)
private val CoroutineScope.infiniteTasks
    get() = produce {
        while (true) {
            val id = UUID.randomUUID()
            val now = LocalDateTime.now()
            val task = TaskDao(
                id = id,
                payload = "$id",
                status = Status.UNPROCESSED,
                attempt = 0,
                createdAt = now,
                updatedAt = now,
                scheduledFor = now,
                message = null,
            )
            send(task)
        }
    }