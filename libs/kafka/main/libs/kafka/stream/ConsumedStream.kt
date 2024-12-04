package libs.kafka.stream

import libs.kafka.*
import libs.kafka.KTable
import libs.kafka.filterNotNull
import libs.kafka.processor.MetadataProcessor
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.ProcessorMetadata
import libs.kafka.processor.StateProcessor.Companion.addProcessor
import libs.kafka.processor.StateProcessor
import libs.kafka.produceWithLogging
import org.apache.kafka.streams.kstream.*
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class ConsumedStream<T : Any> internal constructor(
    private val topic: Topic<T>,
    private val stream: KStream<String, T>,
    private val namedSupplier: () -> String
) {
    fun produce(destination: Topic<T>) {
        val named = "produced-${destination.name}-${namedSupplier()}"
        stream.produceWithLogging(destination, named)
    }

    fun rekey(selectKeyFromValue: (T) -> String): ConsumedStream<T> {
        val rekeyedStream = stream.selectKey { _, value -> selectKeyFromValue(value) }
        return ConsumedStream(topic, rekeyedStream, namedSupplier)
    }

    fun filter(lambda: (T) -> Boolean): ConsumedStream<T> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return ConsumedStream(topic, filteredStream, namedSupplier)
    }

    fun filterKey(lambda: (String) -> Boolean): ConsumedStream<T> {
        val filteredStream = stream.filter { key, _ -> lambda(key) }
        return ConsumedStream(topic, filteredStream, namedSupplier)
    }

    fun <R : Any> map(mapper: (value: T) -> R): MappedStream<R> {
        val mappedStream = stream.mapValues { value -> mapper(value) }
        return MappedStream(topic.name, mappedStream, namedSupplier)
    }

    fun <R : Any> map(mapper: (key: String, value: T) -> R): MappedStream<R> {
        val mappedStream = stream.mapValues { key, value -> mapper(key, value) }
        return MappedStream(topic.name, mappedStream, namedSupplier)
    }

    fun <R : Any> mapWithMetadata(mapper: (value: T, metadata: ProcessorMetadata) -> R): MappedStream<R> {
        val mappedStream = stream
            .addProcessor(MetadataProcessor(topic))
            .mapValues { (kv, metadata) -> mapper(kv.value, metadata) }
        return MappedStream(topic.name, mappedStream, namedSupplier)
    }

    fun <R> mapNotNull(mapper: (key: String, value: T) -> R): MappedStream<R & Any> {
        val valuedStream = stream.mapValues { key, value -> mapper(key, value) }.filterNotNull()
        return MappedStream(topic.name, valuedStream, namedSupplier)
    }

    fun flatMapPreserveType(mapper: (key: String, value: T) -> Iterable<T>): ConsumedStream<T> {
        val fusedStream = stream.flatMapValues { key, value -> mapper(key, value) }
        return ConsumedStream(topic, fusedStream, namedSupplier)
    }

    fun flatMapKeyAndValuePreserveType(mapper: (key: String, value: T) -> Iterable<KeyValue<String, T>>): ConsumedStream<T> {
        val fusedStream = stream.flatMap { key, value -> mapper(key, value).map { it.toInternalKeyValue() } }
        return ConsumedStream(topic, fusedStream, namedSupplier)
    }

    fun <R : Any> flatMap(mapper: (key: String, value: T) -> Iterable<R>): MappedStream<R> {
        val fusedStream = stream.flatMapValues { key, value -> mapper(key, value) }
        return MappedStream(topic.name, fusedStream, namedSupplier)
    }

    fun <R : Any> flatMapKeyAndValue(mapper: (key: String, value: T) -> Iterable<KeyValue<String, R>>): MappedStream<R> {
        val fusedStream = stream.flatMap { key, value -> mapper(key, value).map { it.toInternalKeyValue() } }
        return MappedStream(topic.name, fusedStream, namedSupplier)
    }

    fun <R : Any> mapKeyAndValue(mapper: (key: String, value: T) -> KeyValue<String, R>): MappedStream<R> {
        val fusedStream = stream.map { key, value -> mapper(key, value).toInternalKeyValue() }
        return MappedStream(topic.name, fusedStream, namedSupplier)
    }

    /**
     * Window will change when something exceeds the window frame or when something new comes in.
     * @param windowSize the size of the window
     * |      <- new record
     * ||     <- new record
     *  |     <- first record exceeded
     *  ||    <- new record
     */
    fun slidingWindow(windowSize: Duration): TimeWindowedStream<T> {
        /*
         * TODO: skal noen av vinduene ha gracePeriod?
         * Dvs hvor lenge skal streamen vente på at en melding har et timestamp som passer inn i vinduet.  timestamp enn "nå".
         * Dette vil ta noen out-of-order records som oppstår f.eks dersom klokkene til producerne er ulike
         */
        val sliding = SlidingWindows.ofTimeDifferenceWithNoGrace(windowSize.toJavaDuration())
        val groupSerde = Grouped.with(topic.keySerde, topic.valueSerde)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(sliding)
        return TimeWindowedStream(topic, windowedStream, namedSupplier)
    }

    /**
     * Window size and advance size will overlap some.
     * @param advanceSize must be less than [windowSize]
     *  |||||||||||||
     *             |||||||||||||
     *                        |||||||||||||
     */
    fun hoppingWindow(windowSize: Duration, advanceSize: Duration): TimeWindowedStream<T> {
        val window = TimeWindows
            .ofSizeWithNoGrace(windowSize.toJavaDuration())
            .advanceBy(advanceSize.toJavaDuration())

        val groupSerde = Grouped.with(topic.keySerde, topic.valueSerde)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(window)
        return TimeWindowedStream(topic, windowedStream, namedSupplier)
    }

    /**
     * Tumbling window is a hopping window, but where window size and advance size is equal.
     * This results in no overlaps or duplicates.
     *  |||||||||||||
     *               |||||||||||||
     *                            |||||||||||||
     */
    fun tumblingWindow(windowSize: Duration): TimeWindowedStream<T> {
        val window = TimeWindows.ofSizeWithNoGrace(windowSize.toJavaDuration())
        val groupSerde = Grouped.with(topic.keySerde, topic.valueSerde)
        val windowedStream = stream.groupByKey(groupSerde).windowedBy(window)
        return TimeWindowedStream(topic, windowedStream, namedSupplier)
    }

    /**
     * Creates a new window after [inactivityGap] duration.
     *  |||||||||
     *               ||||||||
     *                           |||||||||||||
     */
    fun sessionWindow(inactivityGap: Duration): SessionWindowedStream<T> {
        val window = SessionWindows.ofInactivityGapWithNoGrace(inactivityGap.toJavaDuration())
        val groupSerde = Grouped.with(topic.keySerde, topic.valueSerde)
        val windowedStream: SessionWindowedKStream<String, T> = stream.groupByKey(groupSerde).windowedBy(window)
        return SessionWindowedStream(topic, windowedStream, namedSupplier)
    }

    fun <U : Any> joinWith(ktable: KTable<U>): JoinedStream<T, U> {
        val joinedStream = stream.join(topic, ktable, ::StreamsPair)
        val named = { "${topic.name}-join-${ktable.table.sourceTopic.name}" }
        return JoinedStream(topic.name, joinedStream, named)
    }

    fun <U : Any> leftJoinWith(ktable: KTable<U>): JoinedStream<T, U?> {
        val joinedStream = stream.leftJoin(topic, ktable, ::StreamsPair)
        val named = { "${topic.name}-left-join-${ktable.table.sourceTopic.name}" }
        return JoinedStream(topic.name, joinedStream, named)
    }

    fun branch(predicate: (T) -> Boolean, consumed: ConsumedStream<T>.() -> Unit): BranchedKStream<T> {
        val splittedStream = stream.split(Named.`as`("split-${namedSupplier()}"))
        return BranchedKStream(topic, splittedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(log: Log.(value: T) -> Unit): ConsumedStream<T> {
        val loggedStream = stream.peek { _, value -> log.invoke(Log.secure, value) }
        return ConsumedStream(topic, loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(key: String, value: T) -> Unit): ConsumedStream<T> {
        val loggedStream = stream.peek { key, value -> log.invoke(Log.secure, key, value) }
        return ConsumedStream(topic, loggedStream, namedSupplier)
    }

    fun repartition(partitions: Int): ConsumedStream<T> {
        val repartition = Repartitioned
            .with(topic.keySerde, topic.valueSerde)
            .withNumberOfPartitions(partitions)
            .withName(topic.name)
        return ConsumedStream(topic, stream.repartition(repartition), namedSupplier)
    }

    fun <U : Any> processor(processor: Processor<T, U>): MappedStream<U> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(topic.name, processorStream, namedSupplier)
    }

    fun processor(processor: Processor<T, T>): ConsumedStream<T> {
        val processorStream = stream.addProcessor(processor)
        return ConsumedStream(topic, processorStream, namedSupplier)
    }

    fun <TABLE : Any, U : Any> processor(processor: StateProcessor<TABLE, T, U>): MappedStream<U> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(topic.name, processorStream, namedSupplier)
    }

    fun forEach(mapper: (key: String, value: T) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach(mapper, named)
    }
}
