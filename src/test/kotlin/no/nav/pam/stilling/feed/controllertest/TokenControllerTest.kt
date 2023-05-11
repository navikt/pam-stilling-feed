package no.nav.pam.stilling.feed.controllertest

import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.LocalDateTime
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
        private const val TOKEN_URL = "$lokalUrlBase/internal/api/newApiToken"
    }

    private val httpClient = HttpClient.newBuilder().build()
    private lateinit var tokenRepository: TokenRepository
    private lateinit var txTemplate: TxTemplate

    @BeforeAll
    fun init() {
        val ds = dataSource
        txTemplate = TxTemplate(ds)
        tokenRepository = TokenRepository(txTemplate)
        kjørFlywayMigreringer(ds)
        startLocalApplication()
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

        val tokenResponse = sendPostRequest(TOKEN_URL, "consumerId=${konsumentFraDb.id}")
        assertEquals(200, tokenResponse.statusCode())

        val jwt = tokenResponse.body().split(" ").last().trim()
        val tokensFraDb = hentTokens()
        val token =  tokensFraDb.first()
        assertEquals(1, tokensFraDb.size)
        assertEquals(token.jwt, jwt)
        assertEquals(token.consumerId, konsumentFraDb.id)
        assertFalse(token.invalidated)
        assertNull(token.invalidatedAt)
    }

    @Test
    fun eldreTokensBlirInvalidertVedNyttToken() {
        sendPostRequest(KONSUMENT_URL, konsumentJson);
        val konsumentFraDb = hentKonsumenter().first()

        sendPostRequest(TOKEN_URL, "consumerId=${konsumentFraDb.id}")
        val førsteToken = hentTokens().first()
        assertFalse(førsteToken.invalidated)

        Thread.sleep(1000L) // For å få ulikt sekund fra forrige token
        sendPostRequest(TOKEN_URL, "consumerId=${konsumentFraDb.id}")
        val tokensEtterAndreKall = hentTokens()
        val andreToken = tokensEtterAndreKall.first { it.id != førsteToken.id}

        assertEquals(2, tokensEtterAndreKall.size)
        assertTrue(tokensEtterAndreKall.first { it.id == førsteToken.id }.invalidated)
        assertNotNull(tokensEtterAndreKall.first { it.id == førsteToken.id }.invalidatedAt)
        assertFalse(andreToken.invalidated)
        assertNull(andreToken.invalidatedAt)
        assertEquals(førsteToken.consumerId, andreToken.consumerId)
        assertNotEquals(førsteToken.jwt, andreToken.jwt)

        Thread.sleep(1000L) // For å få ulikt sekund fra forrige token
        sendPostRequest(TOKEN_URL, "consumerId=${konsumentFraDb.id}")
        val tokensEtterTredjeKall = hentTokens()
        val tredjeToken = tokensEtterTredjeKall.first { it.id !in listOf(førsteToken.id, andreToken.id)}

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

    val konsumentJson = """{
        "identifikator": "$IDENTIFIKATOR",
        "email": "$EMAIL",
        "telefon": "$TELEFON",
        "kontaktperson": "$KONTAKTPERSON"
    }""".trimIndent()

    private fun sendPostRequest(url: String, body: String) = httpClient.send(
        HttpRequest.newBuilder()
            .uri(URI(url))
            .POST(BodyPublishers.ofString(body))
            .build(),
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

    private fun hentTokens() = mutableListOf<TokenTestDTO>().also { tokens ->
        txTemplate.doInTransaction { ctx ->
            ctx.connection().prepareStatement("select * from token").executeQuery().let { rs ->
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
