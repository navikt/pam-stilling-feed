package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.Feed
import no.nav.pam.stilling.feed.dto.FeedItem
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaListenerTest {
    val topic = getLokalEnv()["STILLING_INTERN_TOPIC"]
    val adIds = mutableListOf<String>()

    lateinit var feedService: FeedService
    lateinit var kafkaProducer: KafkaProducer<String?, ByteArray?>
    lateinit var admin: Admin

    @BeforeAll
    fun init() {
        val ds = dataSource
        val txTemplate = TxTemplate(ds)

        feedService = FeedService(FeedRepository(txTemplate), txTemplate, objectMapper)
        kjørFlywayMigreringer(ds)

        val kafkaConfig = mutableMapOf<String, Any>(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java.name,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 0,
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to lokalKafka.bootstrapServers
        )

        kafkaProducer = KafkaProducer<String?, ByteArray?>(kafkaConfig)
        admin = Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to lokalKafka.bootstrapServers))
            .apply {
                if (listTopics().names().get().isEmpty())
                    createTopics(listOf(NewTopic(topic, 1, 1))).values()[topic]?.get()
            }

        startLocalApplication()
    }

    private fun publiserTilKafka() {
        for (i in 1..(Feed.defaultPageSize * 3) + 1) {
            val ad = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
                .copy(
                    uuid = UUID.randomUUID().toString(),
                    title = "Annonse #$i"
                )
            adIds.add(ad.uuid)
            kafkaProducer.send(ProducerRecord(topic, ad.uuid, objectMapper.writeValueAsBytes(ad))).get().offset()
        }
    }

    @Test
    fun skalKonsumereKafkameldinger() {
        publiserTilKafka()
        Assertions.assertThat(adIds.size).isEqualTo(Feed.defaultPageSize * 3 + 1)

        admin.describeTopics(listOf(topic)).allTopicNames().get().forEach{println(it)}
        admin.listConsumerGroups().all().get().forEach{println(it)}

        var maxAntallForsøk = 2
        var ad: FeedItem? = null
        while (maxAntallForsøk > 0 && ad == null) {
            ad = feedService.hentStillingsAnnonse(UUID.fromString(adIds[0]))
            Thread.sleep(11000L)
            maxAntallForsøk--
        }

        Assertions.assertThat(ad).isNotNull
    }
}

