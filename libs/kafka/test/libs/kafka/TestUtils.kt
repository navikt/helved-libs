package libs.kafka

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import libs.kafka.processor.Processor
import libs.kafka.processor.ProcessorMetadata
import libs.kafka.processor.StateProcessor
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.state.TimestampedKeyValueStore
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

data class JsonDto(
    val id: Int,
    val data: String,
)

internal object Topics {
    val A = Topic("A", StringSerde, logValues = true)
    val B = Topic("B", StringSerde, logValues = true)
    val C = Topic("C", StringSerde, logValues = true)
    val D = Topic("D", StringSerde, logValues = true)
    val E = Topic("E", JsonSerde.jackson<JsonDto>(), logValues = true)
}

internal object Tables {
    val B = Table(Topics.B)
    val C = Table(Topics.C)
}

internal class Mock : Streams {
    private lateinit var internalTopology: org.apache.kafka.streams.Topology
    private lateinit var internalStreams: TopologyTestDriver

    companion object {
        internal fun withTopology(topology: Topology.() -> Unit): Mock =
            Mock().apply {
                connect(
                    topology = Topology().apply(topology),
                    config = StreamsConfig("", "", null),
                    registry = SimpleMeterRegistry()
                )
            }
    }

    override fun connect(topology: Topology, config: StreamsConfig, registry: MeterRegistry) {
        topology.registerInternalTopology(this)
        internalStreams = TopologyTestDriver(internalTopology)
    }

    internal fun <V : Any> inputTopic(topic: Topic<V>): TestInputTopic<String, V> =
        internalStreams.createInputTopic(topic.name, topic.keySerde.serializer(), topic.valueSerde.serializer())

    internal fun <V : Any> outputTopic(topic: Topic<V>): TestOutputTopic<String, V> =
        internalStreams.createOutputTopic(topic.name, topic.keySerde.deserializer(), topic.valueSerde.deserializer())


    internal fun advanceWallClockTime(duration: Duration) =
        internalStreams.advanceWallClockTime(duration.toJavaDuration())

    internal fun <T : Any> getTimestampedKeyValueStore(table: Table<T>) =
        internalStreams.getTimestampedKeyValueStore<String, T>(table.stateStoreName)

    override fun ready(): Boolean = true
    override fun live(): Boolean = true
    override fun visulize(): TopologyVisulizer = TopologyVisulizer(internalTopology)
    override fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology) {
        this.internalTopology = internalTopology
    }

    override fun <T : Any> getStore(table: Table<T>): StateStore<T> =
        StateStore(internalStreams.getKeyValueStore(table.stateStoreName))

    override fun close() = internalStreams.close()
}

internal val Int.ms get() = toDuration(DurationUnit.MILLISECONDS)

internal fun <V> TestInputTopic<String, V>.produce(key: String, value: V): TestInputTopic<String, V> =
    pipeInput(key, value).let { this }

internal fun <V> TestInputTopic<String, V>.produceTombstone(key: String): TestInputTopic<String, V> =
    pipeInput(key, null).let { this }

class CustomProcessorWithTable(table: KTable<String>) : StateProcessor<String, String, String>("custom-join", table) {
    override fun process(
        metadata: ProcessorMetadata,
        store: TimestampedKeyValueStore<String, String>,
        keyValue: KeyValue<String, String>
    ): String = "${keyValue.value}${store[keyValue.key].value()}"
}

open class CustomProcessor : Processor<String, String>("add-v2-prefix") {
    override fun process(metadata: ProcessorMetadata, keyValue: KeyValue<String, String>): String =
        "${keyValue.value}.v2"
}
