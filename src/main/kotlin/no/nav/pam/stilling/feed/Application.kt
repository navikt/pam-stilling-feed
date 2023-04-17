package no.nav.pam.stilling.feed

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.json.JavalinJackson
import io.javalin.micrometer.MicrometerPlugin
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat
import no.nav.pam.stilling.feed.config.DatabaseConfig
import no.nav.pam.stilling.feed.config.TxTemplate
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.Logger
import java.util.*
import javax.sql.DataSource

fun main() {
    val env = System.getenv()
    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val dataSource = DatabaseConfig(env, prometheusRegistry.prometheusRegistry).lagDatasource()

    startApp(dataSource, prometheusRegistry, env)
}

val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))

fun startApp(dataSource: DataSource,
             prometheusRegistry : PrometheusMeterRegistry,
             env: Map<String, String>) {
    val txTemplate = TxTemplate(dataSource)
    kjørFlywayMigreringer(dataSource)


    val feedRepository = FeedRepository(txTemplate)
    val feedService = FeedService(feedRepository, txTemplate, objectMapper)
    val feedController = FeedController(feedService, objectMapper)
    val javalin = startJavalin(port = 8080,
        jsonMapper = JavalinJackson(objectMapper),
        meterRegistry = prometheusRegistry
    )

    javalin.get("/internal/isAlive", {it.status(200)})
    javalin.get("/internal/isReady", {it.status(200)})
    javalin.get(
        "/internal/prometheus",
        { it.contentType(TextFormat.CONTENT_TYPE_004).result(prometheusRegistry.scrape()) }
    )

    feedController.setupRoutes(javalin)
}



fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .loggers("slf4j")
        .dataSource(dataSource)
        .load()
        .migrate()
}

fun startJavalin(port: Int = 8080, jsonMapper: JavalinJackson, meterRegistry: PrometheusMeterRegistry): Javalin {
    val requestLogger = LoggerFactory.getLogger("access")
    val micrometerPlugin = MicrometerPlugin.create { micrometerConfig ->
        micrometerConfig.registry = meterRegistry
    }
    return Javalin.create{
        it.requestLogger.http {ctx, ms -> logRequest(ctx, ms, requestLogger)}
        it.http.defaultContentType = "application/json"
        it.jsonMapper(jsonMapper)
        it.plugins.register(micrometerPlugin)
    }.start(port)
}

fun logRequest(ctx: Context, ms: Float, log: Logger) {
    log.info("${ctx.method()} ${ctx.url()} ${ctx.statusCode()}",
        kv("method", ctx.method()),
        kv("requested_uri", ctx.path()),
        kv("requested_url", ctx.url()),
        kv("protocol", ctx.protocol()),
        kv("method", ctx.method()),
        kv("status_code", ctx.statusCode()),
        kv("elapsed_ms", "$ms"))
}