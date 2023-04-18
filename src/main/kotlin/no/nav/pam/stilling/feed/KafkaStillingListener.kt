package no.nav.pam.stilling.feed

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pam.stilling.feed.config.TxTemplate
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.AuthorizationException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class KafkaStillingListener(private val kafkaConsumer: KafkaConsumer<String?, ByteArray?>,
                            private val feedService: FeedService,
                            private val objectMapper: ObjectMapper,
                            private val txTemplate: TxTemplate,
                            private val healthService: HealthService) {
    companion object {
        private val LOG = LoggerFactory.getLogger(FeedRepository::class.java)
    }

    fun startListener() : Thread {
        return thread { startListenerInternal() }
    }

    private fun startListenerInternal() {
        LOG.info("Starter Kafka stilling listener")
        var records: ConsumerRecords<String?, ByteArray?>? = null
        val rollbackCounter = AtomicInteger(0)
        while (true) {
            try {
                records = kafkaConsumer.poll(Duration.ofSeconds(10))
                if (records.count() > 0) {
                    LOG.info("Leste ${records.count()} rader. Keys: {}",
                        records.mapNotNull { it.key() }.joinToString()
                    )
                    handleRecords(records!!)
                    kafkaConsumer.commitSync()
                }
            } catch (e: AuthorizationException) {
                LOG.error("AuthorizationException i consumerloop, restarter app ${e.message}", e)
                healthService.addUnhealthyVote()
            } catch (ke: KafkaException) {
                LOG.error("KafkaException occurred in consumeLoop", ke)
                // Enten så ruller vi tilbake, eller så dreper vi appen - uvisst hva som er best strategi?
                // Rollback har potensiale for å sende svært mange meldinger til topicet på veldig kort tid
                // Har derfor lagt inn en grense på 10 rollback før appen omstartes. Det er fortsatt potensiale
                // for å publisere svært mange meldinger, men det går ikke like fort...
                if (ke.cause != null && ke.cause is AuthorizationException)
                    healthService.addUnhealthyVote()
                else
                    rollback(records!!, kafkaConsumer, rollbackCounter)
            } catch (e: Exception) {
                // Catchall - impliserer at vi skal restarte app
                LOG.error("Uventet Exception i consumerloop, restarter app ${e.message}", e)
                healthService.addUnhealthyVote()
            }
        }
    }

    private fun rollback(
        records: ConsumerRecords<String?, ByteArray?>,
        kafkaConsumer: KafkaConsumer<String?, ByteArray?>,
        rollbackCounter: AtomicInteger
    ) {
        try {
            val firstOffsets = mutableMapOf<String, MutableMap<Int, Long>>()
            records.forEach() {
                val partitions = firstOffsets[it.topic()] ?: mutableMapOf()
                firstOffsets[it.topic()] = partitions
                val currentOffset = it.offset()
                val earliest = partitions[it.partition()] ?: currentOffset
                partitions[it.partition()] = currentOffset.coerceAtMost(earliest)
            }
            firstOffsets.forEach { entry ->
                for (partitionOffset in entry.value) {
                    LOG.info("Ruller tilbake ${entry.key} partition ${partitionOffset.key} til ${partitionOffset.value}")
                    kafkaConsumer.seek(TopicPartition(entry.key, partitionOffset.key), partitionOffset.value)
                }
            }
            rollbackCounter.addAndGet(1)
        } catch (e: Exception) {
            LOG.error("Rollback feilet, restarter appen", e)
            healthService.addUnhealthyVote()
        }
    }

    fun handleRecords(records: ConsumerRecords<String?, ByteArray?>) {
        val now = System.currentTimeMillis()
        val adIds = records.mapNotNull { it.key() }.toList()
        LOG.info("Mottar ${records.count()} records med stillingsannonser: {}", adIds.joinToString(", "))

        val ads = records.mapNotNull { String(it.value()!!) }
        feedService.lagreNyeStillingsAnnonserFraJson(ads)
    }
}
