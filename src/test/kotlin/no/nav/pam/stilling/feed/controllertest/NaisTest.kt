package no.nav.pam.stilling.feed.controllertest

import no.nav.pam.stilling.feed.lokalUrlBase
import no.nav.pam.stilling.feed.startLocalApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NaisTest {

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @Test
    fun isAlive() {
        val request = HttpRequest.newBuilder().uri(URI("$lokalUrlBase/internal/isAlive")).GET().build()
        val response = HttpClient.newBuilder().build().send(request, BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
    }
}