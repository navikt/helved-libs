package libs.kafka

import libs.kafka.processor.LogConsumeTopicProcessor
import libs.kafka.processor.LogProduceTableProcessor
import libs.kafka.processor.MetadataProcessor
import libs.kafka.processor.Processor.Companion.addProcessor
import libs.kafka.processor.ProcessorMetadata
import libs.kafka.stream.ConsumedStream
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Named
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics
import libs.kafka.client.ConsumerFactory
import libs.kafka.client.ProducerFactory
import org.apache.kafka.streams.KafkaStreams.State.*
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.ValueAndTimestamp

private fun <T : Any> nameSupplierFrom(topic: Topic<T>): () -> String = { "from-${topic.name}" }

interface Streams : ProducerFactory, ConsumerFactory, AutoCloseable {
    fun connect(topology: Topology, config: StreamsConfig, registry: MeterRegistry)
    fun ready(): Boolean
    fun live(): Boolean
    fun visulize(): TopologyVisulizer
    fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology)
    fun <T : Any> getStore(table: Table<T>): StateStore<T>
}

class KafkaStreams : Streams {
    private var initiallyStarted: Boolean = false

    private lateinit var internalStreams: org.apache.kafka.streams.KafkaStreams
    private lateinit var internalTopology: org.apache.kafka.streams.Topology

    override fun connect(
        topology: Topology,
        config: StreamsConfig,
        registry: MeterRegistry,
    ) {
        topology.registerInternalTopology(this)

        internalStreams = org.apache.kafka.streams.KafkaStreams(internalTopology, config.streamsProperties())
        KafkaStreamsMetrics(internalStreams).bindTo(registry)
        internalStreams.setUncaughtExceptionHandler(ProcessingErrHandler())
        internalStreams.setStateListener { state, _ -> if (state == RUNNING) initiallyStarted = true }
        internalStreams.setGlobalStateRestoreListener(RestoreListener())
        internalStreams.start()
    }

    override fun ready(): Boolean = initiallyStarted && internalStreams.state() in listOf(CREATED, REBALANCING, RUNNING)
    override fun live(): Boolean = initiallyStarted && internalStreams.state() != ERROR
    override fun visulize(): TopologyVisulizer = TopologyVisulizer(internalTopology)
    override fun close() = internalStreams.close()

    override fun registerInternalTopology(internalTopology: org.apache.kafka.streams.Topology) {
        this.internalTopology = internalTopology
    }

    override fun <T : Any> getStore(table: Table<T>): StateStore<T> = StateStore(
        internalStreams.store(
            StoreQueryParameters.fromNameAndType<ReadOnlyKeyValueStore<String, ValueAndTimestamp<T>>>(
                table.stateStoreName,
                QueryableStoreTypes.keyValueStore()
            )
        )
    )
}
class Topology internal constructor() {
    private val builder = StreamsBuilder()

    fun <T : Any> consume(topic: Topic<T>): ConsumedStream<T> {
        val consumed = consumeWithLogging<T?>(topic).skipTombstone(topic)
        return ConsumedStream(topic, consumed, nameSupplierFrom(topic))
    }

    fun <T : Any> consume(table: Table<T>): KTable<T> {
        val stream = consumeWithLogging<T?>(table.sourceTopic)
        return stream.toKtable(table)
    }

    fun <T : Any> consumeRepartitioned(table: Table<T>, partitions: Int): KTable<T> {
        val internalKTable = consumeWithLogging<T?>(table.sourceTopic)
            .repartition(repartitioned(table, partitions))
            .addProcessor(LogProduceTableProcessor(table))
            .toTable(
                Named.`as`("${table.sourceTopicName}-to-table"),
                materialized(table)
            )

        return KTable(table = table, internalKTable = internalKTable)
    }

    /**
     * The topology does not allow duplicate named nodes.
     * Somethimes it is necessary to consume the same topic again for mocking external responses.
     */
    fun <T : Any> consumeForMock(topic: Topic<T>, namedPrefix: String = "mock"): ConsumedStream<T> {
        val consumed = consumeWithLogging(topic, namedPrefix).skipTombstone(topic, namedPrefix)
        val prefixedNamedSupplier = { "$namedPrefix-${nameSupplierFrom(topic).invoke()}" }
        return ConsumedStream(topic, consumed, prefixedNamedSupplier)
    }

    fun <T : Any> consume(
        topic: Topic<T>,
        onEach: (key: String, value: T?, metadata: ProcessorMetadata) -> Unit,
    ): ConsumedStream<T> {
        val stream = consumeWithLogging<T?>(topic)
        stream.addProcessor(MetadataProcessor(topic)).foreach { _, (kv, metadata) ->
            onEach(kv.key, kv.value, metadata)
        }
        return ConsumedStream(topic, stream.skipTombstone(topic), nameSupplierFrom(topic))
    }

    fun registerInternalTopology(stream: Streams) {
        stream.registerInternalTopology(builder.build())
    }

    internal fun buildInternalTopology() = builder.build()

    private fun <T> consumeWithLogging(topic: Topic<T & Any>): KStream<String, T> = consumeWithLogging(topic, "")

    private fun <T> consumeWithLogging(topic: Topic<T & Any>, namedSuffix: String): KStream<String, T> {
        val consumeInternal = topic.consumed("consume-${topic.name}$namedSuffix")
        val consumeLogger = LogConsumeTopicProcessor<T>(topic, namedSuffix)
        return builder.stream(topic.name, consumeInternal).addProcessor(consumeLogger)
    }
}

fun topology(init: Topology.() -> Unit): Topology = Topology().apply(init)


