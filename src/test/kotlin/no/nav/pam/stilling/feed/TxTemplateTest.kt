package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.TxContext
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import java.util.*
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TxTemplateTest {
    lateinit var txTemplate: TxTemplate
    lateinit var tokenRepository: TokenRepository

    @BeforeAll
    fun init() {
        val ds = dataSource
        kjørFlywayMigreringer(ds)
        txTemplate = TxTemplate(ds)
        tokenRepository = TokenRepository(txTemplate)
    }

    @Test
    fun txTemplateRullerTilbakeVedSqlFeil() {
        val konsumentSomSkalFeile = KonsumentDTO(UUID.randomUUID(), "test1", "", "", "")
        val konsumentSomSkalBestå = KonsumentDTO(UUID.randomUUID(), "test2", "", "", "")

        assertEquals(0, hentAlleKonsumenter().size)

        txTemplate.doInTransaction { ctx ->
            tokenRepository.opprettKonsument(konsumentSomSkalFeile, ctx)
            assertEquals(1, hentAlleKonsumenter(ctx).size)
            assertThrows<PSQLException> { tokenRepository.opprettKonsument(konsumentSomSkalFeile, ctx) }
        }

        assertEquals(0, hentAlleKonsumenter().size)

        tokenRepository.opprettKonsument(konsumentSomSkalBestå)
        assertEquals(1, hentAlleKonsumenter().size)
        assertEquals(konsumentSomSkalBestå.id, hentAlleKonsumenter().first().id)

        txTemplate.doInTransaction { ctx ->
            tokenRepository.opprettKonsument(konsumentSomSkalFeile, ctx)
            assertEquals(2, hentAlleKonsumenter(ctx).size)
            assertContentEquals(
                listOf(konsumentSomSkalBestå.id, konsumentSomSkalFeile.id),
                hentAlleKonsumenter(ctx).map { it.id }
            )
            assertThrows<PSQLException> { tokenRepository.opprettKonsument(konsumentSomSkalFeile, ctx) }
        }

        assertEquals(1, hentAlleKonsumenter().size)
        assertEquals(konsumentSomSkalBestå.id, hentAlleKonsumenter().first().id)
    }

    @Test
    fun txTemplateRullerTilbakeVedVilkårligFeil() {
        assertEquals(0, hentAlleKonsumenter().size)

        assertThrows<Exception> {
            txTemplate.doInTransaction<Nothing> { ctx ->
                tokenRepository.opprettKonsument(KonsumentDTO(UUID.randomUUID(), "", "", "", ""), ctx)
                assertEquals(1, hentAlleKonsumenter(ctx).size)
                throw Exception()
            }
        }

        assertEquals(0, hentAlleKonsumenter().size)
    }

    private fun hentAlleKonsumenter(txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            ctx.connection()
                .prepareStatement("SELECT * FROM feed_consumer").executeQuery()
                .use { generateSequence { if (it.next()) KonsumentDTO.fraDatabase(it) else null }.toList() }
        } ?: emptyList()
}
