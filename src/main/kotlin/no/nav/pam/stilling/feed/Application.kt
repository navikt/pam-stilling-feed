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
import io.javalin.openapi.plugin.redoc.ReDocPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.pam.stilling.feed.config.DatabaseConfig
import no.nav.pam.stilling.feed.config.KafkaConfig
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.config.variable
import no.nav.pam.stilling.feed.sikkerhet.JavalinAccessManager
import no.nav.pam.stilling.feed.sikkerhet.Rolle
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.*
import javax.sql.DataSource

fun main() {
    val env = System.getenv()
    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT).also { registry ->
        LogbackMetrics().bindTo(registry)
    }
    val dataSource = DatabaseConfig(env, prometheusRegistry.prometheusRegistry).lagDatasource()
    val securityConfig = SecurityConfig(issuer = "nav-no", audience = "feed-api-v2", secret = env.variable("PRIVATE_SECRET"))

    startApp(dataSource, prometheusRegistry, securityConfig, env)
}

val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))

const val KONSUMENT_ID_MDC_KEY = "konsument_id"

fun startApp(
    dataSource: DataSource,
    prometheusRegistry: PrometheusMeterRegistry,
    securityConfig: SecurityConfig,
    env: Map<String, String>
): Thread {
    val txTemplate = TxTemplate(dataSource)
    kjørFlywayMigreringer(dataSource)

    val accessManager = JavalinAccessManager(securityConfig, env)

    val leaderElector = LeaderElector(env["ELECTOR_PATH"]!!)
    val tokenRepository = TokenRepository(txTemplate)
    val tokenService = TokenService(tokenRepository, leaderElector, securityConfig, txTemplate)
    val tokenController = TokenController(securityConfig, tokenService)
    val healthService = HealthService()
    val kafkaConfig = KafkaConfig(env)
    val kafkaConsumer = kafkaConfig.kafkaConsumer(env.variable("STILLING_INTERN_TOPIC"), env.variable("STILLING_INTERN_GROUP_ID"))
    val feedRepository = FeedRepository(txTemplate)
    val feedService = FeedService(feedRepository, txTemplate, objectMapper,
        env["STILLING_URL_BASE"] ?: "https://arbeidsplassen.nav.no/stillinger/stilling")
    val feedController = FeedController(feedService, objectMapper)
    val naisController = NaisController(healthService, prometheusRegistry)
    val kafkaListener = KafkaStillingListener(kafkaConsumer, feedService, healthService)

    feedService.fjernDIRFraFeed()
    tokenService.initPublicTokenHvisLeader()

    val javalin = startJavalin(
        port = 8080,
        jsonMapper = JavalinJackson(objectMapper),
        meterRegistry = prometheusRegistry,
        accessManager = accessManager
    )

    naisController.setupRoutes(javalin)
    tokenController.setupRoutes(javalin)
    feedController.setupRoutes(javalin)
    javalin.after { _ -> MDC.remove(KONSUMENT_ID_MDC_KEY) }

    Timer("DenylistRefreshTimer").scheduleAtFixedRate(
        DenylistRefreshTask(securityConfig, tokenRepository),
        0L,
        1000 * 60 * 30 // Refresher denylist en gang hver halvtime
    )

    Timer("PublicTokenRefreshTask").scheduleAtFixedRate(
        PublicTokenRefreshTask(securityConfig, tokenService, leaderElector),
        0L,
        1000 * 60 * 30 // Sjekker om public token skal byttes ut en gang hver halvtime
    )

    if (env.variable("REKJOR_DETALJER_ENABLED").toBooleanStrict()) {
        val rekjørDetaljerKafkaConsumer = kafkaConfig.kafkaConsumer(env.variable("STILLING_INTERN_TOPIC"), env.variable("REKJOR_DETALJER_GROUP_ID"))
        val stillingDetaljerListener = KafkaStillingDetaljerListener(rekjørDetaljerKafkaConsumer, feedService, healthService)
        stillingDetaljerListener.startListener()
    }

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
    val micrometerPlugin = MicrometerPlugin { micrometerConfig ->
        micrometerConfig.registry = meterRegistry
    }

    return Javalin.create {
        it.requestLogger.http { ctx, ms -> logRequest(ctx, ms, requestLogger) }
        it.http.defaultContentType = "application/json"
        it.jsonMapper(jsonMapper)
        it.registerPlugin(micrometerPlugin)

        it.registerPlugin(getOpenApiPlugin())
        it.registerPlugin(SwaggerPlugin { swaggerConfiguration ->
            swaggerConfiguration.roles = arrayOf(Rolle.UNPROTECTED)
            swaggerConfiguration.documentationPath = "/api/openapi.json"
        })
        it.registerPlugin(ReDocPlugin { reDocConfiguration ->
            reDocConfiguration.roles = arrayOf(Rolle.UNPROTECTED)
            reDocConfiguration.documentationPath = "/api/openapi.json"
        })
    }.beforeMatched {ctx ->
        if(ctx.routeRoles().isEmpty()) {
            return@beforeMatched
        }
        accessManager.manage(ctx, ctx.routeRoles())
    }
        .start(port)
}

fun logRequest(ctx: Context, ms: Float, log: Logger) {
    log.info(
        "${ctx.method()} ${ctx.url()} ${ctx.statusCode()}",
        kv("konsument_id", ctx.attribute<String>(KONSUMENT_ID_MDC_KEY)),
        kv("method", ctx.method()),
        kv("requested_uri", ctx.path()),
        kv("requested_url", ctx.url()),
        kv("protocol", ctx.protocol()),
        kv("status_code", ctx.statusCode()),
        kv("elapsed_ms", "$ms")
    )
}
