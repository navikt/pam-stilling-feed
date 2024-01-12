package no.nav.pam.stilling.feed

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.ContactDTO
import no.nav.pam.stilling.feed.dto.FeedAd
import no.nav.pam.stilling.feed.dto.FeedContact
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertEquals

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

        val hentetAdFørEndring = objectMapper.readValue<FeedAd>(feedRepository.hentFeedItem(UUID.fromString(ad.uuid))!!.json)
        feedService.lagreOppdaterteDetaljer(listOf(ad.copy(contactList = mutableListOf(ContactDTO(id=null, name="Petter")))))
        val hentetAdEtterEndring = objectMapper.readValue<FeedAd>(feedRepository.hentFeedItem(UUID.fromString(ad.uuid))!!.json)

        assertEquals(emptyList(), hentetAdFørEndring.contactList)
        assertEquals(listOf(FeedContact(name="Petter", null, null, null, null)), hentetAdEtterEndring.contactList)
        assertThat(hentetAdFørEndring).usingRecursiveComparison().ignoringFields("contactList").isEqualTo(hentetAdEtterEndring)
    }
}
