@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.compose.ui.viewinterop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun <T : android.view.View> AndroidView(
    factory: (android.content.Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {},
) {}

@Composable
fun <T : android.view.View> AndroidView(
    factory: (android.content.Context) -> T,
    modifier: Modifier = Modifier,
    onReset: ((T) -> Unit)? = null,
    onRelease: (T) -> Unit = {},
    update: (T) -> Unit = {},
) {}
