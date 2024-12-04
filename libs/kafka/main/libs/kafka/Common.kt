package libs.kafka

import org.apache.kafka.streams.KeyValue
import libs.utils.*
import net.logstash.logback.argument.StructuredArgument

data class StreamsPair<L, R>(
    val left: L,
    val right: R,
)

data class KeyValue<K, V>(
    val key: K,
    val value: V,
) {
    internal fun toInternalKeyValue(): KeyValue<K, V> {
        return KeyValue(key, value)
    }
}

class Log(name: String) {
    private val logger = logger(name)

    fun trace(msg: String, vararg labels: StructuredArgument) = logger.trace(msg, *labels)
    fun debug(msg: String, vararg labels: StructuredArgument) = logger.debug(msg, *labels)
    fun info(msg: String, vararg labels: StructuredArgument) = logger.info(msg, *labels)
    fun warn(msg: String, vararg labels: StructuredArgument) = logger.warn(msg, *labels)
    fun error(msg: String, vararg labels: StructuredArgument) = logger.error(msg, *labels)

    companion object {
        val secure: Log by lazy { Log("secureLog") }
    }
}
