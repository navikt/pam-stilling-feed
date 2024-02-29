package no.nav.pam.stilling.feed

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pam.stilling.feed.config.TxContext
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.*
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class FeedService(
    private val feedRepository: FeedRepository,
    private val txTemplate: TxTemplate,
    private val objectMapper: ObjectMapper,
    private val stillingUrlBase: String = "https://arbeidsplassen.nav.no/stillinger/stilling"
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(FeedService::class.java)
        private val urlPrefix = "/api/v1"
        private val SKAL_IGNORERE_FINN_ANNONSER = true
    }

    fun lagreNyStillingsAnnonseFraJson(jsonAnnonse: String, txContext: TxContext? = null) : Pair<FeedItem, FeedPageItem> {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val ad = objectMapper.readValue(jsonAnnonse, AdDTO::class.java)
            return@doInTransaction lagreNyStillingsAnnonse(ad, ctx)
        }!!
    }

    /**
     * NB: status==REJECTED gjelder også annonser som er merket som duplikater (tilsynelatende KUN duplikater?).
     * Skal disse maskeres, eller gjelder det kun annonser som er DELETED eller STOPPED?
     */
    private fun adSkalMaskeres(ad: AdDTO) = listOf("STOPPED", "DELETED", "REJECTED").contains(ad.status)

    fun lagreNyStillingsAnnonse(newAd: AdDTO, txContext: TxContext? = null) : Pair<FeedItem, FeedPageItem>? {
        val ad =
            if (!adSkalMaskeres(newAd)) newAd
            else newAd.copy(title = "...", contactList = mutableListOf(), employer = null, businessName = "")

        if (ad.publishedByAdmin == null) {
            LOG.info("Ignorerer annonse ${ad.uuid} siden den ikke er publisert ennå")
            return null
        }

        return txTemplate.doInTransaction(txContext) { ctx ->
            val feedAd = mapAd(ad, stillingUrlBase)
            val active = ad.status == "ACTIVE" && ad.source != "DIR"
            val statusDescription = if (active) "ACTIVE" else "INACTIVE"
            val feedJson = if (active) objectMapper.writeValueAsString(feedAd) else ""

            val feedItem = FeedItem(
                uuid = UUID.fromString(ad.uuid),
                json = feedJson,
                sistEndret = ad.updated.atZone(ZoneId.of("Europe/Oslo")),
                status = statusDescription,
                kilde = feedAd.source
            )
            feedRepository.lagreFeedItem(feedItem, ctx)

            val feedPageItem = FeedPageItem(
                id = UUID.randomUUID(),
                status = statusDescription,
                title = ad.title ?: "?",
                municipal = ad.location?.municipal ?: "?",
                businessName = ad.businessName ?: "?",
                feedItemId = feedItem.uuid,
                seqNo = -1
            )
            val lagretPageItem = feedRepository.lagreFeedPageItem(feedPageItem, feedAd.source, ctx)

            return@doInTransaction Pair(feedItem, lagretPageItem)
        }!!
    }

    fun fjernDIRFraFeed() {
        txTemplate.doInTransaction() { ctx ->
            val items = feedRepository.hentDirekteMeldteFeedItem() ?: mutableListOf()

            items.forEach { item ->
                feedRepository.lagreFeedItem(item.copy(status = "INACTIVE", sistEndret = ZonedDateTime.now(), json = ""), ctx)
                val feedPageItem = FeedPageItem(
                        id = UUID.randomUUID(),
                        status = "INACTIVE",
                        title = "?",
                        municipal = "?",
                        businessName = "?",
                        feedItemId = item.uuid,
                        seqNo = -1
                )
                val lagretPageItem = feedRepository.lagreFeedPageItem(feedPageItem, "DIR", ctx)
            }
        }
    }

    fun lagreNyeStillingsAnnonserFraJson(jsonAnnonser: List<String>, txContext: TxContext? = null) : List<Pair<FeedItem, FeedPageItem>> {
        return txTemplate.doInTransaction(txContext) { ctx ->
            // TODO Hvis vi trenger å optimalisere så er dette en åpenbar kandidat for å batche jdbc-kall
            val ads = jsonAnnonser.mapNotNull { objectMapper.readValue(it, AdDTO::class.java) }
            return@doInTransaction lagreNyeStillingsAnnonser(ads, ctx)
        }!!
    }

    fun lagreOppdaterteDetaljer(annonser: List<AdDTO>, txContext: TxContext? = null) = annonser.map { ad ->
        val annonse =
            if (!adSkalMaskeres(ad)) ad
            else ad.copy(title = "...", contactList = mutableListOf(), employer = null, businessName = "")

        return@map txTemplate.doInTransaction(txContext) { ctx ->
            val feedAd = mapAd(annonse, stillingUrlBase)
            val active = annonse.status == "ACTIVE"
            val feedJson = if (active) objectMapper.writeValueAsString(feedAd) else ""
            feedRepository.oppdaterFeedItemJson(UUID.fromString(annonse.uuid), feedJson, ctx)
        }
    }.filterNotNull().sum()

    // TODO Fjern denne etter gjennomkjøring, kun midlertidig for å migrere
    fun lagreKilde(annonser: List<AdDTO>, txContext: TxContext? = null) = annonser.map { ad ->
        val kilde = ad.source ?: return@map null

        return@map txTemplate.doInTransaction(txContext) { ctx ->
            feedRepository.oppdaterKildeForFeedItem(UUID.fromString(ad.uuid), kilde, ctx)
        }
    }.filterNotNull().sum()

    fun lagreNyeStillingsAnnonser(ads: List<AdDTO>, txContext: TxContext? = null): List<Pair<FeedItem, FeedPageItem>> {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val items = ads.map { ad ->
                lagreNyStillingsAnnonse(ad, txContext)
            }.filterNotNull().toList()
            return@doInTransaction items
        }!!
    }

    fun hentStillingsAnnonse(uuid: UUID, txContext: TxContext? = null): FeedItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            return@doInTransaction feedRepository.hentFeedItem(uuid, SKAL_IGNORERE_FINN_ANNONSER, ctx)
        }
    }

    fun hentFeedHvis(
        id: UUID,
        etag: String? = null,
        sistEndret: ZonedDateTime? = null,
        antall: Int = Feed.defaultPageSize,
        txContext: TxContext? = null
    ): Feed? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val førsteItem = feedRepository.hentFeedPageItem(id, SKAL_IGNORERE_FINN_ANNONSER)

            førsteItem?.let { f ->
                val items = feedRepository.hentFeedPageItemsNyereEnn(f.seqNo, sistEndret = sistEndret, antall = antall, skalIgnorereFinn = SKAL_IGNORERE_FINN_ANNONSER)
                val sisteItem = if (items.size > 0) items[items.size - 1] else førsteItem
                val sisteItemIPage = if (items.size > 1) items[items.size - 2] else sisteItem

                if (etag != null && sistEndret != null &&
                    sisteItemIPage.id.toString() == etag && sisteItemIPage.lastModified.isBefore(sistEndret)) {
                    // Det har ikke vært endringer siden etag og sistEndret
                    return@doInTransaction Feed.emptyFeed
                }

                val feedLines = mutableListOf<FeedLine>()
                feedLines.add(FeedLine.fraFeedPageItem(f, urlPrefix))
                val n: Int = if (sisteItemIPage.id == sisteItem.id) 0 else 1
                items.dropLast(n).forEach { feedLines.add(FeedLine.fraFeedPageItem(it)) }

                val nextId = if (sisteItem.id == sisteItemIPage.id) null else sisteItemIPage.id
                return@doInTransaction Feed(
                    id = førsteItem.id,
                    etag = sisteItemIPage.id.toString(),
                    lastModified = sisteItemIPage.lastModified,
                    next_id = nextId,
                    next_url = nextId?.let { "$urlPrefix/feed/$it" },
                    feed_url = "$urlPrefix/feed/${førsteItem.id}",
                    items = feedLines
                )
            }
            return@doInTransaction null
        }
    }

    fun hentFørsteSide(txContext: TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            return@doInTransaction feedRepository.hentFørsteSide(SKAL_IGNORERE_FINN_ANNONSER, ctx)
        }
    }

    fun hentSisteSide(txContext: TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            return@doInTransaction feedRepository.hentSisteSide(SKAL_IGNORERE_FINN_ANNONSER, ctx)
        }
    }

    fun hentFørsteSideNyereEnn(cutoff: ZonedDateTime, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            feedRepository.hentFørsteSideNyereEnn(cutoff, SKAL_IGNORERE_FINN_ANNONSER, ctx)
        }
}
