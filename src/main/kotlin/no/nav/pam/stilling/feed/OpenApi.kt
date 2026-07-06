package no.nav.pam.stilling.feed

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.javalin.openapi.OpenApiInfo
import io.javalin.openapi.plugin.OpenApiPlugin
import no.nav.pam.stilling.feed.sikkerhet.Rolle

fun getOpenApiPlugin() = OpenApiPlugin { openApiConfig ->
    openApiConfig
        .withRoles(Rolle.UNPROTECTED)
        .withDocumentationPath("/api/openapi.json")
        .withDefinitionConfiguration { _: String, definition ->
            definition
                .info { openApiInfo: OpenApiInfo ->
                    openApiInfo
                        .title("Public feed of job vacancies on Arbeidsplassen.no")
                        .description("OpenAPI specification for the public feed of job vacancies on Arbeidsplassen.no provided by the Norwegian Labour and Welfare Administration")
                        .termsOfService("https://arbeidsplassen.nav.no/vilkar-api")
                        .version("v1")
                        .withContact {
                            it.name("Arbeidsplassen.no")
                            it.email("nav.team.arbeidsplassen@nav.no")
                        }
                        .withLicense {
                            it.name("MIT License")
                            it.identifier("MIT")
                        }
                }
                .server { openApiServer ->
                    openApiServer.url("https://pam-stilling-feed.nav.no/")
                    openApiServer.description("Arbeidsplassen.no")
                }
                .withBearerAuth("BearerAuth")
        }
        .withDefinitionProcessor { content: ObjectNode ->
            // Javalin OpenApi støtter per nå ikke response-headers så vi må legge de på openApi-specen selv
            setResponseHeadersDocumentation(content)

            //  Javalin OpenApi tolker per nå ikke @JsonIgnore og @JsonProperty, må håndteres på egenhånd
            removePropertiesFromSchema(content["components"]["schemas"]["Feed"], "etag", "lastModified")
            renamePropertiesInSchema(
                content["components"]["schemas"]["FeedLine"],
                "feed_entry" to "_feed_entry"
            )
            renamePropertiesInSchema(
                content["components"]["schemas"]["FeedEntryContent"],
                "json" to "ad_content"
            )

            content.toPrettyString()
        }
}

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

private fun removePropertiesFromSchema(schemaNode: JsonNode, vararg propsToRemove: String) =
    propsToRemove.forEach { propToRemove ->
        (schemaNode["properties"] as ObjectNode).remove(propToRemove)
        schemaNode["required"].removeAll { it.toString().removeSurrounding("\"") == propToRemove }
    }

private fun renamePropertiesInSchema(schemaNode: JsonNode, vararg propsToRename: Pair<String, String>) =
    propsToRename.forEach { (currentName, newName) ->
        schemaNode["properties"].let {
            (it as ObjectNode).set<ObjectNode>(newName, it[currentName])
            it.remove(currentName)
        }

        schemaNode["required"].removeAll { it.toString().removeSurrounding("\"") == currentName }
        (schemaNode["required"] as ArrayNode).add(newName)
    }
