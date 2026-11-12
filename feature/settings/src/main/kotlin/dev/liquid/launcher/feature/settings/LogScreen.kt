package dev.liquid.launcher.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liquid.launcher.core.common.DebugLog
import dev.liquid.launcher.core.common.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version by DebugLog.version.collectAsStateCompat()
    val entries = remember(version) { DebugLog.snapshot().asReversed() }
    val time = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    ScreenScaffold(
        title = "Debug log",
        onBack = onBack,
        scroll = false,
        actions = {
            TextButton(onClick = { shareText(context, "Liquid Launcher debug log", DebugLog.format()) }) {
                Text("Share")
            }
            TextButton(onClick = { DebugLog.clear() }) { Text("Clear") }
        },
    ) {
        if (entries.isEmpty()) {
            BodyText("Nothing recorded yet. Newest entries appear at the top.", Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries) { entry ->
                Text(
                    text = "${time.format(Date(entry.wallTime))}  ${entry.message}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = when (entry.level) {
                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                            LogLevel.DECISION -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
    }
}
