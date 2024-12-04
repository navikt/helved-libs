package libs.kafka

import libs.kafka.processor.LogProduceTableProcessor
import libs.kafka.processor.LogProduceTopicProcessor
import libs.kafka.processor.Processor.Companion.addProcessor
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.Repartitioned
import org.apache.kafka.streams.state.KeyValueStore

internal fun <T : Any> KStream<String, T>.produceWithLogging(
    topic: Topic<T>,
    named: String,
) {
    val logger = LogProduceTopicProcessor("log-${named}", topic)
    val options = topic.produced(named)
    return addProcessor(logger).to(topic.name, options)
}

internal fun <L : Any, R : Any, LR> KStream<String, L>.leftJoin(
    left: Topic<L>,
    right: KTable<R>,
    joiner: (String, L, R?) -> LR,
): KStream<String, LR> {
    val ktable = right.internalKTable
    val joined = left leftJoin right
    return leftJoin(ktable, joiner, joined)
}

internal fun <L : Any, R : Any, LR> KStream<String, L>.leftJoin(
    left: Topic<L>,
    right: KTable<R>,
    joiner: (L, R?) -> LR,
): KStream<String, LR> {
    val ktable = right.internalKTable
    val joined = left leftJoin right
    return leftJoin(ktable, joiner, joined)
}

internal fun <L : Any, R : Any, LR> KStream<String, L>.join(
    left: Topic<L>,
    right: KTable<R>,
    joiner: (String, L, R) -> LR,
): KStream<String, LR> {
    val ktable = right.tombstonedInternalKTable
    val joined = left join right
    return join(ktable, joiner, joined)
}

internal fun <L : Any, R : Any, LR> KStream<String, L>.join(
    left: Topic<L>,
    right: KTable<R>,
    joiner: (L, R) -> LR,
): KStream<String, LR> {
    val ktable = right.tombstonedInternalKTable
    val joined = left join right
    return join(ktable, joiner, joined)
}

internal fun <T : Any> KStream<String, T?>.toKtable(table: Table<T>): KTable<T> {
    val internalKTable = addProcessor(LogProduceTableProcessor(table))
        .toTable(
            Named.`as`("${table.sourceTopicName}-to-table"),
            materialized(table)
        )

    return KTable(table, internalKTable)
}

internal fun <T> repartitioned(table: Table<T & Any>, partitions: Int): Repartitioned<String, T> {
    return Repartitioned
        .with(table.sourceTopic.keySerde, table.sourceTopic.valueSerde)
        .withNumberOfPartitions(partitions)
        .withName(table.sourceTopicName)
}

internal fun <T : Any> materialized(table: Table<T>): Materialized<String, T?, KeyValueStore<Bytes, ByteArray>> {
    return Materialized.`as`<String, T, KeyValueStore<Bytes, ByteArray>>(table.stateStoreName)
        .withKeySerde(table.sourceTopic.keySerde)
        .withValueSerde(table.sourceTopic.valueSerde)
}

@Suppress("UNCHECKED_CAST")
internal fun <V> KStream<String, V>.filterNotNull(): KStream<String, V & Any> {
    val filteredInternalKStream = filter { _, value -> value != null }
    return filteredInternalKStream as KStream<String, V & Any>
}

@Suppress("UNCHECKED_CAST")
internal fun <T> org.apache.kafka.streams.kstream.KTable<String, T>.skipTombstone(
    table: Table<T & Any>
): org.apache.kafka.streams.kstream.KTable<String, T & Any> {
    val named = Named.`as`("skip-table-${table.sourceTopicName}-tombstone")
    val filteredInternalKTable = filter({ _, value -> value != null }, named)
    return filteredInternalKTable as org.apache.kafka.streams.kstream.KTable<String, T & Any>
}

internal fun <V> KStream<String, V>.skipTombstone(topic: Topic<V & Any>): KStream<String, V & Any> {
    return skipTombstone(topic, "")
}

@Suppress("UNCHECKED_CAST")
internal fun <V> KStream<String, V>.skipTombstone(
    topic: Topic<V & Any>,
    namedSuffix: String,
): KStream<String, V & Any> {
    val named = Named.`as`("skip-${topic.name}-tombstone$namedSuffix")
    val filteredInternalStream = filter({ _, value -> value != null }, named)
    return filteredInternalStream as KStream<String, V & Any>
}
