package com.albatora.app.data

import android.content.Context
import com.albatora.app.model.AlbaToraDataState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AlbaToraRepository(context: Context) {

    private val dataFile = File(context.applicationContext.filesDir, "albatora_state_v1.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): AlbaToraDataState {
        if (!dataFile.exists()) {
            return AlbaToraDataState.initialEmptyState()
        }
        return runCatching {
            val raw = dataFile.readText(Charsets.UTF_8)
            val decoded = json.decodeFromString<AlbaToraDataState>(raw)
            migrateLegacyState(raw, decoded)
        }.getOrElse {
            AlbaToraDataState.initialEmptyState()
        }
    }

    fun save(state: AlbaToraDataState) {
        runCatching {
            val tmpFile = File(dataFile.parentFile, "${dataFile.name}.tmp")
            tmpFile.writeText(json.encodeToString(state), Charsets.UTF_8)
            tmpFile.renameTo(dataFile)
        }
    }

    fun exportToJson(state: AlbaToraDataState): String = json.encodeToString(state)

    fun importFromJson(rawJson: String): Result<AlbaToraDataState> = runCatching {
        val trimmed = rawJson.trim()
        val decoded = json.decodeFromString<AlbaToraDataState>(trimmed)
        migrateLegacyState(trimmed, decoded)
    }

    companion object {
        /**
         * Upgrades legacy v1.8.2 JSON snapshots that predate onboardingCompleted so existing
         * users with saved data skip first-run onboarding automatically.
         */
        fun migrateLegacyState(rawJson: String, decoded: AlbaToraDataState): AlbaToraDataState {
            val hasExplicitFlag = rawJson.contains("\"onboardingCompleted\"")
            val hasExistingData = decoded.jobs.isNotEmpty() ||
                decoded.records.isNotEmpty() ||
                decoded.templates.isNotEmpty() ||
                decoded.wishlistGoals.isNotEmpty()
            return if (!hasExplicitFlag && hasExistingData) {
                decoded.copy(settings = decoded.settings.copy(onboardingCompleted = true))
            } else {
                decoded
            }
        }
    }
}
