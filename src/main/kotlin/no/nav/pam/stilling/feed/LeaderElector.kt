package no.nav.pam.stilling.feed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.URL
import java.time.LocalDateTime

class LeaderElector(private val electorPath: String) {
    private val hostname = InetAddress.getLocalHost().hostName
    private var leader =  ""
    private var lastCalled = LocalDateTime.MIN
    private val electorUri = "http://$electorPath"

    companion object {
        private val LOG = LoggerFactory.getLogger(LeaderElector::class.java)

        val mapper = ObjectMapper().apply {
            registerModule(
                KotlinModule.Builder()
                    .withReflectionCacheSize(512)
                    .configure(KotlinFeature.NullToEmptyCollection, false)
                    .configure(KotlinFeature.NullToEmptyMap, false)
                    .configure(KotlinFeature.NullIsSameAsDefault, false)
                    .configure(KotlinFeature.SingletonSupport, false)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build()
            )
            registerModule(JavaTimeModule())
        }
    }

    fun isLeader() : Boolean {
        return hostname == getLeader()
    }

    private fun getLeader() : String {
        if (electorPath == "NOLEADERELECTION")
            return hostname
        if (leader.isBlank() || lastCalled.isBefore(LocalDateTime.now().minusMinutes(2))) {
            leader = mapper.readValue(URL(electorUri).readText(), Elector::class.java).name
            lastCalled = LocalDateTime.now()
        }
        return leader
    }
}
@JsonIgnoreProperties(ignoreUnknown = true)
data class Elector(val name: String)