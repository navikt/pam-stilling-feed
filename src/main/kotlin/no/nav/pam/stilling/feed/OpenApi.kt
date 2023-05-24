package no.nav.pam.stilling.feed

import com.fasterxml.jackson.databind.node.ObjectNode
import io.javalin.openapi.BearerAuth
import io.javalin.openapi.OpenApiContact
import io.javalin.openapi.OpenApiInfo
import io.javalin.openapi.OpenApiLicense
import io.javalin.openapi.plugin.DefinitionConfiguration
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.OpenApiPluginConfiguration
import io.javalin.openapi.plugin.SecurityComponentConfiguration
import no.nav.pam.stilling.feed.sikkerhet.Rolle


fun getOpenApiPlugin() = OpenApiPlugin(OpenApiPluginConfiguration()
    .withRoles(Rolle.UNPROTECTED)
    .withDocumentationPath("/api/openapi.json")
    .withDefinitionConfiguration { _: String, definition: DefinitionConfiguration ->
        definition.withOpenApiInfo { openApiInfo: OpenApiInfo ->
                openApiInfo.title = "Public feed of job vacancies on Arbeidsplassen.no"
                openApiInfo.description = "OpenAPI specification for the public feed of job vacancies on Arbeidsplassen.no provided by the Norwegian Labour and Welfare Administration"
                openApiInfo.termsOfService = "https://arbeidsplassen.nav.no/vilkar-api"
                openApiInfo.version = "v1"
                openApiInfo.contact = OpenApiContact().apply {
                    name = "Arbeidsplassen.no"
                    email = "nav.team.arbeidsplassen@nav.no"
                }
                openApiInfo.license = OpenApiLicense().apply {
                    name = "MIT License"
                    identifier = "MIT"
                }
            }
            .withServer { openApiServer ->
                // TODO: Endre til prod-miljø når den eksisterer
                openApiServer.url = "https://pam-stilling-feed.intern.dev.nav.no/"
                openApiServer.description = "Arbeidsplassen.dev.no"
            }
            .withSecurity(SecurityComponentConfiguration().apply {
                withSecurityScheme("BearerAuth", BearerAuth())
            })
            .withDefinitionProcessor {content ->
                // Javalin OpenApi støtter per nå ikke response-headers så vi må legge de på openApi-specen selv
                setResponseHeadersDocumentation(content)
                content.toPrettyString()
            }
    })

private fun setResponseHeadersDocumentation(content: ObjectNode) {
    content["paths"].fields().forEachRemaining {
        if (!listOf("/api/v1/feed", "/api/v1/feed/<feedPageId>").contains(it.key)) return@forEachRemaining

        try {
            val okResponseNode = it.value["get"]["responses"]["200"] as ObjectNode
            okResponseNode.putObject("headers").let { headersNode ->
                headersNode.putObject("ETag").put("description", "Entity tag providing a ID for the resource version.").put("type", "UUID")
                headersNode.putObject("Last-Modified").put("description", "Last modified datetime of resource.").put("type", "RFC 1123 formatted datetime")
            }
        } catch (e: Exception) {
            return@forEachRemaining
        }
    }
}
