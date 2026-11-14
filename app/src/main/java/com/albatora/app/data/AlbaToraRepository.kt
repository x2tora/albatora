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
            // Keep default tangible items only if tangible list was somehow emptied completely
            if (decoded.tangibleItems.isEmpty()) {
                decoded.copy(tangibleItems = AlbaToraDataState.defaultTangibleItems())
            } else {
                decoded
            }
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
        val parsed = json.decodeFromString<AlbaToraDataState>(rawJson.trim())
        if (parsed.tangibleItems.isEmpty()) {
            parsed.copy(tangibleItems = AlbaToraDataState.defaultTangibleItems())
        } else {
            parsed
        }
    }
}
