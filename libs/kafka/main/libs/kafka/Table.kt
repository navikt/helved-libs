package libs.kafka

import libs.kafka.processor.StateInitProcessor
import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.kstream.KTable

data class Table<T : Any>(
    val sourceTopic: Topic<T>,
    val stateStoreName: String = "${sourceTopic.name}-state-store"
) {
    val sourceTopicName: String
        get() = sourceTopic.name
}

class KTable<T : Any>(
    val table: Table<T>,
    val internalKTable: KTable<String, T?>,
) {
    internal val tombstonedInternalKTable: KTable<String, T> by lazy {
        internalKTable.skipTombstone(table)
    }

    fun init(processor: StateInitProcessor<T>) {
        processor.addToStreams()
    }

    fun toStream(): ConsumedStream<T> {
        return ConsumedStream(
            table.sourceTopic,
            internalKTable.toStream().skipTombstone(table.sourceTopic, "to-stream"),
            { "consume-${table.stateStoreName}" })
    }
}

