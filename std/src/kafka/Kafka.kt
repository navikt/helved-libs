package kafka

import env
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.Serdes.StringSerde
import org.apache.kafka.common.serialization.Serializer
import java.util.Properties

interface Producer<T> : AutoCloseable {
    fun produce(key: String, value: T)
}

data class Config(
    val brokers: String = env("KAFKA_BROKERS"),
    val truststore: String = env("KAFKA_TRUSTSTORE_PATH"),
    val keystore: String = env("KAFKA_KEYSTORE_PATH"),
    val credstorePassword: String = env("KAFKA_CREDSTORE_PASSWORD"),
)

fun createProducer(
    clientId: String,
    config: Config,
): org.apache.kafka.clients.producer.Producer<String, String> {
    return createProducer(clientId, config, StringSerde().serializer())
}

fun <T> createProducer(
    clientId: String,
    config: Config,
    serializer: Serializer<T>,
): org.apache.kafka.clients.producer.Producer<String, T> {
    return KafkaProducer(properties(clientId, config), StringSerde().serializer(), serializer)
}

private fun properties(clientId: String, config: Config) = Properties().apply {
    this[CommonClientConfigs.CLIENT_ID_CONFIG] = clientId
    this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = config.brokers
    this[ProducerConfig.ACKS_CONFIG] = "all"
    this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "5"
    this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
    this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"
    this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = config.truststore
    this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = config.credstorePassword
    this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
    this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = config.keystore
    this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = config.credstorePassword
    this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = config.credstorePassword
    this[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
}

