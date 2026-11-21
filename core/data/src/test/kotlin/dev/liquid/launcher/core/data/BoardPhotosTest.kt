package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardPhotosTest {
    @Test
    fun aPendingPhotoSurvivesBeingWrittenDown() {
        val p = BoardPhotos.Pending("/data/cache/photos/1700000000000.jpg", "TD micro", "2026-11-21_TD micro.jpg", 1_700_000_000_000L)
        assertEquals(p, BoardPhotos.decode(BoardPhotos.encode(p)))
    }

    @Test
    fun nothingOrSomethingElseIsNoPendingPhoto() {
        assertNull(BoardPhotos.decode(""))
        assertNull(BoardPhotos.decode("only one part"))
        assertNull(BoardPhotos.decode("/a\nb\nc\nnot a time"))
    }

    @Test
    fun aModuleBecomesAFolderName() {
        assertEquals("TD micro", BoardPhotos.folderName("TD micro"))
        assertEquals("Micro chapitre 3", BoardPhotos.folderName("Micro: chapitre 3!"))
        assertEquals("Notes", BoardPhotos.folderName("::"))
        assertEquals("Économie", BoardPhotos.folderName("Économie"))
    }
}
