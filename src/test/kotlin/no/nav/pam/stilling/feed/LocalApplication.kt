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

val lokalUrlBase = "http://localhost:8080"

val lokalPostgres: PostgreSQLContainer<*>
    get() {
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:14.4-alpine"))
            .withDatabaseName("dbname")
            .withUsername("username")
            .withPassword("pwd")

        postgres.start()
        return postgres
    }

/*
val lokalKafka: KafkaContainer
    get() {
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.2"))
            .withKraft()

        kafka.start()
        return kafka
    }
*/
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

private val env = emptyMap<String, String>()
val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

private var harStartetApplikasjonen = false

fun startLocalApplication() {
    if (!harStartetApplikasjonen) {

        startApp(
            dataSource,
            prometheusRegistry,
            env
        )
        harStartetApplikasjonen = true
    }
}
