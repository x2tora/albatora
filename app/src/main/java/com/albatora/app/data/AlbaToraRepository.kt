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
            json.decodeFromString<AlbaToraDataState>(raw)
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
        json.decodeFromString<AlbaToraDataState>(rawJson.trim())
    }
}
