@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore

abstract class Preferences internal constructor() {
    class Key<T> internal constructor(val name: String)
    abstract operator fun <T> contains(key: Key<T>): Boolean
    abstract operator fun <T> get(key: Key<T>): T?
    abstract fun asMap(): Map<Key<*>, Any>
    fun toMutablePreferences(): MutablePreferences = error("stub")
}

class MutablePreferences internal constructor() : Preferences() {
    override fun <T> contains(key: Key<T>): Boolean = false
    override fun <T> get(key: Key<T>): T? = null
    override fun asMap(): Map<Key<*>, Any> = emptyMap()
    operator fun <T> set(key: Key<T>, value: T) {}
    fun <T> remove(key: Key<T>): T = error("stub")
    fun clear() {}
}

fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = error("stub")
fun intPreferencesKey(name: String): Preferences.Key<Int> = error("stub")
fun longPreferencesKey(name: String): Preferences.Key<Long> = error("stub")
fun floatPreferencesKey(name: String): Preferences.Key<Float> = error("stub")
fun stringPreferencesKey(name: String): Preferences.Key<String> = error("stub")
fun stringSetPreferencesKey(name: String): Preferences.Key<Set<String>> = error("stub")
suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences = error("stub")
