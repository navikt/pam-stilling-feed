package no.nav.pam.stilling.feed.dto

import java.sql.ResultSet
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

data class FeedItem(
    val uuid: UUID,
    val json: String,
    val sistEndret: ZonedDateTime,
    val status: String
) {
    companion object {
        fun fraDatabase(rs: ResultSet, prefix: String = "") = FeedItem(
            uuid = rs.getObject("${prefix}id") as UUID,
            json = rs.getString("${prefix}json"),
            sistEndret = rs.getTimestamp("${prefix}sist_endret").toInstant().atZone(ZoneId.of("Europe/Oslo")),
            status = rs.getString("${prefix}status"),
        )
    }
}

data class FeedPageItem(
    val id: UUID,
    val lastModified: ZonedDateTime = ZonedDateTime.now(), // NB: Denne er det ikke mulig å endre selv, lastModified settes i databasen
    val seqNo: Long, // Verdi blir ignorert og generert av databasen. Monotont stigende med mulige hull
    val status: String,
    val title: String,
    val businessName: String,
    val municipal: String,
    val feedItemId: UUID
) {
    companion object {
        fun fraDatabase(rs: ResultSet, prefix: String = "") = FeedPageItem(
            id = rs.getObject("${prefix}id") as UUID,
            lastModified = rs.getTimestamp("${prefix}sist_endret").toInstant().atZone(ZoneId.of("Europe/Oslo")),
            seqNo = rs.getLong("${prefix}seq_no"),
            status = rs.getString("${prefix}status"),
            title = rs.getString("${prefix}title"),
            businessName = rs.getString("${prefix}business_name"),
            municipal = rs.getString("${prefix}municipal"),
            feedItemId = rs.getObject("${prefix}feed_item_id") as UUID,
        )
    }
}
