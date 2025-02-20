package libs.kafka

import org.apache.kafka.streams.state.ReadOnlyKeyValueStore

typealias StateStoreName = String

class StateStore<K: Any, V>(private val internalStateStore: ReadOnlyKeyValueStore<K, V>) {
    fun getOrNull(key: K): V? = internalStateStore.get(key)
}

