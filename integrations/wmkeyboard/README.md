# Liquid in WM Keyboard

A WM Keyboard plugin (API 5) that hands text to Liquid Launcher's search, or gets a question ready for Claude.

WM Keyboard's plugins cannot start another app or read what you type in it. They can hand text to an app through
the share sheet, which always shows what is shared and where (the `share` permission). So the plugin's panel has its
own box: type or paste, press **Search** or **Ask Claude**, and pick **Search in Liquid** in the share sheet. The
launcher opens search with the text; Ask Claude opens search in Ask mode with it, and the question goes to the Claude
app only when you press Go.

## Install it (on the phone)

1. WM Keyboard › Tools › Plugins › Manage plugins: turn on **Allow plugins** (the first time, the keyboard downloads
   the library that runs plugins).
2. In Liquid Launcher: Settings › Drawer and search › Keyboard › More › **Install the Liquid plugin**. It opens the
   plugin in WM Keyboard, which shows its permission (share) and asks you to confirm.
   (Or copy `liquid.wmplugin` from `app/src/main/assets/wmkeyboard/` to the phone and open it from a file manager.)
3. On the keyboard, open the Plugins tool and tap **Liquid**. To keep it one tap away, add a plugin button for it in
   WM Keyboard's Custom buttons (toolbar, toolbox or floating button).

## Without the plugin

- Select text in any app and pick **Liquid** in the selection menu.
- Share text to **Search in Liquid**.
- From Tasker and the like: `am start -a dev.liquid.launcher.action.SEARCH --es query "text" --ez ask false`.

## Files

`liquid/plugin.json` and `liquid/main.py`; `liquid.wmplugin` is the two in a ZIP (`zip ../liquid.wmplugin plugin.json main.py`
from inside `liquid/`). The launcher bundles a copy in its assets: rebuild it there when the plugin changes.
