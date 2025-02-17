package libs.kafka

import libs.utils.logger
import net.logstash.logback.argument.StructuredArgument
import org.apache.kafka.streams.KeyValue

data class StreamsPair<L, R>(
    val left: L,
    val right: R,
) {
    companion object {
        inline fun <reified L, reified R> of(l: L, r: R): StreamsPair<L, R> {
            return StreamsPair(l, r)
        }
    }
}

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
