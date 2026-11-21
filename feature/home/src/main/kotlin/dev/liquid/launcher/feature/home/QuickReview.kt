package dev.liquid.launcher.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.liquid.launcher.core.data.DeckCard
import dev.liquid.launcher.core.data.FlashcardDeck
import dev.liquid.launcher.core.designsystem.LauncherIcons
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun cardText(ink: Color, size: Int, weight: FontWeight = FontWeight.Normal, alpha: Float = 1f) =
    TextStyle(fontFamily = HomeTheme.font, color = ink.copy(alpha = alpha), fontSize = size.sp, fontWeight = weight)

/**
 * One flashcard, face down (0.12.0, D173; shared by search's card and the quick review since 0.13.0). A tap turns it
 * over to the question, another to the answer, each turn a spring around the vertical axis; then Wrong or Right
 * ([HomeController.answerCard]: wrong comes back tomorrow and writes a note for the check-in, right waits longer).
 * Formulas written in LaTeX are drawn ([MathText]). [caption] is the small line on top.
 */
@Composable
internal fun FlashcardFace(
    card: DeckCard,
    caption: String,
    ink: Color,
    controller: HomeController,
    modifier: Modifier = Modifier,
    onAnswered: (right: Boolean) -> Unit = {},
) {
    var side by remember(card.id, card.due) { mutableIntStateOf(0) }
    val turn by animateFloatAsState(
        side * 180f, spring(dampingRatio = 0.72f, stiffness = MotionScale.stiffness(260f)), label = "flip",
    )
    // The face shown is the one turned towards you: the content swaps at the half turn and is mirrored back.
    val shown = ((turn + 90f) / 180f).toInt().coerceIn(0, 2)
    Box(
        modifier
            .fillMaxWidth()
            .flipAround(turn)
            .clip(cardOutline())
            .background(ink.copy(alpha = if (shown == 0) 0.14f else 0.09f))
            .squishClickable(delayMs = 40L) { if (side < 2) side++ }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.readableWhenFlipped(turn)) {
            Text(card.module.ifBlank { "Flashcard" } + " · " + caption, style = cardText(ink, 11, FontWeight.Medium, alpha = 0.65f))
            Spacer(Modifier.height(4.dp))
            when (shown) {
                0 -> Text("Tap to turn it over", style = cardText(ink, 16, FontWeight.SemiBold))
                1 -> MathText(card.q, cardText(ink, 15, FontWeight.Medium))
                else -> {
                    MathText(card.a, cardText(ink, 15, FontWeight.Medium))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassButton("Wrong", ink, icon = LauncherIcons.Close) {
                            controller.answerCard(card, right = false)
                            onAnswered(false)
                        }
                        GlassButton("Right", ink, icon = LauncherIcons.Check, emphasis = ControlEmphasis.Main) {
                            controller.answerCard(card, right = true)
                            onAnswered(true)
                        }
                    }
                }
            }
        }
    }
}

/** "All done for today" and when the next cards come. */
@Composable
internal fun DeckDone(deck: dev.liquid.launcher.core.data.Deck, today: LocalDate, ink: Color, title: String = "All done for today") {
    val next = remember(deck, today) { FlashcardDeck.next(deck, today) }
    Column {
        Text(title, style = cardText(ink, 16, FontWeight.SemiBold))
        next?.let { (date, count) ->
            val days = ChronoUnit.DAYS.between(today, date)
            val whenText = if (days == 1L) "tomorrow" else "in $days days"
            Spacer(Modifier.height(2.dp))
            Text(if (count == 1) "Next: 1 card $whenText" else "Next: $count cards $whenText", style = cardText(ink, 12, alpha = 0.6f))
        }
    }
}

/** Turns the card around the vertical axis by [degrees] (a little perspective). */
internal fun Modifier.flipAround(degrees: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        rotationY = degrees
        cameraDistance = 14f * density
    }
)

/** Mirrors the content back on odd half turns, so the side facing you reads the right way round. */
internal fun Modifier.readableWhenFlipped(degrees: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        val half = ((degrees + 90f) / 180f).toInt()
        if (half % 2 == 1) rotationY = 180f
    }
)

/**
 * 0.13.0 (D178, Med: "long-press it for a quick review sheet without opening search"): the flashcards due, one after
 * the other, in a glass sheet over the home screen, opened from the pill's live item (or a card search found, which
 * comes first). Each answer slides the next card in; after the last one it says so and closes by itself. Drag it down
 * or go Back to close it ([Presence]).
 */
@Composable
internal fun ReviewSheet(
    start: String,
    glass: GlassConfig,
    lightBackground: Boolean,
    backdrop: Backdrop,
    controller: HomeController,
) {
    val ink = sheetContentColor(glass, lightBackground)
    val deck by controller.data.study.deck.collectAsState()
    val tick = rememberMinuteTick()
    val today = remember(tick) { LocalDate.now() }
    val due = remember(deck, today) { FlashcardDeck.due(deck, today) }
    // The card search found comes first, even when it is not due (answering it early is fine).
    var asked by remember { mutableStateOf(start.takeIf { it.isNotEmpty() }) }
    val card = asked?.let { id -> deck.cards.firstOrNull { it.id == id } } ?: due.firstOrNull()
    var answered by remember { mutableIntStateOf(0) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.enter()) }
    // The last card answered: a moment to see "All done", then the sheet goes.
    LaunchedEffect(card == null, answered) {
        if (card == null && answered > 0) {
            delay(1400)
            if (controller.reviewSheet != null) controller.reviewSheet = null
        }
    }
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.reviewSheet = null },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 24.dp)
                .widthIn(max = 560.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 40.dp.toPx()
                }
                .sheetSurface(backdrop, shape, glass, lightBackground)
                .bloomLight()
                .clip(shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            val left = due.count { it.id != card?.id }
            Text("Flashcards", style = cardText(ink, 13, FontWeight.SemiBold, alpha = 0.7f))
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    card == null -> if (answered > 0) "Well done" else "Nothing due"
                    due.isEmpty() || due.none { it.id == card.id } -> "A card you looked for"
                    left == 0 -> "The last one today"
                    else -> "${left + 1} due today"
                },
                style = cardText(ink, 20, FontWeight.SemiBold),
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = card,
                contentKey = { it?.id },
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) +
                        slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(380f))) { it / 3 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -it / 3 }) using
                        SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.85f, stiffness = MotionScale.stiffness(380f)) }
                },
                label = "reviewCard",
            ) { shownCard ->
                if (shownCard == null) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(cardOutline()).background(ink.copy(alpha = 0.09f)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        DeckDone(deck, today, ink, if (answered > 0) "All done for today" else "No card is due today")
                    }
                } else {
                    val more = due.count { it.id != shownCard.id }
                    FlashcardFace(
                        shownCard,
                        if (more > 0) "$more more due" else "the last one",
                        ink,
                        controller,
                    ) {
                        answered++
                        if (asked == shownCard.id) asked = null
                    }
                }
            }
        }
    }
}
