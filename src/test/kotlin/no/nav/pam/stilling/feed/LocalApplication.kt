package no.nav.pam.stilling.feed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.*

fun main() {
    startLocalApplication()
}

const val lokalUrlBase = "http://localhost:8080"

val lokalPostgres: PostgreSQLContainer<*> =
    PostgreSQLContainer(DockerImageName.parse("postgres:14.4-alpine"))
        .withDatabaseName("dbname")
        .withUsername("username")
        .withPassword("pwd")
        .apply { start() }

val lokalKafka: KafkaContainer =
    KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.2")).withKraft().apply { start() }

val dataSource = HikariConfig().apply {
    jdbcUrl = lokalPostgres.jdbcUrl
    minimumIdle = 1
    maximumPoolSize = 10
    driverClassName = "org.postgresql.Driver"
    initializationFailTimeout = 5000
    username = lokalPostgres.username
    password = lokalPostgres.password
    validate()
}.let(::HikariDataSource)

internal fun TxTemplate.tømTabeller(vararg tabeller: String) = doInTransaction { ctx ->
    tabeller.forEach { ctx.connection().prepareStatement("delete from $it").executeUpdate() }
}

private val env = mutableMapOf(
    "STILLING_INTERN_TOPIC" to "teampam.stilling-intern-1",
    "STILLING_INTERN_GROUP_ID" to "StillingFeed1",
    "security.protocol" to "PLAINTEXT",
    "PRIVATE_SECRET" to "SuperHemmeligNøkkel",
    "STILLING_URL_BASE" to "https://arbeidsplassen.nav.no/stillinger/stilling",
    "KAFKA_BROKERS" to lokalKafka.bootstrapServers,
    "TILGANGSSTYRING_ENABLED" to "true",
    "ELECTOR_PATH" to "NOLEADERELECTION",
    "REKJOR_DETALJER_ENABLED" to "false"
)

fun getLokalEnv() = env

val securityConfig = SecurityConfig(issuer = "nav-test", audience = "feed-api-v2-test", secret = getLokalEnv()["PRIVATE_SECRET"]!!)
val testToken = securityConfig.newTokenFor(KonsumentDTO(UUID.randomUUID(), "test", "test", "test", "test"))
val testAdminToken = securityConfig.newTokenFor(KonsumentDTO(UUID.randomUUID(), "test", "admin@arbeidsplassen.nav.no", "test", "test"))

val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT).also { registry ->
    LogbackMetrics().bindTo(registry)
}

private var harStartetApplikasjonen = false
private var listenerThread: Thread? = null

fun startLocalApplication(): Thread {
    if (!harStartetApplikasjonen) {

        listenerThread = startApp(
            dataSource,
            prometheusRegistry,
            securityConfig,
            env
        )
        harStartetApplikasjonen = true
    }
    return listenerThread!!
}
