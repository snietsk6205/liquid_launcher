# Motion: springs that follow the finger

Contents: 1 One vocabulary · 2 Speed and Feel settings · 3 Following the finger · 4 Overlays (Presence) · 5 Presses
· 6 Settings navigation · 7 Arrivals and lists · 8 Physical stops · 9 Haptics · 10 The keyboard · 11 Screen-wide
transitions · 12 Lessons

Code: `assets/kit/src/main/kotlin/liquid/kit/motion/` (`Motion.kt`, `PressFeel.kt`, `Stagger.kt`) and
`Shims.kt` (`MotionScale`, `Haptics`).

## 1. One vocabulary

Every sheet, menu, folder, label and popup takes its spring from one object, so they all move alike and one setting
changes them together (D82). Up to 0.5.0 each surface had its own numbers and most of them vanished in a frame.

```kotlin
object Motion {
    var style = "liquid"                                  // the "Feel" setting
    fun enter() = spring(damping(), MotionScale.stiffness(stiffness()))            // something large arriving
    fun pop()   = spring((damping() - 0.08f).coerceAtLeast(0.5f), MotionScale.stiffness(stiffness() * 1.35f)) // small, quicker
    fun exit()  = tween<Float>(MotionScale.ms(110))       // leaving: quick, no bounce, never delays what comes next
    fun leave() = spring(1f, MotionScale.stiffness(420f), visibilityThreshold = 0.005f) // an overlay leaving as a picture
}
```

## 2. Speed and Feel settings

- **Feel**: calm (damping 1.0, stiffness 440, no overshoot), liquid (0.78, 540, a soft overshoot, the default),
  lively (0.6, 640, a clear bounce).
- **Speed** (`MotionScale.speed`, 50-200 %): durations divide by it, spring stiffness multiplies by its square (a
  spring's period scales with 1/sqrt(stiffness), so speed² keeps the timing proportional). Every animation in the
  app follows it, including haptics' rhythm (D70, 0.9.20).
- Surfaces with their own feel get their own settings (search: Bounce 30 % = damping 0.81, Speed = stiffness
  360 x speed², Liquid stretch; D118), so tuning one does not change the others.

## 3. Following the finger

- **Progress is an `Animatable`** (0 closed, 1 open). A drag calls `snapTo(position)` every move (UNDISPATCHED, so
  the frame sees it); the release decides by **speed first, then position**: open above +800 px/s, close below
  -800 px/s, otherwise open past 0.4. Then `animateTo(target, spring, initialVelocity = flick / travel)` with the
  velocity clamped to ±14 progress per second.
- **Short travel**: the drawer needs 36 % of the screen height from closed to open (it was 100 %; the log showed
  releases at 0.15-0.25, so four fifths of the transition ran after the finger left and it felt like a separate
  screen, D80). The settle spring is stiff (560).
- **Interruptible**: a spring sent the other way (Back, a tap, a new drag) starts from the speed it has, never from
  rest; a drag on a moving surface takes it where it is (D125).
- **Velocity**: feed the tracker whole pointer changes (historical points too), or batched moves are lost and a
  swipe reads 0 px/s; if it still reads 0 and the swipe took under 600 ms, use its average speed (D84).
- **Gesture arbitration**: one detector per surface for one and two fingers; a second finger within 250 ms turns
  the gesture into a pinch or a two-finger swipe, claimed in the Initial pass so the pager and icons cannot act on
  it (D67). Edge swipes start within 24 dp. A gesture area that shares space with taps consumes only after the touch
  slop and before the long-press timeout, so a tap or hold still reaches what is under it (D156).
- A tick (haptic) when the dragged progress crosses the open point.

## 4. Overlays: `Presence`

`Presence(value, origin, onClose) { content }` keeps an overlay composed while it leaves and gives every overlay the
same exit (D82, D160, D176):

- **Leaves as a picture of itself**: its graphics layer is turned into a bitmap by the GPU once (glass included),
  then that picture shrinks back into what opened it (`origin` rect: a folder into its icon, a menu into the
  pressed icon) or sinks (sheets 12 % of the screen, small overlays 3 %), on `Motion.leave()`, fading over its last
  part. Nothing live is redrawn while it goes, which is what made exits lag before.
- **Sheets** (with `onClose`): follow a finger dragging them down (from anywhere, or from the top of their own list),
  close past a quarter of the screen or on a flick; Android's predictive Back shrinks them toward the edge as the
  thumb moves and closes on release.
- Entrances stay each overlay's own (grow out of the pressed thing, rows arriving one after another).

## 5. Presses (`PressFeel`, `squishClickable`)

- A press **squashes like a drop**: height more than width, then springs back past its size on release. Width and
  height ride two springs of different stiffness, so they wobble slightly out of step ("jelly") (D108).
- A tap too quick for the squash to show still gets it (a push before the spring).
- **Inside lists, wait 50-60 ms** before squashing, so a scroll never squashes what it starts on.
- Large things (cards, rows) move a fixed number of pixels at most, so a full-width card dips gently while an icon
  squishes. Optional "light from the finger": a soft radial light where the finger is.
- Styles: squishy (default), tilt (tips toward the finger, with a highlight), off; intensity, speed and wobble are
  settings (D127).
- Use `Modifier.squishClickable(delayMs = 0) { }` instead of a ripple: the element itself answers.

## 6. Settings navigation (WM Keyboard's push)

```kotlin
SharedTransitionLayout {
    val shared = this
    AnimatedContent(
        targetState = Frame(stack.size, stack.last()),
        transitionSpec = {
            val spec = tween<IntOffset>(MotionScale.ms(340), easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
            val forward = targetState.depth >= initialState.depth
            (if (forward) slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 3 }
             else slideInHorizontally(spec) { -it / 3 } togetherWith slideOutHorizontally(spec) { it })
                .apply { targetContentZIndex = targetState.depth.toFloat() }
        },
        contentKey = { it.depth to it.screen },
    ) { frame ->
        CompositionLocalProvider(LocalKitShared provides shared, LocalKitNav provides this,
            LocalKitScreen provides infoFor(frame.screen), LocalKitBackTo provides { depth -> backTo(depth) }) {
            ScreenContent(frame.screen)
        }
    }
}
```

The arriving page comes the whole way in from the right while the one behind drifts a third left; both are opaque,
nothing fades. A row's icon tile flies into the heading of the page it opens (`Modifier.kitFlight(key)`, bounds on
a 340 ms FastOutSlowIn tween). Defer a page's heavy preview until the push has settled.

