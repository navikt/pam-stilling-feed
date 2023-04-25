package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.TxContext
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.Feed
import no.nav.pam.stilling.feed.dto.FeedItem
import no.nav.pam.stilling.feed.dto.FeedPageItem
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.ZoneId
import java.util.*

class FeedRepository(private val txTemplate: TxTemplate) {
    companion object {
        private val LOG = LoggerFactory.getLogger(FeedRepository::class.java)
    }

    fun lagreFeedItem(feedItem: FeedItem, txContext : TxContext? = null): Int? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val sql = """
                insert into feed_item(id, json, sist_endret, status) 
                values(?, ?, ?, ?)
                on conflict(id) do update
                set json = EXCLUDED.json,
                    sist_endret = EXCLUDED.sist_endret,
                    status = EXCLUDED.status
            """.trimIndent()
            val c = ctx.connection()
            var numRows = 0
            c.prepareStatement(sql).apply {
                this.setObject(1, feedItem.uuid)
                this.setString(2, feedItem.json)
                this.setTimestamp(3, Timestamp(feedItem.sistEndret.toInstant().toEpochMilli()))
                this.setString(4, feedItem.status)
            }.use { statement ->
                numRows = statement.executeUpdate()
            }
            return@doInTransaction numRows
        }
    }

    fun hentFeedItem(id: UUID, txContext : TxContext? = null): FeedItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val sql = """
                select id, json, sist_endret, status
                from feed_item
                where id = ?
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

    fun lagreFeedPageItem(feedPage: FeedPageItem, txContext : TxContext? = null) : FeedPageItem {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val sql = """
                insert into feed_page_item(id, status, title, business_name, municipal, feed_item_id) 
                values(?, ?, ?, ?, ?, ?)
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

    fun hentFeedPageItem(id: UUID, txContext : TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where id = ?
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

    fun hentFeedPageItemsNyereEnn(seqNo: Long, antall: Int = Feed.defaultPageSize, txContext : TxContext? = null): MutableList<FeedPageItem> {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val feedPageItems = mutableListOf<FeedPageItem>()
            // NB: Dette garanterer ikke at vi har monotont stigende sist_endret...
            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where seq_no > ?
                order by seq_no
                limit ?
            """.trimIndent()
            val c = ctx.connection()
            c.prepareStatement(sql).apply {
                this.setObject(1, seqNo)
                this.setInt(2, antall)
            }.use { statement ->
                val resultSet = statement.executeQuery()
                while (resultSet.next())
                    feedPageItems.add(FeedPageItem.fraDatabase(resultSet))
                return@doInTransaction feedPageItems
            }
        }!!
    }

    fun hentFørsteSide(txContext : TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where seq_no in (select min(f2.seq_no) from feed_page_item f2)
            """.trimIndent()
            val c = ctx.connection()
            c.prepareStatement(sql).apply {
            }.use { statement ->
                val resultSet = statement.executeQuery()
                if (!resultSet.next())
                    return@doInTransaction null
                return@doInTransaction FeedPageItem.fraDatabase(resultSet)
            }
        }
    }

    fun hentSisteSide(txContext : TxContext? = null): FeedPageItem? {
        return txTemplate.doInTransaction(txContext) { ctx ->
            val sql = """
                select id, sist_endret, seq_no, status, title, business_name, municipal, feed_item_id
                from feed_page_item
                where seq_no in (select max(f2.seq_no) from feed_page_item f2)
            """.trimIndent()
            val c = ctx.connection()
            c.prepareStatement(sql).apply {
            }.use { statement ->
                val resultSet = statement.executeQuery()
                if (!resultSet.next())
                    return@doInTransaction null
                return@doInTransaction FeedPageItem.fraDatabase(resultSet)
            }
        }
    }

}
