package dev.liquid.launcher.feature.settings

import androidx.compose.runtime.Composable

/**
 * Live previews shown at the top of a settings section. The home module draws them; the app
 * wires it up at start-up, so this module does not depend on the home module.
 */
object SettingsPreviews {
    /** Section ids that have a preview. */
    var sections: Set<String> = emptySet()
    var provider: (@Composable (sectionId: String) -> Unit)? = null
}
