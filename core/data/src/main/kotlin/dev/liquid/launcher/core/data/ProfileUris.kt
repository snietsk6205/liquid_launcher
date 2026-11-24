package dev.liquid.launcher.core.data

/**
 * 0.16.0 (D205): content URIs of another profile. The system file picker's Work tab (Island's or Insular's File Shuttle,
 * where they have one) hands out URIs whose authority starts with the profile's user number ("10@com.android…"). Kept
 * free of Android so it is tested.
 */
object ProfileUris {
    /** The user number an authority names ("10@…" is 10), or null when it names none (this profile). */
    fun userOf(authority: String?): Int? {
        val at = authority?.indexOf('@') ?: return null
        if (at <= 0) return null
        return authority.substring(0, at).toIntOrNull()
    }

    /** This app's own user number ([android.os.Process.myUid] / 100 000). */
    var myUser: () -> Int = { android.os.Process.myUid() / 100_000 }

    fun isOtherProfile(authority: String?): Boolean = userOf(authority)?.let { it != myUser() } ?: false
}
