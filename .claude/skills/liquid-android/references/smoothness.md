# Smoothness: rules that came from measurements

Contents: 1 Budget and targets · 2 Measure on the phone · 3 Reading a frame line · 4 Rules, each with the incident
that taught it · 5 Idle screens · 6 What the CI emulator can and cannot tell

## 1. Budget and targets

90 Hz gives 11.1 ms a frame (60 Hz: 16.7 ms), shared by the app's work (input, composing, layout, recording the
drawing), the render thread (sync, command issue) and the GPU. On Med's phone the app's own work was usually 1-4 ms;
almost all the cost was the render thread ("sync", 7-20 ms) and the GPU (6-26 ms): glass, blur, layers.

| Metric | Target |
|---|---|
| A gesture-driven transition (drawer, search, sheet) | follows the finger, no dropped frames at 90 Hz |
| List scroll | no frame over 16 ms; target 11 ms |
| Search results after a keystroke | under 16 ms for the main source, under 100 ms for all |
| Cold start to first frame | under 400 ms |
| A screen at rest | about zero frames drawn |
| Background service | not visible in the battery list |

## 2. Measure on the phone

- **Frame meter** (`assets/kit/.../diagnostics/FrameMeter.kt`): a `Window.OnFrameMetricsAvailableListener` that
  sums every frame and writes one debug-log line every ~600 frames (or when the screen changes), labelled with what
  was on screen (`FrameCost.label = "drawer; letter area"`): frames, late frames and %, average, GPU, and the
  phases: input+composing, layout, draw, sync (+command issue), worst frame, refresh rate. Install it in
  `onCreate`: `window.addOnFrameMetricsAvailableListener(meter, Handler(Looper.getMainLooper()))`.
- **Speed check** (Diagnostics): plays the main animations (drawer, search, menus, the hub) once per variant of the
  costly part (glass as set, without blur, frosted, liquid, without shadows, without the material, a flat tint),
  one frame line each. The difference between two lines is what that part costs on this phone. **Build an
  optimisation only after the numbers say it helps** (D166: "still glass in motion" was measured first; shipping it
  blind could have made things worse).
- Ask the user for the diagnostics export (device facts + the log) after a change, and read it.

## 3. Reading a frame line

`600 drawn, 116 late (19.3 %), 33.6 ms on average (GPU 7.1 ms; input and composing 2.1, layout 0.0, draw 1.1,
sync 12.6), worst 94.5 ms, at 60 to 90 Hz; drawer; letter area`

| High | Means | Usual fix |
|---|---|---|
| input and composing | recomposition every frame | read animated values in layout/draw; derive booleans; stable keys |
| layout | measuring big trees every frame | fixed sizes, layout modifiers instead of state |
| draw | recording a lot of drawing | cache paths/outlines, fewer draw calls, `drawWithCache` |
| sync (+command issue) | render-thread work: layers with effects, texture uploads, paths | fewer glass/effect layers, record layers only on change, no new bitmaps per frame |
| GPU | fill rate: blur radius, full-screen effects, overdraw | smaller blur, pre-blurred copies, tint tier |

The average includes waiting for vsync; **late %** is the number that matters.

## 4. Rules, each with the incident that taught it

1. **Read animation values in layout or draw, never in composition.** A clock read the icons' palette while
   composing, so each frame of a 260 ms colour ease recomposed and re-measured it (D90); the drawer's tag list and
   the wallpaper sheet recomposed every frame (D160). Use `graphicsLayer { alpha = anim.value }`,
   `Modifier.layout { }`, `Modifier.offset { }` (lambda), `drawBehind { }`.
2. **No live full-screen blur.** A blur whose radius changed every frame over the whole screen was the costliest
   effect of the drawer transition (D78). Blur a copy once off the main thread and cross-fade.
3. **Glass never per list item; scrolling content draws without backdrop.** The study hub's list sampled glass and
   ran at 55-103 ms a frame (D155).
4. **Record a layer only when its inputs change.** A sheet's blurred background picture was recorded again (and
   blurred) on every frame the sheet drew (D183); key it on (size, picture, blur).
5. **Overlays leave as a picture**, not as live glass moving (D176); before that, the exit was a 110 ms alpha-only
   fade with `CompositingStrategy.ModulateAlpha` (D165).
6. **Cache outline paths** by shape and size (`OutlineCache`, ~160 entries): the GPU's path work is then kept
   between frames (D165).
7. **Derive coarse state.** A countdown under a glass sheet changed the home screen every second, so the sheet's
   glass re-sampled every second; while covered, the countdown ticks by the minute (a derived boolean) (D183).
8. **Never compose a whole screen of new rows in one frame.** The letter index jumping more than 3 rows built a
   screen of rows at once and froze (D157): scroll by pixels near the target (prefetch builds rows between frames),
   request far positions for the next layout, cap the glide at ~100 rows a second.
9. **Spread first composition**: one card per frame from the finger outward (D188). Keep a surface that must open
   instantly composed but hidden (the drawer, D32), so its first frame is not a stall.
10. **Colour at draw time**: themed icons are masks tinted while drawing; a palette change is a redraw, not a
    re-render of every icon (D63).
11. **Cache measured text** (`rememberTextMeasurer(cacheSize = 96)`, measure the glyphs once per style).
12. **Per-frame gesture values in plain holders**, not state, when they are read by a collector and never while
    composing (the letter index's finger).
13. **Stable gesture modifiers**: `remember` them and key `pointerInput` on what really changes.
14. **Slow system calls off the hot path**: handing a wallpaper to the system took 3-7 s on MagicOS and made the
    phone sluggish: one writer, the newest wins, done after the transition or at screen off (D74). A 3.2 s window
    query sat in the redirect path until it moved to a side thread (D39).
15. **Battery-cheap motion**: a moving background's clock stops while the window has no focus or is covered (D113).
16. **Memory**: apply icon shapes when rendering instead of keeping extra bitmaps (about 20 MB for 200 apps, D52).

## 5. Idle screens

A still screen should draw about zero frames. A "breathing" halo drew 600 frames a minute at rest until it was made
to rest five minutes between breaths (D165). The CI emulator test counts frames drawn while each screen sits still
(`dumpsys gfxinfo` before and after) and fails a screen that keeps drawing (D155); a blinking cursor needs a higher
limit (two frames a second).

## 6. What the CI emulator can and cannot tell

The emulator draws in software (SwiftShader): its frame times say nothing about the phone. It can tell that a screen
stops drawing when idle, that nothing crashes or ANRs, that transitions end, and the order of events in the app's
log. Timings in the app's log ("the keyboard opened 120 ms after search") are best read from the user's phone.
