package no.nav.pam.stilling.feed.controllertest

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.DatabaseConfig
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.Feed
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedControllerTest {
    var feedRepository: FeedRepository? = null
    var feedService: FeedService? = null
    var txTemplate : TxTemplate? = null

    @BeforeAll
    fun init() {
        val ds = dataSource

//        val env = System.getenv()
//        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
//        val ds = DatabaseConfig(env, prometheusRegistry.prometheusRegistry).lagDatasource()

        txTemplate = TxTemplate(ds)
        feedRepository = FeedRepository(txTemplate!!)
        feedService = FeedService(feedRepository!!, txTemplate!!, objectMapper)
        kjørFlywayMigreringer(ds)

        startLocalApplication()

    }

    @Test
    fun skalLagreOgHenteFeedSider() {
        val adIds = mutableListOf<String>()
        for (i in 1..(Feed.pageSize*3)+1) {
            val ad = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
                .copy(uuid = UUID.randomUUID().toString(),
                    title =  "Annonse #$i"
                )
            adIds.add(ad.uuid)
            val adItem = feedService!!.lagreNyStillingsAnnonse(ad)
        }

        Assertions.assertThat(adIds.size).isEqualTo(Feed.pageSize*3 + 1)

        var førsteSide = feedService!!.hentFørsteSide()
        var side = feedService!!.hentFeedHvis(førsteSide!!.id)!!
        side.items.forEach { adIds.remove(it.feed_entry.uuid) }
        while(side.next_id != null) {
            side = feedService!!.hentFeedHvis(side.next_id!!)!!
            side.items.forEach { adIds.remove(it.feed_entry.uuid) }
            println(side)
        }
        Assertions.assertThat(adIds).isEmpty()
    }
}