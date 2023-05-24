package no.nav.pam.stilling.feed.controllertest

import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.Feed
import no.nav.pam.stilling.feed.dto.FeedEntryContent
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import kotlin.test.assertEquals

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
}
