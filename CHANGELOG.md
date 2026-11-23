# Changelog

What changed in each version of Liquid Launcher, newest first, in four parts: **Added**, **Changed**,
**Removed** (or replaced) and **Fixed**. The GitHub release of a version shows its part of this file, the
Updates screen shows it before you install, and the launcher shows it once after an update.

Versions marked *not published on its own* were built but came to the phone inside the next release.

## 0.16.0 — 2026-11-23

### Added
- The wallpaper library sorts and filters. "Order and filter" (the arrows next to Recent, Favourites and All) puts the
  pictures newest first, most used first or by colour, and filters them by colour (nine swatches) and by shape
  (portrait, landscape, square). The order is remembered; uses are counted from this version on.
- Collections of wallpapers: "+ Collection" in the library makes one, Select adds pictures to it, its chip opens it, and
  holding its chip renames it or takes it away (its pictures stay). Favourites counts as one.
- Select (the check button next to the order): tap several pictures, then make them favourites, add them to a
  collection, take them out of Recent, or delete them. A delete can be undone for 10 seconds.
- A long press on a picture shows it full screen with your home screen over it. Pick picks it; Back or a tap goes back.
- Undo after a new wallpaper: for 10 seconds a glass notice offers Undo (after Apply in the wallpaper screen, a picture
  from the wallpaper strip, or Shuffle) and brings back the wallpaper before, with its placement. The phone gets the new
  wallpaper only once the Undo has gone, so an Undo costs nothing.
- "Set as Liquid wallpaper" in the share menu (one picture or several), and "Liquid wallpaper" in a gallery's "Set as":
  the pictures go into the library and the wallpaper screen opens with the first one picked.

### Changed
- After Place and Done, the picture stays on the screen behind the sheet, with its blur and dim, until you Apply, Cancel
  or pick another one (it used to fade back to the old wallpaper at Done). Cancel fades it back.
- A long press on a picture in the library now shows it full screen; its old menu (favourite, remove from recent,
  delete) is in Select.
- The library's first row is Recent, Favourites and All, with your collections and linked folders on the row below.
- Back in the wallpaper screen goes one step back (the full-screen picture, selecting, placing) before closing it.
- Tapping the picture already picked keeps it as it is (its placement used to be lost).

### Fixed
- Picking a favourite hid its heart: the heart is now on a small dark badge, above the ring.
- After Place and Done, the library jumped back to the top: each list now keeps its place.

## 0.15.3 — 2026-11-23

### Added
- Board photos with the launcher's own camera, several in a row. Board (a class's chip in Today, or the pill's and the
  home menu's) opens a camera inside the launcher on the class of the moment. Take as many photos as you like: each one
  is saved at once in Pictures/ESSAI/<the class> and flies into the corner while it saves, with the count on it, so the
  next can be taken straight away. Done (or Back) closes it once every photo is saved, and the home screen says how many
  there are. The volume keys take photos too; a tap focuses there; two fingers or the presets under the shutter zoom
  (1×, 2×, 5×, and the wide lens when the phone has one); photos come out upright however you hold the phone. The first
  time, Android asks for the camera. A camera app, one photo at a time, is still a choice: Settings › Study companion ›
  More, where the test photo tries the camera you chose.

### Removed
- The unlock animation (0.15.0 to 0.15.2): it played only once the phone was already open. The home screen no longer
  plays a wallpaper transition when you unlock, and its two settings (When you unlock, Unlock animation length) are gone.

## 0.15.2 — 2026-11-22

