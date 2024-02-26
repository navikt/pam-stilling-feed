package no.nav.pam.stilling.feed

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OppdaterDetaljerTest {
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

    @Test
    fun `OppdaterDetaljer oppdaterer bare detaljer`() {
        val ad = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java).copy(uuid = UUID.randomUUID().toString(), contactList = mutableListOf())
        feedService.lagreNyStillingsAnnonse(ad)

        val hentetAdFørEndring = objectMapper.readValue<FeedAd>(feedRepository.hentFeedItem(UUID.fromString(ad.uuid), false)!!.json)
        feedService.lagreOppdaterteDetaljer(listOf(ad.copy(contactList = mutableListOf(ContactDTO(id=null, name="Petter")))))
        val hentetAdEtterEndring = objectMapper.readValue<FeedAd>(feedRepository.hentFeedItem(UUID.fromString(ad.uuid), false)!!.json)

        assertEquals(emptyList(), hentetAdFørEndring.contactList)
        assertEquals(listOf(FeedContact(name="Petter", null, null, null, null)), hentetAdEtterEndring.contactList)
        assertThat(hentetAdFørEndring).usingRecursiveComparison().ignoringFields("contactList").isEqualTo(hentetAdEtterEndring)
    }

    @Test
    fun `Oppdater kilde migrering oppdaterer kilde men ikke detaljer`() {
        val ad = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java).copy(uuid = UUID.randomUUID().toString(), contactList = mutableListOf(), source = null)
        feedService.lagreNyStillingsAnnonse(ad)

        val hentetAdFørEndring = feedRepository.hentFeedItem(UUID.fromString(ad.uuid), false)!!
        feedService.lagreKilde(listOf(ad.copy(contactList = mutableListOf(ContactDTO(id=null, name="Petter")), source = "Stillingsregistrering")))
        val hentetAdEtterEndring = feedRepository.hentFeedItem(UUID.fromString(ad.uuid), false)!!

        val førEndringJson = objectMapper.readValue<FeedAd>(hentetAdFørEndring.json)
        val etterEndringJson = objectMapper.readValue<FeedAd>(hentetAdEtterEndring.json)

        assertEquals(emptyList(), førEndringJson.contactList)
        assertEquals(emptyList(), etterEndringJson.contactList)
        assertNull(hentetAdFørEndring.kilde)
        assertNotNull(hentetAdEtterEndring.kilde)
        assertEquals("Stillingsregistrering", hentetAdEtterEndring.kilde)
        assertThat(hentetAdFørEndring).usingRecursiveComparison().ignoringFields("kilde").isEqualTo(hentetAdEtterEndring)
        assertThat(førEndringJson).usingRecursiveComparison().isEqualTo(etterEndringJson)
    }
}
