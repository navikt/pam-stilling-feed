package no.nav.pam.stilling.feed.sikkerhet

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.security.AccessManager
import io.javalin.security.RouteRole
import no.nav.pam.stilling.feed.KONSUMENT_ID_MDC_KEY
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig.Companion.getBearerToken
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class JavalinAccessManager(private val securityConfig: SecurityConfig, env: Map<String, String>) : AccessManager {
    companion object {
        private val LOG = LoggerFactory.getLogger(JavalinAccessManager::class.java)
    }

    private val tilgangsstyringEnabled = env["TILGANGSSTYRING_ENABLED"]!!.toBooleanStrict()

    override fun manage(handler: Handler, ctx: Context, routeRoles: Set<RouteRole>) {
        require(routeRoles.size == 1) { "Støtter kun bruk av en rolle per endepunkt." }
        require(routeRoles.first() is Rolle) { "Ukonfigurert rolle" }
        val rolle = routeRoles.first() as Rolle

        if (validerAutoriseringForRolle(ctx, rolle)) {
            LOG.info("${rolle.name} med ID ${MDC.get(KONSUMENT_ID_MDC_KEY)} er autorisert")
            handler.handle(ctx)
        } else if (!tilgangsstyringEnabled) {
            LOG.info("${rolle.name} med ID ${MDC.get(KONSUMENT_ID_MDC_KEY)} er IKKE autorisert, men tilgangsstyring er skrudd av")
            handler.handle(ctx)
        } else {
            LOG.warn("${rolle.name} med id ${MDC.get(KONSUMENT_ID_MDC_KEY)} er IKKE autorisert")
            ctx.status(401).result("Unauthorized")
        }
    }

    private fun validerAutoriseringForRolle(ctx: Context, rolle: Rolle) = when (rolle) {
        Rolle.ADMIN -> validerAdmin(ctx)
        Rolle.KONSUMENT -> validerKonsument(ctx)
        Rolle.UNPROTECTED -> true
    }

    private fun validerToken(token: String, ctx: Context) = securityConfig.parseJWT(token).also {
        MDC.put(KONSUMENT_ID_MDC_KEY, it.getKid())
        ctx.attribute(KONSUMENT_ID_MDC_KEY, it.getKid())
    }

    private fun validerAdmin(ctx: Context): Boolean = getBearerToken(ctx)?.let {
        val parsetToken = validerToken(it, ctx)
        val erAdmin = parsetToken.decodedJWT?.subject == "admin@arbeidsplassen.nav.no"
        return erAdmin && parsetToken.erGyldig
    } ?: false

    private fun validerKonsument(ctx: Context) = getBearerToken(ctx)?.let {
        val parsetToken = validerToken(it, ctx)
        return parsetToken.decodedJWT != null && parsetToken.erGyldig
    } ?: false
}
