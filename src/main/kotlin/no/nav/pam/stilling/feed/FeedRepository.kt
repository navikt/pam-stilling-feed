package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.PSTMTUtil
import no.nav.pam.stilling.feed.config.TxContext
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.Feed
import no.nav.pam.stilling.feed.dto.FeedItem
import no.nav.pam.stilling.feed.dto.FeedPageItem
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class FeedRepository(private val txTemplate: TxTemplate) {
    fun lagreFeedItem(feedItem: FeedItem, txContext: TxContext? = null): Int? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val sql = """
                insert into feed_item(id, json, sist_endret, status, kilde)
                values(?, ?, ?, ?, ?)
                on conflict(id) do update
                set json = EXCLUDED.json,
                    sist_endret = EXCLUDED.sist_endret,
                    status = EXCLUDED.status,
                    kilde = EXCLUDED.kilde
            """.trimIndent()
            val c = ctx.connection()
            var numRows = 0
            c.prepareStatement(sql).apply {
                this.setObject(1, feedItem.uuid)
                this.setString(2, feedItem.json)
                this.setTimestamp(3, Timestamp(feedItem.sistEndret.toInstant().toEpochMilli()))
                this.setString(4, feedItem.status)
                this.setString(5, feedItem.kilde)
            }.use { statement ->
                numRows = statement.executeUpdate()
            }
            return@doInTransaction numRows
        }
    }

    fun oppdaterFeedItemJson(id: UUID, oppdatertJson: String, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            return@doInTransaction ctx.connection().prepareStatement("UPDATE feed_item SET json = ? WHERE id = ?")
                .apply {
                    this.setString(1, oppdatertJson)
                    this.setObject(2, id)
                }.executeUpdate()
        } ?: 0

    fun oppdaterKildeForFeedItem(id: UUID, kilde: String, txContext: TxContext? = null) = txTemplate.doInTransaction(txContext) { ctx ->
        val connection = ctx.connection()
        var updated = 0

        updated += connection.prepareStatement("UPDATE feed_item SET kilde = ? WHERE id = ?").apply {
            this.setString(1, kilde)
            this.setObject(2, id)
        }.executeUpdate()

        updated += connection.prepareStatement("UPDATE feed_page_item SET kilde = ? WHERE feed_item_id = ?").apply {
            this.setString(1, kilde)
            this.setObject(2, id)
        }.executeUpdate()

        return@doInTransaction updated
    } ?: 0

    fun hentFeedItem(id: UUID, skalIgnorereFinn: Boolean, txContext: TxContext? = null): FeedItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val ignorerFinnClause = if (skalIgnorereFinn) " and kilde != 'FINN'" else ""
            val sql = """
                select id, json, sist_endret, status, kilde
                from feed_item
                where id = ? $ignorerFinnClause
            """.trimIndent()
            val c = ctx.connection()
            c.prepareStatement(sql).apply {
                this.setObject(1, id)
            }.use { statement ->
                val resultSet = statement.executeQuery()
                if (!resultSet.next())
                    return@doInTransaction null
                return@doInTransaction FeedItem.fraDatabase(resultSet)
            }
        }
    }

    fun lagreFeedPageItem(feedPage: FeedPageItem, kilde: String?, txContext: TxContext? = null): FeedPageItem {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val sql = """
                insert into feed_page_item(id, status, title, business_name, municipal, feed_item_id, kilde)
                values(?, ?, ?, ?, ?, ?, ?)
                returning sist_endret, seq_no
            """.trimIndent()
            val c = ctx.connection()

            c.prepareStatement(sql).apply {
                this.setObject(1, feedPage.id)
                this.setString(2, feedPage.status)
                this.setString(3, feedPage.title)
                this.setString(4, feedPage.businessName)
                this.setString(5, feedPage.municipal)
                this.setObject(6, feedPage.feedItemId)
                this.setString(7, kilde)
            }.use { statement ->
                val rs = statement.executeQuery()
                if (rs.next()) {
                    val lastModified = rs.getTimestamp(1).toInstant().atZone(ZoneId.of("Europe/Oslo"))
                    val seqNo = rs.getLong(2)
                    return@doInTransaction feedPage.copy(lastModified = lastModified, seqNo = seqNo)
                }
            }
            return@doInTransaction feedPage
        }!!
    }

    fun hentFeedPageItem(id: UUID, skalIgnorereFinn: Boolean, txContext: TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val ignorerFinnClause = if (skalIgnorereFinn) " and kilde != 'FINN'" else ""
            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where id = ? $ignorerFinnClause
            """.trimIndent()
            val c = ctx.connection()

            c.prepareStatement(sql).apply {
                this.setObject(1, id)
            }.use { statement ->
                val resultSet = statement.executeQuery()
                if (!resultSet.next())
                    return@doInTransaction null
                return@doInTransaction FeedPageItem.fraDatabase(resultSet)
            }
        }
    }

    fun hentFeedPageItemsNyereEnn(
        seqNo: Long, antall: Int = Feed.defaultPageSize,
        sistEndret: ZonedDateTime? = null,
        skalIgnorereFinn: Boolean,
        txContext: TxContext? = null
    ): MutableList<FeedPageItem> {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val feedPageItems = mutableListOf<FeedPageItem>()
            // NB: Dette garanterer ikke at vi har monotont stigende sist_endret...
            var sistEndretClause = ""
            val ignorerFinnClause = if (skalIgnorereFinn) " and kilde != 'FINN'" else ""

            val params = mutableMapOf<String, (pstmt: PreparedStatement, pos: Int) -> Unit>(
                Pair(":seq_no:") { pstmt, pos -> pstmt.setObject(pos, seqNo) },
                Pair(":antall:") { pstmt, pos -> pstmt.setInt(pos, antall) }
            )
            sistEndret?.let { s ->
                sistEndretClause = " and sist_endret >= :sist_endret: "

                params[":sist_endret:"] = { pstmt, pos -> pstmt.setTimestamp(pos, Timestamp(s.toInstant().toEpochMilli())) }
            }

            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where seq_no > :seq_no: $sistEndretClause $ignorerFinnClause
                order by seq_no
                limit :antall:
            """.trimIndent()
            val c = ctx.connection()
            PSTMTUtil.prepareStatement(c, sql, params).use { statement ->
                val resultSet = statement.executeQuery()
                while (resultSet.next())
                    feedPageItems.add(FeedPageItem.fraDatabase(resultSet))
                return@doInTransaction feedPageItems
            }
        }!!
    }

    fun hentFørsteSide(skalIgnorereFinn: Boolean, txContext: TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val ignorerFinnClause = if (skalIgnorereFinn) " and kilde != 'FINN'" else ""

            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where seq_no in (select min(f2.seq_no) from feed_page_item f2) $ignorerFinnClause
            """.trimIndent()
            val c = ctx.connection()
            c.prepareStatement(sql).use { statement ->
                val resultSet = statement.executeQuery()
                if (!resultSet.next()) return@doInTransaction null
                return@doInTransaction FeedPageItem.fraDatabase(resultSet)
            }
        }
    }

    fun hentSisteSide(skalIgnorereFinn: Boolean, txContext: TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val ignorerFinnClause = if (skalIgnorereFinn) " and kilde != 'FINN'" else ""

            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where seq_no in (select max(f2.seq_no) from feed_page_item f2) $ignorerFinnClause
            """.trimIndent()
            val c = ctx.connection()
            c.prepareStatement(sql).use { statement ->
                val resultSet = statement.executeQuery()
                if (!resultSet.next()) return@doInTransaction null
                return@doInTransaction FeedPageItem.fraDatabase(resultSet)
            }
        }
    }

    fun hentFørsteSideNyereEnn(cutoff: ZonedDateTime, skalIgnorereFinn: Boolean, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            val ignorerFinnClause = if (skalIgnorereFinn) " and kilde != 'FINN'" else ""

            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where seq_no in (select min(f2.seq_no) from feed_page_item f2 where sist_endret > ?) $ignorerFinnClause
            """.trimIndent()

            ctx.connection()
                .prepareStatement(sql)
                .apply { setTimestamp(1, Timestamp(cutoff.toInstant().toEpochMilli())) }
                .executeQuery()
                .takeIf { it.next() }?.let { FeedPageItem.fraDatabase(it) }
        }
}
