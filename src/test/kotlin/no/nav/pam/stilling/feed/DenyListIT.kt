package no.nav.pam.stilling.feed

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.pam.stilling.feed.*
import no.nav.pam.stilling.feed.config.TxTemplate
import no.nav.pam.stilling.feed.dto.AdDTO
import no.nav.pam.stilling.feed.dto.KonsumentDTO
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Instant
import java.util.*
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DenyListIT {
    private val httpClient = HttpClient.newBuilder().build()
    private lateinit var tokenRepository: TokenRepository
    private lateinit var tokenService: TokenService
    private lateinit var feedService: FeedService
    private lateinit var txTemplate: TxTemplate

    @BeforeAll
    fun init() {
        getLokalEnv()["TILGANGSSTYRING_ENABLED"] = "true"

        val ds = dataSource
        txTemplate = TxTemplate(ds)
        tokenRepository = TokenRepository(txTemplate)
        tokenService = TokenService(tokenRepository, txTemplate)
        feedService = FeedService(FeedRepository(txTemplate), txTemplate, objectMapper)
        kjørFlywayMigreringer(ds)
        startLocalApplication()

        javaClass.getResourceAsStream("/ad_dto.json")?.let {
            feedService.lagreNyStillingsAnnonse(objectMapper.readValue<AdDTO>(it))
        }
    }

    @Test
    fun denylistBlirOppdatert() {
        val konsument = KonsumentDTO(UUID.randomUUID(), "test", "test@test.test", "12344321", "Test Testersen")
        tokenService.lagreKonsument(konsument)

        val førsteIssuedAt = Instant.now().minusSeconds(10)
        val førsteToken = securityConfig.newTokenFor(konsument.id.toString(), førsteIssuedAt)
        tokenService.lagreNyttTokenForKonsument(konsument.id, førsteToken, førsteIssuedAt)

        assertEquals(200, hentFeed(førsteToken).statusCode())

        val andreIssuedAt = Instant.now()
        val andreToken = securityConfig.newTokenFor(konsument.id.toString(), andreIssuedAt)
        tokenService.lagreNyttTokenForKonsument(konsument.id, andreToken, andreIssuedAt)

        assertEquals(200, hentFeed(førsteToken).statusCode())
        assertEquals(200, hentFeed(andreToken).statusCode())

        DenylistRefreshTask(securityConfig, tokenRepository).run()

        assertEquals(401, hentFeed(førsteToken).statusCode())
        assertEquals(200, hentFeed(andreToken).statusCode())
    }

    private fun hentFeed(token: String) = httpClient.send(
        HttpRequest.newBuilder()
            .uri(URI("$lokalUrlBase/api/v1/feed"))
            .setHeader("Authorization", "Bearer $token")
            .build(),
        BodyHandlers.ofString()
    )
}
