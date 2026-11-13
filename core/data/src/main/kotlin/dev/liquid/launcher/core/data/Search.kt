package dev.liquid.launcher.core.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Typo-tolerant matching of a query against a label. Higher scores are better; null is no match. */
object Fuzzy {
    private val marks = Regex("\\p{Mn}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Lower case, accents removed, separators collapsed to single spaces. */
    fun normalize(text: String): String =
        marks.replace(Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
            .replace(separators, " ").trim()

    fun score(query: String, label: String): Int? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val l = normalize(label)
        if (l.isEmpty()) return null
        val compactQ = q.replace(" ", "")
        val compactL = l.replace(" ", "")
        val words = l.split(' ')
        return when {
            l == q || compactL == compactQ -> 1000
            l.startsWith(q) || compactL.startsWith(compactQ) -> 900 - minOf(l.length - q.length, 50)
            words.any { it.startsWith(q) } -> 800
            initials(compactQ, words) -> 750
            l.contains(q) -> 600 - minOf(l.indexOf(q), 50)
            else -> subsequence(compactQ, compactL) ?: typo(compactQ, compactL, words)
        }
    }

    /** "yt" matches "You Tube", "gm" matches "Google Maps": letters match word starts in order. */
    private fun initials(q: String, words: List<String>): Boolean {
        if (q.length < 2 || words.size < 2) return false
        val starts = words.mapNotNull { it.firstOrNull() }.joinToString("")
        if (starts.startsWith(q)) return true
        // Also "yt" in "youtube" written as one word with capitals is lost by now; good enough.
        return false
    }

    /** All query letters appear in order. Fewer gaps score higher. */
    private fun subsequence(q: String, l: String): Int? {
        if (q.length < 2) return null
        var li = 0
        var gaps = 0
        var last = -1
        for (c in q) {
            var found = -1
            while (li < l.length) {
                if (l[li] == c) {
                    found = li
                    li++
                    break
                }
                li++
            }
            if (found < 0) return null
            if (last >= 0 && found != last + 1) gaps++
            last = found
        }
        return 500 - gaps * 40 - minOf(l.length - q.length, 60)
    }

    /** One or two typos against the start of the label or of a word. */
    private fun typo(q: String, l: String, words: List<String>): Int? {
        if (q.length < 3) return null
        val allowed = if (q.length >= 6) 2 else 1
        val candidates = (words + l).filter { it.length >= q.length - 1 }
        var best: Int? = null
        for (word in candidates) {
            for (len in (q.length - 1)..(q.length + 1)) {
                if (len > word.length || len <= 0) continue
                val d = damerau(q, word.substring(0, len))
                if (d <= allowed) {
                    val s = 300 - d * 60
                    if (best == null || s > best) best = s
                }
            }
        }
        return best
    }

    /** Optimal string alignment distance (insert, delete, replace, swap neighbours). */
    fun damerau(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
                d[i][j] = v
            }
        }
        return d[a.length][b.length]
    }

    /** Sorts [items] by match quality, then by [boost] (0..100, for example usage), dropping non-matches. */
    fun <T> rank(query: String, items: List<T>, label: (T) -> String, boost: (T) -> Int = { 0 }): List<T> =
        items.mapNotNull { item -> score(query, label(item))?.let { item to it + boost(item).coerceIn(0, 100) } }
            .sortedByDescending { it.second }
            .map { it.first }
}

/** A small calculator for the search field: + - * / % ^, parentheses, decimals (dot or comma). */
object Calculator {
    private val looksLikeMath = Regex("^[\\d\\s.,+\\-*/x×÷%^()]+$")

    /** The result as display text, or null if [input] is not a calculation. */
    fun evaluate(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || !looksLikeMath.matches(text)) return null
        if (text.none { it in "+-*/x×÷%^" }) return null
        if (text.count { it.isDigit() } == 0) return null
        val value = try {
            Parser(text.replace(',', '.').replace('x', '*').replace('×', '*').replace('÷', '/')).parse()
        } catch (e: Exception) {
            return null
        }
        if (value.isNaN() || value.isInfinite()) return null
        return format(value)
    }

    fun format(value: Double): String {
        if (abs(value) < 1e15 && value == floor(value)) return value.toLong().toString()
        val text = String.format(Locale.ROOT, "%.10f", value).trimEnd('0').trimEnd('.')
        return if (text == "-0") "0" else text
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expression()
            skip()
            if (i != s.length) throw IllegalArgumentException("unexpected '${s[i]}'")
            return v
        }

        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }

        private fun eat(c: Char): Boolean {
            skip()
            if (i < s.length && s[i] == c) {
                i++
                return true
            }
            return false
        }

        private fun expression(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                v = when {
                    eat('*') -> v * factor()
                    eat('/') -> v / factor()
                    else -> return v
                }
            }
        }

        private fun factor(): Double {
            val base = unary()
            return if (eat('^')) Math.pow(base, factor()) else base
        }

        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> percent(primary())
        }

        private fun percent(v: Double): Double = if (eat('%')) v / 100.0 else v

        private fun primary(): Double {
            if (eat('(')) {
                val v = expression()
                if (!eat(')')) throw IllegalArgumentException("missing )")
                return v
            }
            skip()
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException("number expected")
            return s.substring(start, i).toDouble()
        }
    }
}

/** "!yt cats" style shortcuts to search sites. */
object Bangs {
    data class Bang(val trigger: String, val name: String, val url: String)

    val all = listOf(
        Bang("yt", "YouTube", "https://www.youtube.com/results?search_query=%s"),
        Bang("g", "Google", "https://www.google.com/search?q=%s"),
        Bang("w", "Wikipedia", "https://en.wikipedia.org/w/index.php?search=%s"),
        Bang("maps", "Maps", "https://www.google.com/maps/search/%s"),
        Bang("gh", "GitHub", "https://github.com/search?q=%s"),
        Bang("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        Bang("tr", "Translate", "https://translate.google.com/?text=%s"),
    )

    /** The bang and the remaining query, if [input] starts with a known "!trigger ". */
    fun parse(input: String): Pair<Bang, String>? {
        val text = input.trim()
        if (!text.startsWith("!")) return null
        val space = text.indexOf(' ')
        val trigger = (if (space < 0) text.substring(1) else text.substring(1, space)).lowercase(Locale.ROOT)
        val bang = all.firstOrNull { it.trigger == trigger } ?: return null
        val rest = if (space < 0) "" else text.substring(space + 1).trim()
        return bang to rest
    }

    fun url(template: String, query: String): String =
        template.replace("%s", java.net.URLEncoder.encode(query, "UTF-8"))
}

object WebSearch {
    fun url(engine: String, query: String): String = Bangs.url(
        when (engine) {
            "google" -> "https://www.google.com/search?q=%s"
            "brave" -> "https://search.brave.com/search?q=%s"
            "startpage" -> "https://www.startpage.com/do/search?q=%s"
            else -> "https://duckduckgo.com/?q=%s"
        },
        query,
    )
}
