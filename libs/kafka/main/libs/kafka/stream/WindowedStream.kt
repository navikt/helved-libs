package libs.kafka.stream

import libs.kafka.Topic
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.SessionWindowedKStream
import org.apache.kafka.streams.kstream.TimeWindowedKStream
import org.apache.kafka.streams.state.SessionStore
import org.apache.kafka.streams.state.WindowStore

class TimeWindowedStream<T : Any> internal constructor(
    private val topic: Topic<T>,
    private val stream: TimeWindowedKStream<String, T>,
    private val namedSupplier: () -> String,
) {
    fun reduce(acc: (T, T) -> T): ConsumedStream<T> {
        val named = "${namedSupplier()}-reduced"

        val materialized = Materialized.`as`<String, T, WindowStore<Bytes, ByteArray>>("$named-store")
            .withKeySerde(topic.keySerde)
            .withValueSerde(topic.valueSerde)

        val reducedStream = stream
            .reduce(acc, Named.`as`("${namedSupplier()}-operation-reduced"), materialized)
            .toStream()
            .selectKey { key, _ -> key.key() }

        return ConsumedStream(topic, reducedStream) { named }
    }
}

class SessionWindowedStream<T : Any> internal constructor(
    private val topic: Topic<T>,
    private val stream: SessionWindowedKStream<String, T>,
    private val namedSupplier: () -> String,
) {
    fun reduce(acc: (T, T) -> T): ConsumedStream<T> {
        val named = "${namedSupplier()}-reduced"

        val materialized = Materialized.`as`<String, T, SessionStore<Bytes, ByteArray>>("$named-store")
            .withKeySerde(topic.keySerde)
            .withValueSerde(topic.valueSerde)

        val reducedStream = stream
            .reduce(acc, Named.`as`("${namedSupplier()}-operation-reduced"), materialized)
            .toStream()
            .selectKey { key, _ -> key.key() }

        return ConsumedStream(topic, reducedStream) { named }
    }
}
