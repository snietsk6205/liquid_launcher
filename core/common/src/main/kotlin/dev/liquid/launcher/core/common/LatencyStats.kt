package dev.liquid.launcher.core.common

/** Keeps the most recent samples and reports median and 95th percentile. Thread-safe. */
class LatencyStats(private val capacity: Int = 200) {
    private val samples = ArrayDeque<Long>()

    fun add(valueMs: Long) = synchronized(samples) {
        samples.addLast(valueMs)
        while (samples.size > capacity) samples.removeFirst()
    }

    fun clear() = synchronized(samples) { samples.clear() }

    val count: Int get() = synchronized(samples) { samples.size }

    fun median(): Long? = percentile(50)

    fun p95(): Long? = percentile(95)

    fun last(): Long? = synchronized(samples) { samples.lastOrNull() }

    fun percentile(p: Int): Long? = synchronized(samples) {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        val index = ((p / 100.0) * (sorted.size - 1)).let { Math.round(it).toInt() }
        sorted[index.coerceIn(0, sorted.size - 1)]
    }

    fun summary(): String {
        val n = count
        if (n == 0) return "no samples"
        return "median ${median()} ms, p95 ${p95()} ms, last ${last()} ms ($n samples)"
    }
}
