package me.brosssh.bundles.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HasuraMetadataTest {
    private val tables: JsonArray by lazy {
        val metadata = requireNotNull(javaClass.getResourceAsStream("/hasura/metadata.json"))
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }
        metadata["metadata"]!!.jsonObject["sources"]!!.jsonArray
            .single().jsonObject["tables"]!!.jsonArray
    }

    @Test
    fun `public bundle and patch data requires an enabled source`() {
        assertEquals(enabledSourceFilter(), userFilter("bundle"))
        assertEquals(
            buildJsonObject { put("bundle", enabledSourceFilter()) },
            userFilter("patch")
        )
        assertEquals(
            buildJsonObject {
                put("patch", buildJsonObject { put("bundle", enabledSourceFilter()) })
            },
            userFilter("patch_package")
        )
    }

    @Test
    fun `public source list includes enabled state without filtering disabled sources`() {
        val permission = userPermission("source")
        val columns = permission["columns"]!!.jsonArray.map { it.jsonPrimitive.content }

        assertTrue("enabled" in columns)
        assertEquals(JsonObject(emptyMap()), permission["filter"])
    }

    @Test
    fun `public bundle data does not expose raw patcher failures`() {
        val columns = userPermission("bundle")["columns"]!!.jsonArray
            .map { it.jsonPrimitive.content }

        assertFalse("patcher_failure" in columns)
    }

    private fun enabledSourceFilter() = buildJsonObject {
        put("source", buildJsonObject {
            put("enabled", buildJsonObject {
                put("_eq", JsonPrimitive(true))
            })
        })
    }

    private fun userFilter(tableName: String) = userPermission(tableName)["filter"]!!.jsonObject

    private fun userPermission(tableName: String): JsonObject {
        val table = tables.single {
            it.jsonObject["table"]!!.jsonObject["name"]!!.jsonPrimitive.content == tableName
        }.jsonObject
        return table["select_permissions"]!!.jsonArray.single {
            it.jsonObject["role"]!!.jsonPrimitive.content == "user"
        }.jsonObject["permission"]!!.jsonObject
    }
}
