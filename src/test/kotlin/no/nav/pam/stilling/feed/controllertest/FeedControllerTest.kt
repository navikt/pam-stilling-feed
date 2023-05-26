package no.nav.pam.stilling.feed.controllertest

import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.Feed
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedControllerTest {
    private lateinit var feedRepository: FeedRepository
    private lateinit var feedService: FeedService
    private lateinit var txTemplate : TxTemplate

    @BeforeAll
    fun init() {
        val ds = dataSource
        txTemplate = TxTemplate(ds)
        feedRepository = FeedRepository(txTemplate)
        feedService = FeedService(feedRepository, txTemplate, objectMapper)
        kjørFlywayMigreringer(ds)

        startLocalApplication()
    }

    @Test
    fun skalLagreOgHenteFeedSider() {
        val adIds = mutableListOf<String>()
        for (i in 1..(Feed.defaultPageSize*3)+1) {
            var ad = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
                .copy(uuid = UUID.randomUUID().toString(),
                    title =  "Annonse #$i"
                )
            if (i % 7 == 0) ad = ad.copy(status = "INACTIVE")
            adIds.add(ad.uuid)
            feedService.lagreNyStillingsAnnonse(ad)
        }

        Assertions.assertThat(adIds.size).isEqualTo(Feed.defaultPageSize*3 + 1)

        var feedPageResponse = getFeedPage()

        val etag = feedPageResponse.second.firstValue("etag").get()
        val lastModified = feedPageResponse.second.firstValue("last-modified").get()
        val etagResponse = sendFeedRequest("$lokalUrlBase/api/v1/feed/${feedPageResponse.first.id}", etag, lastModified)
        assertEquals(304, etagResponse.statusCode())

        feedPageResponse.first.items.forEach { adIds.remove(it.feed_entry.uuid) }

        while(feedPageResponse.first.next_id != null) {
            feedPageResponse = getFeedPage(feedPageResponse.first.next_id.toString())
            feedPageResponse.first.items.forEach { adIds.remove(it.feed_entry.uuid) }
        }

        Assertions.assertThat(adIds).isEmpty()
    }

    @Test
    fun `Siste feedside har next_id og next_url lik null i respons`() {
        val ad = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
        feedService.lagreNyStillingsAnnonse(ad)
        val response = sendFeedRequest("$lokalUrlBase/api/v1/feed?last=true")
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"next_url\":null"))
        assertTrue(response.body().contains("\"next_id\":null"))
    }

    @Test
    fun `Henter første side oppdatert etter If-Modified-Since dersom page ID ikke er gitt`() {
        txTemplate.tømTabeller("feed_page_item")
        val adSource = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
        val updatedBase = LocalDateTime.of(2018, 1, 1, 12, 0, 0, 0)

        for (i in (0L..100L)) {
            val updatedAt = updatedBase.plusDays(i)
            val uuid = UUID.randomUUID()
            feedService.lagreNyStillingsAnnonse(adSource.copy(uuid = uuid.toString(), title = "Annonse oppdatert $updatedAt", updated = updatedAt))
            oppdaterSistEndretIDatabase(updatedAt, uuid)
        }

        for (i in 0L until 100L step 20) {
            val updatedSince = updatedBase.plusDays(i)
            val expectedUpdated = updatedBase.plusDays(i+1)
            val page = getFeedPage(lastModified = formatLocalDatetime(updatedSince)).first

            assertEquals("Annonse oppdatert $expectedUpdated", page.items.first().title)
            assertEquals(expectedUpdated, page.items.first().date_modified?.toLocalDateTime())
        }

        txTemplate.tømTabeller("feed_page_item")
    }

    private fun getFeedPage(pageId: String = "", etag: String? = null, lastModified: String? = null) : Pair<Feed, HttpHeaders> {
        val response = sendFeedRequest("$lokalUrlBase/api/v1/feed/$pageId", etag, lastModified)
        val feed = objectMapper.readValue(response.body(), Feed::class.java)
        return Pair(feed, response.headers())
    }

    private fun sendFeedRequest(url: String, etag: String? = null, lastModified: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI(url))
            .setHeader("Authorization", "Bearer $testToken")

        if (etag != null) request.setHeader("If-None-Match", etag)
        if (lastModified != null) request.setHeader("If-Modified-Since", lastModified)

        return HttpClient.newBuilder()
            .build()
            .send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun formatLocalDatetime(datetime: LocalDateTime) =
        datetime.toZonedDateTime().format(DateTimeFormatter.RFC_1123_DATE_TIME)

    private fun oppdaterSistEndretIDatabase(sistEndret: LocalDateTime, id: UUID) = txTemplate.doInTransaction { ctx ->
        ctx.connection().prepareStatement("update feed_page_item set sist_endret = ? where feed_item_id = ?").apply {
                setTimestamp(1, Timestamp(sistEndret.toZonedDateTime().toInstant().toEpochMilli()))
                setObject(2, id)
            }.executeUpdate()
    }

    private fun LocalDateTime.toZonedDateTime() = atZone(ZoneId.of("Europe/Oslo"))
}
