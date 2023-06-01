package no.nav.pam.stilling.feed

import no.nav.pam.stilling.feed.config.TxContext
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import java.time.Instant
import java.util.*

class TokenService(private val tokenRepository: TokenRepository, private val txTemplate: TxTemplate) {
    fun lagreKonsument(konsument: KonsumentDTO) = tokenRepository.opprettKonsument(konsument)

    fun finnKonsument(konsumentId: UUID) = tokenRepository.hentKonsument(konsumentId)

    fun lagreNyttTokenForKonsument(konsumentId: UUID, jwt: String, issuedAt: Instant, txContext: TxContext? = null) =
        txTemplate.doInTransaction(txContext) { ctx ->
            tokenRepository.invaliderTokensForKonsument(konsumentId, ctx)
            tokenRepository.lagreNyttToken(konsumentId, jwt, issuedAt, ctx)
        }
}
