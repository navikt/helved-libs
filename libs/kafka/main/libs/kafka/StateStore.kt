package libs.kafka

import org.apache.kafka.streams.state.*

typealias StateStoreName = String

data class Store<K: Any, V: Any>(
    val name: StateStoreName,
    val serde: Serdes<K, V>
)

class StateStore<K: Any, V>(private val internalStateStore: ReadOnlyKeyValueStore<K, V>) {
    fun getOrNull(key: K): V? = internalStateStore.get(key)
}

