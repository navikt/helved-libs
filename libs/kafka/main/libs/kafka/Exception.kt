package libs.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.errors.*
import org.slf4j.LoggerFactory
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse as StreamHandler
import org.apache.kafka.streams.errors.ProcessingExceptionHandler.ProcessingHandlerResponse as ProcessingHandler;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler.DeserializationHandlerResponse as ConsumeHandler;


private val secureLog = LoggerFactory.getLogger("secureLog")

class ReplaceThread(message: Any) : RuntimeException(message.toString())

/**
 * Entry point exception handler (consuming records)
 *
 * Exceptions during deserialization, networks issues etc.
 */
class ConsumeAgainErrorHandler : DeserializationExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle(
        context: ErrorHandlerContext,
        record: ConsumerRecord<ByteArray, ByteArray>,
        exception: java.lang.Exception
    ): ConsumeHandler {
        secureLog.warn(
            """
               Exception deserializing record. Retrying...
               Topic: ${record.topic()}
               Partition: ${record.partition()}
               Offset: ${record.offset()}
               TaskId: ${context.taskId()}
            """.trimIndent(),
            exception
        )
        return ConsumeHandler.FAIL
    }
}
class ConsumeNextErrorHandler : DeserializationExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle(
        context: ErrorHandlerContext,
        record: ConsumerRecord<ByteArray, ByteArray>,
        exception: java.lang.Exception
    ): DeserializationExceptionHandler.DeserializationHandlerResponse {
        secureLog.warn(
            """
               Exception deserializing record. Reading next record...
               Topic: ${record.topic()}
               Partition: ${record.partition()}
               Offset: ${record.offset()}
               TaskId: ${context.taskId()}
            """.trimIndent(),
            exception
        )
        return DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE
    }
}

class ProcessNextHandler: ProcessingExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle(
        c: ErrorHandlerContext, 
        r: org.apache.kafka.streams.processor.api.Record<*, *>, 
        e: java.lang.Exception,
    ): ProcessingHandler {
        secureLog.error("Feil ved prosessering av record, logger og leser neste record", e)
        return ProcessingHandler.CONTINUE
    }
}

/**
 * Processing exception handling (process records in the user code)
 *
 * Exceptions not handled by Kafka Streams
 * Three options:
 *  1. replace thread
 *  2. shutdown indicidual stream instance
 *  3. shutdown all streams instances (with the same application-id
 */
class ProcessingErrHandler : StreamsUncaughtExceptionHandler {
    override fun handle(exception: Throwable): StreamHandler = logAndReplaceThread(exception)

    private fun logAndReplaceThread(err: Throwable): StreamHandler {
        secureLog.error("Feil ved prosessering av record, logger og leser neste record", err)
        return StreamHandler.REPLACE_THREAD
    }

    private fun logAndShutdownClient(err: Throwable): StreamHandler {
        secureLog.error("Uventet feil, logger og avslutter client", err)
        return StreamHandler.SHUTDOWN_CLIENT
    }
}

/**
 * Exit point exception handler (producing records)
 *
 * Exceptions due to serialization, networking etc.
 */
class ProducerErrHandler : ProductionExceptionHandler {
    override fun configure(configs: MutableMap<String, *>) {}

    override fun handle(
        context: ErrorHandlerContext?,
        record: ProducerRecord<ByteArray, ByteArray>?,
        exception: java.lang.Exception?
    ): ProductionExceptionHandler.ProductionExceptionHandlerResponse {
        secureLog.error("Feil i streams, logger og leser neste record", exception)
        return ProductionExceptionHandler.ProductionExceptionHandlerResponse.FAIL
    }
}

