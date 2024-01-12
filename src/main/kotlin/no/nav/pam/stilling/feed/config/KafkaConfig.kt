package no.nav.pam.stilling.feed.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.util.*

class KafkaConfig(private val env: Map<String, String>) {
    companion object {
        private val LOG = LoggerFactory.getLogger(KafkaConfig::class.java)
    }

    fun kafkaConsumer(topic: String, groupId: String) : KafkaConsumer<String?, ByteArray?> {
        val consumer: KafkaConsumer<String?, ByteArray?> = KafkaConsumer(kafkaConsumerProperties(groupId))

        consumer.subscribe(Collections.singleton(topic), object : ConsumerRebalanceListener {
            override fun onPartitionsRevoked(partitions: Collection<TopicPartition?>) {
                partitions.forEach { tp ->
                    LOG.info("Rebalance: no longer assigned to topic {}, partition {}",
                        tp?.topic(), tp?.partition())
                }
            }

            override fun onPartitionsAssigned(partitions: Collection<TopicPartition?>) {
                partitions.forEach { tp ->
                    LOG.info("Rebalance: assigned to topic {}, partition {}",
                        tp?.topic(), tp?.partition())
                }
            }
        })

        return consumer
    }
    fun kafkaConsumerProperties(groupId: String): Map<String, Any> {
        val clientId = "PamStillingFeed"
        val autoOffsetResetConfig = "earliest"

        val props = mutableMapOf<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = env.variable("KAFKA_BROKERS")

        if (!env["KAFKA_CREDSTORE_PASSWORD"].isNullOrBlank()) {
            props[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = env.variable("KAFKA_CREDSTORE_PASSWORD")
            props[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = env.variable("KAFKA_CREDSTORE_PASSWORD")
            props[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = env.variable("KAFKA_CREDSTORE_PASSWORD")
            props[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"
            props[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
        }

        env["KAFKA_TRUSTSTORE_PATH"]?.let { props[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = it }
        env["KAFKA_KEYSTORE_PATH"]?.let { props[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = it }
        env["KAFKA_TRUSTSTORE_PATH"]?.let { props[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL" }

        props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        props[ConsumerConfig.CLIENT_ID_CONFIG] = clientId
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = ByteArrayDeserializer::class.java.name
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = autoOffsetResetConfig
        props[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = 500000
        props[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = 10000
        props[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = 3000
        return props
    }
}
