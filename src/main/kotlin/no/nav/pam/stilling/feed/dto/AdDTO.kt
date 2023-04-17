package no.nav.pam.stilling.feed.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdDTO(
        val id: Long?,
        val uuid: String,
        val created: LocalDateTime = LocalDateTime.now(),
        val createdBy: String?,
        val updated: LocalDateTime = LocalDateTime.now(),
        val updatedBy: String? = null,
        val mediaList: MutableList<MediaDTO>? = mutableListOf<MediaDTO>(),
        val contactList: MutableList<ContactDTO> = mutableListOf(),
        val location: LocationDTO? = null,
        val locationList: MutableList<LocationDTO> = mutableListOf(),
        val properties: MutableMap<String, String> = mutableMapOf(),

    val title: String? = null,
    val status: String? = null,
    val privacy: String? = null,
    val source: String? = null,
    val medium: String? = null,
    val reference: String? = null,
    val published: LocalDateTime? = null,
    val expires: LocalDateTime? = null,
    val employer: CompanyDTO? = null,
    val categoryList: MutableList<CategoryDTO> = mutableListOf(),
    val administration: AdministrationDTO? = null,
    val publishedByAdmin: LocalDateTime? = null,
    val businessName: String? = null,

    // Meta fields not part of core ad data model
    val isFirstPublished : Boolean = false, // Set to true by API user when an ad is published the first time
    val isDeactivatedByExpiry :Boolean = false,
    val isActivationOnPublishingDate : Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdministrationDTO(
    val id: Long,
    val status: String? = null,
    val comments: String? = null,
    val reportee: String? = null,
    val remarks: MutableList<String> = mutableListOf(),
    var navIdent: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CategoryDTO (
    val id: Long?,
    val code: String,
    var categoryType: String,
    var name: String,
    var description: String? = null,
    var parentId: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CompanyDTO (
    val id: Long?,
    val uuid: String,
    val created: LocalDateTime = LocalDateTime.now(),
    val createdBy: String?,
    val updated: LocalDateTime = LocalDateTime.now(),
    val updatedBy: String? = null,
    val mediaList: MutableList<MediaDTO>? = mutableListOf<MediaDTO>(),
    val contactList: MutableList<ContactDTO> = mutableListOf(),
    val location: LocationDTO? = null,
    val locationList: MutableList<LocationDTO> = mutableListOf(),
    val properties: MutableMap<String, String> = mutableMapOf(),

    val name: String,
    val orgnr: String? = null,
    val status: String? = null,
    val parentOrgnr: String? = null,
    val publicName: String? = null,
    val deactivated: LocalDateTime? = null,
    val orgform: String? = null,
    val employees: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ContactDTO (
    val id: Long?,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val title: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LocationDTO (
    var address: String? = null,
    var postalCode: String? = null,
    var county: String? = null,
    var municipal: String? = null,
    var municipalCode: String? = null,
    var city: String? = null,
    var country: String? = null,
    var latitude: String? = null,
    var longitude: String? = null
)

/**
 * Basic DTO for media content, which contains a filename (with extension) and a media link URL for accessing the media
 * content.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MediaDTO (
    val id: Long?,
    var filename: String,
    var mediaLink:  String? = null
)
