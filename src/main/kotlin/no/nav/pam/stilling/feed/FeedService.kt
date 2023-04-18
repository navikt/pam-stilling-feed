package no.nav.pam.stilling.feed

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pam.stilling.feed.config.TxContext
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.*
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class FeedService(private val feedRepository: FeedRepository,
                  private val txTemplate: TxTemplate,
                  private val objectMapper: ObjectMapper) {
    companion object {
        private val LOG = LoggerFactory.getLogger(FeedService::class.java)

        private val urlPrefix = "/api/v1"
    }

    fun lagreNyStillingsAnnonseFraJson(jsonAnnonse: String, txContext: TxContext? = null) : Pair<FeedItem, FeedPageItem> {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val ad = objectMapper.readValue(jsonAnnonse, AdDTO::class.java)
            return@doInTransaction lagreNyStillingsAnnonse(ad, ctx)
        }!!
    }

    fun lagreNyStillingsAnnonse(ad: AdDTO, txContext: TxContext? = null) : Pair<FeedItem, FeedPageItem>? {
        if (ad.source == "FINN") {
            LOG.info("Ignorerer annonse $ad.uuid siden det er en finn annonse")
            return null
        } else if (ad.publishedByAdmin == null) {
            LOG.info("Ignorerer annonse $ad.uuid siden den ikke er publisert ennå")
            return null
        }

        return txTemplate.doInTransaction(txContext) { ctx ->
            val feedAd = mapAd(ad, "localhost")
            val active = ad.status == "ACTIVE"
            val feedJson = if (active) objectMapper.writeValueAsString(feedAd) else ""

            val feedItem = FeedItem(uuid = UUID.fromString(ad.uuid),
                json = feedJson,
                sistEndret = ad.updated.atZone(ZoneId.of("Europe/Oslo")))
            feedRepository.lagreFeedItem(feedItem, ctx)

            val feedPageItem = FeedPageItem(
                id = UUID.randomUUID(),
                status = if (active) "ACTIVE" else "INACTIVE",
                title = ad.title ?: "?",
                municipal = ad.location?.municipal ?: "?",
                businessName = ad.businessName ?: "?",
                feedItemId = feedItem.uuid,
                seqNo = -1
            )
            val lagretPageItem = feedRepository.lagreFeedPageItem(feedPageItem, ctx)

            return@doInTransaction Pair(feedItem, lagretPageItem)
        }!!
    }
    fun lagreNyeStillingsAnnonserFraJson(jsonAnnonser: List<String>, txContext: TxContext? = null) : List<Pair<FeedItem, FeedPageItem>> {
        return txTemplate.doInTransaction(txContext) { ctx ->
            // TODO Hvis vi trenger å optimalisere så er dette en åpenbar kandidat for å batche jdbc-kall
            val ads = jsonAnnonser.mapNotNull { objectMapper.readValue(it, AdDTO::class.java) }
            return@doInTransaction lagreNyeStillingsAnnonser(ads, ctx)
        }!!
    }
    fun lagreNyeStillingsAnnonser(ads: List<AdDTO>, txContext: TxContext? = null) : List<Pair<FeedItem, FeedPageItem>> {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val items = ads.map { ad ->
                lagreNyStillingsAnnonse(ad, txContext)
            }.filterNotNull().toList()
            return@doInTransaction items
        }!!
    }

    fun hentStillingsAnnonse(uuid: UUID, txContext: TxContext? = null) : FeedItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val feedItem = feedRepository.hentFeedItem(uuid, ctx)

            return@doInTransaction feedItem
        }
    }

    fun hentFeedHvis(id: UUID, etag: String? = null, sistEndret: ZonedDateTime? = null, txContext : TxContext? = null): Feed? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val førsteItem = feedRepository.hentFeedPageItem(id)
            førsteItem?.let { f ->
                val items = feedRepository.hentFeedPageItemsNyereEnn(f.seqNo)
                val sisteItem = if (items.size > 0) items[items.size-1] else førsteItem
                val sisteItemIPage = if (items.size > 1) items[items.size-2] else sisteItem
                if (etag !=  null && sistEndret != null &&
                    sisteItemIPage.id.toString() == etag && sisteItemIPage.lastModified.isBefore(sistEndret)) {
                    // Det har ikke vært endringer siden etag og sistEndret
                    return@doInTransaction Feed.emptyFeed
                }

                val feedLines = mutableListOf<FeedLine>()
                feedLines.add(FeedLine.fraFeedPageItem(f, urlPrefix))
                val n : Int = if (sisteItemIPage.id == sisteItem.id) 0 else 1
                items.dropLast(n).forEach { feedLines.add( FeedLine.fraFeedPageItem(it)) }

                val nextId = if (sisteItem.id == sisteItemIPage.id) null else sisteItemIPage.id
                return@doInTransaction Feed(id = førsteItem.id,
                    etag = sisteItemIPage.id.toString(),
                    lastModified = sisteItemIPage.lastModified,
                    next_id = nextId,
                    next_url = "$urlPrefix/feed/$nextId",
                    feed_url = "$urlPrefix/feed/${førsteItem.id.toString()}",
                    items = feedLines
                )
            }
            return@doInTransaction null
        }
    }
    fun hentFørsteSide(txContext : TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            return@doInTransaction feedRepository.hentFørsteSide(ctx)
        }
    }
    fun hentSisteSide(txContext : TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            return@doInTransaction feedRepository.hentSisteSide(ctx)
        }
    }
}