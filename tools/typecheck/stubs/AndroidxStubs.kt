// Type-check stubs for the few androidx APIs that are only on Google's Maven (not reachable here).
// Signatures follow the real libraries closely enough for the launcher's calls; bodies never run.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext: ProvidableCompositionLocal<android.content.Context> = staticCompositionLocalOf { error("stub") }
val LocalView: ProvidableCompositionLocal<android.view.View> = staticCompositionLocalOf { error("stub") }

// Android-only parts of Compose foundation used by the launcher.
