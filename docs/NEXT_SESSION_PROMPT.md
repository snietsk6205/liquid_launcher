# Liquid Launcher: my feedback on 0.10.1 (brief for the next session)

> Status: handled by the session of 2026-11-18 afternoon (0.10.2 and 0.10.3, branch `ccr-f4d083cc-vpyudk`); what was
> done, what was not and why are in `docs/FABLE_LOG.md`, last section.

Continue Liquid Launcher (repo `snietsk6205/liquid_launcher`, branch `claude/exciting-knuth-s83frp`; the last
session also worked on `claude/exciting-knuth-s83frp-1jp272`, both end at the same commit). 0.10.1 is released and
on my HONOR 90 Lite. This session is my feedback after using it.

## Attached images
1. The study hub as it opens now.
2. The search page.
3. The home screen: the clock, the line under it, the pill.
4. The app drawer: the letter index on the right, the tag strip, the drawer's pill.
5. Two clock designs I like (left: tall condensed digits with a small date above; right: hours over minutes, big
   rounded digits, a line above them).

## Read before coding (max 15 minutes)
- `docs/FABLE_LOG.md`: the last section ("Session of 2026-11-18 … 0.10.1"): what exists, known limits, how the
  releases went.
- `DECISIONS.md` D139–D143, and `CHANGELOG.md` 0.10.1.
- `docs/reference/wallpaper-stoped.wmtheme.json`: my WM Keyboard theme (its background picture removed).
- `tools/typecheck/README.md`: the local type check. Run `fetch.sh` once, then `tc.sh` and `ut.sh` after every
  change; CI is still the only real build.

## How we work (unchanged)
- No local Android build: the proxy blocks dl.google.com. Every compile check is a CI run. The in-app updater reads
  GitHub's "latest release".
- No approvals: I am not watching. When unsure, take the most sensible choice, write it under "Questions for Med"
  in `docs/FABLE_LOG.md`, and continue.
- Releases: at most 2. One safety release around the middle (0.10.2) and one final release at the end (0.10.3),
  published only when the head commit message contains `[release]`; never write that marker in any other commit
  message. Each release has its CHANGELOG.md entry.
- Never leave the branch broken. Work in the order below; unfinished items stay on the branch for the next session.
- Quality bar: no placeholder settings, no fake effects, nothing that does nothing; every visual option gets a live
  preview. If Android or MagicOS makes something impossible, log why and build the best real alternative.
- Every new option or feature goes to its right place: the right entry › page › tab in `SettingsMap` (with search
  keywords), and on the home screen where I would look for it.
- Calendar contract: do not break it (the `✓ `/`✗ ` titles, `[x]` lines, `— launcher: …` lines after `P2-BLOCK`,
  `Note: …` events, the `launcher-data` event are read by my evening check-in). No network calls to Claude. No data
  loss: migrate, never reset my settings, layouts, tags or themes. Keep everything smooth on a mid-range phone and
  measure with the frame meter.

## What to do, in this order

### 1. Pills: back to liquid
0.10.1 made the pills dark (`liquidLauncher.space` 0xA6141218, added by `ThemeStore.withLauncherDefaults` and
`migrate010`). It hides the liquid glass and spoils the design.
- Home pill and drawer pill: no dark fill by default. The theme's glass (its frost and its own translucent colour),
  so the liquid shows, as before 0.10.1.
- The search bar (the search page's field): keep a tint, but much lighter (about a third of now) so the liquid
  still shows.
- On my phone: remove the dark values the 0.10.1 migration wrote, only where they are still exactly those values;
  keep anything I set myself.

### 2. My keyboard theme as the default, colours from the wallpaper
- `docs/reference/wallpaper-stoped.wmtheme.json` becomes the default theme for every button, chip, round button,
  sheet, menu, card and the pills (it replaces `ThemeDocs.Default`). Nothing is removed from the Theme lab: it is a
  solid starting point I will tune.
- By default every colour follows the wallpaper, the accent included: buttons and secondary buttons ← the
  wallpaper's dark colour, chips ← muted, the main button and the accent ← vibrant, pressed ← vibrant, text ← light,
  menus, sheets and cards ← dark. Keep each colour's transparency from my theme. Text must stay readable (dark text
  when a role colour is light). The fixed blue stays available as a choice.
- Switch my phone to it once, on update; keep my current look as a preset in the Theme lab ("Before 0.10.2").

### 3. Long press on the pill
0.10.1 made a long press on the pill show "Customise the pill" and a gear instead of doing its action. Put it back:
the pill's long press runs its own action (Settings by default). The Customise chip stays on the dock, folders, the
search page and the hub. Add "Customise the pill" to the actions I can choose for the pill.

