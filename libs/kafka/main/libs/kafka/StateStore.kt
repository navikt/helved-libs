package libs.kafka

import org.apache.kafka.streams.state.ReadOnlyKeyValueStore

typealias StateStoreName = String

class StateStore<T>(private val internalStateStore: ReadOnlyKeyValueStore<String, T>) {
    fun getOrNull(key: String): T? = internalStateStore.get(key)

    fun approximateNumEntries() = internalStateStore.approximateNumEntries()
}

