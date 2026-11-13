package dev.liquid.launcher.core.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.liquid.launcher.core.common.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Values of all settings. Reads come from an in-memory map, so they are instant and safe to
 * call from the accessibility service; writes update the map at once and persist in the
 * background. Only values that differ from the default are stored.
 */
class SettingsStore internal constructor(
    private val dataStore: DataStore<Preferences>?,
    private val scope: CoroutineScope,
    initial: Map<String, Any>,
) {
    private val _values = MutableStateFlow(initial)

    /** Stored (non-default) values by key. */
    val values: StateFlow<Map<String, Any>> = _values

    @Suppress("UNCHECKED_CAST")
    fun <T> get(setting: Setting<T>): T = decode(setting, _values.value[setting.key])

    fun <T> flow(setting: Setting<T>): Flow<T> =
        _values.map { decode(setting, it[setting.key]) }.distinctUntilChanged()

    fun isChanged(setting: Setting<*>): Boolean = _values.value.containsKey(setting.key)

    fun <T> set(setting: Setting<T>, value: T) {
        if (value == setting.default) {
            reset(setting)
            return
        }
        val encoded = encode(setting, value)
        _values.update { it + (setting.key to encoded) }
        persist { prefs ->
            when (encoded) {
                is Boolean -> prefs[booleanPreferencesKey(setting.key)] = encoded
                is Int -> prefs[intPreferencesKey(setting.key)] = encoded
                else -> prefs[stringPreferencesKey(setting.key)] = encoded.toString()
            }
        }
    }

    fun reset(setting: Setting<*>) {
        if (!isChanged(setting)) return
        _values.update { it - setting.key }
        persist { prefs ->
            prefs.remove(booleanPreferencesKey(setting.key))
            prefs.remove(intPreferencesKey(setting.key))
            prefs.remove(stringPreferencesKey(setting.key))
        }
    }

    private fun persist(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val store = dataStore ?: return
        scope.launch {
            try {
                store.edit { block(it) }
            } catch (e: Exception) {
                DebugLog.error("Settings", "write failed: $e")
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        /**
         * The process-wide store. The first call reads the file on the calling thread (a few
         * milliseconds) so that every later read is in memory.
         */
        fun get(context: Context): SettingsStore {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val dataStore = context.applicationContext.settingsDataStore
                val initial = try {
                    runBlocking { dataStore.data.first() }.asMap().mapKeys { it.key.name }
                } catch (e: Exception) {
                    DebugLog.error("Settings", "read failed, using defaults: $e")
                    emptyMap()
                }
                val store = SettingsStore(
                    dataStore = dataStore,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    initial = initial,
                )
                instance = store
                return store
            }
        }

        /** A store that keeps values in memory only, for tests. */
        fun inMemory(initial: Map<String, Any> = emptyMap()): SettingsStore =
            SettingsStore(null, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), initial)

        internal const val LIST_SEPARATOR = "\n"

        internal fun encode(setting: Setting<*>, value: Any?): Any = when (setting) {
            is StringListSetting -> (value as List<*>).joinToString(LIST_SEPARATOR)
            else -> value as Any
        }

        @Suppress("UNCHECKED_CAST")
        internal fun <T> decode(setting: Setting<T>, stored: Any?): T {
            if (stored == null) return setting.default
            return when (setting) {
                is BoolSetting -> (stored as? Boolean ?: setting.default) as T
                is IntSetting -> ((stored as? Int)?.coerceIn(setting.min, setting.max) ?: setting.default) as T
                is ChoiceSetting -> {
                    val value = stored as? String
                    (if (setting.options.any { it.first == value }) value else setting.default) as T
                }
                is TextSetting -> (stored as? String ?: setting.default) as T
                is ActionSetting -> (stored as? String ?: setting.default) as T
                is StringListSetting -> {
                    val text = stored as? String ?: return setting.default
                    text.split(LIST_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() } as T
                }
            }
        }
    }
}

/** Observes one setting from Compose. */
@Composable
fun <T> SettingsStore.observe(setting: Setting<T>): State<T> {
    val flow = remember(this, setting) { flow(setting) }
    return flow.collectAsState(initial = get(setting))
}

/**
 * Builds a value from several settings and recomposes only when that value changes. Use a
 * data class so unrelated settings (for example the redirect counters, which change on every
 * redirect) never recompose the caller. [build] must not capture changing state.
 */
@Composable
fun <T> SettingsStore.derive(build: SettingsStore.() -> T): State<T> {
    val flow = remember(this) { values.map { build() }.distinctUntilChanged() }
    return flow.collectAsState(initial = build())
}
