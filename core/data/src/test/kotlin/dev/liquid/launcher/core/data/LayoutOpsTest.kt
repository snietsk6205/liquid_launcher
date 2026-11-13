package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {
    private val app1 = AppRef("a", "a.Main", 0)
    private val app2 = AppRef("b", "b.Main", 0)

    @Test
    fun defaultLayoutHasAClockAcrossTheTop() {
        val layout = LayoutOps.defaultLayout(columns = 7, rows = 12)
        val clock = layout.pages.single().items.single()
        assertEquals(ItemKind.WIDGET, clock.kind)
        assertEquals(7, clock.w)
        assertEquals(3, clock.h)
    }

    @Test
    fun appsGoToTheFirstFreeCellBelowTheClock() {
        var layout = LayoutOps.defaultLayout(7, 12)
        layout = LayoutOps.addApp(layout, 0, app1, 7, 12)!!
        layout = LayoutOps.addApp(layout, 0, app2, 7, 12)!!
        val apps = layout.pages[0].items.filter { it.kind == ItemKind.APP }
        assertEquals(listOf(0 to 3, 1 to 3), apps.map { it.x to it.y })
        assertTrue(layout.containsApp(app1))
    }

    @Test
    fun addingFailsWhenThePageIsFull() {
        var layout = HomeLayout(pages = listOf(HomePage("p")))
        repeat(4) { layout = LayoutOps.addApp(layout, 0, AppRef("p$it", "c", 0), 2, 2)!! }
        assertNull(LayoutOps.addApp(layout, 0, app1, 2, 2))
    }

    @Test
    fun movingOntoAnOccupiedCellOrOffTheGridIsRefused() {
        var layout = HomeLayout(pages = listOf(HomePage("p")))
        layout = LayoutOps.addApp(layout, 0, app1, 4, 4)!!
        layout = LayoutOps.addApp(layout, 0, app2, 4, 4)!!
        val first = layout.pages[0].items[0]
        assertNull(LayoutOps.moveItem(layout, first.id, 0, 1, 0, 4, 4))
        assertNull(LayoutOps.moveItem(layout, first.id, 0, 4, 0, 4, 4))
        assertNull(LayoutOps.moveItem(layout, first.id, 0, 0, 0, 4, 4))
        val moved = LayoutOps.moveItem(layout, first.id, 0, 3, 3, 4, 4)
        assertNotNull(moved)
        assertEquals(3 to 3, moved!!.find(first.id)!!.second.let { it.x to it.y })
    }

    @Test
    fun movingBetweenPages() {
        var layout = HomeLayout(pages = listOf(HomePage("p"), HomePage("q")))
        layout = LayoutOps.addApp(layout, 0, app1, 4, 4)!!
        val id = layout.pages[0].items[0].id
        val moved = LayoutOps.moveItem(layout, id, 1, 2, 2, 4, 4)!!
        assertTrue(moved.pages[0].items.isEmpty())
        assertEquals(1, moved.find(id)!!.first)
    }

    @Test
    fun aWideItemBlocksAllItsCells() {
        val layout = LayoutOps.defaultLayout(4, 6)
        val page = layout.pages[0]
        assertFalse(LayoutOps.isFree(page, 3, 2, 1, 1, 4, 6))
        assertTrue(LayoutOps.isFree(page, 3, 3, 1, 1, 4, 6))
    }

    @Test
    fun removeItemAndRemoveApp() {
        var layout = HomeLayout(pages = listOf(HomePage("p"), HomePage("q")))
        layout = LayoutOps.addApp(layout, 0, app1, 4, 4)!!
        layout = LayoutOps.addApp(layout, 1, app1, 4, 4)!!
        layout = LayoutOps.addApp(layout, 1, app2, 4, 4)!!
        val withoutOne = LayoutOps.removeItem(layout, layout.pages[1].items[1].id)!!
        assertFalse(withoutOne.containsApp(app2))
        assertFalse(LayoutOps.removeApp(layout, app1).containsApp(app1))
    }

    @Test
    fun theLastPageCannotBeRemoved() {
        val one = HomeLayout(pages = listOf(HomePage("p")))
        assertNull(LayoutOps.removePage(one, 0))
        assertEquals(1, LayoutOps.removePage(LayoutOps.addPage(one), 1)!!.pages.size)
    }

    @Test
    fun undoAndRedo() {
        val repo = LayoutRepository(
            JsonStore(null, HomeLayout.serializer(), HomeLayout(), kotlinx.coroutines.GlobalScope),
        )
        repo.ensureDefault(7, 12)
        val start = repo.layout.value
        assertTrue(repo.commit(LayoutOps.addApp(start, 0, app1, 7, 12)))
        assertFalse(repo.commit(null))
        assertTrue(repo.canUndo.value)
        repo.undo()
        assertEquals(start, repo.layout.value)
        assertTrue(repo.canRedo.value)
        repo.redo()
        assertTrue(repo.layout.value.containsApp(app1))
    }

    @Test
    fun appRefRoundTrips() {
        val ref = AppRef("com.example", "com.example.Main", 10)
        assertEquals(ref, AppRef.parse(ref.key))
        assertNull(AppRef.parse("nonsense"))
        assertEquals(LauncherAction.LaunchApp(ref), LauncherAction.decode(LauncherAction.LaunchApp(ref).encode()))
        assertEquals(LauncherAction.OpenDrawer, LauncherAction.decode("drawer"))
        assertEquals(LauncherAction.None, LauncherAction.decode("???"))
    }

    @Test
    fun pagesCanBeReorderedAndTheDefaultFollowsItsPage() {
        var layout = HomeLayout(pages = listOf(HomePage("a"), HomePage("b"), HomePage("c")))
        layout = LayoutOps.setDefaultPage(layout, 1)!!
        assertEquals(1, layout.defaultIndex)
        layout = LayoutOps.movePage(layout, 1, 2)!!
        assertEquals(listOf("a", "c", "b"), layout.pages.map { it.id })
        assertEquals(2, layout.defaultIndex)
        assertNull(LayoutOps.movePage(layout, 0, 3))
    }

    @Test
    fun removingTheDefaultPageFallsBackToTheFirst() {
        var layout = HomeLayout(pages = listOf(HomePage("a"), HomePage("b")))
        layout = LayoutOps.setDefaultPage(layout, 1)!!
        layout = LayoutOps.removePage(layout, 1)!!
        assertEquals(null, layout.defaultPage)
        assertEquals(0, layout.defaultIndex)
    }

    @Test
    fun widgetsResizeOnlyIntoFreeSpace() {
        var layout = LayoutOps.defaultLayout(5, 9)
        val clock = layout.pages[0].items.single()
        layout = LayoutOps.addApp(layout, 0, app1, 5, 9)!! // lands at (0, 3)
        assertNotNull(LayoutOps.resizeItem(layout, clock.id, 0, 0, 5, 2, 5, 9))
        assertNull(LayoutOps.resizeItem(layout, clock.id, 0, 0, 5, 4, 5, 9))
        assertNull(LayoutOps.resizeItem(layout, clock.id, 0, 0, 6, 3, 5, 9))
        val wider = LayoutOps.resizeItem(layout, clock.id, 1, 0, 4, 3, 5, 9)!!
        assertEquals(1, wider.find(clock.id)!!.second.x)
    }

    @Test
    fun swipeActionsAreSetAndCleared() {
        var layout = LayoutOps.addApp(LayoutOps.defaultLayout(5, 9), 0, app1, 5, 9)!!
        val id = layout.pages[0].items.first { it.kind == ItemKind.APP }.id
        layout = LayoutOps.setSwipe(layout, id, up = true, action = "notifications")!!
        assertEquals("notifications", layout.find(id)!!.second.swipeUp)
        assertNull(LayoutOps.setSwipe(layout, id, up = true, action = "notifications"))
        layout = LayoutOps.setSwipe(layout, id, up = true, action = "none")!!
        assertNull(layout.find(id)!!.second.swipeUp)
    }

    @Test
    fun droppingAnAppOnAnAppMakesAFolderAndMoreJoinIt() {
        val app3 = AppRef("c", "c.Main", 0)
        var layout = HomeLayout(pages = listOf(HomePage("p")))
        layout = LayoutOps.dropApp(layout, 0, 0, 0, app1, 4, 4)!!
        layout = LayoutOps.dropApp(layout, 0, 0, 0, app2, 4, 4)!!
        val folder = layout.pages[0].items.single()
        assertEquals(ItemKind.FOLDER, folder.kind)
        assertEquals(listOf(app1, app2), folder.folder)
        assertNull(LayoutOps.dropApp(layout, 0, 0, 0, app2, 4, 4))
        layout = LayoutOps.dropApp(layout, 0, 1, 0, app3, 4, 4)!!
        val moved = layout.pages[0].items.first { it.app == app3 }.id
        layout = LayoutOps.dropItem(layout, moved, 0, 0, 0, 4, 4)!!
        assertEquals(listOf(app1, app2, app3), layout.pages[0].items.single().folder)
        assertTrue(layout.containsApp(app3))
    }

    @Test
    fun takingAppsOutOfAFolderUnwrapsItAtOne() {
        var layout = HomeLayout(pages = listOf(HomePage("p")))
        layout = LayoutOps.dropApp(layout, 0, 0, 0, app1, 4, 4)!!
        layout = LayoutOps.dropApp(layout, 0, 0, 0, app2, 4, 4)!!
        val id = layout.pages[0].items.single().id
        layout = LayoutOps.takeFromFolder(layout, id, app2, 4, 4)!!
        val items = layout.pages[0].items
        assertEquals(2, items.size)
        assertEquals(ItemKind.APP, items.first { it.id == id }.kind)
        assertEquals(app1, items.first { it.id == id }.app)
        assertEquals(app2, items.first { it.id != id }.app)
    }

    @Test
    fun pageGridOnlyWhenItemsFit() {
        val base = LayoutOps.defaultLayout(5, 9)
        val withApp = LayoutOps.addApp(base, 0, AppRef("a", "A", 0), 5, 9)!!
        val item = withApp.pages[0].items.last()
        val tooSmall = LayoutOps.setPageSettings(withApp, 0, item.x, null, null, null, 5, 9)
        org.junit.Assert.assertNull(tooSmall)
        val ok = LayoutOps.setPageSettings(withApp, 0, null, 9, false, " Study ", 5, 9)!!
        org.junit.Assert.assertEquals(9, ok.pages[0].rows)
        org.junit.Assert.assertEquals(false, ok.pages[0].labels)
        org.junit.Assert.assertEquals("Study", ok.pages[0].name)
    }
}
