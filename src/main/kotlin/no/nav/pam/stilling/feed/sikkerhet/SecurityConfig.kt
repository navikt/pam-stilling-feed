package no.nav.pam.stilling.feed.sikkerhet

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.interfaces.DecodedJWT
import io.javalin.http.Context
import io.javalin.security.RouteRole
import no.nav.pam.stilling.feed.TokenController
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig.Companion.getKid
import org.slf4j.LoggerFactory
import java.time.Instant

class SecurityConfig(private val issuer: String, private val audience: String, secret: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TokenController::class.java)
        val PUBLIC_TOKEN_ID = "public-token"

        fun getBearerToken(ctx: Context): String? = ctx.header("Authorization")?.let {
            if (it.startsWith("bearer ", true)) it.substring("bearer ".length).trim()
            else null
        }

        fun DecodedJWT.getKid(): String? = getClaim("kid").asString()
    }

    private val denylist: MutableList<String> = mutableListOf()

    private val algorithm = Algorithm.HMAC256(secret)

    private fun newHmacJwtVerifier(): JWTVerifier =
        JWT.require(algorithm) // signature
            .withIssuer(issuer)
            .withAudience(audience)
            .build()

    fun newTokenFor(konsument: KonsumentDTO, issuedAt: Instant = Instant.now(), expires: Instant? = null): String =
        JWT.create()
            .withSubject(konsument.email)
            .withClaim("kid", konsument.id.toString())
            .withIssuer(issuer)
            .withAudience(audience)
            .withIssuedAt(issuedAt)
            .withExpiresAt(expires)
            .sign(algorithm)

    fun parseJWT(token: String) =
        try {
            val decoded = JWT.decode(token)
            ParsetJWT(decoded, validerAccessToken(decoded))
        } catch (e: JWTDecodeException) {
            LOG.info("Unable to decode JWT token: ${e.message}")
            ParsetJWT(null, false)
        }

    private fun validerAccessToken(decodedJWT: DecodedJWT) = try {
        newHmacJwtVerifier().verify(decodedJWT)
        if (isDenied(decodedJWT)) {
            LOG.warn("Bruk av invalidert token - Konsument: ${decodedJWT.getKid()}")
            false
        } else true
    } catch (e: Exception) {
        LOG.warn("Feil ved validering av token - Konsument: ${decodedJWT.getKid()} - Error: $e")
        false
    }

    fun updateDenylist(oppdatertDenylist: List<String>?) = oppdatertDenylist?.let {
        denylist.apply { addAll(it) }.distinct()
    }

    private fun isDenied(jwt: DecodedJWT) = denylist.contains(jwt.token)
}

enum class Rolle : RouteRole { ADMIN, KONSUMENT, UNPROTECTED }

data class ParsetJWT(val decodedJWT: DecodedJWT?, val erGyldig: Boolean) {
    fun getKid() = decodedJWT?.getKid() ?: "UKJENT"
}
