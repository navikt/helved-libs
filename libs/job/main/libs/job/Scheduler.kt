package libs.job

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * A coroutine scheduler for doing concurrent tasks.
 *
 * @param feedRPM - how many times per minute the scheduler will call [feed]
 * @param errorCooldownMs - How long to wait before recovering from an error
 */
abstract class Scheduler<T>(
    private val feedRPM: Int = 1,
    private val errorCooldownMs: Long = 500,
    context: CoroutineContext = Dispatchers.IO
) : AutoCloseable {

    /**
     * Feed the scheduler with some data,
     * Example from datasources like Kafka, Postgres, A RESTful-poll, in-memory-database.
     */
    abstract suspend fun feed(): List<T>

    /**
     * What to do with each fed element
     */
    abstract suspend fun task(fed: T)

    /**
     * What to do when an error occurs
     */
    abstract suspend fun onError(fed: T, err: Throwable)

    /**
     * If you are using leader elections, you may allow only the leader to feed the scheduler.
     * Use default if you only have one application instance, or if you are handling
     * race conditions outside of the scheduler (e.g. with db lock)
     */
    open fun isLeader(): Boolean = true

    private fun flow(): Flow<T> = flow {
        while (true) {
            if (isLeader()) {
                feed().forEach {
                    emit(it)
                }
            }
            delay((60_000 / feedRPM).milliseconds)
        }
    }

    private val job: Job = CoroutineScope(context).launch {
        while (isActive) {
            flow().distinctUntilChanged().collect {
                try {
                    task(it)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onError(it, e)
                    delay(errorCooldownMs)
                }
            }
        }
    }

    override fun close() {
        if (!job.isCompleted) {
            runBlocking {
                job.cancelAndJoin()
            }
        }
    }
}
