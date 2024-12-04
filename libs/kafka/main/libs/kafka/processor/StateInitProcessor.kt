package libs.kafka.processor

import libs.kafka.KTable
import libs.kafka.StateStore
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.processor.api.FixedKeyProcessor
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext
import org.apache.kafka.streams.processor.api.FixedKeyRecord
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp

internal interface KStateInitProcessor<T> {
    fun init(store: StateStore<T>)
}

abstract class StateInitProcessor<T>(
    private val named: String,
    private val table: KTable<T & Any>,
) : KStateInitProcessor<T> {
    internal fun addToStreams() {
        val stateStoreName = table.table.stateStoreName
        val internalStream = table.internalKTable.toStream()
        internalStream.processValues(
            { InternalProcessor() },
            Named.`as`(named),
            stateStoreName,
        )
    }

    private inner class InternalProcessor : FixedKeyProcessor<String, T?, T> {
        private lateinit var store: ReadOnlyKeyValueStore<String, ValueAndTimestamp<T>>

        override fun init(context: FixedKeyProcessorContext<String, T>) {
            this.store = context.getStateStore(table.table.stateStoreName)
            init(StateStore(store))
        }

        override fun process(record: FixedKeyRecord<String, T?>) {}
    }
}
