package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.TxContext
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
    fun hentEllerOpprettKonsument(konsument: KonsumentDTO, txContext: TxContext? = null): KonsumentDTO =
        txTemplate.doInTransaction(txContext) { ctx ->
            val eksisterende = tokenRepository.hentKonsument(konsument.identifikator, ctx)
            if (eksisterende.isEmpty()) {
                tokenRepository.opprettKonsument(konsument, ctx)
                return@doInTransaction konsument
            } else {
                return@doInTransaction eksisterende.first()
            }
        } ?: konsument

    fun finnKonsument(konsumentId: UUID) = tokenRepository.hentKonsument(konsumentId)

    fun lagreNyttTokenForKonsument(konsumentId: UUID, jwt: String, issuedAt: Instant, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            tokenRepository.invaliderTokensForKonsument(konsumentId, ctx)
            tokenRepository.lagreNyttToken(konsumentId, jwt, issuedAt, ctx)
        }

    fun hentPublicToken(txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            tokenRepository.hentKonsument(SecurityConfig.PUBLIC_TOKEN_ID, ctx).firstOrNull()?.let { k ->
                tokenRepository.hentGyldigeTokens(ctx)?.firstOrNull()
            }
        }

    /**
     * Invaliderer eventuelt eksisterende public token og oppretter et nytt
     * @return nytt public token
     */
    fun invaliderOgOpprettNyttPublicToken(txContext: TxContext? = null): String =
        txTemplate.doInTransaction(txContext) { ctx ->
            val konsument = tokenRepository.hentKonsument(SecurityConfig.PUBLIC_TOKEN_ID, ctx).first()
            tokenRepository.invaliderTokensForKonsument(konsument.id, ctx)

            return@doInTransaction opprettPublicToken(arbeidsplassen = konsument, txContext = ctx)
        }!!

    fun initPublicTokenHvisLeader() {
        if (leaderElector.isLeader()) {
            val arbeidsplassen = opprettArbeidsplassenKonsument()

            val issuedAt = Instant.now()
            val expires = issuedAt.plus(35, ChronoUnit.DAYS)
            val jwtToken = securityConfig.newTokenFor(arbeidsplassen, issuedAt, expires)

            lagreNyttTokenForKonsument(arbeidsplassen.id, jwtToken, issuedAt)
        }
    }

    private fun opprettArbeidsplassenKonsument(txContext: TxContext? = null): KonsumentDTO =
        txTemplate.doInTransaction(txContext) { ctx ->
            val arbeidsplassen = hentEllerOpprettKonsument(
                KonsumentDTO(
                    identifikator = SecurityConfig.PUBLIC_TOKEN_ID,
                    email = "nav.team.arbeidsplassen@nav.no",
                    telefon = "55 55 33 33",
                    kontaktperson = "Team Arbeidsmarked"
                ), ctx
            )
            return@doInTransaction arbeidsplassen
        }!!

    private fun opprettPublicToken(arbeidsplassen: KonsumentDTO,
                                   varighet: TemporalAmount = Duration.ofDays(35),
                                   txContext: TxContext? = null): String =
        txTemplate.doInTransaction(txContext) { ctx ->
            val issuedAt = Instant.now()
            val expires = issuedAt.plus(varighet)
            val jwtToken = securityConfig.newTokenFor(arbeidsplassen, issuedAt, expires)

            lagreNyttTokenForKonsument(arbeidsplassen.id, jwtToken, issuedAt, ctx)
            return@doInTransaction jwtToken
        }!!
}


