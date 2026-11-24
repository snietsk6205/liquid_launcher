package dev.liquid.launcher.core.data

/**
 * The wallpaper screen's steps as plain data (0.16.0, D197), so their order can be tested without a screen.
 *
 * A picture tapped in the library is ringed at once ([chosen]) and loaded ([loaded]); Place shows it full screen while the
 * fingers move it ([placing]); Done keeps it on the screen behind the sheet as a preview ([placed]) until Apply, Cancel or
 * another pick (0.11.1 to 0.15.3 faded it back to the current wallpaper at Done, D158); Apply sets the picture loaded, with
 * the placement it was given. A long press shows one picture full screen ([peeking]) without picking it; "Select" makes
 * taps choose several pictures ([selection]) instead of picking one.
 *
 * Keys name a picture: "lib:<library id>" or "uri:<content uri>" ([keyOfLibrary], [keyOfUri]).
 */
data class PickerFlow(
    /** The picture tapped last: ringed at once, while it loads. */
    val chosen: String? = null,
    /** The picture loaded for placing and Apply (the last one tapped, once it has loaded). */
    val loaded: String? = null,
    /** Pinch and drag on the screen place [loaded]. */
    val placing: Boolean = false,
    /** Placed and Done: the screen shows [loaded] behind the sheet until Apply, Cancel or another pick. */
    val placed: Boolean = false,
    /** A long press: this picture fills the screen with the home screen over it, picked or not. */
    val peeking: String? = null,
    /** Taps choose several pictures (for favourites, collections, recent and delete) instead of picking one. */
    val selecting: Boolean = false,
    val selection: Set<String> = emptySet(),
) {
    /** What the home screen behind the sheet shows. */
    enum class Shows { CURRENT, PLACING, PLACED, PEEK }

    val shows: Shows
        get() = when {
            peeking != null -> Shows.PEEK
            placing -> Shows.PLACING
            placed -> Shows.PLACED
            else -> Shows.CURRENT
        }

    /** The sheet steps aside while a picture is placed or peeked at, and comes back where it was. */
    val sheetAside: Boolean get() = placing || peeking != null

    /** What Apply sets: the picture loaded, once the last one tapped is that one (Apply waits for a picture still loading). */
    val applies: String? get() = loaded?.takeIf { chosen == null || chosen == it }

    /** Apply has to wait: the picture tapped last is still loading. */
    val waiting: Boolean get() = chosen != null && chosen != loaded

    /**
     * A tap on a picture. While selecting, it is chosen or let go. Otherwise it is picked: a new picture ends the preview
     * of one placed before; the picture already picked stays as it is, placement included (0.15.3 loaded it again and
     * dropped the placement).
     */
    fun tap(key: String): PickerFlow = when {
        selecting -> toggle(key)
        key == chosen && key == loaded -> this
        else -> copy(chosen = key, placed = false, placing = false)
    }

    /** The picture [key] finished loading; an older tap that loads late is ignored. */
    fun loaded(key: String): PickerFlow = if (key == chosen) copy(loaded = key) else this

    /** The picture [key] could not be read: the ring goes back to the one loaded before. */
    fun failed(key: String): PickerFlow = if (key == chosen) copy(chosen = loaded) else this

    /** Place: only once a picture is loaded (the screen loads the current wallpaper's picture first when none is). */
    fun place(): PickerFlow = if (loaded != null && applies != null) copy(placing = true, placed = false, peeking = null) else this

    /** Done: the placed picture stays on the screen as a preview. */
    fun done(): PickerFlow = if (placing) copy(placing = false, placed = true) else this

    /** Cancel (or Back with nothing else open): the preview ends; the screen closes. */
    fun cancel(): PickerFlow = copy(placing = false, placed = false, peeking = null)

    fun peek(key: String): PickerFlow = copy(peeking = key)

    fun endPeek(): PickerFlow = copy(peeking = null)

    /** The peeked picture becomes the pick (the "Pick" button of the peek). */
    fun pickPeeked(): PickerFlow = peeking?.let { key -> copy(peeking = null).let { if (it.selecting) it else it.tap(key) } } ?: this

    fun startSelecting(first: String? = null): PickerFlow = copy(selecting = true, selection = setOfNotNull(first), peeking = null)

    fun toggle(key: String): PickerFlow = copy(selection = if (key in selection) selection - key else selection + key)

    fun selectAll(keys: Collection<String>): PickerFlow = copy(selection = selection + keys)

    fun endSelecting(): PickerFlow = copy(selecting = false, selection = emptySet())

    /** Pictures taken out of the library (deleted) can no longer be chosen, selected or peeked at. */
    fun without(keys: Set<String>): PickerFlow {
        val keptLoaded = loaded?.takeIf { it !in keys }
        val keptChosen = if (chosen != null && chosen in keys) keptLoaded else chosen
        return copy(
            chosen = keptChosen,
            loaded = keptLoaded,
            placed = placed && keptLoaded != null,
            placing = placing && keptLoaded != null,
            peeking = peeking?.takeIf { it !in keys },
            selection = selection - keys,
        )
    }

    /**
     * Back, one step at a time: the peek, then selecting, then placing (kept, as Done); null when nothing of the screen
     * is left open, so Back closes the screen (fading a preview back first).
     */
    fun back(): PickerFlow? = when {
        peeking != null -> endPeek()
        selecting -> endSelecting()
        placing -> done()
        else -> null
    }

    companion object {
        fun keyOfLibrary(id: String): String = "lib:$id"
        fun keyOfUri(uri: String): String = "uri:$uri"

        /** The library id in [key], or null for a folder picture. */
        fun libraryId(key: String?): String? = key?.takeIf { it.startsWith("lib:") }?.removePrefix("lib:")
    }
}
