package dev.liquid.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietRulesTest {
    private val own = "dev.liquid.launcher"
    private fun through(n: QuietRules.Incoming, people: String = "", emails: String = "", tags: String = "important") =
        QuietRules.letThrough(n, QuietRules.list(people), QuietRules.list(emails), QuietRules.list(tags), own)

    @Test
    fun callsAlarmsOngoingAndTheSystemAlwaysGetThrough() {
        assertTrue(through(QuietRules.Incoming("com.whatsapp", category = "call")))
        assertTrue(through(QuietRules.Incoming("com.google.android.deskclock", category = "alarm")))
        assertTrue(through(QuietRules.Incoming("com.spotify.music", ongoing = true)))
        assertTrue(through(QuietRules.Incoming("android")))
        assertTrue(through(QuietRules.Incoming(own)))
        assertFalse(through(QuietRules.Incoming("com.instagram.android", category = "social", title = "New like")))
    }

    @Test
    fun peopleByWholeName_emailByAccount_appsByTag() {
        val msg = QuietRules.Incoming("com.whatsapp", category = "msg", title = "Maman", text = "Tu rentres quand ?")
        assertTrue(through(msg, people = "maman, Yassine"))
        assertFalse(through(msg, people = "Yassine"))
        // "Ali" is not "Alice".
        assertFalse(through(QuietRules.Incoming("com.whatsapp", title = "Alice"), people = "Ali"))
        assertTrue(through(QuietRules.Incoming("com.whatsapp", title = "Ali (2 messages)"), people = "Ali"))
        assertTrue(through(QuietRules.Incoming("org.telegram", people = listOf("Prof Ben Salah")), people = "Ben Salah"))
        val school = QuietRules.Incoming("com.google.android.gm", category = "email", title = "Scolarité", text = "Planning DS", subText = "med@essai.ucar.tn")
        val personal = school.copy(subText = "med@gmail.com")
        assertTrue(through(school, emails = "@essai.ucar.tn"))
        assertFalse(through(personal, emails = "@essai.ucar.tn"))
        assertTrue(through(QuietRules.Incoming("com.bank", tags = setOf("Important"))))
        assertFalse(through(QuietRules.Incoming("com.bank", tags = setOf("finance"))))
    }

    @Test
    fun theSummaryCountsByAppMostFirst() {
        val held = List(6) { "WhatsApp" } + List(5) { "Instagram" } + List(3) { "Gmail" } + listOf("X", "Y")
        assertEquals("WhatsApp 6 · Instagram 5 · Gmail 3 · X 1 · others 1", QuietRules.summary(held))
        assertEquals("Gmail 1", QuietRules.summary(listOf("Gmail")))
    }
}
