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
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.pam.stilling.feed.sikkerhet.JavalinAccessManager
import no.nav.pam.stilling.feed.config.DatabaseConfig
import no.nav.pam.stilling.feed.config.KafkaConfig
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.config.variable
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
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

const val SUBJECT_MDC_KEY = "subject"

fun startApp(
    dataSource: DataSource,
    prometheusRegistry: PrometheusMeterRegistry,
    env: Map<String, String>
): Thread {
    val txTemplate = TxTemplate(dataSource)
    kjørFlywayMigreringer(dataSource)

    val securityConfig = SecurityConfig(issuer = "nav-no", audience = "feed-api-v2", secret = env.variable("PRIVATE_SECRET"))
    val accessManager = JavalinAccessManager(securityConfig)

    val auth = AuthController(securityConfig)
    val healthService = HealthService()
    val kafkaConsumer = KafkaConfig(env).kafkaConsumer()
    val feedRepository = FeedRepository(txTemplate)
    val feedService = FeedService(feedRepository, txTemplate, objectMapper,
        env["STILLING_URL_BASE"] ?: "https://arbeidsplassen.nav.no/stillinger/stilling")
    val feedController = FeedController(feedService, objectMapper)
    val naisController = NaisController(healthService, prometheusRegistry)
    val kafkaListener = KafkaStillingListener(kafkaConsumer, feedService, objectMapper, txTemplate, healthService)

    val javalin = startJavalin(port = 8080,
        jsonMapper = JavalinJackson(objectMapper),
        meterRegistry = prometheusRegistry,
        accessManager = accessManager
    )

    naisController.setupRoutes(javalin)
    auth.setupRoutes(javalin)
    feedController.setupRoutes(javalin)
    javalin.after { _ -> MDC.remove(SUBJECT_MDC_KEY)}

    return kafkaListener.startListener()
}

fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .loggers("slf4j")
        .dataSource(dataSource)
        .load()
        .migrate()
}

fun startJavalin(
    port: Int = 8080,
    jsonMapper: JavalinJackson,
    meterRegistry: PrometheusMeterRegistry,
    accessManager: JavalinAccessManager
): Javalin {
    val requestLogger = LoggerFactory.getLogger("access")
    val micrometerPlugin = MicrometerPlugin.create { micrometerConfig ->
        micrometerConfig.registry = meterRegistry
    }
    return Javalin.create{
        it.accessManager(accessManager)
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
