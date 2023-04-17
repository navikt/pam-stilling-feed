package no.nav.pam.stilling.feed.config

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.util.*

class KafkaConfig(private val env: Map<String, String>) {
    private val topic = ""
    companion object {
        private val LOG = LoggerFactory.getLogger(KafkaConfig::class.java)
    }

    fun kafkaConsumer() : KafkaConsumer<String?, ByteArray?> {
        val consumer: KafkaConsumer<String?, ByteArray?> = KafkaConsumer(env)

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
}