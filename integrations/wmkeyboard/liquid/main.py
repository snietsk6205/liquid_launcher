# Liquid, a WM Keyboard plugin (API 5) for Liquid Launcher.
#
# A plugin cannot start another app or read what you type in it (WM Keyboard's rules). It can hand text to an app
# through the share sheet, which always shows what goes and where. So: type or paste in the box, press a button, and
# pick "Search in Liquid" in the share sheet. Liquid Launcher opens search with the text; "Ask Claude" opens search
# ready to ask Claude with it (the question goes to the Claude app only when you press Go there).
from wm import plugin, ui, share

state = plugin.state(text="")

ASK_PREFIX = "Ask Claude: "


def set_text(value):
    state.text = value


def search(_=None):
    text = state.text.strip()
    if text:
        share.text(text)


def ask():
    text = state.text.strip()
    if text:
        share.text(ASK_PREFIX + text)


def render():
    with ui.column():
        ui.input(
            label="Liquid",
            value=state.text,
            placeholder="Apps, calculations, web, Claude",
            on_change=set_text,
            on_submit=search,
            key="text",
        )
        with ui.row():
            ui.button("Search", on_click=search, primary=True, icon="search")
            ui.button("Ask Claude", on_click=ask, icon="chat")
        ui.caption('Then pick "Search in Liquid" in the share sheet.')
