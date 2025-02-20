package libs.kafka.stream

import libs.kafka.*
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named

/**
 * R kan defineres som nullable.
 * Dette er opp til kallstedet for opprettelsen av JoinedKStream.
 * */
class JoinedStream<K: Any, L : Any, R> internal constructor(
    private val serdes: Serdes<K, L>,
    private val stream: KStream<K, StreamsPair<L, R>>,
    private val namedSupplier: () -> String

) {
    fun <LR : Any> map(serde: StreamSerde<LR>, mapper: (L, R) -> LR): MappedStream<K, LR> {
        val mappedStream = stream.mapValues { (left, right) -> mapper(left, right) }
        return MappedStream(Serdes(serdes.key, serde), mappedStream, namedSupplier)
    }

    fun <LR : Any> map(serde: StreamSerde<LR>, mapper: (key: K, L, R) -> LR): MappedStream<K, LR> {
        val mappedStream = stream.mapValues { key, (left, right) -> mapper(key, left, right) }
        return MappedStream(Serdes(serdes.key, serde), mappedStream, namedSupplier)
    }

    // todo: keySerde: StreamSerde<K2>
    fun <K2: Any> rekey(serde: StreamSerde<K2>, mapper: (L, R) -> K2): JoinedStream<K2, L, R> {
        val rekeyedStream = stream.selectKey { _, (left, right) -> mapper(left, right) }
        return JoinedStream(Serdes(serde, serdes.value), rekeyedStream, namedSupplier)
    }

    // todo: keySerde: StreamSerde<K2>
    fun <K2: Any, LR : Any> mapKeyValue(serdes: Serdes<K2, LR>, mapper: (K, L, R) -> KeyValue<K2, LR>): MappedStream<K2, LR> {
        val mappedStream = stream.map { key, (left, right) -> mapper(key, left, right).toInternalKeyValue() }
        return MappedStream(serdes, mappedStream, namedSupplier)
    }

    // todo: keySerde: StreamSerde<K2>
    fun <K2: Any, LR : Any> flatMapKeyValue(serdes: Serdes<K2, LR>, mapper: (K, L, R) -> Iterable<KeyValue<K2, LR>>): MappedStream<K2, LR> {
        val stream = stream.flatMap { key, (left, right) -> mapper(key, left, right).map { it.toInternalKeyValue() } }
        return MappedStream(serdes, stream, namedSupplier)
    }

    fun <LR> mapNotNull(serde: StreamSerde<LR & Any>, mapper: (L, R) -> LR): MappedStream<K, LR & Any> {
        val mappedStream = stream.mapValues { _, (left, right) -> mapper(left, right) }.filterNotNull()
        return MappedStream(Serdes(serdes.key, serde), mappedStream, namedSupplier)
    }

    fun filter(lambda: (StreamsPair<L, R>) -> Boolean): JoinedStream<K, L, R> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return JoinedStream(serdes, filteredStream, namedSupplier)
    }

    fun branch(
        serde: StreamSerde<StreamsPair<L, R>>,
        predicate: (StreamsPair<L, R>) -> Boolean,
        consumed: MappedStream<K, StreamsPair<L, R>>.() -> Unit,
    ): BranchedMappedKStream<K, StreamsPair<L, R>> {
        val branchedStream = stream.split(Named.`as`("split-${namedSupplier()}"))
        return BranchedMappedKStream(Serdes(serdes.key, serde), branchedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(log: Log.(left: L, right: R) -> Unit): JoinedStream<K, L, R> {
        val loggedStream = stream.peek { _, (left, right) -> log.invoke(Log.secure, left, right) }
        return JoinedStream(serdes, loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(key: K, left: L, right: R) -> Unit): JoinedStream<K, L, R> {
        val loggedStream = stream.peek { key, (left, right) -> log.invoke(Log.secure, key, left, right) }
        return JoinedStream(serdes, loggedStream, namedSupplier)
    }

    fun <LR : Any> processor(serde: StreamSerde<LR>, processor: Processor<K, StreamsPair<L, R>, LR>): MappedStream<K, LR> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(Serdes(serdes.key, serde), processorStream, namedSupplier)
    }

    fun processor(processor: Processor<K, StreamsPair<L, R>, StreamsPair<L, R>>): JoinedStream<K, L, R> {
        val processorStream = stream.addProcessor(processor)
        return JoinedStream(serdes, processorStream, namedSupplier)
    }
}
