package libs.kafka

import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.kstream.KTable

data class Table<K: Any, V: Any>(
    val sourceTopic: Topic<K, V>,
    val serdes: Serdes<K, V> = sourceTopic.serdes,
    val stateStoreName: StateStoreName = "${sourceTopic.name}-state-store"
) {
    val sourceTopicName: String
        get() = sourceTopic.name
}

class KTable<K: Any, V : Any>(
    val table: Table<K, V>,
    val serdes: Serdes<K, V> = table.serdes,
    val internalKTable: KTable<K, V?>,
) {
    internal val tombstonedInternalKTable: KTable<K, V> by lazy {
        internalKTable.skipTombstone(table)
    }

    fun toStream(): ConsumedStream<K, V> {
        return ConsumedStream(
            serdes,
            internalKTable.toStream().skipTombstone(table.sourceTopic, "to-stream"),
            { "consume-${table.stateStoreName}" })
    }
}

