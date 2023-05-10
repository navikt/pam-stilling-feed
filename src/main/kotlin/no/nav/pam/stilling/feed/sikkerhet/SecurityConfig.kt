package no.nav.pam.stilling.feed.sikkerhet

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.interfaces.DecodedJWT
import io.javalin.http.Context
import io.javalin.security.RouteRole
import no.nav.pam.stilling.feed.TokenController
import org.slf4j.LoggerFactory
import java.util.*

class SecurityConfig(private val issuer: String, private val audience: String, secret: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TokenController::class.java)

        fun getBearerToken(ctx: Context): String? = ctx.header("Authorization")?.let {
            if (it.startsWith("bearer ", true)) it.substring("bearer ".length).trim()
            else null
        }
    }

    private val algorithm = Algorithm.HMAC256(secret)

    private fun newHmacJwtVerifier(): JWTVerifier =
        JWT.require(algorithm) // signature
            .withIssuer(issuer)
            .withAudience(audience)
            .build()

    fun newTokenFor(subject: String, issuedAt: Date = Date(), expires: Date? = null): String =
        JWT.create()
            .withSubject(subject)
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
        true
    } catch (e: Exception) {
        false
    }
}

enum class Rolle : RouteRole { KONSUMENT, UNPROTECTED }
data class ParsetJWT(val decodedJWT: DecodedJWT?, val erGyldig: Boolean)