### 4. The study hub (image 1)
- It opens at full height with the whole day, not collapsed: the block now or next, the timeline, tasks, tomorrow.
  Collapsed stays an option ("Study hub opens").
- Block cards are not expanded by default: title, time and step count; a tap shows the steps. Follow "Blocks in
  Today" (mine is compact); the block now or next is no longer forced open.
- In image 1 the block now or next appears twice (its card and the timeline), the header line is cut ("0/3 …") and
  the chip row is cut ("Quiz m…"). Fix those.

### 5. The line under the clock (image 3)
It does not take the clock's colours: the clock is pale green from the wallpaper, the line is white with a dark halo
and its ✓ is a dark grey disc. By default the line, its ring and its ✓ take the clock's own colours (the clock
design's colour, from the wallpaper), with the halo subtle or off. Keep the other choices (automatic contrast, white,
black).

### 6. The search page: fewer cards (image 2)
It is overwhelming and does not fit: the calendar is cut at the top, the chip rows run off the edge. By default:
the search field, my apps row, and one study card (next block + today's progress). The calendar month and the exam
countdown are one tap away (for example a compact "Today · 24 days to Checkpoint 2" row that opens them). The `!`
commands appear when I type `!`, the Claude prompts when I type `?` or under one "Ask Claude" chip. Nothing under
the status bar, nothing cut at the edges. I can still add every card back on the search page's settings page.

### 7. Tags in the drawer (the tag list from the drawer's tag button)
- Swap the tick and the pin: the pin on the left, the tick on the right (keep the apps button).
- Reachable from the bottom: the search field and my pinned tags at the bottom near my thumb (the list grows
  upwards); Clear and Done stay at the bottom.

### 8. The letter index (image 4)
I use the String style (both sides, letters always shown).
- Let me move it: where the string's top and bottom ends sit (lower and shorter, within thumb reach), not only for
  the rail.
- Letter colours follow the wallpaper by default (or the theme's text, the accent, my own colour).
- More fluid: follow the finger at the display's frame rate, read positions while drawing (no recomposition per
  frame), springs that never jump. Measure it with the frame meter and log the cost.
- In image 4 the letters touch the last column's labels: keep the grid clear of the index while it shows.

### 9. Clock designs (image 5)
Add both designs to the clock's gallery (its Customise sheet), drawn live with the clock's colour, size and font
options:
- Tall: tall condensed digits with a soft bevel (a light edge and a shaded edge), the colon as two dots, a small
  bold date line above.
- Stacked: hours over minutes, big heavy rounded digits in a light tint of the wallpaper's colour, with an "at a
  glance" line above (the date and the study line).
- Optional, only if it can be done well and offline: the right picture's depth effect (the clock behind the
  wallpaper's subject). If not, log why.

### 10. What may already exist
Last time I attached my settings export to check whether some of this already exists and I did not find it. For each
item above, first check the registry: if an option exists, write where it is (entry › page › tab) in the log under
"Already there", and make it easier to find (search keywords, the first tab of its page). Known ones: "Colour of the
line under the clock" (Study › Study companion › Look), "Study hub opens" (Study › Study companion › Layout), "Rail
position" (Drawer and search › Letter index, rail style only), "Long press on the pill" (Home › Pill › Behaviour),
the search page's cards (Drawer and search › Search › Layout).

### 11. Fixes and improvements found after 0.10.1
- "Export for AI" lists options as changed when their stored value equals the default (for example Press motion
  "squishy"): list only real changes.
- CI: emulator checks 2 and 3 (Home pressed) flaked right after the first start (run 85 needed three attempts): wait
  for the redirect engine's first window event before pressing Home.
- When I send my debug log, write the frame meter's numbers ("Frames:") for the moving background and textures in
  the log.
- If time is left, the known limits in the log: SINK does not dip the neighbouring buttons, Individual pills switch
  their glass at the end of the morph, picking apps works only from the drawer.

## My current settings that matter here
Letter index: String, both sides, always shown, letter size 15, spacing 15, curve width 100, haptics off. Drawer:
tabs hidden, icon size 42, side margins 16. Tags: 3 chips, counts shown, a tap selects several, several tags show
apps with all of them. Pill: offset 116, width 64 %, height 50, icons 32, the study item a circle in a pebble. Clock:
size 115, date shown, weight normal. Today: blocks compact. Icons follow the wallpaper on the home screen. Touch
effect: swoosh, rainbow. Search: bounce 0, stretch 0, blur 4, the home screen falls back 16. Home: 7 columns.

## End of the session
The last push includes the final `[release]` with its CHANGELOG entry and a FABLE_LOG entry: the version released,
each item above done / partly done / not started, known bugs, "Questions for Med", and the exact next step.
