# Settings: every option declared once

Contents: 1 The registry · 2 The store · 3 Where options live (the map) · 4 The screens · 5 Search, changed, reset,
recent · 6 Export, import, backup, AI tuning · 7 Migrations and no data loss · 8 Checklist for a new option

Code: `assets/kit/src/main/kotlin/liquid/kit/settings/` (`Setting.kt`, `SettingsStore.kt`, `AppSettings.kt`) and
`ui/SettingsKit.kt`.

## 1. The registry (D10)

Hundreds of options stay maintainable because each one is a declaration, and everything else is generated from the
declarations: the screens, settings search, per-option reset, the "changed" dots, export, import, backups, profiles,
presets and the AI-tuning export.

```kotlin
val Feel = ChoiceSetting(
    key = "motion.feel", default = "liquid",
    options = listOf("calm" to "Calm (no overshoot)", "liquid" to "Liquid (a soft overshoot)", "lively" to "Lively"),
    title = "Feel",
    summary = "How sheets, menus and popups move: one spring for all of them",
    section = "motion", keywords = "animation spring bounce overshoot motion feel",
)
```

Types: `BoolSetting`, `IntSetting` (min, max, step, unit), `ChoiceSetting` (value to label), `TextSetting`
(placeholder), `ColorSetting` ("#RRGGBB", empty = automatic), `StringListSetting`, `ActionSetting` (an action of the
app as text). Each has key, default, title, summary, section, keywords, hidden.

- **Keys never change** once shipped (`section.name`, lowercase camel after the dot). Rename the title instead.
- **Internal state** is declared the same way in section "state" with `hidden = true`: stored, backed up, never
  listed (`state.recentlyChanged`, `state.pendingPhoto`, counters, "what the keyboard answered").
- Title: a few words. Summary: one plain sentence saying what changes, often with the default's reason.
  Keywords: the words a person would type, synonyms included (vibration/haptic, colour/color, bounce/spring).

## 2. The store (D11)

`SettingsStore`: reads come from an in-memory map (instant, safe from any thread, even an accessibility service);
writes update the map at once and persist to DataStore Preferences in the background. **Only values that differ from
the default are stored**, so improving a default reaches everyone who never touched it.

```kotlin
val feel by store.observe(AppSettings.Feel)                        // State<T>, recomposes on change
val dim by store.derive { get(AppSettings.Dim) / 100f >= 0.45f }  // recomposes only when the derived value changes
store.set(AppSettings.Feel, "calm"); store.reset(AppSettings.Feel); store.get(AppSettings.Feel)
```

Wire the motion settings at start and on change: `Motion.style = feel`, `MotionScale.speed = speed / 100f`,
`Haptics.enabled = haptics`.

## 3. Where options live (the map, D140)

- Settings has **seven entries** at most on its first screen (Liquid Launcher: Home, Drawer and search, Study, Look,
  Wallpaper and layouts, Motion and feel, System), each with its hue and icon.
- An entry holds **pages, one per surface** of the app (the pill, the drawer, search, the keyboard…), each with a
  live preview at the top and up to **four tabs**: Layout, Look, Behaviour, More.
- `SettingsMap` names each key's page and tab (a key ending in `*` is a prefix); options of a page's sections that
  no tab names fall into More. **A unit test fails if an option has no page or a page names a key that does not
  exist.**
- Fine-tuning options sit under a collapsed "Advanced" row on their tab (D83).
- Editors that are not single options (a list of cards to order, a theme lab) are registered too, with a title,
  summary and keywords, so settings search finds them.
- Every new option goes to its right place (entry > page > tab, with keywords), and the user is told when an option
  moved.

## 4. The screens

Built from the kit (look and motion in `design.md` §5 and `motion.md` §6): `KitScaffold(title, onBack, actions)`
with a heading and breadcrumbs, `KitSection` headers, `KitGroup { rows }` of `KitNavRow(title, summary, icon,
accent, value, flightKey)`, `KitSegments` for the tabs, `KitChips` for short choices, `KitSlider` for numbers (with
the value shown), switches tinted with the page's hue, a "?" button on each row that explains the option in one
sentence, the live preview of the surface at the top of the page (deferred until the page push has settled).

The first screen: a search field, "Recently changed" (the last options changed), then the seven entries as a
group of rows with their icon tiles.

## 5. Search, changed, reset, recent

- **Search** matches title, summary, keywords and section names, ranks title matches first and forgives one typing
  mistake (D83); results show the path (Entry › Page › Tab) with the match in bold; a tap opens the page on the
  tab with the option lit up and scrolled into view.
- **Changed** means different from the default, not "stored" (a restore can store a default value, D147): a dot on
  the row, counts on tabs, "show changed only".
- **Reset** per option (long press or menu), per page, per section.
- **Recently changed**: the store keeps the last 8 keys changed (not hidden ones, not state).

## 6. Export, import, backup, AI tuning

- **Export/import** are filtered dumps of the store: typed text values `b:true`, `i:42`, `s:text`; import takes only
  keys it knows (old files keep working) and treats missing keys as defaults.
- **Backup** is one zip (`.llbackup`): `backup.json` (version, date), `settings.json`, `data/*.json`, `files/`
  (pictures). Restore decodes everything first, then replaces the stores in memory, so a broken file cannot leave a
  half-applied state; no restart (D50). A daily backup can run as a periodic JobScheduler job into a folder the user
  picked (SAF), keeping the newest N (D142).
- **Tuning with Claude** (no network): "Export for AI" writes a Markdown file with the registry's facts for the
  changed and relevant options and the rules ("answer with one JSON patch `{"set":…,"reset":…,"why":…}`"), shared
  through the share sheet; the pasted answer is validated against the registry (unknown ids, wrong kinds, values out
  of range are refused and shown in red), only valid entries apply, and one Undo restores what it touched (D141).

## 7. Migrations and no data loss

- `SettingsMigrations` runs once per version step: it may move a value to a new meaning (a removed choice falls back
  to the nearest one, a stored "Always" becomes "Hidden until used"), never resets the user's choices.
- Removed options: delete the declaration; a stored value is simply ignored (import skips unknown keys).
- Data files (layouts, decks, tags) are JSON written whole to a temp file and renamed into place; a file that cannot
  be read is kept aside (`x.broken.json`) and the app starts from the default, logging why.
- Every change of format keeps old data reading (add fields with defaults; never repurpose a field).

## 8. Checklist for a new option

1. Declare it (key, default, title, plain summary, section, keywords).
2. Place it on its page and tab in the map (the test will remind you).
3. Read it where it is used with `observe`/`derive`/`get`; if it is visual, make the page's live preview show it.
4. Pick a default that is right for most people, explain it in the summary, and log the choice in DECISIONS.md if it
   was not asked for.
5. Changelog line in plain words, saying where the option is (Settings › Entry › Page › Tab).
