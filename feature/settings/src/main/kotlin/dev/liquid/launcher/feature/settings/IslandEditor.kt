package dev.liquid.launcher.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.liquid.launcher.core.data.IslandProfile

/**
 * Settings › Wallpaper and layouts › Folders and Island › More (0.16.0, D205): what the launcher sees of Island or Insular
 * (only from outside: whether the app is there, whether a work profile exists and is paused) and, in plain words, the
 * ways to reach its pictures: File Shuttle where Insular has it, else an ordinary folder or one synced with Syncthing.
 * Nothing here assumes File Shuttle exists (Med: Insular may not have it).
 */
@Composable
internal fun IslandEditor() {
    val context = LocalContext.current
    val tick by IslandProfile.changes.collectAsState()
    val app = remember(tick) { IslandProfile.appName(context) }
    val profile = remember(tick) { IslandProfile.profile(context) != null }
    val paused = remember(tick) { IslandProfile.paused(context) }
    InfoCard {
        Text("Island and Insular", style = MaterialTheme.typography.titleMedium)
        BodyText(
            when {
                app == null && !profile -> "Neither Island nor Insular is on this phone, and there is no work profile. Linked folders and \"+ Files\" work with the phone's own folders."
                app == null -> "There is a work profile, but neither Island nor Insular was found."
                !profile -> "$app is installed, but no work profile was found."
                paused -> "$app's work profile is paused: its files cannot be read now. The library shows saved copies until it is back."
                else -> "$app is installed and its work profile is on."
            },
        )
        BodyText(
            "To use pictures kept inside ${app ?: "Island"}: in the wallpaper screen, \"+ Files\" (single pictures) or \"+ Folder\" " +
                "(a whole folder) opens Android's file picker. If ${app ?: "Island"} has File Shuttle and it is on, the picker shows a " +
                "Work tab next to Personal: pick there. Every picture applied is copied into the launcher, so the wallpaper keeps " +
                "working while ${app ?: "Island"} is frozen, paused or locked.",
        )
        BodyText(
            "If the Work tab never shows (some versions of Insular have no File Shuttle), keep your wallpapers in an ordinary " +
                "folder of the phone, or sync a folder from ${app ?: "Island"} to the phone with Syncthing, and link that folder with " +
                "\"+ Folder\". Hold a folder's chip in the library to choose which layouts use it.",
        )
    }
}
