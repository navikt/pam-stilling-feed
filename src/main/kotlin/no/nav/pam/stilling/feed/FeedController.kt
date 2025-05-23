package no.nav.pam.stilling.feed

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Javalin
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.openapi.*
import no.nav.pam.stilling.feed.dto.Feed
import no.nav.pam.stilling.feed.dto.FeedEntryContent
import no.nav.pam.stilling.feed.sikkerhet.Rolle
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class FeedController(private val feedService: FeedService, private val objectMapper: ObjectMapper) {
    companion object {
        private val LOG = LoggerFactory.getLogger(FeedRepository::class.java)
        const val defaultOutboundPageSize: Int = 1000
        const val MAX_PAGE_SIZE = 10000
    }

    private fun hentKonsumentId() = MDC.get(KONSUMENT_ID_MDC_KEY)

    fun setupRoutes(javalin: Javalin) {
        javalin.get(
            "/api/v1/feed/{feed_page_id}",
            { ctx -> hentFeed(ctx, ctx.pathParam("feed_page_id")) },
            Rolle.KONSUMENT
        )
        javalin.get(
            "/api/v1/feed",
            { ctx -> hentFeed(ctx) },
            Rolle.KONSUMENT
        )
        javalin.get(
            "/api/v1/feedentry/{entry_id}",
            { ctx -> hentFeedItem(ctx, ctx.pathParam("entry_id")) },
            Rolle.KONSUMENT
        )
    }

    @OpenApi(
        path = "/api/v1/feed",
        tags = ["Feed"],
        methods = [HttpMethod.GET],
        operationId = "feed",
        description = "Returns first page of the feed by default, or last if query param 'last' is provided",
        security = [OpenApiSecurity(name = "BearerAuth")],
        queryParams = [OpenApiParam(name="last", description = "Flag for requesting the last (newest) page of the feed", required = false, allowEmptyValue = true)],
        headers = [
            OpenApiParam(name = "If-None-Match", description = "Entity tag - Specific version of a resource. Provided by response header ETag", required = false, allowEmptyValue = true, type = UUID::class),
            OpenApiParam(name = "If-Modified-Since", description = "Last-modified datetime in RFC 1123 format. Provided by response header Last-Modified", required = false, allowEmptyValue = true, type = String::class, example = "Thu, 31 Dec 1992 11:56:00 +0200")
        ],
        responses = [
            OpenApiResponse(status = "200", description = "Feed page", content = [OpenApiContent(from = Feed::class)]),
            OpenApiResponse(status = "304", description = "Feed page is empty, or its contents have not changed since last time"),
            OpenApiResponse(status = "400", description = "Invalid input in the request"),
            OpenApiResponse(status = "404", description = "Could not find page"),
        ]
    )
    fun hentFeed(ctx: Context) {
        val sisteSide = ctx.queryParam("last")
        val ifModifiedSince = strToZonedDateTime(ctx.header("If-Modified-Since"))

        val feedPageItem =
            if (sisteSide != null) feedService.hentSisteSide()
            else if (ifModifiedSince != null) feedService.hentFørsteSideNyereEnn(ifModifiedSince)
            else feedService.hentFørsteSide()

        if (feedPageItem == null) {
            LOG.warn("Kunne ikke finne feedPageItem - Konsument: ${hentKonsumentId()}")
            ctx.status(404)
        } else
            hentFeed(ctx, feedPageItem.id.toString())
    }

    private fun toInt(s: String?, defaultValue: Int): Int = s?.toIntOrNull() ?: defaultValue

    @OpenApi(
        path = "/api/v1/feed/{feedPageId}",
        tags = ["Feed"],
        methods = [HttpMethod.GET],
        operationId = "feed/{feedPageId}",
        description = "Returns the specified feed page, or 304 response if the page has not changed since last time (based on If-None-Match and If-Modified-Since headers)",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name="feedPageId", description = "A unique ID for the feed page", required = true, allowEmptyValue = false, type = UUID::class)],
        headers = [
            OpenApiParam(name = "If-None-Match", description = "Entity tag. Specific version of a resource. Provided by response header ETag", required = false, allowEmptyValue = true, type = UUID::class),
            OpenApiParam(name = "If-Modified-Since", description = "Last-modified datetime in RFC 1123 format. Provided by response header Last-Modified", required = false, allowEmptyValue = true, type = String::class, example = "Thu, 31 Dec 1992 11:56:00 +0200")
        ],
        responses = [
            OpenApiResponse(status = "200", description = "The specified feed page", content = [OpenApiContent(from = Feed::class)]),
            OpenApiResponse(status = "304", description = "Feed page is empty, or its contents have not changed since last time"),
            OpenApiResponse(status = "400", description = "Invalid input in the request"),
            OpenApiResponse(status = "404", description = "Could not find page"),
        ]
    )
    fun hentFeed(ctx: Context, feedPageId: String) {
        val pageSize = toInt(ctx.queryParam("pageSize"), defaultOutboundPageSize)
        if (pageSize > MAX_PAGE_SIZE) {
            ctx.status(HttpStatus.BAD_REQUEST.code)
            ctx.contentType(ContentType.TEXT_PLAIN)
            ctx.result("pageSize must be less than or equal to 10000")
            LOG.warn("Mottok ugyldig kall fra ${hentKonsumentId()}, det ble spurt om en for stor pageSize: $pageSize")
            return
        }

        LOG.info("Henter feed - Konsument: ${hentKonsumentId()} - Side: $feedPageId")
        val etag = ctx.header("If-None-Match")
        val ifModifiedSinceStr = ctx.header("If-Modified-Since")

        val feed = feedService.hentFeedHvis(
            id = UUID.fromString(feedPageId),
            etag = etag,
            antall = pageSize,
            sistEndret = strToZonedDateTime(ifModifiedSinceStr)
        )

        if (feed == null) {
            LOG.warn("Kunne ikke finne side - Konsument: ${hentKonsumentId()} - Side: $feedPageId")
            ctx.status(404)
        } else if (feed.items.isEmpty()) {
            LOG.warn("Side er tom - Konsument: ${hentKonsumentId()} - Side: $feedPageId")
            ctx.status(304)
        } else {
            ctx.header("ETag", feed.etag)
            ctx.header("Last-Modified", zonedDatetimeToStr(feed.lastModified))
            ctx.json(feed)
        }
    }

    @OpenApi(
        path = "/api/v1/feedentry/{entryId}",
        tags = ["Feed"],
        methods = [HttpMethod.GET],
        operationId = "feedentry/{entryId}",
        description = "Returns details for the specified feed entry",
        security = [OpenApiSecurity(name = "BearerAuth")],
        pathParams = [OpenApiParam(name="entryId", description = "A unique ID for the feed entry", required = true, allowEmptyValue = false, type = UUID::class)],
        responses = [
            OpenApiResponse(status = "200", description = "The specified feed entry", content = [OpenApiContent(from = FeedEntryContent::class)]),
            OpenApiResponse(status = "404", description = "Could not find feed entry"),
        ]
    )
    fun hentFeedItem(ctx: Context, feedEntryId: String) {
        LOG.info("Henter feed item - Konsument: ${hentKonsumentId()} - feedEntryId: $feedEntryId")

        val feed = feedService.hentStillingsAnnonse(UUID.fromString(feedEntryId))

        if (feed == null) {
            LOG.warn("Kunne ikke finne feedItem - Konsument: ${hentKonsumentId()} - feedEntryId: $feedEntryId")
            ctx.status(404)
        } else {
            ctx.json(FeedEntryContent.fraFeedItem(feed, objectMapper))
        }
    }

    private fun strToZonedDateTime(zt: String?): ZonedDateTime? {
        zt?.let {
            try {
                return ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
            } catch (e: Exception) {
                LOG.info("Greide ikke å konvertere dato zt. Feil format: ${e.message}", e)
            }
        }
        return null
    }

    private fun zonedDatetimeToStr(zt: ZonedDateTime): String = zt.format(DateTimeFormatter.RFC_1123_DATE_TIME)
}
