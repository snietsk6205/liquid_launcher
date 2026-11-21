package dev.liquid.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticesTest {
    @Test
    fun newestOfAKindReplacesTheWaitingOne_andStaleOnesAreDropped() {
        var now = 1000L
        Notices.clock = { now }
        while (Notices.next() != null) Unit
        Notices.post(NoticeKind.Layout, "Study")
        Notices.post(NoticeKind.TagFilter, "Media")
        Notices.post(NoticeKind.Layout, "Projects")
        assertEquals("Media", Notices.next()?.text)
        assertEquals("Projects", Notices.next()?.text)
        assertNull(Notices.next())

        Notices.post(NoticeKind.TagFilter, "Tools")
        Notices.post(NoticeKind.Update, "Update available")
        now += NoticeKind.TagFilter.patienceMs + 1
        // The tag notice waited too long; the update notice keeps for hours.
        assertEquals("Update available", Notices.next()?.text)
        assertNull(Notices.next())
    }
}
