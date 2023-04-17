package no.nav.pam.stilling.feed.config

import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.sql.DataSource

class TxTemplate(private val ds: DataSource) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TxTemplate::class.java)
    }

    fun <R> doInTransaction(existingContext: TxContext?, txBlock: (ctx: TxContext) -> R) : R? {
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
            conn.use { c->
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
        rollbackOnly = true}

    fun isRollbackOnly() = rollbackOnly
    fun connection() = conn
}
