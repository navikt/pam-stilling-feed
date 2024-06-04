package no.nav.pam.stilling.feed.servicetest

import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.Feed
import no.nav.pam.stilling.feed.dto.FeedAd
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedServiceTest {
    lateinit var feedRepository: FeedRepository
    lateinit var feedService: FeedService
    lateinit var txTemplate: TxTemplate

    @BeforeAll
    fun init() {
        val ds = dataSource
        kjørFlywayMigreringer(ds)
        txTemplate = TxTemplate(ds)
        feedRepository = FeedRepository(txTemplate)
        feedService = FeedService(feedRepository, txTemplate, objectMapper)
    }

    private fun antallAnnonser(): Int = dataSource.connection.use {
        val rs = it.prepareStatement("select count(*) from feed_item").executeQuery()
        rs.next()
        rs.getInt(1)
    }

    private fun antallSider(): Int = dataSource.connection.use {
        val rs = it.prepareStatement("select count(*) from feed_page_item").executeQuery()
        rs.next()
        rs.getInt(1) % Feed.defaultPageSize
    }

    @Test
    fun skalLagreOgOppdatereAdsSomFeedItems() {
        val ad1 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = UUID.randomUUID().toString())
        val ad2 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = UUID.randomUUID().toString())
        val ad1_update = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = ad1.uuid, title = ad1.title + " endret")
        val ad3maskert = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = UUID.randomUUID().toString(), status = "STOPPED")

        val antallEksisterendeAnnonser = antallAnnonser()

        feedService.lagreNyStillingsAnnonse(ad1)
        feedService.lagreNyStillingsAnnonseFraJson(objectMapper.writeValueAsString(ad2))
        feedService.lagreNyStillingsAnnonse(ad1_update)
        feedService.lagreNyStillingsAnnonse(ad3maskert)

        val antallAnnonser = antallAnnonser()
        Assertions.assertThat(antallAnnonser - antallEksisterendeAnnonser).isEqualTo(3)

        val lagretAd1 = feedService.hentStillingsAnnonse(UUID.fromString(ad1.uuid))!!
        val lagretFeedAd = objectMapper.readValue(lagretAd1.json, FeedAd::class.java)
        Assertions.assertThat(lagretFeedAd.title).isEqualTo(ad1_update.title)
        Assertions.assertThat(lagretFeedAd.extent).isEqualTo("Heltid")

        val lagretAd3maskert = feedService.hentStillingsAnnonse(UUID.fromString(ad3maskert.uuid))!!
        Assertions.assertThat(lagretAd3maskert.json).isEmpty()
        val feedSide = feedService.hentSisteSide()!!
        Assertions.assertThat(feedSide.title).isNotEqualTo(ad3maskert.title)
    }

    @Test
    fun skalLagreFeedSider() {
        val adIds = mutableListOf<String>()
        for (i in 1..(Feed.defaultPageSize * 3) + 1) {
            val ad = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
                .copy(uuid = UUID.randomUUID().toString(), title = "Annonse #$i")
            adIds.add(ad.uuid)
            feedService.lagreNyStillingsAnnonse(ad)
        }

        Assertions.assertThat(adIds.size).isEqualTo(Feed.defaultPageSize * 3 + 1)

        var førsteSide = feedService.hentFørsteSide()
        var side = feedService.hentFeedHvis(førsteSide!!.id)!!
        side.items.forEach { adIds.remove(it.feed_entry.uuid) }
        while (side.next_id != null) {
            side = feedService.hentFeedHvis(side.next_id!!)!!
            side.items.forEach { adIds.remove(it.feed_entry.uuid) }
        }
        Assertions.assertThat(adIds).isEmpty()
    }

    @Test
    fun skalLagreOgOppdatereAdsSomFeedItemsITransaksjoner() {
        val ad1 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = UUID.randomUUID().toString())
        val ad2 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = UUID.randomUUID().toString())
        val ad3 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
            .copy(uuid = UUID.randomUUID().toString())

        txTemplate.doInTransaction { ctx ->
            feedService.lagreNyStillingsAnnonse(ad1, ctx)

            txTemplate.doInTransaction { ctxNew -> // Implisitt ny kontekst som ikke er del av eksisterende transaksjon
                feedService.lagreNyStillingsAnnonseFraJson(objectMapper.writeValueAsString(ad2), ctxNew)
            }
            txTemplate.doInTransaction(ctx) { ctxNew -> // Propagerer kontekst og deltar dermed i eksisterende transaksjon
                feedService.lagreNyStillingsAnnonseFraJson(objectMapper.writeValueAsString(ad2), ctxNew)
            }
            ctx.setRollbackOnly()
        }

        val lagretAd1 = feedService.hentStillingsAnnonse(UUID.fromString(ad1.uuid))
        val lagretAd2 = feedService.hentStillingsAnnonse(UUID.fromString(ad2.uuid))
        val lagretAd3 = feedService.hentStillingsAnnonse(UUID.fromString(ad3.uuid))
        Assertions.assertThat(lagretAd1?.status).isNull()
        Assertions.assertThat(lagretAd2?.status).isNotNull
        Assertions.assertThat(lagretAd3?.status).isNull()
    }

    @Test
    fun `Svarer ikke ut FINN-annonser`() {
        val ikkeFinnAd1 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java).copy(uuid = UUID.randomUUID().toString())
        val finnAd1 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java).copy(uuid = UUID.randomUUID().toString(), source = "FINN")
        val ikkeFinnAd2 = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java).copy(uuid = UUID.randomUUID().toString())

        val antallEksisterendeAnnonser = antallAnnonser()
        val antallEksisterendeFeedItems = feedService.hentFeedHvis(feedService.hentFørsteSide()!!.id, antall = antallEksisterendeAnnonser * 10)!!.items.size

        feedService.lagreNyStillingsAnnonse(ikkeFinnAd1)
        feedService.lagreNyStillingsAnnonseFraJson(objectMapper.writeValueAsString(finnAd1))
        feedService.lagreNyStillingsAnnonse(ikkeFinnAd2)

        val antallAnnonser = antallAnnonser()
        assertEquals(3, antallAnnonser - antallEksisterendeAnnonser)

        val lagretIkkeFinnAd1 = feedService.hentStillingsAnnonse(UUID.fromString(ikkeFinnAd1.uuid))
        val lagretFinnAd1 = feedService.hentStillingsAnnonse(UUID.fromString(finnAd1.uuid))
        val lagretIkkeFinnAd2 = feedService.hentStillingsAnnonse(UUID.fromString(ikkeFinnAd2.uuid))

        assertNotNull(lagretIkkeFinnAd1)
        assertNull(lagretFinnAd1)
        assertNotNull(lagretIkkeFinnAd2)

        val feedPage = feedService.hentFeedHvis(feedService.hentFørsteSide()!!.id, antall = antallEksisterendeFeedItems + Feed.defaultPageSize)!!
        assertEquals(2, feedPage.items.size - antallEksisterendeFeedItems)
    }
}
