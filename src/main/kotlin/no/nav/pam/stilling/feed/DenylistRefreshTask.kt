package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import org.slf4j.LoggerFactory
import java.util.*

class DenylistRefreshTask(
    private val securityConfig: SecurityConfig, private val tokenRepository: TokenRepository
) : TimerTask() {
    companion object {
        private val LOG = LoggerFactory.getLogger(TokenController::class.java)
    }

    override fun run() {
        val nyDenylist = tokenRepository.hentInvaliderteTokens()
        LOG.info("Refresher denylist - Antall invaliderte tokens: ${nyDenylist?.size}")
        securityConfig.updateDenylist(nyDenylist)
    }
}
