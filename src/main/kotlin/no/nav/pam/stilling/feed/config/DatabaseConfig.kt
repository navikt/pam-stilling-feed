package no.nav.pam.stilling.feed.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

class DatabaseConfig(env: Map<String, String>,
                     private val meterRegistry: PrometheusMeterRegistry =
                         PrometheusMeterRegistry(PrometheusConfig.DEFAULT)) {
    private val host = env.variable("DB_HOST")
    private val port = env.variable("DB_PORT")
    private val database = env.variable("DB_DATABASE")
    private val user = env.variable("DB_USERNAME")
    private val pw = env.variable("DB_PASSWORD")

    fun lagDatasource() = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://$host:$port/$database"
        minimumIdle = 2
        maximumPoolSize = 25
        driverClassName = "org.postgresql.Driver"
        initializationFailTimeout = 5000
        username = user
        password = pw
        metricRegistry = meterRegistry
        validate()
    }.let(::HikariDataSource)

}

fun Map<String, String>.variable(felt: String) = this[felt] ?: error("$felt er ikke angitt")
