package no.nav.pam.stilling.feed

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.interfaces.Payload
import io.javalin.Javalin
import io.javalin.http.Context
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*

class AuthController(private val issuer: String, private val audience: String, secret: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(AuthController::class.java)
    }

        private val algorithm = Algorithm.HMAC256(secret)

        fun newHmacJwtVerifier(): JWTVerifier =
            JWT.require(algorithm) // signature
                .withIssuer(issuer)
                .withAudience(audience)
                .build()

        fun newTokenFor(subject: String, expires: Date? = null): String =
            JWT.create()
                .withSubject(subject)
                .withIssuer(issuer)
                .withAudience(audience)
                .withIssuedAt(Date())
                .withExpiresAt(expires)
                .sign(algorithm)

    fun setupRoutes(javalin: Javalin) {
        javalin.get("/internal/api/tokenInfo") { ctx ->
            tokenInfo(ctx)
        }
        javalin.post("/internal/api/newApiToken") { ctx ->
            newApiToken(ctx)
        }
    }

    private fun newApiToken(ctx: Context) {
        try {
            val subject = ctx.formParam("subject")
            val expires = ctx.formParam("expires")?.let {
                parseDateOptionallyTime(it)
            }?.let {
                Date.from(it.atZone(ZoneId.of("Europe/Oslo")).toInstant())
            }

            if (subject == null) {
                ctx.status(400)
                ctx.contentType("text/plain")
                ctx.result("Missing required parameter: subject")
            } else {
                val newToken = newTokenFor(subject, expires)
                LOG.info("New token created for $subject")
                ctx.status(200)
                ctx.contentType("text/plain")
                ctx.result("For subject: ${subject}\nAuthorization: Bearer ${newToken}\n")
            }
        } catch (e: Exception) {
            ctx.status(400)
            ctx.contentType("text/plain")
            ctx.result("Bad input data")
        }
    }

    private fun tokenInfo(ctx: Context) {
        val authHeader = getBearerToken(ctx)
        authHeader?.let { t ->
            try {
                val decoded = JWT.decode(t)
                val valid = try {
                    newHmacJwtVerifier().verify(decoded)
                    "OK"
                } catch (e: Exception) {
                    "Not OK"
                }

                ctx.result(
                    """
    
                        Token information:
                        Algorithm:    ${decoded.algorithm}
                        Subject:      ${decoded.subject}
                        Issuer:       ${decoded.issuer}
                        Issued at:    ${decoded.issuedAt}
                        Expires:      ${decoded.expiresAt ?: "not set"}
                        Verification: ${valid}
                        
                    """.trimIndent()
                )
                ctx.contentType("text/plain")
                ctx.status(200)
            } catch (e: JWTDecodeException) {
                ctx.status(501)
                ctx.result("Unable to decode JWT token: ${e.message}")
            }
        }

        if (authHeader == null) {
            ctx.status(501)
            ctx.result("Missing Authorization header")
        }
    }


    fun getBearerToken(ctx: Context): String? {
        ctx.header("Authorization")?.let {
            if (it.startsWith("bearer ", true))
                return it.substring("bearer ".length).trim()
        }
        return null
    }


    class DenylistVerifier(private val denylist: Map<String, Long>) {
        fun isDenied(jwt: Payload): Boolean {
            return (denylist[jwt.subject] == 0L ||
                    denylist[jwt.subject] == jwt.issuedAt.time)
        }
    }

    private fun parseDateOptionallyTime(d: String): LocalDateTime? {
        val dateTimeFormatter = DateTimeFormatterBuilder()
            .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE)
            .toFormatter()
        return try {
            val temporal = dateTimeFormatter.parse(d)
            if (temporal.isSupported(ChronoField.HOUR_OF_DAY)) {
                LocalDateTime.from(temporal)
            } else {
                LocalDate.from(temporal).atStartOfDay()
            }
        } catch (e: Exception) {
            null
        }
    }
}