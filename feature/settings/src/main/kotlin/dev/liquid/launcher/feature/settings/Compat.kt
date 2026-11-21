package dev.liquid.launcher.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow

/** Short alias so call sites read the same for every StateFlow. */
@Composable
internal fun <T> StateFlow<T>.collectAsStateCompat(): State<T> = collectAsState()
