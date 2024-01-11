package no.nav.pam.stilling.feed.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pam.stilling.feed.objectMapper
import no.nav.pam.yrkeskategorimapper.StyrkCodeConverter
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

data class FeedAd(
    val uuid: String,
    val published: ZonedDateTime,
    val expires: ZonedDateTime,
    val updated: ZonedDateTime,
    val workLocations: List<FeedLocation>,
    val contactList: List<FeedContact>,
    val title: String,
    val description: String?,
    val sourceurl: String?,
    val source: String?,
    val applicationUrl: String?,
    val applicationDue: String?,
    val occupationCategories: List<FeedOccupation>,
    val categoryList: List<FeedCategory>,
    val jobtitle: String?,
    val link: String,
    val employer: FeedEmployer,
    val engagementtype: String?,
    val extent: String?,
    val starttime: String?,
    val positioncount: String?,
    val sector: String?
)

data class FeedLocation(
    val country: String?,
    val address: String?,
    val city: String?,
    val postalCode: String?,
    val county: String?,
    val municipal: String?
)

data class FeedContact(
    val name: String?,
    val email: String?,
    val phone: String?,
    val role: String?,
    val title: String?
)

data class FeedEmployer(
    val name: String,
    val orgnr: String?,
    val description: String?,
    val homepage: String?
)

