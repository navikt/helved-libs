package libs.kafka.stream

import libs.kafka.Log
import libs.kafka.Topic
import libs.kafka.processor.Processor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.StateProcessor
import libs.kafka.processor.StateProcessor.Companion.addProcessor
import libs.kafka.produceWithLogging
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named

class MappedStream<T : Any> internal constructor(
    private val sourceTopicName: String,
    private val stream: KStream<String, T>,
    private val namedSupplier: () -> String,
) {
    fun produce(topic: Topic<T>) {
        val named = "produced-${topic.name}-${namedSupplier()}"
        stream.produceWithLogging(topic, named)
    }

    fun <R : Any> map(mapper: (T) -> R): MappedStream<R> {
        val mappedStream = stream.mapValues { lr -> mapper(lr) }
        return MappedStream(sourceTopicName, mappedStream, namedSupplier)
    }

    fun <R : Any> map(mapper: (key: String, value: T) -> R): MappedStream<R> {
        val mappedStream = stream.mapValues { key, value -> mapper(key, value) }
        return MappedStream(sourceTopicName, mappedStream, namedSupplier)
    }

    fun <R : Any> flatMap(mapper: (value: T) -> Iterable<R>): MappedStream<R> {
        val flattenedStream = stream.flatMapValues { _, value -> mapper(value) }
        return MappedStream(sourceTopicName, flattenedStream, namedSupplier)
    }

    fun rekey(mapper: (value: T) -> String): MappedStream<T> {
        val rekeyedStream = stream.selectKey { _, value -> mapper(value) }
        return MappedStream(sourceTopicName, rekeyedStream, namedSupplier)
    }

    fun filter(lambda: (T) -> Boolean): MappedStream<T> {
        val filteredStream = stream.filter { _, value -> lambda(value) }
        return MappedStream(sourceTopicName, filteredStream, namedSupplier)
    }

    fun branch(predicate: (T) -> Boolean, consumed: MappedStream<T>.() -> Unit): BranchedMappedKStream<T> {
        val named = Named.`as`("split-${namedSupplier()}")
        val branchedStream = stream.split(named)
        return BranchedMappedKStream(sourceTopicName, branchedStream, namedSupplier).branch(predicate, consumed)
    }

    fun secureLog(logger: Log.(value: T) -> Unit): MappedStream<T> {
        val loggedStream = stream.peek { _, value -> logger.invoke(Log.secure, value) }
        return MappedStream(sourceTopicName, loggedStream, namedSupplier)
    }

    fun secureLogWithKey(log: Log.(key: String, value: T) -> Unit): MappedStream<T> {
        val loggedStream = stream.peek { key, value -> log.invoke(Log.secure, key, value) }
        return MappedStream(sourceTopicName, loggedStream, namedSupplier)
    }

    fun <U : Any> processor(processor: Processor<T, U>): MappedStream<U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(sourceTopicName, processedStream, namedSupplier)
    }

    fun <TABLE : Any, U : Any> processor(processor: StateProcessor<TABLE, T, U>): MappedStream<U> {
        val processedStream = stream.addProcessor(processor)
        return MappedStream(sourceTopicName, processedStream, namedSupplier)
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
