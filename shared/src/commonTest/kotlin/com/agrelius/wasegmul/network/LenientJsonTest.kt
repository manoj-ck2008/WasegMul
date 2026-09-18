package com.agrelius.wasegmul.network

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Crowd-typed OFF payloads must decode without throwing (audit §3.58):
 * `packagings_complete` / `number_of_units` arrive as numbers, booleans or strings.
 */
class LenientJsonTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Test
    fun packagingsComplete_acceptsBoolAndString() {
        val fromBool = json.decodeFromString<OffProductDto>(
            """{"code":"x","packagings_complete":true}"""
        )
        assertEquals(1, fromBool.packagingsComplete)

        val fromString = json.decodeFromString<OffProductDto>(
            """{"code":"x","packagings_complete":"0"}"""
        )
        assertEquals(0, fromString.packagingsComplete)
    }

    @Test
    fun packagingsComplete_garbageBecomesNull() {
        val dto = json.decodeFromString<OffProductDto>(
            """{"code":"x","packagings_complete":"complete-ish"}"""
        )
        assertNull(dto.packagingsComplete)
    }

    @Test
    fun numberOfUnits_acceptsString() {
        val dto = json.decodeFromString<OffPackagingComponentDto>(
            """{"number_of_units":"2"}"""
        )
        assertEquals(2, dto.numberOfUnits)
    }
}
