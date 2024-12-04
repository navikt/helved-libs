package libs.kafka.processor

import libs.kafka.KTable
import libs.kafka.KeyValue
import libs.kafka.processor.ProcessorMetadata
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.FixedKeyProcessor
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext
import org.apache.kafka.streams.processor.api.FixedKeyRecord
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import kotlin.jvm.optionals.getOrNull

internal interface KStateProcessor<T, U, R> {
    fun process(
        metadata: ProcessorMetadata,
        store: TimestampedKeyValueStore<String, T>,
        keyValue: KeyValue<String, U>,
    ): R
}

abstract class StateProcessor<T : Any, U, R>(
    private val named: String,
    private val table: KTable<T>,
) : KStateProcessor<T, U, R> {
    internal companion object {
        internal fun <T : Any, U, R> KStream<String, U>.addProcessor(
            processor: StateProcessor<T, U, R>
        ): KStream<String, R> = processValues(
            { processor.run(StateProcessor<T, U, R>::InternalProcessor) },
            Named.`as`("stateful-operation-${processor.named}"),
            processor.table.table.stateStoreName,
        )
    }

    private inner class InternalProcessor : FixedKeyProcessor<String, U, R> {
        private lateinit var context: FixedKeyProcessorContext<String, R>
        private lateinit var store: TimestampedKeyValueStore<String, T>

        override fun init(context: FixedKeyProcessorContext<String, R>) {
            this.context = context
            this.store = context.getStateStore(table.table.stateStoreName)
        }

        override fun process(record: FixedKeyRecord<String, U>) {
            val recordMeta = requireNotNull(context.recordMetadata().getOrNull()) {
                "Denne er bare null når man bruker punctuators. Det er feil å bruke denne klassen til punctuation."
            }

            val metadata = ProcessorMetadata(
                topic = recordMeta.topic(),
                partition = recordMeta.partition(),
                offset = recordMeta.offset(),
                timestamp = record.timestamp(),
                systemTimeMs = context.currentSystemTimeMs(),
                streamTimeMs = context.currentStreamTimeMs(),
            )

            val valueToForward: R = process(
                metadata = metadata,
                store = store,
                keyValue = KeyValue(record.key(), record.value()),
            )

            context.forward(record.withValue(valueToForward))
        }
    }
}

