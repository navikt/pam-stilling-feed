package no.nav.pam.stilling.feed.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
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
    val title: String,
    val description: String?,
    val sourceurl: String?,
    val source: String?,
    val applicationUrl: String?,
    val applicationDue: String?,
    val occupationCategories: List<FeedOccupation>,
    val jobtitle: String?,
    val link: String,
    val employer: FeedEmployer,
    val engagementtype: String?,
    val extent: String?,
    val starttime: String?,
    val positioncount: String?,
    val sector: String?)

data class FeedLocation(
    val country: String?,
    val address: String?,
    val city: String?,
    val postalCode: String?,
    val county: String?,
    val municipal: String?)

data class FeedEmployer(
    val name: String,
    val orgnr: String?,
    val description: String?,
    val homepage: String?)

data class FeedOccupation(val level1: String,
                          val level2: String)


private fun toZonedDateTime(ldt: LocalDateTime?, default: LocalDateTime? = null) : ZonedDateTime? {
    if (ldt == null && default == null)
        return null
    else if (ldt == null)
        return toZonedDateTime(default, null)
    else
        return ldt.atZone(ZoneId.of("Europe/Oslo"))
}

fun mapAd(source: AdDTO, host: String?): FeedAd {
    val link = "https://$host/stillinger/stilling/${source.uuid}"

    return FeedAd(
        uuid = source.uuid,
        published = toZonedDateTime(source.published)!!,
        expires = toZonedDateTime(source.expires)!!,
        updated = toZonedDateTime(source.updated)!!,
        workLocations = source.locationList.map { l -> mapLocation(l) },
        title = source.title ?: "",
        description = source.properties["adtext"],
        sourceurl = source.properties["sourceurl"],
        source = source.source,
        applicationUrl = source.properties["applicationurl"],
        applicationDue = source.properties["applicationdue"],
        // TODO her må vi legge på noe annet, f.eks JANZZ konsept eller STYRK
        occupationCategories = emptyList<FeedOccupation>(),//source.categoryList.map { FeedOccupation(it.level1, it.level2) },
        jobtitle = source.properties["jobtitle"],
        link = link,
        employer = mapEmployer(source),
        engagementtype = source.properties["engagementtype"],
        extent = source.properties["extent"],
        starttime = source.properties["starttime"],
        positioncount = source.properties["positioncount"],
        sector = source.properties["sector"]
    )
}

fun mapEmployer(source: AdDTO): FeedEmployer {
    return FeedEmployer(
        source.businessName ?: source.employer.let { e -> e?.name ?: "" },
        source.employer.let { e -> e?.orgnr },
        source.properties["employerdescription"],
        source.properties["employerhomepage"]
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


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Feed(val version: String = "1.0",
                val title: String = "Arbeidplassen Stillinger", // TODO her må det da finnes noe bedre
                val home_page_url: String = "https://arbeidsplassen.nav.no/stillinger-feed",
                val feed_url: String = "https://arbeidsplassen.nav.no/stillinger-feed", // self
                val description: String = "Dette er spennende greier",
                val next_url: String,
                @JsonIgnore
                val lastModified: ZonedDateTime = ZonedDateTime.now(),
                @JsonIgnore
                val etag: String = "",
                val id: UUID = UUID.randomUUID(),
                val next_id: UUID? = null,
                val items: MutableList<FeedLine> = mutableListOf()
) {
    companion object {
        val pageSize: Int = 5
        val emptyFeed = Feed(
            next_url = "",
            lastModified = ZonedDateTime.now(),
            etag = "",
            items = mutableListOf<FeedLine>()
        )
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FeedLine(val id: String,
                    val url: String, // Url til FeedItem
                    val title: String,
                    val content_text: String = "Stillingsannonse",
                    //val date_published: ZonedDateTime?, // kanskje ikke lurt å ha med dette?
                    val date_modified: ZonedDateTime?, // kanskje ikke lurt å ha med dette?
                    @JsonProperty("_feed_entry")
                    val feed_entry: FeedEntry
                    ) {
    companion object {
        fun fraFeedPageItem(feedPageItem: FeedPageItem, urlPrefix: String? = "/api/v1") =
            FeedLine(
                id = feedPageItem.feedItemId.toString(),
                url = "$urlPrefix/feed/${feedPageItem.feedItemId.toString()}",
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
data class FeedEntry(val uuid: String,
                     val status: String,
                     val title: String,
                     val businessName: String,
                     val municipal: String,
                     val sistEndret: ZonedDateTime)
