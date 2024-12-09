package no.nav.pam.stilling.feed.servicetest

import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenServiceTest {
    lateinit var tokenService: TokenService
    lateinit var tokenRepository: TokenRepository
    lateinit var txTemplate: TxTemplate

    @BeforeAll
    fun init() {
        val ds = dataSource
        kjørFlywayMigreringer(ds)
        txTemplate = TxTemplate(ds)
        tokenRepository = TokenRepository(txTemplate)
        tokenService = TokenService(tokenRepository, LeaderElector("NOLEADERELECTION"), securityConfig, txTemplate)

        tokenService.initPublicTokenHvisLeader()
    }


    @Test
    fun skalHentePublicToken() {
        val publicToken = tokenService.hentPublicToken()
        assertNotNull(publicToken)
        assertEquals(publicToken, tokenService.hentPublicToken())
    }

    @Test
    fun skalInvalidereOgLageNyttToken() {
        txTemplate.doInTransaction() { ctx ->
            val eksisterendePublicToken = tokenService.hentPublicToken(ctx)
            // Må vente i minst 1 sekund for at iat skal bli forskjellig på før og etter tokenet
            Thread.sleep(1001)
            val nyttPublicToken = tokenService.invaliderOgOpprettNyttPublicToken(ctx)
            Assertions.assertThat(nyttPublicToken).isNotEqualTo(eksisterendePublicToken)
            Assertions.assertThat(nyttPublicToken).isEqualTo(tokenService.hentPublicToken(ctx))
        }
    }
}