### Changed
- When you unlock: the home screen now wakes the way a wallpaper change looks, and at once. A picture of the home screen
  asleep (softer, paler and darker, taken as the screen turns off) leaves over the live screen in your transition's style,
  from a random point, while the screen settles under it, exactly as when you change the wallpaper (0.15.1 used a blurred
  wallpaper, where the ripple's bend could not be seen). It starts the moment the home screen is in front with the phone
  unlocked (0.15.1 could wait for the unlock's broadcast), takes its own short length, 380 ms (Settings › Wallpaper ›
  Behaviour › Unlock animation length; 0.15.1 used the transition's 800 ms × 1.35), and the launcher's arrival fade after
  a redirect no longer plays on top of it.
- The Theme lab in the new Settings look: its heading with the breadcrumbs, the preview on a card, the four views as one
  segmented control, the groups and pills as chips, every option on grouped cards with the thick slider and the chips.
  "Tune the glass" takes the same chips and sliders on a panel in the theme's colours, and the last stock chips and
  sliders in Settings (letter areas, looks, colours) are the kit's now.

### Fixed
- Search: 0.15.1's fix for a keyboard taller than expected was only partial (the keyboard could still be handed back
  while the pill was gliding up to it, for a moment over the pill). The keyboard is now handed back only once that glide
  has ended, and that glide ends a little sooner (its last fraction of a pixel took about 170 ms).
- Search: Back pressed while the keyboard is still rising now puts it away, as it does once the keyboard is up. It used
  to come straight back, so the next Back only hid it again instead of closing search.

## 0.15.1 — 2026-11-22

### Fixed
- Search: when the keyboard turned out taller than the launcher expected (the first time, before it has a picture of the
  keyboard, or after the keyboard's size changed), the keyboard could stand at its full height over the pill for a moment
  while the pill was still gliding up to it. The keyboard is now handed back to the system only once the pill has reached
  it.

## 0.15.0 — 2026-11-22

### Changed
- Search opens in one motion that lands: the pill and the keyboard rise together and stop together at the top. With
  Bounce, the pill no longer floats above the keyboard and falls back onto it (your log: search reached 1.13 to 1.15 of
  open, the pill 25 to 30 dp above the keyboard); it squashes onto the keyboard like a drop instead. The keyboard is
  handed back to the system as soon as it is fully up, so a swipe that closes search soon after never meets it still held
  (that collision made the keyboard jump up before going down). A finger pushing search away takes the keyboard down with
  it from the first movement. A keyboard that says it is taller for a moment while search is open (WM Keyboard rebuilding
  itself after a wallpaper change) no longer throws the pill up and down; one that stays taller is followed after a
  moment, on a glide. Search's cards come in one after the other as it opens, rising into place, so the first frame of
  the swipe no longer builds the whole page at once.
- Settings in WM Keyboard's style, in your wallpaper's colours: each place has its own hue (Home blue, Drawer and search
  indigo, Study green, Look pink, Wallpaper cyan, Motion purple, System orange) on its icon tiles and, faintly, on its
  heading; a page opens with its name large beside its icon, under breadcrumbs you can tap to go back, and the name
  moves into the bar as you scroll; rows sit on cards stacked into groups; pages slide in from the right while the one
  behind drifts left, and a row's icon flies into the heading of the page it opens; a page's rows come in one after the
  other. Tabs are one segmented control with a sliding highlight, short choices are chips (one tap), sliders are thick
  with a tall handle, switches and values take the place's hue, every option has a "?" that explains it, search shows
  the words that matched in bold and where each option lives. Nothing moved and nothing was removed; the live previews
  appear once the page has slid in.

### Added
- When you unlock (Settings › Wallpaper and layouts › Wallpaper › Behaviour): the home screen comes out from under
  frosted glass as you unlock, with the wallpaper's transition (Ripple for you), starting from a different place each
  time; Ink, Melt and Shine vary each time too. Its own style can be chosen, or Off.

### Fixed
- Your wallpaper behind the keys did nothing: WM Keyboard answered "no theme is called "Liquid wallpaper 2"" at every
  change. Settings › Keyboard now shows which of the two themes WM Keyboard has (step 3 is ticked once both are there),
  says on the home screen when a hand-over fails because a theme is missing (a tap opens the page), and also tells you
  when WM Keyboard's automatic light and dark theme hides the one the launcher picked. The theme that is there gets the
  new picture too.
- The pill could jump up as search (or the drawer) finished closing while the keyboard was still on its way down: it
  rested 116 dp (your distance from the edge) above the keyboard instead of on its own place.

## 0.14.0 — 2026-11-22

### Changed
- Today is simple now: one question, "What would you like to study today?", with today's classes as chips (tap what
  you'll study) or a field to type it, and one big button, **Study with Claude**. It starts a 45-minute study session
  on the pill and opens Claude with your day as context: today's classes, what you picked, the minutes you studied,
  your notes, what is recommended and the next exam. Claude then asks what you covered in class and a few questions
  to see where you are, asks for today's lesson, and you study together. Below it: **Recommended** (up to four), your
  absences as plain numbers, **Quiz me** and the **note for tonight**. Gone: the timeline, the block cards, the task
  list, the sync row, the buttons at the top, the other chips and the block button. The header shows the date, the
  minutes studied today and the next exam counted down.
- Study sessions: while one runs, the pill counts it down, the line under the clock says "Studying <what>" with a ✓ to
  finish, and apps tagged distracting wait behind the speed bump, as during a block. It ends by itself after 45 minutes.
- The check-in is the start of a study chat now: the evening line and the search page's check-in open Claude with the
  same context. New instructions for Claude are in the repository (docs/checkin-prompt.md): no more study blocks or
  errands in your calendar, no questions about absences, no five-minute steps.
- Absences are numbers only, in Today and on the search page: each module's count from your "present?" answers, no
  meter and no limit.
- Prompt templates: Quiz me alone by default (Explain, Stuck, Reschedule and Résumé are gone unless you had changed
  them).

### Added
- Recommended tasks in Today: Claude writes up to three at the end of each study chat (launcher-data `recommended`),
  and the launcher adds what it knows itself: the flashcards due and a deadline within two weeks (red within three
  days). Tick one when it is done (kept for the day); tap one to pick it as what to study; tap the flashcards for the
  quick review.

### Removed
- Settings that only shaped the old Today: Blocks in Today, Done and Skip buttons, Tomorrow in Today and Study hub
  opens. Today always opens at its full height.

## 0.13.1 — 2026-11-22

### Added
- Your wallpaper behind the keys (Settings → Drawer and search → Keyboard → Look): WM Keyboard's background becomes the
  part of your wallpaper the keyboard covers, and changes with every wallpaper you set in the launcher. Android does
  not let a keyboard read the wallpaper's picture (WM Keyboard only gets its colours), so the launcher hands it the
  picture, either blurred and dimmed the way search shows the wallpaper (the keyboard carries search's background on),
  or sharp like the home screen. Set it up once on that page: let WM Keyboard read your photos, let other apps control
  it, and add the two themes "Liquid wallpaper 1" and "2"; the launcher switches between them so WM Keyboard reads each
  new picture at once. "Try it now" does a change on the spot and says what WM Keyboard answered.
- A second line under the clock, smaller: what comes after, the tasks left, and an exam within two weeks (amber, red
  from three days, in hours). Settings → Study companion → Second line.

### Changed
- The line under the clock, reimagined: it no longer repeats the pill. While the pill counts a focus down, the line
  shows the block's next step with a ✓ that ticks it in your calendar (then the next step, then "mark it done"); a
  block without steps shows what comes after it. In a class it says until when and in which room; before a class, in
  how long and where; with more than an hour free, "Free until 14:00" and what comes then. Leaving in the morning,
  "present?", the rest after getting home, the evening check-in, a block to mark, the tasks and tomorrow stay. The
  "Study block" widget shows the same.

### Fixed
- Search after a wallpaper change: the first time, the pill went up too high and stayed there (WM Keyboard, rebuilt
  with its new colours, says it is taller for a moment, then comes down). The pill now keeps to the keyboard's usual
  height, and comes down onto the keyboard whenever the keyboard settles lower while search is open.
- The study hub was redrawn every second while a focus ran (the pill's countdown behind it), at about a tenth of a
  second per frame: the countdown now waits while the hub, search or the drawer covers it. Sheets with a picture on
  their board no longer blur it again on every frame.

## 0.13.0 — 2026-11-21

### Added
- The live pill: a new item in the pill shows what is happening now, and changes as it does. The focus timer counts
  down to the end of its block; a class or block starting soon counts down ("Analyse in 12 min"), the one running
  shows a small ring and the minutes left; what is playing shows its cover and title with its own play/pause (a tap
  opens the app playing it); and when nothing else is on, the flashcards due today. When something changes, its
  capsule takes the new content and the pill swells and settles like a drop. It takes no room while nothing is
  happening. It is in the pill already (search, live, date); Settings → Pill → Layout sets what it shows, how long
  before a class it counts down, and the change of shape. What is playing needs notification access (the switch "Quiet
  during blocks" uses); the Pill page offers it when it is off.
- Flashcards one tap away: while cards are due, a small count rides on the live item. A tap or a long press on it
  opens a quick review over the home screen, without search: the cards one after the other, turned over and answered
  as in search (wrong comes back tomorrow, right waits longer), closing by itself after the last one. Drag it down or
  go back to close it sooner.
- Search everything: besides apps, search finds people from your contacts, events of your calendars (a month back to
  six months ahead), your notes for the evening check-in, your flashcards and options of Settings. A person opens
  their card, an event opens in your calendar app, a flashcard opens in the quick review, an option opens its page in
  Settings with the option lit up. Accents do not matter ("revision" finds "Révision"). Contacts are read only after
  you allow them, from a row search offers once; each kind can be turned off in Settings → Search → More.

### Changed
- Search opens in one motion: the pill only rises, straight to just above the keyboard, and the keyboard rises with it
  from the first frame (a picture of it stands in until the real one is ready, which takes a moment on some phones).
  Closing takes the keyboard down with the pill. The liquid stretch follows the pill's own speed, and settles without
  a wobble when Bounce is 0; Speed and Bounce stay in Settings → Search → Behaviour. The picture of the keyboard is
  taken through the redirect service: if it never shows, switch that service off and on once in Android's
  accessibility settings.
- Closing is smoother everywhere: folders, menus and sheets leave as a picture of themselves, shrinking back into what
  opened them (a folder into its icon, a menu into the app it came from) or sinking away, on a spring without bounce.
  Sheets follow your finger when you drag them down, and Android's back gesture shrinks them as your thumb moves.
- The keyboard theme (Settings → Drawer and search → Keyboard) is now built on your own WM Keyboard theme file, so its
  board, toolbar and chips stay yours and only the keys and popups take the launcher's colours. No picture is put
  inside it any more, so the toolbar is sharp, and "Follow wallpaper colors" is on: after one import, WM Keyboard
  recolours it by itself at every wallpaper change. "Use another file" takes another theme as the base.
- Board photos: every camera app on the phone can be chosen, also those that cannot hand a photo back to another app;
  those open as they are, and when you come back the photo picker opens on your newest photos and saves the one you
  pick in its class's folder.

### Removed
- "WM Keyboard layout while searching" and the Liquid plugin for WM Keyboard: the layout switch redrew the keyboard
  while it slid in, and the plugin could only pass text through the share sheet. Settings → Drawer and search →
  Keyboard → Behaviour now shows how to bind a WM Keyboard mode to the launcher instead, which applies before the
  keyboard is drawn.

### Fixed
- Opening Settings at a page from the launcher (Customise, a gesture or button set to open a page, the drawer's menu,
  an option found by search) always shows that page now. When Settings had been left open in the background on
  another page, it used to come back on that page instead.

## 0.12.0 — 2026-11-21

### Added
- Flashcards with repeats: launcher-data can now carry several cards (`flashcards`), and the launcher keeps every card
  it reads in a deck on the phone. Wrong brings a card back tomorrow; right waits longer each time (1, 3, 7, 14, then
  30 days). Search's Flashcards card shows the card that has waited longest, how many more are due today, then "All
  done for today" and when the next ones come. A wrong answer still writes its note for the evening check-in. The
  single card of before still works, and the deck with its progress goes into your backups.
- Formulas on flashcards: LaTeX between `$…$` (inside a sentence) or `$$…$$` (on its own line) is drawn on the phone,
  without the internet: subscripts and superscripts, fractions, roots, integrals, sums and limits with their bounds,
  Greek letters, arrows, ∞, ℝ and text.
- Settings → Drawer and search → Keyboard, for WM Keyboard:
  - "WM Keyboard layout while searching": while search is open the keyboard switches to the layout you pick from its
    list, and back to yours afterwards (WM Keyboard › Privacy › Let other apps control the keyboard must be on).
  - "A keyboard theme in your colours": a WM Keyboard theme made from your launcher theme with today's wallpaper
    colours (and the wallpaper behind the keys if you like), with a preview; open it in WM Keyboard, save it or share it.
  - "Install the Liquid plugin": a plugin for WM Keyboard's Plugins tool that hands what you type to Liquid's search,
    or gets it ready to ask Claude.
- "Liquid" in the text selection menu of any app, and "Search in Liquid" in the share sheet: search opens with the
  text. Other apps (Tasker and the like) can open search with `dev.liquid.launcher.action.SEARCH`.

### Changed
- Search and the drawer's field no longer get autocorrection or an automatic capital (app names stay as you type
  them), and the keyboard shows a Search key; in Ask Claude mode it is ordinary text with a Send key.
- Search's "Daily flashcard" card is now "Flashcards" (Settings → Search → Layout → Cards).

### Fixed
- 0.11.3: with search open and its keyboard up, opening recents or a dialog over the launcher could drop the search
  field under the keyboard until you came back. The launcher now lays itself out without the keyboard only when an
  app was just started from it.
- Calendar descriptions edited on Google Calendar's web page: characters it writes as numeric codes (such as `&#58;`
  for a colon) are read as those characters, in launcher-data and in study blocks.

## 0.11.3 — 2026-11-21

### Added
- Settings → Drawer and search → Search → Layout: the search pill's height, its width and its gap above the keyboard,
  each shown in the page's preview.
- Settings → System → Redirect → Look: "Try the arrival" shows the system's home screen, so you see the launcher come
  back the way it is set.

### Changed
- Search: the launcher slides the keyboard in itself, in step with the pill, from the moment you start swiping towards
  search. The field rides on the keyboard: never above it, never above an empty gap. If the keyboard will not be moved
  (or is not ready within 1.5 s) it comes up the usual way and the field rides on it. The debug log says which way
  each opening went, when the keyboard was ready and where the field and the keyboard were.
- Arriving after a redirect ("Arrival after a redirect", was "Content animation on return"): fading in now covers the
  whole screen, wallpaper included, over the system's home screen instead of cutting to it. "At once" arrives on the
  launcher's last picture when it is up to date, and the icons no longer disappear and come back.
- The launcher's last picture stays usable after you open an app from search: the keyboard on its way out no longer
  makes it out of date.

### Fixed
- The clock and every timer showed the old time for some seconds after unlocking. They now read the phone's clock
  when the screen turns on, at unlock, when the launcher comes back and on the system's minute tick; the debug log
  says when a minute on screen was late, and by how much.

## 0.11.2 — 2026-11-20

### Added
- Settings → Study companion → More → "Camera for board photos": choose the camera app the "Board" chip of a class
  uses (any camera on the phone, or the phone's default), and "Take a test photo" to try the whole way outside a
  class (the photo is saved in Pictures/ESSAI/Test).
- Settings → Diagnostics → "Speed check": plays the drawer, search, the home menu and the study hub on your home
  screen, with the glass as you set it and then without its blur, frosted, liquid, without shadows, without the
  theme's material and as a flat tint. Each step's cost goes to the debug log; it takes about a minute.
- The debug log's "Frames:" lines say at what refresh rate the screen ran, and each search opening says at what
  height the keyboard opened and where the field waited for it.

### Changed
- Search: the field waits at the lowest height your keyboard opened at lately, and only the keyboard pushes it up
  from there, so it no longer goes above the keyboard and comes back down. A keyboard already sliding in is no
  longer asked for a second time (that could restart it).
- Liquid glass is the default ("Kind of glass: Liquid" in the theme): the edge bends the light. A theme you saved
  keeps its own glass.
- Closing a sheet, a folder, the wallpaper screen, the speed bump card, the bulk tag sheet or the bar while picking
  apps is a quick fade (0.1 s) again: in 0.11.1 they sank back, which drew their glass anew on every frame.
- The shapes of themed buttons, pills and materials (Letterpress, wood, metal…) are worked out once per size instead
  of on every frame; the study hub spent most of each frame on them.
- The halo on the pill for a block left unmarked breathes every 5 minutes instead of every 14 seconds (each breath
  redrew the pill's glass: 600 frames a minute with nothing moving).

### Fixed
- Board photos: the camera now writes into a private file and the launcher saves it in Pictures/ESSAI/<class>.
  The photo being taken is written down, so it is still saved if the launcher is restarted while the camera is
  open, and it is kept whenever the camera wrote it, whatever the camera answers. Before, the folder appeared but
  the photo could be lost. Every step goes to the debug log.

## 0.11.1 — 2026-11-20

### Added
- Letter areas, in place of 0.11.0's letter circle: plain circles over the drawer's apps. Start a swipe inside
  one and the letters come out at its edge with the bubble, as if your finger were on them there, and follow
  your finger wherever it goes; the list follows. A tap or a long press inside an area still opens the app under
  it (or its menu): only a swipe starts the letters. Add as many areas as you like in Settings → Drawer and
  search → Letter index ("Letter areas", on the first tab): for each one the edge the letters come out of (left
  or right), where it is (across and down the apps) and its size, with Remove and Reset; colour, opacity (0 %:
  invisible, still working) and opacity while the apps scroll as before, with a live preview.
- "Letters only from the areas" (on by default): the screen's edge is left to Back; switch it off for the edge
  to pull the letters too.
- The debug log's "Frames:" lines split each frame into input and composing, layout, drawing and sync, next to
  the GPU, so a slow screen shows where its time goes.

### Changed
- Wallpaper screen: tapping a picture only picks it. It is ringed, with a check, in the sheet; your screen keeps
  its wallpaper (you see the picture full screen while you place it, and it fades back after). Apply plays your
  wallpaper transition from the Apply button, from the old wallpaper to the new one, with its icon colours and
  looks; the files and the system wallpaper are written once the transition is over.
- Smoother letter scrolling: the list glides to the letter at up to about 100 rows a second instead of jumping a
  long way at once, which built a whole screen of apps in one frame (the letters froze, bent); short moves are
  scrolled by pixels, so the list builds the next row ahead, between frames.
- Sheets, folders, the speed bump card, the "Customise" chip, the bar while picking apps, the bulk tag sheet,
  the wallpaper screen's sheet, its placing bar and "Show controls" now leave the way they came: sheets sink
  back, a folder shrinks into its icon, the chip back to your finger. Several of them vanished in one frame.
- The drawer's tag list no longer rebuilds the whole drawer on every frame while it grows, and the wallpaper
  screen no longer rebuilds itself on every frame while its sheet rises; thumbnails fade in as they load.

### Removed
- The letter circle (turned like a knob or a clock face, held to move): the letter areas replace it. A circle
  you had moved becomes the first area, where it was; "The edge pulls the letters too" becomes "Letters only
  from the areas" off.

### Fixed
- Wallpaper screen: Apply without a new picture no longer wipes the current picture's own look (blur, dim,
  saturation and vignette from "This picture"), and opening the screen shows that look rather than the one for
  all pictures.

## 0.11.0 — 2026-11-20

### Added
- The letter circle: a ring of arcs in three shades of your wallpaper's colour floats over the drawer's apps,
  by default on the right, a little above the middle, in reach of one thumb. Slide round it and the
  letters come out at the edge with the bubble, exactly as when you pull them, and the list follows. It turns
  like a knob (clockwise goes on through the alphabet, one turn for A to Z) or, if you choose, like a clock
  face (A at twelve; touching a spot jumps to its letter). It fades while you scroll the apps and comes back
  when the list stops. Hold it still to lift it, then drag it where you want it. Settings → Drawer and search →
  Letter index: on or off, how it turns, size, place, colour (wallpaper, accent or yours), opacity, opacity
  while scrolling, and how far to turn for A to Z, with a live preview.
- The wallpaper screen has a handle at the top of the sheet: pull it up for about three and a half rows of
  pictures, down for two; it stays where you leave it.
- The debug log's "Frames:" lines name the screen that was open (home, drawer, search, study hub, wallpaper
  screen…), and the system's own icon colours are logged next to the launcher's guess when they arrive.

### Changed
- The letters at the edge hide until you use them (the new default for "Show the letters"); with the circle
  on, the edge is left to Back. If you had "Always", it becomes "Hidden until you use them" once; "The edge
  pulls the letters too" gives the edge back. "Show the letters" moved to the Letter index's first tab, and
  settings search finds it by "fade" or "hide".
- Opening search: the field goes to the height your keyboard opens at, not the height it grew to while you
  typed (a suggestion strip, emoji); if it still lands elsewhere, the field eases there. A keyboard request
  the phone dropped is made again after 200 ms (it was 350).
- Picking a picture in the wallpaper screen plays your wallpaper transition (the ripple starts from the
  picture you tapped), as in the wallpaper strip; Apply then only closes the sheet. The pictures show two
  rows and a bit (one and a half before).
- Icons in "System colours": right after a new wallpaper or layout they take the colours Android will work
  out (Material You's tonal spot, computed the same way), so they no longer change a second time when MagicOS
  reports its colours seconds later; if MagicOS still differs, the icons fade over in 0.6 s instead of jumping.
  The launcher waits up to 45 s for the system's colours (20 s before).
- Faster redirects: MagicOS draws its home screen smaller while going home, and the launcher now sees that
  against the screen (it compared it with its window, which MagicOS shrinks too), so most redirects skip the
  quarter-second wait that keeps a recents swipe from being taken for going home.
- Study hub: the buttons and chips that scroll with the day are drawn without sampling the glass behind them,
  and the ones that stay put no longer redraw their glass on every frame of a scroll, so scrolling the day is
  lighter. Dragging the letters: a long way is jumped and only its last rows glide.
- The Letter index page's preview runs through the letters twice and then rests (it ran for as long as the
  page was open); changing an option plays it again.

### Fixed
- Drawer: "Side margins" works on both sides again (0.10.3's room for the letters overruled it on their side).
- The wallpaper screen's "Blur and dim" tab was cut to "Blur and".
- The drawer motion preview in Settings no longer writes "drawer: open/close" to the debug log every 1.7 s.

## 0.10.3 — 2026-11-18

### Added
- Two clock designs in the clock's gallery (edit mode → Customise on the clock). Tall: tall condensed digits
  with a soft bevel, the colon as two round dots and a small bold date above. Stacked: hours over minutes in
  big, heavy, rounded digits in a light tint of the wallpaper's colour, with the date and the study line
  above them. Both follow the clock's colour, size, weight, font, fill and shadow.
- Settings search also finds what a page edits with its own editor: the search page's cards, the pill's
  items and study item, the home and app menus, the drawer's tabs and tags, and the Theme lab.

### Changed
- The stacked clock design of before is called "Two lines" (hours and minutes in two colours); clocks that
  use it look as before.
- Study → Study companion: the line under the clock and its colour, how the hub opens and how its blocks
  show are on the first tab (Layout), where you look first.

### Fixed
- With individual pills, the glass (blur, rim, bend) now flows from the home pill's to the drawer pill's as the
  drawer opens, like their colours; it used to switch at the end.

## 0.10.2 — 2026-11-18

### Added
- "Customise the pill" is an action of its own: choose it for the pill's long press (or tap or swipe) to
  get the chip that opens the pill's settings page.
- The letter index (String) can sit lower and shorter: "String: top end" and "String: bottom end" in
  Settings → Drawer and search → Letter index → Layout. Only that stretch of the edge takes your finger;
  the rest of the edge keeps Back.
- "Letter colour" for the index (Look tab): from the wallpaper by default, or the theme's text, the
  accent, your own colour, or white and dark as before.
- In the Theme lab: "Search bar tint" and "Material on the pills" (Colours → Pills), the preset
  "Before 0.10.2", and Blueberry (the fixed blue) one tap away for the accent.
- On the search page, typing "!" lists the study commands and typing "?" lists your Claude prompts.
- The frame meter also measures dragging the letter index: "Frames: … letter index" lines in the
  debug log.

### Changed
- The pills are liquid glass again: the home and drawer pills have no dark fill, only the theme's own
  frost and its see-through colour. The search bar keeps a tint, a third as strong as before.
- Your WM Keyboard theme "wallpaper stoped" is the default theme, for every button, chip, round button,
  sheet, menu, card and the pills. Every colour follows the wallpaper and keeps its own transparency:
  buttons, menus, sheets and cards take its dark colour, chips its muted one, the main button, the
  accent and pressed buttons its vibrant one, text its light one. Text turns dark where light text
  would not read. Your phone switched to it once; the look you had is the preset "Before 0.10.2".
- A long press on the pill runs its own action again (Settings by default) instead of showing
  "Customise the pill". The Customise chip stays on the dock, folders, the search page and the hub.
- The study hub opens at full height with the whole day: the block now or next, the timeline, tasks,
  tomorrow. Collapsed is still a choice ("Study hub opens").
- Block cards in the hub follow "Blocks in Today": in compact mode a block shows its title, time and
  step count, and a tap shows its steps and buttons (the block now or next too).
- The line under the clock, its ring and its ✓ take the clock's own colour (from the wallpaper when
  the clock takes it there), with the clock's own soft shadow instead of a halo.
- The search page is calmer: the field, your apps, one study card (the block now or next with today's
  progress) and one Ask Claude chip. "Today · 24 days to …" opens the calendar month and the exam
  countdown. Every other card can be added back in the search page's settings.
- The drawer's tag list works from the bottom: Clear and Done, the search field above them and your
  pinned tags right above it, with the list growing upwards. The pin is on the left of each tag, the
  tick on the right.
- The letter index follows the finger more smoothly: each edge's string has its own spring, the
  letters fade across between edges instead of jumping, and the letters near the finger grow and turn
  bold gradually.

### Fixed
- The block now or next appeared twice in the hub (its card and the timeline).
- The hub's header ("0/3 …") and its row of chips ("Quiz m…") were cut; the chips now wrap.
- On the search page the calendar was cut under the status bar and the chip rows ran off the edge.
- The index letters touched the last column's labels in the drawer; the list now keeps a lane clear.
- "Like the clock" for the line under the clock used the screen's text colour, not the clock's.
- "Export for AI" listed options as changed when their stored value was the default (for example
  Press motion "squishy"); Settings showed a dot on them too. Only real changes count now.

## 0.10.1 — 2026-11-18

### Added
- The Theme lab (Settings → Look): every option of a WM Keyboard theme, in nine groups (Glass, Material,
  Textures, Colours, Shape and size, Press effect, Moving background, Background image, Extras) and Pills. A large
  live preview shows a sample, the home screen, the drawer or search, and every change is applied at once. Undo
  and redo, reset per group, and a long press on an option to reset it, explain it or copy its id. Options that
  only matter with another one are greyed, with the reason.
- Presets in the lab: your keyboard backup, the look you had before this version, the default. Import and export
  of `.wmtheme.json` files, and "Export my tuning" (only what you changed).
- Any colour of the theme can follow the wallpaper: its dominant, vibrant, muted, dark or light colour.
- The three pills (home, drawer, search) share one look, or each has its own; "Copy from" copies one to another.
- The theme reaches every surface: sheets, menus, cards, fields, checkboxes, the pills, the hub. Buttons take the
  keyboard's material (caps, metal, wood, leather, paper, neon…) with its edge and label effect, textures, the 11
  shapes, sizes, gaps, fonts, the press motion, the light under the finger and particle bursts. The moving
  background moves over the home wallpaper; the background picture and stickers sit behind sheets; the blur
  behind sheets blurs the home screen while one is open.
- A frame meter: while the home screen keeps drawing (a moving background), the debug log says every ten seconds
  how many frames were late and what was on screen, so the cost of an effect is known on the phone itself.
- "Customise …" on the home screen: a long press on the dock, an open folder, the search page or the hub (and on
  the pill, while its long press is left on Settings) shows one glass chip that opens that surface's settings page.
- Every option can be held for Reset, Explain (what it does, where it lives, its default, range, what it needs,
  whether layouts keep it, its id) or Copy id.
- Backup and Claude (Settings → System): "Back up now", with or without wallpapers. A backup every day into a
  folder you choose (on the phone or a cloud drive), keeping the newest seven copies. Restoring first shows what
  the backup holds and every option it would change (now → then); a backup without wallpapers keeps only the
  library pictures the phone has.
- Tune with Claude: "Send to Claude" shares every option (what it does, its range, default and value now) with your
  question and the rules for answering (only a JSON list of changes) to the Claude app; "Export for AI" saves the
  same as a Markdown file. Paste Claude's answer back: every change is listed before anything happens, entries that
  are not valid (an unknown option, a wrong kind of value, out of range) are shown in red and left out, and "Undo"
  puts everything back in one tap. The launcher makes no network call for this.
- Picking many apps in the drawer: hold an app and choose Select, then tap the others. A bar offers Tag, Untag,
  Hide and Add to home for all of them at once.
- The Now card and the Today widget have their own "Customise" sheet in edit mode, like the date and battery
  widgets: a glass card or none, corners, text or accent colour, and which parts show (the block's tasks and its
  buttons; tasks and times).
- With the drawer's tabs at the top, their pill rises out of the home pill as the drawer opens and sinks back into
  it as it closes, following the finger.
- The layouts sheet shows real previews: each layout's home page with its own wallpaper (or the one on screen) and
  the apps' real icons, instead of dots.
- A layout's wallpaper can be any picture of the library: hold the layout in the layouts sheet → "Choose its
  wallpaper…", then tap a picture.
- A layout for a time of day: choose it on the Layouts page, with its hours (from, until, over midnight too) and
  days (Monday to Friday, the weekend, every day). It comes in and goes by itself like the trip-home and exam-week
  layouts, which come first; the layout from before comes back unless you switched by hand.
- Each wallpaper can keep its own look: in the wallpaper screen, "Blur and dim" → "This picture": blur, dim,
  saturation and a vignette, used whenever that picture is the wallpaper.
- "Tint colour" of the glass can be "The wallpaper's colour": every glass surface takes the wallpaper's muted tone
  and follows it when the wallpaper changes.
- A tag's own list: in the drawer's tag list, the apps button beside a tag opens every app with a check, and a
  search, to tag or untag many apps in one place.

### Changed
- The study hub opens collapsed: the block now or next, with "The whole day" one tap away (or push the list up,
  or tap the handle); it springs between the two heights and any spring can be caught by the finger. "Study hub
  opens" in Settings → Study brings back the whole day at once.
- The hub reads over any wallpaper: "Dim behind the hub", "Blur behind the hub" (the home screen blurs as the hub
  grows), and "Text in the hub" (automatic: light over a dark hub or a strong dim; or always light or dark).
- The line under the clock picks its colour for the wallpaper behind it (dark over a light picture, light over a
  dark one) with a soft halo; it can also follow the clock, or be always white or always black.
- Settings in seven entries: Home, Drawer and search, Study, Look, Wallpaper and layouts, Motion and feel, System.
  Each holds pages, one per surface (Pill, Dock, App drawer, Letter index, Liquid glass…); a page keeps its live
  preview at the top and shows its options in tabs: Layout, Look, Behaviour, More. A dot marks what you changed,
  the tabs count it, and "Reset" resets one tab.
- Search shows where each option lives (Home › Pill › Behaviour) and opens its page on its tab with the option lit
  up; "Recently changed" does the same. Search also matches page and entry names.
- An option that only matters when another one is on stays in its place, greyed, and says why ("Only when
  “Haptic feedback” is on") instead of disappearing.
- Merged pages: Drawer motion into App drawer (Behaviour), Hand-over into Redirect, Detection with Exclusions and
  safety, Home menu with Long-press menu (Menus). Effects and touch is split into Press and touch and Moving
  wallpaper. Renamed: Theme → Colours and fonts, Scroll index → Letter index, Saved layouts and backup → Layouts
  (backup moved to System → Backup and Claude). Setup checklist, Diagnostics, Calibration, Debug log, Updates and
  About are in System; the Theme lab is in Look. No option was removed and none was reset: their stored names are
  unchanged, and links to the old sections open the page that has their options.
- The accent is a fixed blue (Blueberry, #5484ED) unless you had chosen your own colour. The main button keeps
  its block colour.
- The search bar and the other pills are dark by default when a theme does not say otherwise.
- A theme's moving background and press splash take the place of the launcher's own while the theme has one.
- Backups now include the theme's pictures (its background, textures and stickers).

### Removed
- The theme editor of 0.9.14 (Settings → Buttons → Edit the theme): the Theme lab replaces it, with every option
  it had.
- Keyboard options that mean nothing in a launcher are kept in the theme file but not shown: the suggestion bar,
  toolbar, one-handed panel and navigation bar colours, landscape and animated backgrounds, popup placement,
  gesture trail width, sounds, key lighting, per-key overrides, and the "meld" splash (letters merging between
  keys).

## 0.10.0 — 2026-11-17

### Added
- This changelog. The Updates screen shows what a new version brings before you install it, and lists every
  version under "All changes". After an update, a "What's new" card appears once above the pill ("Read all" shows
  everything, "Got it" hides it).

### Changed
- Sheets can no longer run off the screen: at most 85 % of the space above the keyboard, the title and the
  buttons stay in place and the middle scrolls. The keyboard lifts the whole sheet, so the field you type in stays
  in sight.
- An app's Tags (from its long-press menu) open as a sheet: the list of tags scrolls, and the "New tag" field stays
  at the bottom with a + button, however many tags you have. With more than eight tags, a search narrows the list.
- The drawer's tag list uses the whole height left and ends above the drawer's pill, so Clear and Done are always
  visible.
- The layouts sheet and a widget's Customise sheet use the same sheet.
- Long menus (an app's long press, a tab's menu) and big folders scroll inside their card.
- Lists in dialogs (choices in Settings, colour pickers, list editors) scroll.
- A new version is published only when it is meant to be: two releases at most per working session, each with
  its changelog entry. Builds in between are only tested.

### Fixed
- With nine tags or more, the "New tag" field of an app's Tags could not be reached.
- The drawer's tag list hid its Clear and Done buttons behind the drawer's pill.
- 0.9.22 and 0.9.23 were never published: their build was fine but the emulator test failed on a Home-button check
  that ran while the accessibility service was reconnecting, and on a clock-line check that did not expect the
  evening check-in after 22:30. Both checks were wrong, not the launcher; they arrive with this version.

## 0.9.23 — 2026-11-17

*Not published on its own: it came in 0.10.0.*

### Added
- The calendar widget has its own "Customise" sheet in edit mode: a glass card or none, corner size,
  colour (text or accent), the month grid or a compact week, and the next events under it. It changes as
  you choose.

## 0.9.22 — 2026-11-17

*Not published on its own: it came in 0.10.0.*

### Added
- The date and battery widgets have a "Customise" sheet in edit mode, like the clock: size, weight,
  colour, font, and which parts show (weekday, month, year; ring, percentage, "charging").

## 0.9.21 — 2026-11-17

### Added
- Layouts for a while: hold a layout in the layouts sheet and switch to it for 30 minutes, 1 hour or
  2 hours. When the time is up the previous layout comes back, unless you changed layout by hand. "Keep it"
  stays on the new one.

## 0.9.20 — 2026-11-17

*Not published on its own: it came in 0.9.21.*

### Changed
- Every animation follows the motion speed setting (26 of them used a fixed speed before).
- Every vibration follows the haptics switch and strength (the letter index and the tag strip had their own).

## 0.9.19 — 2026-11-17

*Not published on its own: it came in 0.9.21.*

### Added
- Past papers for exam week: choose a folder with one sub-folder per module. In the week before an exam
  the search page shows a "Past papers" card, the next exam's module first; a tap opens a paper in your PDF
  app.

## 0.9.18 — 2026-11-17

### Added
- Battery saving on the trip home: under 30 % and not charging, the glass is frosted, other apps' widgets
  rest and the moving wallpaper stops, until you are home or charging.

## 0.9.17 — 2026-11-17

*Not published on its own: it came in 0.9.18.*

### Added
- Other apps' widgets take the launcher's look: their solid backgrounds give way to the wallpaper or the
  glass, and their text takes the launcher's colour, after every update they send. A switch for all of
  them and one per widget.

## 0.9.16 — 2026-11-17

*Not published on its own: it came in 0.9.18.*

### Added
- Quiet during study blocks (off by default): notifications wait until the block ends, then a summary says
  what came. Calls, alarms, ongoing ones, chosen people, school mail and apps tagged "important" get
  through. Nothing is lost: Android gives the held ones back at the end of the block.

## 0.9.15 — 2026-11-17

*Not published on its own: it came in 0.9.18.*

### Changed
- Switching tags in the drawer glides: one selection slides from chip to chip, and apps glide to their new
  places instead of jumping.
- Typing `!` or `?` first in the drawer's field hands over to the search page, where the study commands and
  questions to Claude run.

## 0.9.14 — 2026-11-17

### Added
- A theme editor (Settings → Buttons → Edit the theme): every colour and its opacity, border, shape,
  corners, sheen, glass, shadow and glow of buttons, the main button, chips and round buttons, the frost
  under them, the accent and the glass tint, with a live preview.

### Fixed
- A sheen on a button now covers the whole button at any size.

## 0.9.13 — 2026-11-17

*Not published on its own: it came in 0.9.14.*

### Added
- Your WM Keyboard theme styles every button, chip and round button by default. Themes can be imported
  (WM Keyboard themes and backups) and exported.

### Changed
- The theme's accent and glass colour were applied once where you had not chosen your own.
- "Tune the glass" stays still unless you press Move.

### Removed
- The launcher's own button style is no longer the default; it stays available as an option.

## 0.9.12 — 2026-11-17

*Not published on its own: it came in 0.9.14.*

### Added
- Real liquid glass: light is bent through the rounded edge of each glass surface, split into colours,
  caught on one edge and shaded on the other.
- "Tune the glass": live sliders and presets (Real glass, Clear, Frosted, Thick, Subtle, Your keyboard).

### Removed
- The glass library's lens effect is replaced by the new glass. It stays as the fallback when a phone
  cannot run the new one, and as a setting.

## 0.9.11 — 2026-11-17

### Added
- Layouts that switch by themselves: one for the trip home (after the last class until you are home) and
  one for exam week; the previous layout comes back afterwards unless you changed it by hand.
- The hub's "On the way home?" card shows what the check-in planned for the trip.

## 0.9.10 — 2026-11-17

*Not published on its own: it came in 0.9.11.*

### Removed
- The glass box behind the calendar, Now card and Today widgets. They now sit on the wallpaper; the card
  is an option.

## 0.9.9 — 2026-11-17

### Added
- Sunday review card on the search page: the week's blocks, focus time, absences and days to the next
  exam; a tap reviews the week with Claude.

## 0.9.8 — 2026-11-17

*Not published on its own: it came in 0.9.9.*

### Added
- The drawer's search field shows the study glance and has a one-tap Ask Claude.
- A size setting for the tag button (bigger by default, 50 dp).

## 0.9.7 — 2026-11-17

*Not published on its own: it came in 0.9.9.*

### Added
- Glass takes on the colour of the current study block's module.
- "Photo of the board": the camera saves the photo straight into a folder named after the class.

## 0.9.6 — 2026-11-17

*Not published on its own: it came in 0.9.9.*

### Changed
- The drawer's letter index is now a short rail within thumb reach, with a magnifier while you drag. It no
  longer triggers Back by accident.

### Removed
- The full-height letter strip on the edge is no longer the default; it stays as an option.

## 0.9.5 — 2026-11-17

*Not published on its own: it came in 0.9.9.*

### Added
- Data from the evening check-in: confirmed exam dates (≈ until confirmed), an absence meter per module
  (marked unverified), the grade average once every coefficient is known, and a daily flashcard that turns
  over.

## 0.9.4 — 2026-11-17

### Added
- Rest after getting home: a rest ring, then a Start button for the evening's first block.
- Wind-down: from 22:30 the wallpaper loses colour and the evening check-in comes first.
- Speed bump: during a study block, apps tagged "distracting" wait behind a card for 5 seconds.
- Icon motion: press speed, an optional wobble while editing, and icons that land when you come back.

### Removed
- Nine of the eleven press motions. Squish (crisper), Tilt and Off remain.

## 0.9.3 — 2026-11-17

*Not published on its own: it came in 0.9.4.*

### Added
- A study-first search page: cards you put in your order (widgets, timeline, exam countdown, next block,
  study commands, Ask Claude, apps).
- Commands `!next`, `!exam`, `!plan`, `!abs`, and `?question` for Claude.
- "Leave by" before the first class, "present?" after each class, a pulse when a block ended without a
  mark, and "Reschedule".

### Fixed
- The calendar on the search page is no longer cut off at the bottom.

## 0.9.2 — 2026-11-17

### Added
- The pill's study item is yours: an icon, an app's icon, your picture or a liquid circle; frame, size,
  colour and day ring. Every pill item can have its own tap, long press and swipes.
- A long press on the study item writes a note for tonight's check-in.

## 0.9.1 — 2026-11-17

### Added
- The study hub ("Today"): grows out of the pill on a spring and shrinks back under your finger, with the
  timeline, the block now or next, tasks, notes, tomorrow, quick notes and Claude prompts.
- Arrival home (home Wi-Fi, the charger or "I'm home") and "Shift tonight", which moves the evening's
  blocks.
- A double tap on the clock opens the hub with the note field ready.

### Removed
- The old Today panel: the hub replaces it.

## 0.9.0 — 2026-11-17

### Changed
- Going home during a call now redirects as usual. The old switch "Do not redirect during calls" is
  replaced by "During calls" with three choices.

### Fixed
- Going home during a phone or Messenger call stayed on HONOR Home.
- The drawer and keyboard no longer flash on the way home after opening an app from the drawer.
