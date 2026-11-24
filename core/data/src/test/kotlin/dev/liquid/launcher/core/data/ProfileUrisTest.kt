package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUrisTest {
    @Test
    fun aWorkProfileUriNamesItsUser() {
        ProfileUris.myUser = { 0 }
        assertEquals(10, ProfileUris.userOf("10@com.android.externalstorage.documents"))
        assertEquals(null, ProfileUris.userOf("com.android.externalstorage.documents"))
        assertEquals(null, ProfileUris.userOf(null))
        assertTrue(ProfileUris.isOtherProfile("10@com.android.providers.media.documents"))
        assertFalse(ProfileUris.isOtherProfile("0@com.android.providers.media.documents"))
        assertFalse(ProfileUris.isOtherProfile("com.android.externalstorage.documents"))
    }
}
