package dev.liquid.launcher.feature.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.designsystem.liquidGlass
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Current time, updated at the start of each minute. */
@Composable
fun rememberMinuteTick(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000 + 25)
        }
    }
    return now
}

/** Big digital clock that fills the space it is given. */
@Composable
fun ClockWidget(
    config: ClockConfig,
    color: Color,
    shadow: Shadow?,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
) {
    val now = rememberMinuteTick()
    val time = remember(now, config.h24) {
        LocalTime.now().format(DateTimeFormatter.ofPattern(if (config.h24) "HH:mm" else "h:mm", Locale.getDefault()))
    }
    val date = remember(now) { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())) }
    val shape = RoundedCornerShape(28.dp)
    val textColor = if (config.glass) {
        if (glass.lightGlass(Surfaces.Clock, lightBackground)) Color(0xFF16181C) else Color.White
    } else {
        color
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
            .then(if (config.glass) Modifier.liquidGlass(backdrop, shape, glass.style(Surfaces.Clock, lightBackground)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        // Digits are about 0.6 em wide; leave room for the date line when shown.
        val heightShare = if (config.showDate) 0.62f else 0.78f
        val byHeight = maxHeight.value * heightShare
        val byWidth = maxWidth.value / (time.length * 0.62f)
        val sizeDp = minOf(byHeight, byWidth) * config.scale
        val fontSize = with(density) { sizeDp.dp.toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                style = TextStyle(
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Light,
                    shadow = if (config.glass) null else shadow,
                ),
                maxLines = 1,
            )
            if (config.showDate) {
                Text(
                    text = date,
                    style = TextStyle(color = textColor.copy(alpha = 0.85f), fontSize = fontSize * 0.2f, shadow = if (config.glass) null else shadow),
                    maxLines = 1,
                )
            }
        }
    }
}
