package no.nav.pam.stilling.feed

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.Javalin
import io.javalin.http.Context
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import no.nav.pam.stilling.feed.sikkerhet.Rolle
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig.Companion.getBearerToken
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*

class TokenController(private val securityConfig: SecurityConfig, private val tokenService: TokenService) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TokenController::class.java)
    }

    fun setupRoutes(javalin: Javalin) {
        // TODO Burde vi ha noe auth på dette selv om det bare er tilgjengelig på internt nett?
        javalin.get("/internal/api/tokenInfo", { ctx -> tokenInfo(ctx) }, Rolle.UNPROTECTED)
        javalin.post("/internal/api/newApiToken", { ctx -> nyttApiToken(ctx) }, Rolle.UNPROTECTED)
        javalin.post("/internal/api/newConsumer", { ctx -> nyKonsument(ctx) }, Rolle.UNPROTECTED)
    }

    private fun nyKonsument(ctx: Context) = try {
        val konsument = objectMapper.readValue<KonsumentDTO>(ctx.body())
        val success = tokenService.lagreKonsument(konsument)

        if (success != null && success > 0) {
            ctx.status(200)
            ctx.contentType("text/plain")
            ctx.result("""
                Opprettet ny konsument:
                ID:             ${konsument.id}
                Identifikator:  ${konsument.identifikator}
                Email:          ${konsument.email}
                Telefon:        ${konsument.telefon}
                Kontaktperson:  ${konsument.kontaktperson}
                Opprettet:      ${konsument.opprettet}
            """.trimIndent())
        } else {
            ctx.status(500)
            ctx.result("Kunne ikke opprette ny konsument")
        }
    } catch (e: Exception) {
        ctx.status(400)
        ctx.result("Bad input data")
    }

    private fun nyttApiToken(ctx: Context) {
        try {
            val subject = ctx.formParam("consumerId")?.let(::parseUuid)
            val expires = ctx.formParam("expires")?.let(::parseDateOptionallyTime)
                ?.let { Date.from(it.atZone(ZoneId.of("Europe/Oslo")).toInstant()) }

            if (subject == null) {
                ctx.status(400)
                ctx.contentType("text/plain")
                ctx.result("Missing required parameter: consumerId")
            } else if(tokenService.finnKonsument(subject) == null) {
                ctx.status(404)
                ctx.contentType("text/plain")
                ctx.result("Consumer $subject not found")
            } else {
                val issuedAt = Date()
                val newToken = securityConfig.newTokenFor(subject.toString(), issuedAt, expires)
                tokenService.lagreNyttTokenForKonsument(subject, newToken, issuedAt)

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
            val (decodedJWT, erGyldig) = securityConfig.parseJWT(t)

            if (decodedJWT != null) {
                ctx.result("""
                    Token information:
                    Algorithm:    ${decodedJWT.algorithm}
                    Subject:      ${decodedJWT.subject}
                    Issuer:       ${decodedJWT.issuer}
                    Issued at:    ${decodedJWT.issuedAt}
                    Expires:      ${decodedJWT.expiresAt ?: "not set"}
                    Verification: ${if (erGyldig) "OK" else "Not OK"}
                """.trimIndent())
                ctx.contentType("text/plain")
                ctx.status(200)
            } else {
                ctx.status(501)
                ctx.result("Unable to decode JWT token")
            }
        }

        if (authHeader == null) {
            ctx.status(501)
            ctx.result("Missing Authorization header")
        }
    }

    class DenylistVerifier(private val denylist: Map<String, Long>) {
        fun isDenied(jwt: Payload): Boolean {
            return (denylist[jwt.subject] == 0L ||
                    denylist[jwt.subject] == jwt.issuedAt.time)
        }
    }

    private fun parseUuid(s: String?) = try { UUID.fromString(s) } catch (_: Exception) { null }

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