## 7. Arrivals and lists

- **Staggered rows** (`kitStaggerIn(index)` / `StaggerIn`): each row fades in and rises 6 dp over 220 ms, 35 ms after
  the one before, past 8 rows the rest come together. Read in the draw phase: a redraw per frame, no recomposition.
- **Compose a screen over frames**: `rememberRevealCount(total)` composes one more card per frame, from the one
  nearest the finger outward; the first frame stays quick (search's page, D188).
- **A return** (the app coming back to the front) settles its content in: scale 0.94 to 1 with a fade, about
  180 ms, slightly staggered from the centre, unless the screen is already up to date.
- Lists that jump far: glide at most ~100 rows a second and scroll by pixels within a few rows of the target so the
  lazy list's prefetch builds rows between frames (D157).

## 8. Physical stops: squash, do not overshoot

When something lands on a real limit (a pill on the keyboard, a sheet at the top), clamp its position to the limit
and turn the spring's overshoot into a squash of the moving thing onto it: at most 10 % of its height, half as much
wider, origin at the contact edge (`squash = ((progress - 1) * 0.75).coerceIn(0, 0.10)`). An overshoot past a
physical limit reads as a bug ("the pill goes at the top", D188).

## 9. Haptics

One object, one switch, a strength: `tick` when something passes a point (the open point, a letter, a snap),
`confirm` when a gesture is recognised or an item is picked up. Android 14+: `SEGMENT_TICK` /
`SEGMENT_FREQUENT_TICK` for scrubbing; older: `CLOCK_TICK`, `VIRTUAL_KEY`, `CONTEXT_CLICK`, `LONG_PRESS`.

## 10. The keyboard (when a field opens with the UI)

What made search feel like one motion (D169, D175, D188, D194):
- Ask for the keyboard **on the first frame of the gesture** (focus without showing:
  `KeyboardOptions(showKeyboardOnFocus = false)`, then
  `windowInsetsController.controlWindowInsetsAnimation(ime(), -1, LinearInterpolator(), signal, listener)`).
- Until the controller is ready (60-390 ms on the phone), a **picture of the keyboard** (taken once it stood still
  last time) rises in its place; once ready, put the keyboard exactly where the picture is and drop the picture two
  frames later.
- Move it with the UI's progress (`setInsetsAndAlpha`, share = rise x progress / shown height), stay fully up while
  the UI bounces, **hand it back** (`finish(true)`) once it is up and still for two frames and any glide to its real
  height is over.
- **One control per opening**: closing while the opening's control still holds the keyboard lets that one take it
  down (asking twice makes the system take the first back and the keyboard jump).
- If the system takes it back mid-way (the user's Back), respect it: do not ask again.
- Fallback when refused: the field rides on the keyboard, `max(navigationBar, ime)`, never above it.
- Remember the height it opens at (the lowest of the last six openings) and ignore a growth shorter than 160 ms
  (keyboards that rebuild themselves report a taller size for a moment).

## 11. Screen-wide transitions (wallpaper, layout, theme)

Record the screen into a graphics layer every frame; at the change, take that picture, lay it over, change
everything underneath, then let the picture leave while the new screen settles in from slightly larger, smaller or
offset (D59, D103). Styles as AGSL shaders on the picture: Ripple (from where the user tapped), Ink (dissolves
through soft noise with a faint glowing edge), Shine (a diagonal pane of glass with a bright refracting edge), Melt
(runs down in uneven streams), plus Liquid, Zoom, Fade. Load and place the new picture first; write files and hand
anything to the system after the transition.

## 12. Lessons

- An animation that starts after the change is already visible feels broken, however pretty (the unlock reveal was
  removed for that, D195). Start on the first frame or do not animate.
- "Instant" means starting at once and lasting about 300-400 ms; a 1 s transition reads as slow.
- Moving many things in step (home away, drawer in, wallpaper drifting the same way) reads as one space; moving one
  thing over a still screen reads as a new screen.
- Things should leave the way they came.
