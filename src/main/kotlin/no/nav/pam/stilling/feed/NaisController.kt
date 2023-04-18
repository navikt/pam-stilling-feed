package no.nav.pam.stilling.feed

import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class NaisController(private val healthService: HealthService,
                     private val prometheusMeterRegistry: PrometheusMeterRegistry) {
    companion object {
        private val LOG = LoggerFactory.getLogger(FeedRepository::class.java)
    }

    fun setupRoutes(javalin: Javalin) {
        javalin.get("/internal/isAlive") {
            if (healthService.isHealthy())
                it.status(200)
            else
                it.status(HttpStatus.SERVICE_UNAVAILABLE)
        }
        javalin.get("/internal/isReady") { it.status(200) }
        javalin.get("/internal/prometheus") { it.contentType(TextFormat.CONTENT_TYPE_004).result(prometheusMeterRegistry.scrape()) }
    }
}

class HealthService {
    private val unhealthyVotes = AtomicInteger(0)

    fun addUnhealthyVote() =
        unhealthyVotes.addAndGet(1)

    fun unhealthyVotes() = unhealthyVotes.get()

    fun isHealthy() = unhealthyVotes.get() == 0
}