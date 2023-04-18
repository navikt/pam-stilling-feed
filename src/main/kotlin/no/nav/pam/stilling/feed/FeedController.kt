package no.nav.pam.stilling.feed

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.javalin.http.Context
import no.nav.pam.stilling.feed.dto.FeedEntryContent
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class FeedController(private val feedService: FeedService,  private val objectMapper: ObjectMapper) {
    companion object {
        private val LOG = LoggerFactory.getLogger(FeedRepository::class.java)
    }

    fun setupRoutes(javalin: Javalin) {
        javalin.get("/api/v1/feed/{feed_page_id}") { ctx -> hentFeed(ctx, ctx.pathParam("feed_page_id")) }
        javalin.get("/api/v1/feed") { ctx -> hentFeed(ctx) }
        javalin.get("/api/v1/feedentry/{entry_id}") { ctx -> hentFeedItem(ctx, ctx.pathParam("entry_id")) }
    }

    fun hentFeed(ctx: Context) {
        val sisteSide = ctx.queryParam("last")
        val feedPageItem = if (sisteSide != null) feedService.hentSisteSide() else feedService.hentFørsteSide()
        if (feedPageItem == null)
            ctx.status(404)
        else
            hentFeed(ctx, feedPageItem.id.toString())
    }

    fun hentFeed(ctx: Context, feedPageId: String) {
        val etag = ctx.header("If-None-Match")
        val ifModifiedSinceStr = ctx.header("If-Modified-Since")

        val feed = feedService.hentFeedHvis(id = UUID.fromString(feedPageId), etag = etag,
            sistEndret = strToZonedDateTime(ifModifiedSinceStr))

        if (feed == null) {
            ctx.status(404)
        } else if (feed.items.isEmpty()) {
            ctx.status(304)
        } else {
            ctx.header("ETag", feed.etag)
            ctx.header("Last-Modified", zonedDatetimeToStr(feed.lastModified))
            ctx.json(feed)
        }
    }

    fun hentFeedItem(ctx: Context, feedEntryId: String) {
        val feed = feedService.hentStillingsAnnonse(UUID.fromString(feedEntryId))

        if (feed == null) {
            ctx.status(404)
        } else {
            ctx.json(FeedEntryContent.fraFeedItem(feed, objectMapper))
        }
    }

    private fun strToZonedDateTime(zt: String?) : ZonedDateTime? {
        zt?.let {
            try {
                return ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
            } catch (e: Exception) {
                LOG.info("Greide ikke å konvertere dato zt. Feil format: ${e.message}", e)
            }
        }
        return null
    }

    private fun zonedDatetimeToStr(zt: ZonedDateTime): String =
        zt.format(DateTimeFormatter.RFC_1123_DATE_TIME)

}