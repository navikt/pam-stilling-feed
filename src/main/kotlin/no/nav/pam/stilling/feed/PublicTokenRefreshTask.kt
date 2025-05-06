package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class PublicTokenRefreshTask(
    private val securityConfig: SecurityConfig,
    private val tokenService: TokenService,
    private val leaderElector: LeaderElector
) : TimerTask() {
    companion object {
        private val LOG = LoggerFactory.getLogger(TokenController::class.java)
    }

    override fun run() {
        if (leaderElector.isLeader()) {
            val publicToken = tokenService.hentPublicToken()
            val publicJwt = securityConfig.parseJWT(publicToken!!)
            if (publicJwt.decodedJWT!!.expiresAtAsInstant
                    .isBefore(Instant.now().minus(Duration.ofHours(5)))
            ) {
                LOG.info("Gjeldende public token utgår snart. Invalider og opprett et nytt")
                tokenService.invaliderOgOpprettNyttPublicToken()
            }
        }
    }
}
