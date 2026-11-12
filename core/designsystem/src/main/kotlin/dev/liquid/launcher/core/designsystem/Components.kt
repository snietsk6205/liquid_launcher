package dev.liquid.launcher.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icons bundled with the design system (Material Symbols paths, Apache-2.0). */
object LauncherIcons {
    val ArrowBack = R.drawable.ic_arrow_back
    val ChevronRight = R.drawable.ic_chevron_right
    val Search = R.drawable.ic_search
    val CheckCircle = R.drawable.ic_check_circle
    val Error = R.drawable.ic_error
    val Info = R.drawable.ic_info
    val CircleOutline = R.drawable.ic_circle_outline
    val Close = R.drawable.ic_close
    val Settings = R.drawable.ic_settings
    val Home = R.drawable.ic_home
}

@Composable
fun LauncherIcon(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
