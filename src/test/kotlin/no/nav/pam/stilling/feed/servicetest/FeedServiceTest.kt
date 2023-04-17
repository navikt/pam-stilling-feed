package no.nav.pam.stilling.feed.servicetest

import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.Feed
import no.nav.pam.stilling.feed.dto.FeedPageItem
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedServiceTest {
    var feedRepository: FeedRepository? = null
    var feedService: FeedService? = null
    var txTemplate : TxTemplate? = null

    @BeforeAll
    fun init() {
        val ds = dataSource
        kjørFlywayMigreringer(ds)
        txTemplate = TxTemplate(ds)
        feedRepository = FeedRepository(txTemplate!!)
        feedService = FeedService(feedRepository!!, txTemplate!!, objectMapper)
    }

    private fun antallAnnonser(): Int = dataSource.connection.use {
        val rs = it.prepareStatement("select count(*) from feed_item").executeQuery()
        rs.next()
        rs.getInt(1)
    }

    private fun antallSider(): Int = dataSource.connection.use {
        val rs = it.prepareStatement("select count(*) from feed_page_item").executeQuery()
        rs.next()
        rs.getInt(1) % Feed.pageSize
    }

    @Test
    fun skalLagreOgOppdatereAdsSomFeedItems() {
        val ad1 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = UUID.randomUUID().toString())
        val ad2 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = UUID.randomUUID().toString())
        val ad1_update = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = ad1.uuid, title = ad1.title + " endret")

        val antallEksisterendeAnnonser = antallAnnonser()

        feedService!!.lagreNyStillingsAnnonse(ad1)
        feedService!!.lagreNyStillingsAnnonseFraJson(objectMapper.writeValueAsString(ad2))
        feedService!!.lagreNyStillingsAnnonse(ad1_update)

        val antallAnnonser = antallAnnonser()
        Assertions.assertThat(antallAnnonser-antallEksisterendeAnnonser).isEqualTo(2)

        val lagretAd1 = feedService!!.hentStillingsAnnonse(UUID.fromString(ad1.uuid))!!
        //Assertions.assertThat(lagretAd1.json).isEqualTo(ad1_update.title)
    }

    @Test
    fun skalLagreFeedSider() {
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