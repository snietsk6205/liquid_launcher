@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.datastore.core

interface DataStore<T> {
    val data: kotlinx.coroutines.flow.Flow<T>
    suspend fun updateData(transform: suspend (t: T) -> T): T
}
