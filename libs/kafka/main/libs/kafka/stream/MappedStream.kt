package libs.kafka.stream

import libs.kafka.*
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.StateProcessor
import libs.kafka.processor.StateProcessor.Companion.addProcessor
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named

class MappedStream<S: Any, T : Any> internal constructor(
    private val topic: Topic<S>,
    private val stream: KStream<String, T>,
    private val namedSupplier: () -> String,
) {
    fun produce(topic: Topic<T>) {
        val named = "produced-${topic.name}-${namedSupplier()}"
        stream.produceWithLogging(topic, named)
    }

    fun <R : Any> map(mapper: (T) -> R): MappedStream<S, R> {
        val mappedStream = stream.mapValues { lr -> mapper(lr) }
        return MappedStream(topic, mappedStream, namedSupplier)
    }

    fun <R : Any> map(mapper: (key: String, value: T) -> R): MappedStream<S, R> {
        val mappedStream = stream.mapValues { key, value -> mapper(key, value) }
        return MappedStream(topic, mappedStream, namedSupplier)
    }

    fun <U : Any> leftJoinWith(ktable: KTable<U>, serde: () -> StreamSerde<T>): JoinedStream<S, T, U?> {
        val joinedStream = stream.leftJoin(topic, serde(), ktable, ::StreamsPair)
        val named = { "${topic.name}-left-join-${ktable.table.sourceTopic.name}" }
        return JoinedStream(topic, joinedStream, named)
    }

    fun <R : Any> flatMap(mapper: (value: T) -> Iterable<R>): MappedStream<S, R> {
        val flattenedStream = stream.flatMapValues { _, value -> mapper(value) }
        return MappedStream(topic, flattenedStream, namedSupplier)
    }

    fun rekey(mapper: (value: T) -> String): MappedStream<S, T> {
        val rekeyedStream = stream.selectKey { _, value -> mapper(value) }
        return MappedStream(topic, rekeyedStream, namedSupplier)
    }

    fun filter(lambda: (T) -> Boolean): MappedStream<S, T> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return MappedStream(topic, filteredStream, namedSupplier)
    }

    fun branch(predicate: (T) -> Boolean, consumed: MappedStream<S, T>.() -> Unit): BranchedMappedKStream<S, T> {
        val named = Named.`as`("split-${namedSupplier()}")
        val branchedStream = stream.split(named)
        return BranchedMappedKStream(topic, branchedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(logger: Log.(value: T) -> Unit): MappedStream<S, T> {
        val loggedStream = stream.peek { _, value -> logger.invoke(Log.secure, value) }
        return MappedStream(topic, loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(key: String, value: T) -> Unit): MappedStream<S, T> {
        val loggedStream = stream.peek { key, value -> log.invoke(Log.secure, key, value) }
        return MappedStream(topic, loggedStream, namedSupplier)
    }

    fun <U : Any> processor(processor: Processor<T, U>): MappedStream<S, U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(topic, processedStream, namedSupplier)
    }

    fun <TABLE : Any, U : Any> processor(processor: StateProcessor<TABLE, T, U>): MappedStream<S, U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(topic, processedStream, namedSupplier)
    }

    fun forEach(mapper: (key: String, value: T) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach(mapper, named)
    }

    fun forEach(mapper: (value: T) -> Unit) {
        val named = Named.`as`("foreach-${namedSupplier()}")
        stream.foreach { _, value -> mapper(value) }
    }
}
