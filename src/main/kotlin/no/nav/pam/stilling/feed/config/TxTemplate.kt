package no.nav.pam.stilling.feed.config

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource

/**
 * TxTemplate er løst basert på ideen bak spring TransactionTemplate – bare mye enklere og mer eksplisitt.
 * Bruksmønsteret er slik:
 *
 * ```
 * txTemplate = TxTemplate(HikariDataSource(...))
 *
 * txTemplate.doInTransaction(txContext) { ctx ->
 *   val conn = ctx.connection()  // IKKE LUKK CONNECTION ETTER BRUK
 *   conn.prepareStatement("heftig sql kode her").apply {
 *      it.setObject(verdier som skal inn i spørringen)
 *   }.use { statement ->
 *      val resultSet = statement.executeQuery()
 *      if (verden_faller_i_grus_vi_må_rulle_tilbake())
 *          ctx.setRollbackOnly()
 *      return@doInTransaction doSomethingWithResultSet(resultSet)
 *   }
 * }
 * ```
 *
 * Prinsipper:
 *  - Du får JDBC connection fra TxTemplate
 *  - Du lukker ikke JDBC connection selv
 *  - Du lukker ressurser du bruker fra connection (Statement/PreparedStatement)
 *  - Hvis du skal kalle andre tjenester/funksjoner som skal være en del av samme transkasjon, så sender du med
 *    eksisterende txTemplate. TxTemplate holder styr på context og om det allerede eksisterer en transaksjon
 *
 *
 *
 * Likheter og forskjeller fra Spring TransactionTemplate
 * - TxTemplate forholder seg kun til JDBC connection fra en DataSource. Det er kun testet med postgresql og HikariCP
 *   Spring TransactionTemplate er supergenerisk og er i stand til å joine transaksjoner fra andre datakilder (f.eks køer)
 * - Både Spring og TxTemplate baserer seg på at du må utføre det som skal gjøres i en transaksjon innenfor en
 *   doInTransaction() blokk.
 * - Spring tilbyr annotasjoner og aspekter for å abstrahere bort doInTransaction - TxTemplate vil aldri gjøre det
 * - Spring lagrer transaction context i en ThreadLocal slik at du ikke trenger å propagere context selv
 *   TxTemplate gjør det eksplisitt. Det gir mer "støy" i koden, men er utrolig mye mer robust,
 *
 */
class TxTemplate(private val ds: DataSource) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TxTemplate::class.java)
    }

    fun <R> doInTransaction(existingContext: TxContext? = null, txBlock: (ctx: TxContext) -> R): R? {
        val conn = existingContext?.connection() ?: ds.connection
        val isNestedTransaction = existingContext != null

        val autocommit = conn.autoCommit
        conn.autoCommit = false

        var result: R? = null
        var resultEx: Throwable? = null

        val ctx = existingContext ?: TxContext(conn)

        try {
            result = txBlock(ctx)
        } catch (e: Exception) {
            LOG.warn("Exception nådde TxTemplate. Ruller tilbake transaksjon: ${e.message}", e)
            ctx.setRollbackOnly()
            resultEx = e
        }

        if (!isNestedTransaction) {
            conn.use { c ->
                if (ctx.isRollbackOnly())
                    c.rollback()
                else
                    c.commit()
                c.autoCommit = autocommit
            }
        }

        if (result == null && resultEx != null)
            throw resultEx
        return result
    }
}

class TxContext(private val conn: Connection) {
    private var rollbackOnly = false

    fun setRollbackOnly() {
        rollbackOnly = true
    }

    fun isRollbackOnly() = rollbackOnly
    fun connection() = conn
}


/**
 * Utilityobjekt som er ment som en fattig trøst for de som savner Spring NamedParameterJdbcTemplate.
 * Dette er svært enkelt uten noen forsøk på smart eller magisk logikk. Det er basert på enkel substituering
 * av :verdi: til ? i en streng, og en måte å holde orden på hvilken ordinal verdien skal ha.
 *
 * Forventet bruk/oppførsel:
 *
 * ```
 * val params = mapOf<String, (pstmt: PreparedStatement, pos: Int) -> Unit>(
 *     Pair(":bar:") { pstmt, pos -> pstmt.setString(pos, "bar_verdi") },
 *     Pair(":baz:") { pstmt, pos -> pstmt.setString(pos, "baz_verdi") }
 *   )
 * val preparedStatement = prepareStatement(connection,
 *      "select * from foo where bar=:bar: and baz=:baz: and gazonk > :bar:",
 *      params
 *    )
 * preparedStatement.execute()...
 * ```
 *
 * her er forventet oppførsel at prapareStatement returnerer et PreparedStatement med følgende SQL:
 * ```
 * "select * from foo where bar=? and baz=? and gazonk > ?"
 * ```
 * og at følgende kode har kjørt:
 * ```
 * pstmt.setString(1, "bar_verdi")
 * pstmt.setString(2, "baz_verdi")
 * pstmt.setString(3, "bar_verdi")
 * ```
 */
object PSTMTUtil {
    fun prepareStatement(
        conn: Connection,
        sql: String,
        params: Map<String, (pstmt: PreparedStatement, pos: Int) -> Unit>
    ): PreparedStatement {
        val preparedSql = sql.replace(Regex(":[^:]+:"), "?")
        val pstmt = conn.prepareStatement(preparedSql)

        val placeholders = findPlaceholders(sql)
        applyParams(params, placeholders, pstmt)

        return pstmt
    }

    private fun applyParams(
        params: Map<String, (pstmt: PreparedStatement, pos: Int) -> Unit>,
        placeholders: List<String>,
        pstmt: PreparedStatement
    ) {
        params.forEach { me ->
            var pos = 0
            placeholders.forEach { p ->
                pos++
                if (p == me.key)
                    me.value(pstmt, pos)
            }
        }
    }

    private fun findPlaceholders(sql: String): List<String> =
        Regex(":[^:]+:").findAll(sql).map { mr ->
            mr.value
        }.toList()
}
