package com.albatora.app

import com.albatora.app.data.AlbaToraRepository
import com.albatora.app.model.AlbaToraDataState
import com.albatora.app.model.Job
import com.albatora.app.model.UserPersona
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun legacyJsonWithExistingJobsSkipsOnboardingAutomatically() {
        val legacyRawJson = """
            {
              "jobs": [
                {
                  "id": "job_1",
                  "name": "편의점",
                  "baseAmount": 10320
                }
              ],
              "settings": {
                "defaultMinWage": 10320
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<AlbaToraDataState>(legacyRawJson)
        val migrated = AlbaToraRepository.migrateLegacyState(legacyRawJson, decoded)

        assertTrue(migrated.settings.onboardingCompleted)
        assertEquals(UserPersona.MULTI_JOB, migrated.settings.userPersona)
    }

    @Test
    fun legacyEmptyJsonShowsOnboarding() {
        val emptyRawJson = """
            {
              "jobs": [],
              "records": [],
              "settings": {
                "defaultMinWage": 10320
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<AlbaToraDataState>(emptyRawJson)
        val migrated = AlbaToraRepository.migrateLegacyState(emptyRawJson, decoded)

        assertFalse(migrated.settings.onboardingCompleted)
    }

    @Test
    fun explicitReopenedOnboardingFlagIsPreservedEvenWithData() {
        val stateWithDataAndReopened = AlbaToraDataState(
            jobs = listOf(Job(id = "job_1", name = "카페")),
        )
        val encoded = json.encodeToString(stateWithDataAndReopened)
        val decoded = json.decodeFromString<AlbaToraDataState>(encoded)
        val migrated = AlbaToraRepository.migrateLegacyState(encoded, decoded)

        assertFalse(migrated.settings.onboardingCompleted)
    }
}
