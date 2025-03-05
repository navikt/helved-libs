package libs.kafka

import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.concurrent.Future
import org.apache.kafka.clients.producer.MockProducer as ApacheMockProducer;

class MockProducer<K : Any, V : Any>(
    topic: Topic<K, V>,
    private val testTopic: TestTopic<K, V>,
) : ApacheMockProducer<K, V>(true, topic.serdes.key.serializer(), topic.serdes.value.serializer()) {
    override fun send(record: ProducerRecord<K, V>, callback: Callback): Future<RecordMetadata> {
        testTopic.produce(record.key(), record::value)
        return super.send(record, callback)
    }
}
