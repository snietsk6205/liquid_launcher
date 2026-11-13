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

/** Icons bundled with the design system (Material Icons paths, Apache-2.0). */
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
    val Apps = R.drawable.ic_apps
    val Notifications = R.drawable.ic_notifications
    val Lock = R.drawable.ic_lock
    val Edit = R.drawable.ic_edit
    val Add = R.drawable.ic_add
    val Delete = R.drawable.ic_delete
    val Undo = R.drawable.ic_undo
    val Redo = R.drawable.ic_redo
    val Check = R.drawable.ic_check
    val VisibilityOff = R.drawable.ic_visibility_off
    val Visibility = R.drawable.ic_visibility
    val RemoveCircle = R.drawable.ic_remove_circle
    val AddCircle = R.drawable.ic_add_circle
    val Work = R.drawable.ic_work
    val Calendar = R.drawable.ic_calendar
    val Schedule = R.drawable.ic_schedule
    val Tune = R.drawable.ic_tune
    val Recents = R.drawable.ic_recents
    val DragHandle = R.drawable.ic_drag_handle
    val ArrowUp = R.drawable.ic_arrow_up
    val ArrowDown = R.drawable.ic_arrow_down
    val Space = R.drawable.ic_space
    val Web = R.drawable.ic_web
    val Calculate = R.drawable.ic_calculate
    val Label = R.drawable.ic_label
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
