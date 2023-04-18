package no.nav.pam.stilling.feed.controllertest

import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.Feed
import no.nav.pam.stilling.feed.dto.FeedEntryContent
import no.nav.pam.stilling.feed.dto.FeedItem
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
            var ad = objectMapper.readValue(javaClass.getResourceAsStream("/ad_dto.json"), AdDTO::class.java)
                .copy(uuid = UUID.randomUUID().toString(),
                    title =  "Annonse #$i"
                )
            if (i % 7 == 0) ad = ad.copy(status = "INACTIVE")
            adIds.add(ad.uuid)
            val adItem = feedService!!.lagreNyStillingsAnnonse(ad)
        }

        Assertions.assertThat(adIds.size).isEqualTo(Feed.pageSize*3 + 1)

        var feedPageResponse = getFeedPage()
        feedPageResponse.first.items.forEach { adIds.remove(it.feed_entry.uuid) }

        while(feedPageResponse.first.next_id != null) {
            feedPageResponse = getFeedPage(feedPageResponse.first.next_id.toString())
            feedPageResponse.first.items.forEach { f ->
                val feedItem = getFeedItem(f.feed_entry.uuid)
                //println(feedItem.first.json)
            }
            feedPageResponse.first.items.forEach { adIds.remove(it.feed_entry.uuid) }
        }
        Assertions.assertThat(adIds).isEmpty()

        // TODO legg på test på last modified og etag
    }

    private fun getFeedPage(pageId: String = "", etag: String? = null, lastModified: String? = null) : Pair<Feed, HttpHeaders> {
        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/v1/feed/$pageId"))
            .GET()
            .build()

        val response = HttpClient.newBuilder()
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())

        val feed = objectMapper.readValue(response.body(), Feed::class.java)
        return Pair(feed, response.headers())
    }
    private fun getFeedItem(itemId: String = "", etag: String? = null, lastModified: String? = null) : Pair<FeedEntryContent, HttpHeaders> {
        val request = HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/v1/feedentry/$itemId"))
            .GET()
            .build()

        val response = HttpClient.newBuilder()
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())

        val feed = objectMapper.readValue(response.body(), FeedEntryContent::class.java)
        return Pair(feed, response.headers())
    }
}