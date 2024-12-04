package libs.kafka

import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp

class StateStore<T>(private val internalStateStore: ReadOnlyKeyValueStore<String, ValueAndTimestamp<T>>) {
    infix operator fun get(key: String): T? = internalStateStore[key]?.value()

    fun forEach(loop: (key: String, value: T) -> Unit) =
        internalStateStore.all().use { iterator ->
            iterator.asSequence().forEach { record ->
                loop(record.key, record.value.value())
            }
        }

    fun forEachTimestamped(loop: (key: String, value: T, timestamp: Long) -> Unit) =
        internalStateStore.all().use { iterator ->
            iterator.asSequence().forEach { record ->
                loop(record.key, record.value.value(), record.value.timestamp())
            }
        }

    fun approximateNumEntries() = internalStateStore.approximateNumEntries()
}

