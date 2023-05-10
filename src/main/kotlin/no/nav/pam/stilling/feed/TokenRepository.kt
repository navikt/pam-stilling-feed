package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.TxContext
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import java.sql.Timestamp
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
                    setString(3, konsument.identifikator)
                    setString(4, konsument.identifikator)
                    setString(5, konsument.identifikator)
                    setTimestamp(6, Timestamp.valueOf(konsument.opprettet))
                }.executeUpdate()
        }


    fun hentKonsument(id: UUID, txContext : TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            ctx.connection()
                .prepareStatement("select * from feed_consumer where id = ?")
                .apply { setObject(1, id) }
                .executeQuery()
                .takeIf { it.next() }?.let { KonsumentDTO.fraDatabase(it) }
        }

    fun invaliderTokensForKonsument(konsumentId: UUID, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            ctx.connection()
                .prepareStatement("UPDATE token SET invalidated=TRUE AND invalidated_at=? WHERE consumer_id=?")
                .apply {
                    setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
                    setObject(2, konsumentId)
                }.executeUpdate()
        }

    fun lagreNyttToken(konsumentId: UUID, jwt: String, issuedAt: Date, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            ctx.connection()
                .prepareStatement("INSERT INTO token(id, consumer_id, jwt, issued_at) VALUES(?, ?, ?, ?)")
                .apply {
                    setObject(1, UUID.randomUUID())
                    setObject(2, konsumentId)
                    setString(3, jwt)
                    setTimestamp(4, Timestamp.from(issuedAt.toInstant()))
                }.executeUpdate()
        }
}
