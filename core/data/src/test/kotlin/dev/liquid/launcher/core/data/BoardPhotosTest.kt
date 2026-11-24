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

    // 0.15.3 (D196): the launcher's camera names each photo of a series after its date, class and time.
    @Test
    fun aSeriesOfPhotosGetsNamesOfTheirOwn() {
        val zone = java.time.ZoneId.of("Africa/Tunis")
        val first = java.time.ZonedDateTime.of(2026, 11, 23, 10, 59, 54, 0, zone)
        assertEquals("2026-11-23_Stat Des cours_10-59-54.jpg", BoardPhotos.photoName("Stat Des cours", first))
        assertEquals("2026-11-23_Stat Des cours_11-00-06.jpg", BoardPhotos.photoName("Stat Des cours", first.plusSeconds(12)))
        assertEquals("2026-11-23_Micro chapitre 3_09-05-01.jpg", BoardPhotos.photoName("Micro: chapitre 3!", first.withHour(9).withMinute(5).withSecond(1)))
    }

    @Test
    fun aPhotoNameKeepsPlainDigitsInAnyLanguage() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-TN-u-nu-arab"))
            val at = java.time.ZonedDateTime.of(2026, 11, 23, 8, 7, 6, 0, java.time.ZoneId.of("UTC"))
            assertEquals("2026-11-23_Algo_08-07-06.jpg", BoardPhotos.photoName("Algo", at))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }
}
