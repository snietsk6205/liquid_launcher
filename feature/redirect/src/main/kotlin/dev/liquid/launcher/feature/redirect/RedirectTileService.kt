package dev.liquid.launcher.feature.redirect

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.liquid.launcher.core.settings.LauncherSettings
import dev.liquid.launcher.core.settings.SettingsStore

/** Quick Settings tile that switches the redirect on and off. */
class RedirectTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val store = SettingsStore.get(this)
        store.set(LauncherSettings.RedirectEnabled, !store.get(LauncherSettings.RedirectEnabled))
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val enabled = SettingsStore.get(this).get(LauncherSettings.RedirectEnabled)
        val connected = RedirectStatus.connected.value
        tile.state = if (enabled && connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !connected -> "Service off"
            enabled -> "On"
            else -> "Off"
        }
        tile.updateTile()
    }
}
