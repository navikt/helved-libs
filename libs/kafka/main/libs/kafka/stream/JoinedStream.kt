package libs.kafka.stream

import libs.kafka.KeyValue
import libs.kafka.Log
import libs.kafka.StreamsPair
import libs.kafka.filterNotNull
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named

/**
 * R kan defineres som nullable.
 * Dette er opp til kallstedet for opprettelsen av JoinedKStream.
 * */
class JoinedStream<L : Any, R> internal constructor(
    private val sourceTopicName: String,
    private val stream: KStream<String, StreamsPair<L, R>>,
    private val namedSupplier: () -> String

) {
    fun <LR : Any> map(mapper: (L, R) -> LR): MappedStream<LR> {
        val mappedStream = stream.mapValues { (left, right) -> mapper(left, right) }
        return MappedStream(sourceTopicName, mappedStream, namedSupplier)
    }

    fun <LR : Any> map(mapper: (key: String, L, R) -> LR): MappedStream<LR> {
        val mappedStream = stream.mapValues { key, (left, right) -> mapper(key, left, right) }
        return MappedStream(sourceTopicName, mappedStream, namedSupplier)
    }

    fun <LR : Any> mapKeyValue(mapper: (String, L, R) -> KeyValue<String, LR>): MappedStream<LR> {
        val mappedStream = stream.map { key, (left, right) -> mapper(key, left, right).toInternalKeyValue() }
        return MappedStream(sourceTopicName, mappedStream, namedSupplier)
    }

    fun <LR : Any> flatMapKeyValue(mapper: (String, L, R) -> Iterable<KeyValue<String, LR>>): MappedStream<LR> {
        val stream = stream.flatMap { key, (left, right) -> mapper(key, left, right).map { it.toInternalKeyValue() } }
        return MappedStream(sourceTopicName, stream, namedSupplier)
    }

    fun <LR> mapNotNull(mapper: (L, R) -> LR): MappedStream<LR & Any> {
        val mappedStream = stream.mapValues { _, (left, right) -> mapper(left, right) }.filterNotNull()
        return MappedStream(sourceTopicName, mappedStream, namedSupplier)
    }

    fun filter(lambda: (StreamsPair<L, R>) -> Boolean): JoinedStream<L, R> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return JoinedStream(sourceTopicName, filteredStream, namedSupplier)
    }

    fun branch(
        predicate: (StreamsPair<L, R>) -> Boolean,
        consumed: MappedStream<StreamsPair<L, R>>.() -> Unit,
    ): BranchedMappedKStream<StreamsPair<L, R>> {
        val branchedStream = stream.split(Named.`as`("split-${namedSupplier()}"))
        return BranchedMappedKStream(sourceTopicName, branchedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(log: Log.(left: L, right: R) -> Unit): JoinedStream<L, R> {
        val loggedStream = stream.peek { _, (left, right) -> log.invoke(Log.secure, left, right) }
        return JoinedStream(sourceTopicName, loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(key: String, left: L, right: R) -> Unit): JoinedStream<L, R> {
        val loggedStream = stream.peek { key, (left, right) -> log.invoke(Log.secure, key, left, right) }
        return JoinedStream(sourceTopicName, loggedStream, namedSupplier)
    }

    fun <LR : Any> processor(processor: Processor<StreamsPair<L, R>, LR>): MappedStream<LR> {
        val processorStream = stream.addProcessor(processor)
        return MappedStream(sourceTopicName, processorStream, namedSupplier)
    }

    fun processor(processor: Processor<StreamsPair<L, R>, StreamsPair<L, R>>): JoinedStream<L, R> {
        val processorStream = stream.addProcessor(processor)
        return JoinedStream(sourceTopicName, processorStream, namedSupplier)
    }
}
