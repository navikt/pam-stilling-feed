package no.nav.pam.stilling.feed.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import io.prometheus.client.CollectorRegistry

class DatabaseConfig(env: Map<String, String>,
                     private val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry) {
    private val host = env.variable("DB_HOST")
    private val port = env.variable("DB_PORT")
    private val database = env.variable("DB_DATABASE")
    private val user = env.variable("DB_USERNAME")
    private val pw = env.variable("DB_PASSWORD")

    fun lagDatasource() = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://$host:$port/$database"
        minimumIdle = 1
        maximumPoolSize = 2
        driverClassName = "org.postgresql.Driver"
        initializationFailTimeout = 5000
        username = user
        password = pw
        metricsTrackerFactory = PrometheusMetricsTrackerFactory(collectorRegistry)
        validate()
    }.let(::HikariDataSource)

}

fun Map<String, String>.variable(felt: String) = this[felt] ?: error("$felt er ikke angitt")
