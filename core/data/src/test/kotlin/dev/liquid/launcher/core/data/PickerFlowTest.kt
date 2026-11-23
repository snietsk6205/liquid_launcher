package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.16.0 (D197): the wallpaper screen's steps, in the order Med used them when the preview vanished after Done. */
class PickerFlowTest {
    private val a = PickerFlow.keyOfLibrary("a")
    private val b = PickerFlow.keyOfLibrary("b")

    @Test
    fun selectPlaceDoneApply() {
        var f = PickerFlow()
        // Tap: ringed at once; Apply waits until it has loaded.
        f = f.tap(a)
        assertEquals(a, f.chosen)
        assertTrue(f.waiting)
        assertNull(f.applies)
        assertEquals(PickerFlow.Shows.CURRENT, f.shows)
        f = f.loaded(a)
        assertEquals(a, f.applies)
        // Place: full screen, the sheet steps aside (and comes back where it was).
        f = f.place()
        assertEquals(PickerFlow.Shows.PLACING, f.shows)
        assertTrue(f.sheetAside)
        // Done: the placed picture stays on the screen behind the sheet (it faded back to the old wallpaper up to 0.15.3).
        f = f.done()
        assertEquals(PickerFlow.Shows.PLACED, f.shows)
        assertFalse(f.sheetAside)
        assertEquals(a, f.chosen)
        // Apply sets the picture placed.
        assertEquals(a, f.applies)
        assertFalse(f.waiting)
    }

    @Test
    fun anotherPickEndsThePreviewAndTheSamePickKeepsIt() {
        var f = PickerFlow().tap(a).loaded(a).place().done()
        // Tapping the placed picture again keeps it, placement and preview included.
        assertEquals(f, f.tap(a))
        f = f.tap(b)
        assertEquals(PickerFlow.Shows.CURRENT, f.shows)
        assertNull(f.applies)
        // An older tap that loads late changes nothing.
        assertEquals(f, f.loaded(a))
        f = f.loaded(b)
        assertEquals(b, f.applies)
    }

    @Test
    fun placeWaitsForAPictureAndAFailedLoadGoesBack() {
        assertEquals(PickerFlow(), PickerFlow().place())
        var f = PickerFlow().tap(a).loaded(a).tap(b)
        // Still loading b: Place does not place a.
        assertFalse(f.place().placing)
        f = f.failed(b)
        assertEquals(a, f.chosen)
        assertEquals(a, f.applies)
    }

    @Test
    fun peekAndSelectingNeverPick() {
        var f = PickerFlow().tap(a).loaded(a)
        f = f.peek(b)
        assertEquals(PickerFlow.Shows.PEEK, f.shows)
        assertTrue(f.sheetAside)
        assertEquals(a, f.applies)
        f = f.endPeek()
        f = f.startSelecting(b)
        f = f.tap(a)
        assertEquals(setOf(a, b), f.selection)
        assertEquals(a, f.chosen)
        f = f.tap(b)
        assertEquals(setOf(a), f.selection)
        f = f.endSelecting()
        assertTrue(f.selection.isEmpty())
        // The peek's "Pick" picks it.
        f = f.peek(b).pickPeeked()
        assertEquals(b, f.chosen)
        assertNull(f.peeking)
    }

    @Test
    fun backGoesOneStepAtATime() {
        var f: PickerFlow? = PickerFlow().tap(a).loaded(a).place()
        f = f!!.peek(b)
        f = f.back()
        assertEquals(PickerFlow.Shows.PLACING, f!!.shows)
        // Back while placing keeps the placement, like Done.
        f = f.back()
        assertEquals(PickerFlow.Shows.PLACED, f!!.shows)
        assertNull(f.back())
        assertNull(PickerFlow().startSelecting().back()!!.back())
    }

    @Test
    fun deletedPicturesLeaveTheFlow() {
        var f = PickerFlow().tap(a).loaded(a).place().done().startSelecting(a).toggle(b)
        f = f.without(setOf(a))
        assertNull(f.loaded)
        assertNull(f.chosen)
        assertFalse(f.placed)
        assertEquals(setOf(b), f.selection)
    }
}
