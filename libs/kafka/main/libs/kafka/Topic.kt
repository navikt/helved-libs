package libs.kafka

import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Joined
import org.apache.kafka.streams.kstream.Produced

open class Topic<T : Any>(
    val name: String,
    val valueSerde: StreamSerde<T>,
    val keySerde: StreamSerde<String> = StringSerde,
    val logValues: Boolean = false,
) {
    internal fun consumed(named: String): Consumed<String, T> {
        return Consumed.with(keySerde, valueSerde).withName(named)
    }

    internal open fun produced(named: String): Produced<String, T> {
        return Produced.with(keySerde, valueSerde).withName(named)
    }

    internal infix fun <U : Any> join(right: KTable<U>): Joined<String, T, U> {
        return Joined.with(
            keySerde,
            valueSerde,
            right.table.sourceTopic.valueSerde,
            "$name-join-${right.table.sourceTopic.name}",
        )
    }

    internal infix fun <U : Any> leftJoin(right: KTable<U>): Joined<String, T, U?> {
        return Joined.with(
            keySerde,
            valueSerde,
            right.table.sourceTopic.valueSerde,
            "$name-left-join-${right.table.sourceTopic.name}",
        )
    }
}
