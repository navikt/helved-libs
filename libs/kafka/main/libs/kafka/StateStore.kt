package libs.kafka

import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore

typealias StateStoreName = String

data class Store<K : Any, V : Any>(
    val name: StateStoreName,
    val serde: Serdes<K, V>
)

class KStore<K : Any, V : Any>(
    val store: Store<K, V>,
    val internalKTable: org.apache.kafka.streams.kstream.KTable<K, V>,
) {
    fun <U : Any> join(table: KTable<K, U>): ConsumedStream<K, StreamsPair<V, U?>> =
        ConsumedStream(internalKTable.join(table.internalKTable, ::StreamsPair).toStream()) {
            "consume-${store.name}-join-${table.table.stateStoreName}"
        }
}

class StateStore<K : Any, V>(private val internalStateStore: ReadOnlyKeyValueStore<K, V>) {
    fun getOrNull(key: K): V? = internalStateStore.get(key)
}