data class FeedOccupation(
    val level1: String,
    val level2: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FeedCategory(
    var categoryType: String, // STYRK08, ESCO, JANZZ
    var code: String, // styrk kode,
    var name: String,
    var description: String? = null,
    var score: Double = 0.0
)

private fun toZonedDateTime(ldt: LocalDateTime?, default: LocalDateTime? = null): ZonedDateTime? {
    if (ldt == null && default == null)
        return null
    else if (ldt == null)
        return toZonedDateTime(default, null)
    else
        return ldt.atZone(ZoneId.of("Europe/Oslo"))
}

fun mapAd(source: AdDTO, stillingUrlBase: String?): FeedAd {
    // TODO Burde vi her ha en link til arbeidsplassen?
    // https://arbeidsplassen.nav.no/stillinger/stilling/
    // eller
    // https://arbeidsplassen.dev.nav.no/stillinger/stilling/
    val link = "$stillingUrlBase/${source.uuid}"

    return FeedAd(
        uuid = source.uuid,
        published = toZonedDateTime(source.published)!!,
        expires = toZonedDateTime(source.expires)!!,
        updated = toZonedDateTime(source.updated)!!,
        workLocations = source.locationList.map { l -> mapLocation(l) },
        contactList = source.contactList?.map { c -> mapContact(c) } ?: emptyList(),
        title = source.title ?: "",
        description = source.properties["adtext"] ?: "",
        sourceurl = source.properties["sourceurl"] ?: "",
        source = source.source,
        applicationUrl = source.properties["applicationurl"] ?: "",
        applicationDue = source.properties["applicationdue"] ?: "",
        // TODO her må vi legge på noe annet, f.eks JANZZ konsept eller STYRK
        occupationCategories = mapOccupations(source.categoryList),
        categoryList = mapCategories(source),
        jobtitle = source.properties["jobtitle"] ?: "",
        link = link,
        employer = mapEmployer(source),
        engagementtype = source.properties["engagementtype"] ?: "",
        extent = source.properties["extent"] ?: "",
        starttime = source.properties["starttime"] ?: "",
        positioncount = source.properties["positioncount"] ?: "1",
        sector = source.properties["sector"] ?: ""
    )
}

// Mapper fra STYRK til PYRK
// TODO Skal vi ha med PYRK i ny feed? Er ikke det kun for stillingssøket i elastic
// Hvis vi ikke skal ha det så må vi fjerne avhengigheten til mapperen i gradle
private fun mapOccupations(categories: List<CategoryDTO>): List<FeedOccupation> {
    val pyrkMapper = StyrkCodeConverter() // NB: Dette bør ikke gjøres for hver annonse side det leser inn ressurser hver gang...
    val occupations = categories
        .asSequence()
        .filter { c -> "STYRK08NAV" == c.categoryType || "STYRK08" == c.categoryType }
        .mapNotNull { c -> pyrkMapper.lookup(c.code).orElse(null) }
        .filter { o -> "0" != o.styrkCode }
        .distinct()
        .map { o -> FeedOccupation(o.categoryLevel1, o.categoryLevel2) }
        .sortedBy { it.level1 }
        .toList()
    return occupations
}

/**
 * Dette er *IKKE* bra:
 * Her får vi med alle kategoriene fra janzz classifier og må lete litt her og der for å finne en slags score
 */
private fun mapCategories(ad: AdDTO): List<FeedCategory> {
    val scores = mutableMapOf<String, Double>()
    val styrkScore = ad.properties.getOrDefault("classification_styrk08_score", "0.0")
    val styrkCode = ad.properties.get("classification_styrk08_code")
    styrkCode?.let { s -> scores["STYRK08:$s"] = styrkScore.toDouble() }
    val escoCode = ad.properties.get("classification_esco_code")
    // Vi tar ikke med esco score fra pam-ad... Bruk styrk siden den bør være ganske lik
    escoCode?.let { e -> scores["ESCO:$e"] = styrkScore.toDouble() }

    // Hent STYRK scores fra searchtags, dette er en gedigen omvei
    ad.properties.get("searchtags")?.let { objectMapper.readValue(it, object : TypeReference<List<SearchTag>>() {}) }
        ?.forEach { t -> scores["STYRK08:${t.label}"] = t.score }

    val cats = ad.categoryList
        .asSequence()
        .filter { c -> c.categoryType != null && c.code != null }
        .mapNotNull { c ->
            FeedCategory(
                categoryType = c.categoryType,
                code = c.code,
                name = c.name,
                description = c.description ?: "",
                score = if (c.score != null && c.score > 0.0) c.score
                    else scores["${c.categoryType}:${c.code}"] ?: scores["${c.categoryType}:${c.name}"] ?: 0.0
            )
        }
        .sortedBy { it.categoryType }
        .distinctBy { "${it.categoryType}:${it.code}" }
        .toList()
    return cats
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchTag(var label: String, val score: Double)

fun mapEmployer(source: AdDTO): FeedEmployer {
    return FeedEmployer(
        source.businessName ?: source.employer.let { e -> e?.name ?: "" },
        source.employer.let { e -> e?.orgnr },
        source.properties["employerdescription"] ?: "",
        source.properties["employerhomepage"] ?: ""
    )
}

fun mapLocation(sourceLocation: LocationDTO): FeedLocation {
    return FeedLocation(
        sourceLocation.country,
        sourceLocation.address,
        sourceLocation.city,
        sourceLocation.postalCode,
        sourceLocation.county,
        sourceLocation.municipal
    )
}

fun mapContact(sourceContact: ContactDTO) =
    FeedContact(sourceContact.name, sourceContact.email, sourceContact.phone, sourceContact.role, sourceContact.title)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Feed(
    val version: String = "1.0",
    val title: String = "Stillingsfeeden fra arbeidsplassen.no",
    val home_page_url: String = "https://arbeidsplassen.nav.no",
    val feed_url: String = "https://arbeidsplassen.nav.no/stillinger-feed",
    val description: String = "Feed med stillinger fra arbeidsplassen.no - En av Norges største oversikter over utlyste stillinger",
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val next_url: String?,
    @JsonIgnore
    val lastModified: ZonedDateTime = ZonedDateTime.now(),
    @JsonIgnore
    val etag: String = "",
    val id: UUID = UUID.randomUUID(),
    @JsonInclude(JsonInclude.Include.ALWAYS)
    val next_id: UUID? = null,
    val items: MutableList<FeedLine> = mutableListOf()
) {
    companion object {
        const val defaultPageSize: Int = 10
        val emptyFeed = Feed(
            next_url = "",
            lastModified = ZonedDateTime.now(),
            etag = "",
            items = mutableListOf()
        )
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FeedLine(
    val id: String,
    val url: String, // Url til FeedItem
    val title: String,
    val content_text: String = "Stillingsannonse",
    val date_modified: ZonedDateTime?, // kanskje ikke lurt å ha med dette?
    @JsonProperty("_feed_entry")
    val feed_entry: FeedEntry
) {
    companion object {
        fun fraFeedPageItem(feedPageItem: FeedPageItem, urlPrefix: String? = "/api/v1") =
            FeedLine(
                id = feedPageItem.feedItemId.toString(),
                url = "$urlPrefix/feedentry/${feedPageItem.feedItemId}",
                title = feedPageItem.title,
                date_modified = feedPageItem.lastModified,
                feed_entry = FeedEntry(
                    uuid = feedPageItem.feedItemId.toString(),
                    status = feedPageItem.status,
                    title = feedPageItem.title,
                    businessName = feedPageItem.businessName,
                    municipal = feedPageItem.municipal,
                    sistEndret = feedPageItem.lastModified // TODO Er dette riktig? Det er vel ikke det?
                )
            )
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FeedEntry(
    val uuid: String,
    val status: String,
    val title: String,
    val businessName: String,
    val municipal: String,
    val sistEndret: ZonedDateTime
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FeedEntryContent(
    val uuid: UUID,
    @JsonProperty("ad_content")
    val json: FeedAd?,
    val sistEndret: ZonedDateTime,
    val status: String
) {
    companion object {
        fun fraFeedItem(item: FeedItem, objectMapper: ObjectMapper) = FeedEntryContent(
            uuid = item.uuid,
            json = if (item.json.isBlank()) null else objectMapper.readValue(item.json, FeedAd::class.java),
            sistEndret = item.sistEndret,
            status = item.status,
        )
    }
}
