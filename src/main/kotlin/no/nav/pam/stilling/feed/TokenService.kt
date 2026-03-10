package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.*

class TokenService(private val tokenRepository: TokenRepository,
                   private val leaderElector: LeaderElector,
                   private val securityConfig: SecurityConfig,
                   private val txTemplate: TxTemplate) {
    fun lagreKonsument(konsument: KonsumentDTO) = tokenRepository.opprettKonsument(konsument)

    /**
     * Hvis konsument med samme identifikator finnes fra før, så returneres den.
     * Hvis ikke så opprettes konsumenten.
     */
    fun hentEllerOpprettKonsument(konsument: KonsumentDTO): KonsumentDTO =
        txTemplate.doInTransaction { ctx ->
            val eksisterende = tokenRepository.hentKonsument(konsument.identifikator)
            if (eksisterende.isEmpty()) {
                tokenRepository.opprettKonsument(konsument)
                return@doInTransaction konsument
            } else {
                return@doInTransaction eksisterende.first()
            }
        } ?: throw IllegalArgumentException("Greide hverken å hente eller opprette konsument ${konsument.id}")

    fun finnKonsument(konsumentId: UUID) = tokenRepository.hentKonsument(konsumentId)

    fun hentKonsumenter(spørring: String) = tokenRepository.hentKonsumenter(spørring) ?: emptyList()

    fun lagreNyttTokenForKonsument(konsumentId: UUID, jwt: String, issuedAt: Instant) =
        txTemplate.doInTransactionNullable { ctx ->
            tokenRepository.invaliderTokensForKonsument(konsumentId)
            tokenRepository.lagreNyttToken(konsumentId, jwt, issuedAt)
        }

    fun hentPublicToken() =
        txTemplate.doInTransactionNullable { ctx ->
            tokenRepository.hentKonsument(SecurityConfig.PUBLIC_TOKEN_ID).firstOrNull()?.let { k ->
                tokenRepository.hentGyldigeTokens(k.id)?.firstOrNull()
            }
        }

    /**
     * Invaliderer eventuelt eksisterende public token og oppretter et nytt
     * @return nytt public token
     */
    fun invaliderOgOpprettNyttPublicToken(): String =
        txTemplate.doInTransaction { ctx ->
            val konsument = tokenRepository.hentKonsument(SecurityConfig.PUBLIC_TOKEN_ID).first()
            tokenRepository.invaliderTokensForKonsument(konsument.id)

            return@doInTransaction opprettPublicToken(arbeidsplassen = konsument)
        }

    fun initPublicTokenHvisLeader() {
        if (leaderElector.isLeader()) {
            val arbeidsplassen = opprettArbeidsplassenKonsument()

            val issuedAt = Instant.now()
            val expires = issuedAt.plus(35, ChronoUnit.DAYS)
            val jwtToken = securityConfig.newTokenFor(arbeidsplassen, issuedAt, expires)

            lagreNyttTokenForKonsument(arbeidsplassen.id, jwtToken, issuedAt)
        }
    }

    private fun opprettArbeidsplassenKonsument(): KonsumentDTO =
        txTemplate.doInTransaction { ctx ->
            val arbeidsplassen = hentEllerOpprettKonsument(
                KonsumentDTO(
                    identifikator = SecurityConfig.PUBLIC_TOKEN_ID,
                    email = "nav.team.arbeidsplassen@nav.no",
                    telefon = "55 55 33 33",
                    kontaktperson = "Team Arbeidsmarked"
                )
            )
            return@doInTransaction arbeidsplassen
        }

    private fun opprettPublicToken(arbeidsplassen: KonsumentDTO,
                                   varighet: TemporalAmount = Duration.ofDays(35)): String =
        txTemplate.doInTransaction { ctx ->
            val issuedAt = Instant.now()
            val expires = issuedAt.plus(varighet)
            val jwtToken = securityConfig.newTokenFor(arbeidsplassen, issuedAt, expires)

            lagreNyttTokenForKonsument(arbeidsplassen.id, jwtToken, issuedAt)
            return@doInTransaction jwtToken
        }
}


