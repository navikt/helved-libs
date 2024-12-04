package libs.kafka

import libs.kafka.processor.StateInitProcessor
import libs.kafka.processor.StateScheduleProcessor
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

    fun schedule(scheduler: StateScheduleProcessor<T>) {
        scheduler.addToStreams()
    }

    fun init(processor: StateInitProcessor<T>) {
        processor.addToStreams()
    }
}
