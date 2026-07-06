package no.nav.pam.stilling.feed

import io.javalin.http.HttpStatus
import io.javalin.router.JavalinDefaultRoutingApi
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.pam.stilling.feed.sikkerhet.Rolle
import java.util.concurrent.atomic.AtomicInteger

class NaisController(
    private val healthService: HealthService, private val prometheusMeterRegistry: PrometheusMeterRegistry
) {
    fun setupRoutes(routes: JavalinDefaultRoutingApi) {
        routes.get("/internal/isReady", { it.status(200) }, Rolle.UNPROTECTED)
        routes.get(
            "/internal/isAlive",
            { if (healthService.isHealthy()) it.status(HttpStatus.OK) else it.status(HttpStatus.SERVICE_UNAVAILABLE) },
            Rolle.UNPROTECTED
        )
        routes.get(
            "/internal/prometheus",
            { it.contentType("text/plain; version=0.0.4; charset=utf-8").result(prometheusMeterRegistry.scrape()) },
            Rolle.UNPROTECTED
        )
    }
}

class HealthService {
    private val unhealthyVotes = AtomicInteger(0)
    fun addUnhealthyVote() = unhealthyVotes.addAndGet(1)
    fun isHealthy() = unhealthyVotes.get() == 0
}