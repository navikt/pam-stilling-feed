package no.nav.pam.stilling.feed

import io.mockk.every
import io.mockk.mockk
import no.nav.pam.stilling.feed.config.PSTMTUtil
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.test.assertEquals


class PSTMTUtilTest {

    @Test
    fun skalSubstituereVerdier() {
        val sql = "select foo from bar where foobar =:foobaz: and gazonk = :foobaz: and id = :id:"
        val conn = mockk<Connection>()
        every { conn.prepareStatement(any()) }.returns(mockk<PreparedStatement>())
        val verdier = Array<String>(3) { p -> "" }

        val params = mapOf<String, (pstmt: PreparedStatement, pos: Int) -> Unit>(
            Pair(":foobaz:") { pstmt, pos -> verdier[pos-1] = "foo $pos" },
            Pair(":id:") { pstmt, pos -> verdier[pos-1] = "id $pos" }
        )
        val preparedStatement = PSTMTUtil.prepareStatement(conn, sql, params)

        //verdier.forEach { println(it) }
        assertEquals("foo 1", verdier[0])
        assertEquals("foo 2", verdier[1])
        assertEquals("id 3", verdier[2])
    }
}