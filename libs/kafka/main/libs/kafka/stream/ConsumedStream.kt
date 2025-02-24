package libs.kafka.stream

import kotlin.time.Duration
import kotlin.time.toJavaDuration
import libs.kafka.*
import libs.kafka.KTable
import libs.kafka.filterNotNull
import libs.kafka.processor.MetadataProcessor
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.ProcessorMetadata
import libs.kafka.processor.StateProcessor
import libs.kafka.processor.StateProcessor.Companion.addProcessor
import libs.kafka.produceWithLogging
import org.apache.kafka.streams.kstream.*

@Suppress("UNCHECKED_CAST")
class ConsumedStream<K: Any, V : Any> internal constructor(
    private val serdes: Serdes<K, V>,
    private val stream: KStream<K, V>,
    private val namedSupplier: () -> String,
    private val topic: Topic<K, V>? = null,
) {
    fun produce(topic: Topic<K, V>) {
        val named = "produced-${topic.name}-${namedSupplier()}"
        stream.produceWithLogging(topic, named)
    }

    fun <K2: Any> rekey(serde: StreamSerde<K2>, selectKeyFromValue: (V) -> K2): ConsumedStream<K2, V> {
        val rekeyedStream = stream.selectKey { _, value -> selectKeyFromValue(value) }
        return ConsumedStream(Serdes(serde, serdes.value), rekeyedStream, namedSupplier)
    }
    fun rekey(selectKeyFromValue: (V) -> K): ConsumedStream<K, V> {
        val rekeyedStream = stream.selectKey { _, value -> selectKeyFromValue(value) }
        return ConsumedStream(serdes, rekeyedStream, namedSupplier)
    }

    fun <K2: Any> rekey(serde: StreamSerde<K2>, selectKeyFromValue: (K, V) -> K2): ConsumedStream<K2, V> {
        val rekeyedStream = stream.selectKey { key, value -> selectKeyFromValue(key, value) }
        return ConsumedStream(Serdes(serde, serdes.value), rekeyedStream, namedSupplier)
    }
    fun rekey(selectKeyFromValue: (K, V) -> K): ConsumedStream<K, V> {
        val rekeyedStream = stream.selectKey { key, value -> selectKeyFromValue(key, value) }
        return ConsumedStream(serdes, rekeyedStream, namedSupplier)
    }

    fun filter(lambda: (V) -> Boolean): ConsumedStream<K, V> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return ConsumedStream(serdes, filteredStream, namedSupplier)
    }

    fun filterKey(lambda: (K) -> Boolean): ConsumedStream<K, V> {
        val filteredStream = stream.filter { key, _ -> lambda(key) }
        return ConsumedStream(serdes, filteredStream, namedSupplier)
    }

    fun <U : Any> map(serde: StreamSerde<U>, mapper: (value: V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues { value -> mapper(value) }
        return MappedStream(Serdes(serdes.key, serde), mappedStream, namedSupplier)
    }
    fun map(mapper: (value: V) -> V): MappedStream<K, V> {
        val mappedStream = stream.mapValues { value -> mapper(value) }
        return MappedStream(serdes, mappedStream, namedSupplier)
    }

    fun <U : Any> map(serde: StreamSerde<U>, mapper: (key: K, value: V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues { key, value -> mapper(key, value) }
        return MappedStream(Serdes(serdes.key, serde), mappedStream, namedSupplier)
    }
    fun map(mapper: (key: K, value: V) -> V): MappedStream<K, V> {
        val mappedStream = stream.mapValues { key, value -> mapper(key, value) }
        return MappedStream(serdes, mappedStream, namedSupplier)
    }

    fun <U : Any> mapWithMetadata(serde: StreamSerde<U> = serdes.value as StreamSerde<U>, mapper: (value: V, metadata: ProcessorMetadata) -> U): MappedStream<K, U> {
        val mappedStream = stream.addProcessor(MetadataProcessor(namedSupplier())).mapValues { (kv, metadata) -> mapper(kv.value, metadata) }
        return MappedStream(Serdes(serdes.key, serde), mappedStream, namedSupplier)
    }
    fun mapWithMetadata(mapper: (value: V, metadata: ProcessorMetadata) -> V): MappedStream<K, V> {
        val mappedStream = stream.addProcessor(MetadataProcessor(namedSupplier())).mapValues { (kv, metadata) -> mapper(kv.value, metadata) }
        return MappedStream(serdes, mappedStream, namedSupplier)
    }

    fun <U> mapNotNull(serde: StreamSerde<U & Any> = serdes.value as StreamSerde<U & Any>, mapper: (key: K, value: V) -> U): MappedStream<K, U & Any> {
        val valuedStream = stream.mapValues { key, value -> mapper(key, value) }.filterNotNull()
        return MappedStream(Serdes(serdes.key, serde), valuedStream, namedSupplier)
    }
    fun mapNotNull(mapper: (key: K, value: V) -> V?): MappedStream<K, V> {
        val valuedStream = stream.mapValues { key, value -> mapper(key, value) }.filterNotNull()
        return MappedStream(serdes, valuedStream, namedSupplier)
    }

    fun flatMapPreserveType(mapper: (key: K, value: V) -> Iterable<V>): ConsumedStream<K, V> {
        val fusedStream = stream.flatMapValues { key, value -> mapper(key, value) }
        return ConsumedStream(serdes, fusedStream, namedSupplier)
    }

    fun flatMapKeyAndValuePreserveType(mapper: (key: K, value: V) -> Iterable<KeyValue<K, V>>): ConsumedStream<K, V> {
        val fusedStream = stream.flatMap { key, value -> mapper(key, value).map { it.toInternalKeyValue() } }
        return ConsumedStream(serdes, fusedStream, namedSupplier)
    }

    fun <U : Any> flatMap(serde: StreamSerde<U> = serdes.value as StreamSerde<U>, mapper: (key: K, value: V) -> Iterable<U>): MappedStream<K, U> {
        val fusedStream = stream.flatMapValues { key, value -> mapper(key, value) }
        return MappedStream(Serdes(serdes.key, serde), fusedStream, namedSupplier)
    }

    fun <U : Any> flatMapKeyAndValue(serde: StreamSerde<U> = serdes.value as StreamSerde<U>, mapper: (key: K, value: V) -> Iterable<KeyValue<K, U>>): MappedStream<K, U> {
        val fusedStream = stream.flatMap { key, value -> mapper(key, value).map { it.toInternalKeyValue() } }
        return MappedStream(Serdes(serdes.key, serde), fusedStream, namedSupplier)
    }

    fun <U : Any> mapKeyAndValue(serde: StreamSerde<U> = serdes.value as StreamSerde<U>, mapper: (key: K, value: V) -> KeyValue<K, U>): MappedStream<K, U> {
        val fusedStream = stream.map { key, value -> mapper(key, value).toInternalKeyValue() }
        return MappedStream(Serdes(serdes.key, serde), fusedStream, namedSupplier)
    }

    /**
     * Window will change when something exceeds the window frame or when something new comes in.
     * @param windowSize the size of the window
     * |      <- new record
     * ||     <- new record
     *  |     <- first record exceeded
     *  ||    <- new record
     */
    fun slidingWindow(windowSize: Duration): TimeWindowedStream<K, V> {
        /*
         * TODO: skal noen av vinduene ha gracePeriod?
         * Dvs hvor lenge skal streamen vente på at en melding har et timestamp som passer inn i vinduet.  timestamp enn "nå".
         * Dette vil ta noen out-of-order records som oppstår f.eks dersom klokkene til producerne er ulike
         */
        val sliding = SlidingWindows.ofTimeDifferenceWithNoGrace(windowSize.toJavaDuration())
        val groupSerde = Grouped.with(serdes.key, serdes.value)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(sliding)
        return TimeWindowedStream(serdes, windowedStream, namedSupplier)
    }

    /**
     * Window size and advance size will overlap some.
     * @param advanceSize must be less than [windowSize]
     *  |||||||||||||
     *             |||||||||||||
     *                        |||||||||||||
     */
    fun hoppingWindow(windowSize: Duration, advanceSize: Duration): TimeWindowedStream<K, V> {
        val window = TimeWindows
            .ofSizeWithNoGrace(windowSize.toJavaDuration())
            .advanceBy(advanceSize.toJavaDuration())

        val groupSerde = Grouped.with(serdes.key, serdes.value)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(window)
        return TimeWindowedStream(serdes, windowedStream, namedSupplier)
    }

    /**
     * Tumbling window is a hopping window, but where window size and advance size is equal.
     * This results in no overlaps or duplicates.
     *  |||||||||||||
     *               |||||||||||||
     *                            |||||||||||||
     */
    fun tumblingWindow(windowSize: Duration): TimeWindowedStream<K, V> {
        val window = TimeWindows.ofSizeWithNoGrace(windowSize.toJavaDuration())
        val groupSerde = Grouped.with(serdes.key, serdes.value)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(window)
        return TimeWindowedStream(serdes, windowedStream, namedSupplier)
    }

    /**
     * Creates a new window after [inactivityGap] duration.
     *  |||||||||
     *               ||||||||
     *                           |||||||||||||
     */
    fun sessionWindow(inactivityGap: Duration): SessionWindowedStream<K, V> {
        val window = SessionWindows.ofInactivityGapWithNoGrace(inactivityGap.toJavaDuration())
        val groupSerde = Grouped.with(serdes.key, serdes.value)
        val windowedStream: SessionWindowedKStream<K, V> = stream.groupByKey(groupSerde).windowedBy(window)
        return SessionWindowedStream(serdes, windowedStream, namedSupplier)
    }

    fun <R : Any> join(left: Topic<K, V>, right: KTable<K, R>): JoinedStream<K, V, R> {
        val joinedStream = stream.join(left, right, ::StreamsPair)
        val named = { "${left.name}-join-${right.table.sourceTopic.name}" }
        return JoinedStream(left.serdes, joinedStream, named)
    }

    fun <R : Any> leftJoin(left: Topic<K, V>, right: KTable<K, R>): JoinedStream<K, V, R?> {
        val named = { "${left.name}-left-join-${right.table.sourceTopic.name}" }
        val joinedStream = stream.leftJoin(left, right, ::StreamsPair)
        return JoinedStream(left.serdes, joinedStream, named)
    }

    fun branch(predicate: (V) -> Boolean, consumed: ConsumedStream<K, V>.() -> Unit): BranchedKStream<K, V> {
        val splittedStream = stream.split(Named.`as`("split-${namedSupplier()}"))
        return BranchedKStream(serdes, splittedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(log: Log.(value: V) -> Unit): ConsumedStream<K, V> {
        val loggedStream = stream.peek { _, value -> log.invoke(Log.secure, value) }
        return ConsumedStream(serdes, loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(key: K, value: V) -> Unit): ConsumedStream<K, V> {
        val loggedStream = stream.peek { key, value -> log.invoke(Log.secure, key, value) }
        return ConsumedStream(serdes, loggedStream, namedSupplier)
    }

    fun repartition(partitions: Int): ConsumedStream<K, V> {
        val repartition = Repartitioned
            .with(serdes.key, serdes.value)
            .withNumberOfPartitions(partitions)
            .withName(topic?.name ?: namedSupplier())
        return ConsumedStream(serdes, stream.repartition(repartition), namedSupplier)
    }

    fun <U : Any> processor(serde: StreamSerde<U>, processor: Processor<K, V, U>): MappedStream<K, U> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(Serdes(serdes.key, serde), processorStream, namedSupplier)
    }
    fun  processor(processor: Processor<K, V, V>): MappedStream<K, V> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(serdes, processorStream, namedSupplier)
    }

    fun <TABLE : Any, U : Any> processor(serde: StreamSerde<U>, processor: StateProcessor<K, TABLE, V, U>): MappedStream<K, U> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(Serdes(serdes.key, serde), processorStream, namedSupplier)
    }
    fun <TABLE : Any> processor(processor: StateProcessor<K, TABLE, V, V>): MappedStream<K, V> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(serdes, processorStream, namedSupplier)
    }

    fun forEach(mapper: (key: K, value: V) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach(mapper, named)
    }
}
