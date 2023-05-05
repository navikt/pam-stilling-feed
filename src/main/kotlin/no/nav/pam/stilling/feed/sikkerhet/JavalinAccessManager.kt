package no.nav.pam.stilling.feed.sikkerhet

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import io.javalin.security.AccessManager
import io.javalin.security.RouteRole
import no.nav.pam.stilling.feed.SUBJECT_MDC_KEY
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig.Companion.getBearerToken
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class JavalinAccessManager(private val securityConfig: SecurityConfig, env: Map<String, String>) : AccessManager {
    companion object {
        private val LOG = LoggerFactory.getLogger(JavalinAccessManager::class.java)
    }

    private val tilgangsstyringEnabled = env["TILGANGSSTYRING_ENABLED"].toBoolean()

    override fun manage(handler: Handler, ctx: Context, routeRoles: Set<RouteRole>) {
        require(routeRoles.size == 1) { "Støtter kun bruk av en rolle per endepunkt." }
        if (tilgangsstyringEnabled) validerAutoriseringForRolle(ctx, routeRoles.first())
        handler.handle(ctx)
    }

    private fun validerAutoriseringForRolle(ctx: Context, rolle: RouteRole) = when (rolle) {
        Rolle.KONSUMENT -> validerKonsument(ctx)
        Rolle.UNPROTECTED -> null
        else -> throw InternalServerErrorResponse("Ukonfigurert rolle")
    }

    private fun validerKonsument(ctx: Context) = getBearerToken(ctx)?.let {
        val parsetToken = securityConfig.parseJWT(it)
        val subject = parsetToken.decodedJWT?.subject ?: "UKJENT"
        MDC.put(SUBJECT_MDC_KEY, subject)

        if (parsetToken.decodedJWT == null || !parsetToken.erGyldig) {
            LOG.info("Uautorisert request - Subject $subject")
            throw UnauthorizedResponse()
        }
    } ?: throw UnauthorizedResponse()
}
