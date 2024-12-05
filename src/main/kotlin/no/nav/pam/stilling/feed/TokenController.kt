package no.nav.pam.stilling.feed

import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.Javalin
import io.javalin.http.Context
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import no.nav.pam.stilling.feed.dto.TokenRequestDTO
import no.nav.pam.stilling.feed.sikkerhet.Rolle
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig.Companion.getBearerToken
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig.Companion.getKid
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId

class TokenController(private val securityConfig: SecurityConfig, private val tokenService: TokenService) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TokenController::class.java)
    }

    fun setupRoutes(javalin: Javalin) {
        javalin.get("/api/publicToken", { ctx -> publicToken(ctx) }, Rolle.UNPROTECTED)
        javalin.get("/internal/api/tokenInfo", { ctx -> tokenInfo(ctx) }, Rolle.ADMIN)
        javalin.post("/internal/api/newApiToken", { ctx -> nyttApiToken(ctx) }, Rolle.ADMIN)
        javalin.post("/internal/api/newConsumer", { ctx -> nyKonsument(ctx) }, Rolle.ADMIN)
    }

    private fun publicToken(ctx: Context) {
        val publicToken = tokenService.hentPublicToken()
        if (publicToken == null) {
            ctx.status(404).result("Public token is not currently available")
        } else {
            ctx.status(200)
            ctx.contentType("text/plain")
            ctx.result("""
                Current public token for Nav Job Vacancy Feed:
                $publicToken
            """.trimIndent())
        }
    }

    private fun nyKonsument(ctx: Context) = try {
        val konsument = objectMapper.readValue<KonsumentDTO>(ctx.body())
        val success = tokenService.lagreKonsument(konsument)

        if (success != null && success > 0) {
            LOG.info("New consumer created with ID ${konsument.id}")
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
            LOG.error("Error when creating new consumer")
            ctx.status(500)
            ctx.result("Kunne ikke opprette ny konsument")
        }
    } catch (e: Exception) {
        LOG.error("Error when creating new consumer", e)
        ctx.status(400)
        ctx.result("Bad input data")
    }

    private fun nyttApiToken(ctx: Context) {
        try {
            val (konsumentId, expires) = objectMapper.readValue<TokenRequestDTO>(ctx.body())
            val konsument = tokenService.finnKonsument(konsumentId)

            if (konsument == null) {
                ctx.status(404)
                ctx.contentType("text/plain")
                ctx.result("Consumer $konsumentId not found")
            } else {
                val issuedAt = Instant.now()
                val newToken = securityConfig.newTokenFor(konsument, issuedAt, expires?.atStartOfDay(ZoneId.of("Europe/Oslo"))?.toInstant())
                tokenService.lagreNyttTokenForKonsument(konsumentId, newToken, issuedAt)

                LOG.info("New token created for $konsumentId")
                ctx.status(200)
                ctx.contentType("text/plain")
                ctx.result("For consumer: ${konsumentId}\nAuthorization: Bearer ${newToken}\n")
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
                    Algorithm:      ${decodedJWT.algorithm}
                    Subject:        ${decodedJWT.subject}
                    Konsument ID:   ${decodedJWT.getKid()}
                    Issuer:         ${decodedJWT.issuer}
                    Issued at:      ${decodedJWT.issuedAt}
                    Expires:        ${decodedJWT.expiresAt ?: "not set"}
                    Verification:   ${if (erGyldig) "OK" else "Not OK"}
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
}
