package libs.kafka

import org.apache.kafka.streams.state.*

typealias StateStoreName = String

class StateStore<K: Any, V>(private val internalStateStore: ReadOnlyKeyValueStore<K, ValueAndTimestamp<V>>) {
    fun getOrNull(key: K): V? = internalStateStore.get(key)?.value()
}

