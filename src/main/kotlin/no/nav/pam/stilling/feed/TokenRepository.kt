package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.TxContext
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class TokenRepository(private val txTemplate: TxTemplate) {
    fun opprettKonsument(konsument: KonsumentDTO, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            ctx.connection()
                .prepareStatement("""
                        INSERT INTO feed_consumer(id, identifikator, email, telefon, kontaktperson, opprettet)
                        VALUES(?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).apply {
                    setObject(1, konsument.id)
                    setString(2, konsument.identifikator)
                    setString(3, konsument.email)
                    setString(4, konsument.telefon)
                    setString(5, konsument.kontaktperson)
                    setTimestamp(6, Timestamp.valueOf(konsument.opprettet))
                }.executeUpdate()
        }

    fun hentKonsument(identifikator: String, txContext: TxContext? = null) : List<KonsumentDTO> =
        txTemplate.doInTransaction(txContext) { ctx ->
            val konsumenter = mutableListOf<KonsumentDTO>()
            val rs = ctx.connection()
                .prepareStatement("SELECT * FROM feed_consumer " +
                        "WHERE identifikator = ? order by opprettet desc")
                .apply { setObject(1, identifikator) }
                .executeQuery()

            while (rs.next())
                konsumenter.add(KonsumentDTO.fraDatabase(rs))

             return@doInTransaction konsumenter
        } ?: emptyList()

    fun hentKonsument(id: UUID, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            ctx.connection()
                .prepareStatement("SELECT * FROM feed_consumer WHERE id = ?")
                .apply { setObject(1, id) }
                .executeQuery()
                .takeIf { it.next() }?.let { KonsumentDTO.fraDatabase(it) }
        }

    fun hentKonsumenter(spørring: String, txContext: TxContext? = null): List<KonsumentDTO>? =
        txTemplate.doInTransaction(txContext) { ctx ->
            val konsumenter = mutableListOf<KonsumentDTO>()
            val rs = ctx.connection()
                .prepareStatement("SELECT * FROM feed_consumer "
                + "WHERE lower(cast(id as text) || identifikator || email || telefon || kontaktperson) LIKE ? "
                + "ORDER BY opprettet desc")
                .apply { setString(1, "%${spørring.lowercase()}%") }
                .executeQuery()

            while (rs.next())
                konsumenter.add(KonsumentDTO.fraDatabase(rs))

            return@doInTransaction konsumenter
        }

    fun invaliderTokensForKonsument(konsumentId: UUID, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            ctx.connection()
                .prepareStatement("UPDATE token SET invalidated=TRUE, invalidated_at=? WHERE consumer_id=?")
                .apply {
                    setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
                    setObject(2, konsumentId)
                }.executeUpdate()
        }

    fun lagreNyttToken(konsumentId: UUID, jwt: String, issuedAt: Instant, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            ctx.connection()
                .prepareStatement("INSERT INTO token(id, consumer_id, jwt, issued_at) VALUES(?, ?, ?, ?)")
                .apply {
                    setObject(1, UUID.randomUUID())
                    setObject(2, konsumentId)
                    setString(3, jwt)
                    setTimestamp(4, Timestamp.from(issuedAt))
                }.executeUpdate()
        }

    fun hentInvaliderteTokens(txContext: TxContext? = null) = txTemplate.doInTransaction(txContext) { ctx ->
        ctx.connection().prepareStatement("SELECT jwt FROM token WHERE invalidated=true").executeQuery().use {
            generateSequence { if (it.next()) it.getString(1) else null }.toList()
        }
    }

    fun hentGyldigeTokens(konsumentId: UUID? = null, txContext: TxContext? = null) = txTemplate.doInTransaction(txContext) { ctx ->
        val consumerClause = konsumentId?.let { "and consumer_id = ? " } ?: ""
        ctx.connection().prepareStatement("SELECT jwt " +
                "FROM token " +
                "WHERE invalidated=false " +
                consumerClause +
                "ORDER BY issued_at desc")
            .apply { if (konsumentId != null) setObject(1, konsumentId) }
            .executeQuery().use {
            generateSequence { if (it.next()) it.getString(1) else null }.toList()
        }
    }
}
