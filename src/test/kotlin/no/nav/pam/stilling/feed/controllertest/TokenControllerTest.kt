package no.nav.pam.stilling.feed.controllertest

import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import no.nav.pam.stilling.feed.sikkerhet.SecurityConfig
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenControllerTest {
    companion object {
        private const val IDENTIFIKATOR = "Test"
        private const val EMAIL = "test@test.no"
        private const val TELEFON = "12344321"
        private const val KONTAKTPERSON = "Test Testersen"
        private const val KONSUMENT_URL = "$lokalUrlBase/internal/api/newConsumer"
        private const val PUBLIC_TOKEN_URL = "$lokalUrlBase/api/publicToken"
        private const val TOKEN_URL = "$lokalUrlBase/internal/api/newApiToken"
    }

    private val httpClient = HttpClient.newBuilder().build()
    private lateinit var tokenRepository: TokenRepository
    private lateinit var tokenService: TokenService
    private lateinit var txTemplate: TxTemplate

    @BeforeAll
    fun init() {
        val ds = dataSource
        txTemplate = TxTemplate(ds)
        tokenRepository = TokenRepository(txTemplate)
        tokenService = TokenService(tokenRepository, LeaderElector("NOLEADERELECTION"), securityConfig, txTemplate)
        kjørFlywayMigreringer(ds)
        startLocalApplication()
    }

    @AfterEach
    fun tømTabeller() {
        txTemplate.tømTabeller("token", "feed_consumer")
    }

    @Test
    @Disabled("Det er noen timing issues her...")
    fun skalHentePublicToken() {
        var response = sendGetRequest(PUBLIC_TOKEN_URL)
        if (response.statusCode() != 200) {
            // #!$% retry...
            response = sendGetRequest(PUBLIC_TOKEN_URL)
        }
        Assertions.assertThat(response.statusCode()).isEqualTo(200)

        val publicToken = tokenService.hentPublicToken()
        Assertions.assertThat(response.body()).contains(publicToken)
    }

    @Test
    fun lagerKonsument() {
        val response = sendPostRequest(KONSUMENT_URL, konsumentJson)
        assertEquals(200, response.statusCode())

        val konsumenterFraDb = hentKonsumenter()
        val konsument = konsumenterFraDb.first()

        assertEquals(1, konsumenterFraDb.size)
        assertEquals(IDENTIFIKATOR, konsument.identifikator)
        assertEquals(EMAIL, konsument.email)
        assertEquals(TELEFON, konsument.telefon)
        assertEquals(KONTAKTPERSON, konsument.kontaktperson)
    }

    @Test
    fun oppretterTokenForKonsument() {
        sendPostRequest(KONSUMENT_URL, konsumentJson);
        val konsumentFraDb = hentKonsumenter().first()

        val tokenResponse = sendPostRequest(TOKEN_URL, tokenRequestJson(konsumentFraDb.id))
        assertEquals(200, tokenResponse.statusCode())

        val jwt = tokenResponse.body().split(" ").last().trim()
        val tokensFraDb = hentTokens()
        val token = tokensFraDb.first()
        assertEquals(1, tokensFraDb.size)
        assertEquals(token.jwt, jwt)
        assertEquals(token.consumerId, konsumentFraDb.id)
        assertFalse(token.invalidated)
        assertNull(token.invalidatedAt)
    }

    @Test
    fun oppretterTokenMedExpiryForKonsument() {
        sendPostRequest(KONSUMENT_URL, konsumentJson);
        val konsumentFraDb = hentKonsumenter().first()

        val tokenResponse = sendPostRequest(TOKEN_URL, tokenRequestJson(konsumentFraDb.id, "2050-01-01"))
        assertEquals(200, tokenResponse.statusCode())

        val jwt = tokenResponse.body().split(" ").last().trim()

        val tokensFraDb = hentTokens()
        val token = tokensFraDb.first()
        assertEquals(1, tokensFraDb.size)
        assertEquals(token.jwt, jwt)
        assertEquals(token.consumerId, konsumentFraDb.id)
        assertFalse(token.invalidated)
        assertNull(token.invalidatedAt)

        val (decodedJWT, erGyldig) = securityConfig.parseJWT(jwt)
        assertTrue(erGyldig)
        assertEquals(decodedJWT!!.expiresAt, Date.from(LocalDate.of(2050, 1,1).atStartOfDay(ZoneId.of("Europe/Oslo")).toInstant()))
    }

    @Test
    fun eldreTokensBlirInvalidertVedNyttToken() {
        sendPostRequest(KONSUMENT_URL, konsumentJson);
        val konsumentFraDb = hentKonsumenter().first { it.identifikator != SecurityConfig.PUBLIC_TOKEN_ID }

        sendPostRequest(TOKEN_URL, tokenRequestJson(konsumentFraDb.id))
        val konsumentTokens = hentTokens(konsumentFraDb.id)
        val førsteToken = konsumentTokens.first()
        assertFalse(førsteToken.invalidated)

        Thread.sleep(1000L) // For å få ulikt sekund fra forrige token
        sendPostRequest(TOKEN_URL, tokenRequestJson(konsumentFraDb.id))
        val tokensEtterAndreKall = hentTokens(konsumentFraDb.id)
        val andreToken = tokensEtterAndreKall.first { it.id != førsteToken.id }

        assertEquals(2, tokensEtterAndreKall.size)
        assertTrue(tokensEtterAndreKall.first { it.id == førsteToken.id }.invalidated)
        assertNotNull(tokensEtterAndreKall.first { it.id == førsteToken.id }.invalidatedAt)
        assertFalse(andreToken.invalidated)
        assertNull(andreToken.invalidatedAt)
        assertEquals(førsteToken.consumerId, andreToken.consumerId)
        assertNotEquals(førsteToken.jwt, andreToken.jwt)

        Thread.sleep(1000L) // For å få ulikt sekund fra forrige token
        sendPostRequest(TOKEN_URL, tokenRequestJson(konsumentFraDb.id))
        val tokensEtterTredjeKall = hentTokens(konsumentFraDb.id)
        val tredjeToken = tokensEtterTredjeKall.first { it.id !in listOf(førsteToken.id, andreToken.id) }

        assertEquals(3, tokensEtterTredjeKall.size)
        assertFalse(tredjeToken.invalidated)
        assertNull(tredjeToken.invalidatedAt)

        tokensEtterTredjeKall.filter { it.id in listOf(førsteToken.id, andreToken.id) }.forEach {
            assertTrue(it.invalidated)
            assertNotNull(it.invalidatedAt)
            assertEquals(it.consumerId, tredjeToken.consumerId)
            assertNotEquals(it.jwt, tredjeToken.jwt)
        }
    }

    @Test
    fun `Får ikke opprettet konsument eller token uten riktig subject i token`() {
        var response = sendPostRequest(KONSUMENT_URL, konsumentJson, testToken)
        assertEquals(401, response.statusCode())

        response = sendPostRequest(TOKEN_URL, tokenRequestJson(UUID.randomUUID()), testToken)
        assertEquals(401, response.statusCode())
    }

    @Test
    fun `Får ikke opprettet konsument eller token uten token`() {
        var response = sendPostRequestUtenToken(KONSUMENT_URL, konsumentJson)
        assertEquals(401, response.statusCode())

        response = sendPostRequestUtenToken(TOKEN_URL, tokenRequestJson(UUID.randomUUID()))
        assertEquals(401, response.statusCode())
    }

    val konsumentJson = """{
        "identifikator": "$IDENTIFIKATOR",
        "email": "$EMAIL",
        "telefon": "$TELEFON",
        "kontaktperson": "$KONTAKTPERSON"
    }""".trimIndent()

    fun tokenRequestJson(konsumentId: UUID, exp: String? = null) = """{
        "konsumentId": "$konsumentId",
        "expires": ${exp?.let { "\"$it\"" }}
    }""".trimIndent()

    private fun sendPostRequest(url: String, body: String, token: String = testAdminToken) = httpClient.send(
        HttpRequest.newBuilder()
            .uri(URI(url))
            .POST(BodyPublishers.ofString(body))
            .setHeader("Authorization", "Bearer $token")
            .build(),
        BodyHandlers.ofString()
    )
    private fun sendGetRequest(url: String, token: String = testAdminToken) = httpClient.send(
        HttpRequest.newBuilder()
            .uri(URI(url))
            .GET()
            .setHeader("Authorization", "Bearer $token")
            .build(),
        BodyHandlers.ofString()
    )

    private fun sendPostRequestUtenToken(url: String, body: String) = httpClient.send(
        HttpRequest.newBuilder().uri(URI(url)).POST(BodyPublishers.ofString(body)).build(),
        BodyHandlers.ofString()
    )

    private fun hentKonsumenter() = mutableListOf<KonsumentDTO>().also { konsumenter ->
        txTemplate.doInTransaction() { ctx ->
            ctx.connection().prepareStatement("select * from feed_consumer").executeQuery().let {
                while (it.next()) {
                    konsumenter.add(KonsumentDTO.fraDatabase(it))
                }
            }
        }
    }

    private fun hentTokens(konsumentId: UUID? = null) = mutableListOf<TokenTestDTO>().also { tokens ->
        txTemplate.doInTransaction { ctx ->
            var sql = "select * from token"
            if (konsumentId != null) {sql += " where consumer_id = '$konsumentId'"}
            ctx.connection().prepareStatement(sql).executeQuery().let { rs ->
                while (rs.next()) tokens.add(
                    TokenTestDTO(
                        id = rs.getObject("id") as UUID,
                        consumerId = rs.getObject("consumer_id") as UUID,
                        jwt = rs.getString("jwt"),
                        issuedAt = rs.getTimestamp("issued_at").toLocalDateTime(),
                        invalidated = rs.getBoolean("invalidated"),
                        invalidatedAt = rs.getTimestamp("invalidated_at")?.toLocalDateTime()
                    )
                )
            }
        }
    }

    private data class TokenTestDTO(
        val id: UUID,
        val consumerId: UUID,
        val jwt: String,
        val issuedAt: LocalDateTime,
        val invalidated: Boolean,
        val invalidatedAt: LocalDateTime?
    )
}
