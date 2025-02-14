package libs.kafka.stream

import libs.kafka.KeyValue
import libs.kafka.Log
import libs.kafka.StreamsPair
import libs.kafka.Topic
import libs.kafka.filterNotNull
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named

/**
 * R kan defineres som nullable.
 * Dette er opp til kallstedet for opprettelsen av JoinedKStream.
 * */
class JoinedStream<S: Any, L : Any, R> internal constructor(
    private val topic: Topic<S>,
    private val stream: KStream<String, StreamsPair<L, R>>,
    private val namedSupplier: () -> String

) {
    fun <LR : Any> map(mapper: (L, R) -> LR): MappedStream<S, LR> {
        val mappedStream = stream.mapValues { (left, right) -> mapper(left, right) }
        return MappedStream(topic, mappedStream, namedSupplier)
    }

    fun <LR : Any> map(mapper: (key: String, L, R) -> LR): MappedStream<S, LR> {
        val mappedStream = stream.mapValues { key, (left, right) -> mapper(key, left, right) }
        return MappedStream(topic, mappedStream, namedSupplier)
    }

    fun rekey(mapper: (L, R) -> String): JoinedStream<S, L, R> {
        val rekeyedStream = stream.selectKey { _, (left, right) -> mapper(left, right) }
        return JoinedStream(topic, rekeyedStream, namedSupplier)
    }

    fun <LR : Any> mapKeyValue(mapper: (String, L, R) -> KeyValue<String, LR>): MappedStream<S, LR> {
        val mappedStream = stream.map { key, (left, right) -> mapper(key, left, right).toInternalKeyValue() }
        return MappedStream(topic, mappedStream, namedSupplier)
    }

    fun <LR : Any> flatMapKeyValue(mapper: (String, L, R) -> Iterable<KeyValue<String, LR>>): MappedStream<S, LR> {
        val stream = stream.flatMap { key, (left, right) -> mapper(key, left, right).map { it.toInternalKeyValue() } }
        return MappedStream(topic, stream, namedSupplier)
    }

    fun <LR> mapNotNull(mapper: (L, R) -> LR): MappedStream<S, LR & Any> {
        val mappedStream = stream.mapValues { _, (left, right) -> mapper(left, right) }.filterNotNull()
        return MappedStream(topic, mappedStream, namedSupplier)
    }

    fun filter(lambda: (StreamsPair<L, R>) -> Boolean): JoinedStream<S, L, R> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return JoinedStream(topic, filteredStream, namedSupplier)
    }

    fun branch(
        predicate: (StreamsPair<L, R>) -> Boolean,
        consumed: MappedStream<S, StreamsPair<L, R>>.() -> Unit,
    ): BranchedMappedKStream<S, StreamsPair<L, R>> {
        val branchedStream = stream.split(Named.`as`("split-${namedSupplier()}"))
        return BranchedMappedKStream(topic, branchedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(log: Log.(left: L, right: R) -> Unit): JoinedStream<S, L, R> {
        val loggedStream = stream.peek { _, (left, right) -> log.invoke(Log.secure, left, right) }
        return JoinedStream(topic, loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(key: String, left: L, right: R) -> Unit): JoinedStream<S, L, R> {
        val loggedStream = stream.peek { key, (left, right) -> log.invoke(Log.secure, key, left, right) }
        return JoinedStream(topic, loggedStream, namedSupplier)
    }

    fun <LR : Any> processor(processor: Processor<StreamsPair<L, R>, LR>): MappedStream<S, LR> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(topic, processorStream, namedSupplier)
    }

    fun processor(processor: Processor<StreamsPair<L, R>, StreamsPair<L, R>>): JoinedStream<S, L, R> {
        val processorStream = stream.addProcessor(processor)
        return JoinedStream(topic, processorStream, namedSupplier)
    }
}
