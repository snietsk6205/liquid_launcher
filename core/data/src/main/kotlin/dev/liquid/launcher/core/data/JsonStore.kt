package dev.liquid.launcher.core.data

import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

internal val LauncherJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    prettyPrint = true
}

/**
 * A small document kept in memory and saved as one JSON file. Reads are instant; writes go
 * to memory at once and to disk a moment later, through a temporary file so a crash cannot
 * leave a half-written document.
 */
class JsonStore<T>(
    private val file: File?,
    private val serializer: KSerializer<T>,
    default: T,
    private val scope: CoroutineScope,
    private val writeDelayMs: Long = 400,
) {
    private val _value = MutableStateFlow(load(default))
    val value: StateFlow<T> = _value
    private var pending: Job? = null

    private fun load(default: T): T {
        val source = file ?: return default
        if (!source.exists()) return default
        return try {
            LauncherJson.decodeFromString(serializer, source.readText())
        } catch (e: Exception) {
            DebugLog.error("Data", "could not read ${source.name}, starting fresh: $e")
            source.renameTo(File(source.parentFile, source.name + ".broken"))
            default
        }
    }

    fun get(): T = _value.value

    fun set(newValue: T) {
        if (newValue == _value.value) return
        _value.value = newValue
        val target = file ?: return
        pending?.cancel()
        pending = scope.launch {
            delay(writeDelayMs)
            try {
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, target.name + ".tmp")
                temp.writeText(LauncherJson.encodeToString(serializer, newValue))
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.renameTo(target)
                }
            } catch (e: Exception) {
                DebugLog.error("Data", "could not write ${target.name}: $e")
            }
        }
    }

    inline fun update(transform: (T) -> T) = set(transform(get()))
}
