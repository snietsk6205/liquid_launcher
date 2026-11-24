# Med's skills

| Skill | Use it for |
|---|---|
| `liquid-android/` | Any Android app: Liquid Launcher's design (liquid glass, wallpaper colours, WM Keyboard-style settings), motion, smoothness rules, settings system, diagnostics, documents and habits, plus a ready Compose kit module (`assets/kit/`). |
| `github-fast-ci/` | Any repo on GitHub: fast Actions (caches, markers, emulator snapshots, parallel parts), releases from CHANGELOG.md, reading a failed run quickly. Templates for Android, Node/web and Python. |

Claude Code loads skills in this folder automatically in this repo. To use them elsewhere:

- **Another repo**: copy the skill's folder into that repo's `.claude/skills/` and commit it.
- **Your own computer** (Claude Code): copy the folder into `~/.claude/skills/`.
- **claude.ai**: upload the packaged `.skill` file (Settings › Capabilities › Skills), or use "Save skill" on the
  file card Claude sends.

Keep them up to date: when a project teaches something new (a decision, a CI trick), add it to the skill's reference
file, not only to the project's logs.
