package libs.kafka.processor

import libs.kafka.KeyValue
import libs.kafka.Topic
import libs.kafka.Table
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("secureLog")

internal class LogConsumeTopicProcessor<T>(
    private val topic: Topic<T & Any>,
    namedSuffix: String = "",
) : Processor<T, T>("log-consume-${topic.name}$namedSuffix") {

    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<String, T>): T {
        log.trace(
            "Konsumerer Topic ${metadata.topic}",
            kv("key", keyValue.key),
            kv("topic", metadata.topic),
            kv("partition", metadata.partition),
            kv("offset", metadata.offset),
            if (topic.logValues) kv("value", keyValue.value) else null,
        )

        return keyValue.value
    }
}

internal class LogProduceTableProcessor<T>(
    private val table: Table<T & Any>,
) : Processor<T, T>("log-produced-${table.sourceTopicName}") {

    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<String, T>): T {
        when (keyValue.value) {
            null -> log.trace(
                "Produserer tombstone til KTable ${table.sourceTopicName}",
                kv("key", keyValue.key),
                kv("table", table.sourceTopicName),
                kv("store", table.stateStoreName),
                kv("partition", metadata.partition),
            )

            else -> log.trace(
                "Produserer til KTable ${table.sourceTopicName}",
                kv("key", keyValue.key),
                kv("table", table.sourceTopicName),
                kv("store", table.stateStoreName),
                kv("partition", metadata.partition),
                if (table.sourceTopic.logValues) kv("value", keyValue.value) else null,
            )
        }
        return keyValue.value
    }
}

internal class LogProduceTopicProcessor<T> internal constructor(
    named: String,
    private val topic: Topic<T & Any>,
) : Processor<T, T>(named) {

    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<String, T>): T {
        log.trace(
            "Produserer til Topic ${topic.name}",
            kv("key", keyValue.key),
            kv("source_topic", metadata.topic),
            kv("topic", topic.name),
            kv("partition", metadata.partition),
            if (topic.logValues) kv("value", keyValue.value) else null,
        )
        return keyValue.value
    }
}

