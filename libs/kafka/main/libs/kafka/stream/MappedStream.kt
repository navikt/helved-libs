package libs.kafka.stream

import libs.kafka.*
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.StateProcessor
import libs.kafka.processor.StateProcessor.Companion.addProcessor
import libs.kafka.processor.LogProduceStateStoreProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named

class MappedStream<K: Any, V : Any> internal constructor(
    private val serdes: Serdes<K, V>,
    private val stream: KStream<K, V>,
    private val namedSupplier: () -> String,
) {
    fun produce(topic: Topic<K, V>) {
        val named = "produced-${topic.name}-${namedSupplier()}"
        stream.produceWithLogging(topic, named)
    }

    fun materialize(stateStoreName: StateStoreName = "${namedSupplier()}-to-table"): StateStoreName {
        val loggedStream = stream.addProcessor(LogProduceStateStoreProcessor(stateStoreName))
        loggedStream.toTable(materialized(stateStoreName, serdes))
        return stateStoreName
    }

    fun <U : Any> map(serde: StreamSerde<U>, mapper: (V) -> U): MappedStream<K, U> {
        val mappedStream = stream.mapValues { lr -> mapper(lr) }
        return MappedStream(Serdes(serdes.key, serde), mappedStream, namedSupplier)
    }
    fun map(mapper: (V) -> V): MappedStream<K, V> {
        val mappedStream = stream.mapValues { lr -> mapper(lr) }
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

    fun <U : Any> leftJoin(right: KTable<K, U>): JoinedStream<K, V, U?> {
        val named = "${namedSupplier()}-left-join-${right.table.sourceTopic.name}"
        val joinedStream = stream.leftJoin(named, serdes, right, ::StreamsPair)
        return JoinedStream(serdes, joinedStream, { named })
    }

    fun <U : Any> flatMap(serde: StreamSerde<U>, mapper: (value: V) -> Iterable<U>): MappedStream<K, U> {
        val flattenedStream = stream.flatMapValues { _, value -> mapper(value) }
        return MappedStream(Serdes(serdes.key, serde), flattenedStream, namedSupplier)
    }
    fun flatMap(mapper: (value: V) -> Iterable<V>): MappedStream<K, V> {
        val flattenedStream = stream.flatMapValues { _, value -> mapper(value) }
        return MappedStream(serdes, flattenedStream, namedSupplier)
    }

    fun <K2: Any> rekey(serde: StreamSerde<K2>, mapper: (value: V) -> K2): MappedStream<K2, V> {
        val rekeyedStream = stream.selectKey { _, value -> mapper(value) }
        return MappedStream(Serdes(serde, serdes.value), rekeyedStream, namedSupplier)
    }
    fun rekey(mapper: (value: V) -> K): MappedStream<K, V> {
        val rekeyedStream = stream.selectKey { _, value -> mapper(value) }
        return MappedStream(serdes, rekeyedStream, namedSupplier)
    }

    fun filter(lambda: (V) -> Boolean): MappedStream<K, V> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return MappedStream(serdes, filteredStream, namedSupplier)
    }

    fun branch(predicate: (V) -> Boolean, consumed: MappedStream<K, V>.() -> Unit): BranchedMappedKStream<K, V> {
        val named = Named.`as`("split-${namedSupplier()}")
        val branchedStream = stream.split(named)
        return BranchedMappedKStream(serdes, branchedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(logger: Log.(value: V) -> Unit): MappedStream<K, V> {
        val loggedStream = stream.peek { _, value -> logger.invoke(Log.secure, value) }
        return MappedStream(serdes, loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(key: K, value: V) -> Unit): MappedStream<K, V> {
        val loggedStream = stream.peek { key, value -> log.invoke(Log.secure, key, value) }
        return MappedStream(serdes, loggedStream, namedSupplier)
    }

    fun <U : Any> processor(serde: StreamSerde<U>, processor: Processor<K, V, U>): MappedStream<K, U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(Serdes(serdes.key, serde), processedStream, namedSupplier)
    }

    fun <TABLE : Any, U : Any> stateProcessor(serde: StreamSerde<U>, processor: StateProcessor<K, TABLE, V, U>): MappedStream<K, U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(Serdes(serdes.key, serde), processedStream, namedSupplier)
    }
    fun <TABLE : Any> stateProcessor(processor: StateProcessor<K, TABLE, V, V>): MappedStream<K, V> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(serdes, processedStream, namedSupplier)
    }

    fun forEach(mapper: (key: K, value: V) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach(mapper, named)
    }

    fun forEach(mapper: (value: V) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach { _, value -> mapper(value) }
    }
}
