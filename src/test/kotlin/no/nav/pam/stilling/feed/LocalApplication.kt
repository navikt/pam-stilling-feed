package no.nav.pam.stilling.feed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

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

private val env = mutableMapOf(
    "STILLING_INTERN_TOPIC" to "teampam.stilling-intern-1",
    "STILLING_INTERN_GROUP_ID" to "StillingFeed1",
    "security.protocol" to "PLAINTEXT",
    "PRIVATE_SECRET" to "SuperHemmeligNøkkel",
    "STILLING_URL_BASE" to "https://arbeidsplassen.nav.no/stillinger/stilling",
    "KAFKA_BROKERS" to lokalKafka.bootstrapServers
)
fun getLokalEnv() = env

val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

private var harStartetApplikasjonen = false
private var listenerThread: Thread? = null

fun startLocalApplication(): Thread {
    if (!harStartetApplikasjonen) {

        listenerThread = startApp(
            dataSource,
            prometheusRegistry,
            env
        )
        harStartetApplikasjonen = true
    }
    return listenerThread!!
}
