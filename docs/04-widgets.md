# 4. Widgets

## 4.1 Two kinds

| | Built-in widgets | AppWidgets (from other apps) |
|---|---|---|
| Built with | Native Compose, inside the launcher process | The other app's RemoteViews, shown in an `AppWidgetHostView` |
| Theming | Full: fonts, colors, glass, shapes, motion | Limited: clip corners, padding, optional glass plate behind |
| Animation and touch | Anything Compose can do, 90 fps | Whatever the provider supports |
| Data | Shared in-process repositories, no IPC | Provider updates on its own schedule |
| Cost | Very low | One extra view hierarchy each |
| Permission | None | One system dialog per provider the first time |

Rule: anything I can build myself (clocks, date, study, search, app rows) is a built-in widget. AppWidgets are for other apps' content (music player, weather app, notes app).

## 4.2 Built-in widget framework

Each built-in widget is a small class:

- `id`, name, category, preview
- default, minimum and maximum cell span
- an options list declared in the same **settings registry** as the launcher settings, so its config screen, search, reset and export come for free
- `@Composable Content(options, sizeInCells, sizeInPx, theme)`

Shared data sources, each started only while at least one visible widget uses it: clock ticker (minute or second precision as needed), calendar, tasks, battery, media session, weather, app usage.

### Common options on every built-in widget

- **Surface**: none / tint / frosted / liquid glass; opacity; border; shadow; corner shape and radius
- **Color**: theme role / wallpaper-derived / fixed; separate text, accent and secondary colors
- **Typography**: font (any installed or imported), weight, width, size (auto-fit to the widget or fixed), letter spacing
- **Padding and alignment** inside the cell area
- **Tap action, long-press action, swipe actions** (action picker)
- **Visible in profiles**
- Save as style preset, copy style / paste style between widgets

## 4.3 Built-in widget list

### Clocks

| Widget | Description | Specific options |
|---|---|---|
| **Big Digital** | Large HH:MM like your current one | 12/24 h, separator style, blink, seconds, leading zero, handwritten/outlined/filled fonts, stretch to width |
| **Stacked** | Hours above minutes | Per-line color and weight, overlap amount |
| **Minimal** | Small time with date line | Date format, order, divider |
| **Analog** | Dial with hands | Dial style (none, ticks, numerals, dots), hand shapes, second hand (smooth/tick/off), center cap |
| **Word clock** | "ten past two" | Language, casing, precision (1 / 5 min), highlight color |
| **Flip clock** | Split-flap digits | Card color, flip speed |
| **Date + next event** | Date and the upcoming calendar event or study block | Calendars, hide when none, countdown |
| **World clock** | 2–4 time zones | Cities, layout |

### Date and calendar

| Widget | Description |
|---|---|
| **Date line** | Free format string (`EEE d MMM`), week number, day progress |
| **Month grid** | Current month, dots on days with events or study blocks |
| **Agenda** | Next N events, grouped by day |
| **Countdown** | Days until a date (exam), with optional progress bar |

### Study (details in doc 5)

| Widget | Description |
|---|---|
| **Now card** | Current block with progress ring, time left, its tasks with tap-to-complete |
| **Today list** | Today's blocks and tasks, check to complete |
| **Week view** | 7 columns of blocks with completion state |
| **Next block chip** | One line: "Next: Analysis 16:00 (in 25 min)" |
| **Focus stats** | Blocks done today/this week, streak, friction count |
| **Quick add** | One-tap field to drop a task into the inbox |

### Search

| Widget | Description |
|---|---|
| **Search pill** | The liquid-glass bar; options in settings 3.6 |
| **Search icon** | Compact round button version |

### Apps

| Widget | Description |
|---|---|
| **Suggested apps** | Predicted apps row or grid |
| **Recent apps** | Last launched apps |
| **Tag row** | All apps with a tag, scrollable |
| **Large folder** | A folder shown expanded |
| **Contacts row** | Chosen contacts; tap action per contact (call, message, app) |

### System

| Widget | Description | Needs |
|---|---|---|
| **Battery** | Level as ring, bar or text; charging state | nothing |
| **Media** | Current track, artwork, controls | notification access |
| **Weather** | Now, today range, optional 3-day row | network (opt-in), city or location |
| **Notification digest** | Count or icons of pending notifications | notification access |
| **Settings shortcuts** | Row of buttons opening system panels (Wi-Fi, Bluetooth, sound…) | nothing |

A normal app cannot switch Wi-Fi, Bluetooth or mobile data directly on Android 13 and later (the phone runs 15), so those buttons open the system panel instead of toggling.

### Utility

| Widget | Description |
|---|---|
| **Text template** | Free text with variables: `{time:HH:mm}`, `{date:EEEE}`, `{battery}`, `{next_event}`, `{task_current}`, `{block_left}`. Covers most one-off custom widgets. |
| **Note** | Sticky text, edit in place |
| **Image** | Picture or frame, crop and shape, optional slideshow from a folder |
| **Action button** | Icon + label that runs any action or chain |
| **Spacer / divider** | Line or empty block for layout |

## 4.4 Placing

- From edit mode → Add → Widgets, or long-press empty space → Widgets.
- Drag from the picker onto any page, panel, or the dock. The drop preview shows the footprint in cells; occupied cells turn it red unless "push items aside" or "allow overlap" is on.
- Tap instead of drag = place in the first free area of the current page.
- Widgets use the same grid as icons, any span from 1 × 1 to the full page.
- AppWidgets: on drop the launcher allocates an id and binds it. If the system asks for permission, one dialog appears; if the widget has a configuration activity, it opens next.

## 4.5 Resizing

- Select in edit mode → four edge handles and four corner handles, snapping to cells (or half cells).
- A size label ("4 × 2") shows while dragging. Limits come from the widget's min/max span; AppWidget limits come from the provider but can be overridden ("ignore provider limits").
- Built-in widgets re-layout continuously during the drag. AppWidgets get their new size reported on release.
- Content scaling option for AppWidgets: fit / scale to fill.

## 4.6 Theming

- Built-in widgets inherit the active theme by default; every inherited value can be overridden per widget and reset individually.
- Wallpaper-derived colors update when the wallpaper or profile changes.
- Glass surfaces on widgets use the same quality tier as the rest of the launcher and count toward the glass budget (see doc 1, 1.4).
- AppWidgets: rounded clip, padding override, optional glass or tint plate behind, optional color filter (grayscale or tint) to make foreign widgets fit a monochrome setup.

## 4.7 Widget stacks (later phase)

Several widgets in one slot, swiped vertically, with an optional rule to bring one to the front (for example the Now card during a study block).
