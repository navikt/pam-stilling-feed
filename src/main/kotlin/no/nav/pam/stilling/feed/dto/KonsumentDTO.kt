package no.nav.pam.stilling.feed.dto

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

data class KonsumentDTO(
    val id: UUID = UUID.randomUUID(),
    val identifikator: String,
    val email: String,
    val telefon: String,
    val kontaktperson: String,
    val opprettet: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun fraDatabase(rs: ResultSet) = KonsumentDTO(
            id = rs.getObject("id") as UUID,
            identifikator = rs.getString("identifikator"),
            email = rs.getString("email"),
            telefon = rs.getString("telefon"),
            kontaktperson = rs.getString("kontaktperson"),
            opprettet = (rs.getObject("opprettet") as Timestamp).toLocalDateTime()
        )
    }
}
