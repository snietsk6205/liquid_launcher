@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates

fun Modifier.systemGestureExclusion(): Modifier = this
fun Modifier.systemGestureExclusion(exclusion: (LayoutCoordinates) -> Rect): Modifier = this
