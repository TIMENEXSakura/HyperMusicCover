"""Exercise the Compose editor through its launcher on a disposable Android emulator."""
import colorsys
import json
import os
from pathlib import Path
import re
import shlex
import struct
import subprocess
import time
import xml.etree.ElementTree as ET


PKG = "com.github.zyl6932.HyperMusicCover"
MAIN = PKG + "/com.os4.musiccover.MainActivity"
DE = f"/data/user_de/0/{PKG}/shared_prefs/settings.xml"
OUT = Path("diagnostics")
RES = Path("app/src/main/res/values")
STRINGS = {entry.get("name"): "".join(entry.itertext())
           for xml in RES.glob("*.xml") for entry in ET.parse(xml).getroot().findall("string")}


def adb(*args, timeout=30, data=None):
    return subprocess.check_output(["adb", *args], input=data, timeout=timeout)


def shell(*args):
    return adb("shell", shlex.join([str(v) for v in args])).decode().strip()


def tree():
    output = adb("exec-out", "uiautomator", "dump", "/dev/tty", timeout=20)
    start = output.find(b"<hierarchy")
    end = output.find(b"</hierarchy>", start)
    if start < 0 or end < 0:
        raise ValueError("UIAutomator did not return a complete hierarchy")
    return ET.fromstring(output[start:end + len(b"</hierarchy>")])


def shot(name):
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    try:
        ET.ElementTree(tree()).write(OUT / (name + ".xml"), encoding="utf-8")
    except (ValueError, subprocess.SubprocessError) as error:
        print(f"{name}: hierarchy unavailable: {error}", flush=True)


def labels(*keys):
    return [STRINGS[key].strip('"').replace("|", "") if key.startswith("lyric_editor_sample_")
            else STRINGS[key] for key in keys]


def bounds(node):
    coordinates = list(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
    if len(coordinates) == 4 and coordinates[2] > coordinates[0] and coordinates[3] > coordinates[1]:
        return coordinates
    return None


def dismiss_launcher_anr(root):
    title = root.find(".//node[@resource-id='android:id/alertTitle']")
    close = root.find(".//node[@resource-id='android:id/aerr_close']")
    if title is None or close is None or title.get("package") != "android":
        return False
    if title.get("text", "").replace("’", "'") != "Pixel Launcher isn't responding":
        return False
    area = bounds(close)
    if area is None:
        return False
    ET.ElementTree(root).write(OUT / "launcher-anr.xml", encoding="utf-8")
    print("Dismissing emulator Pixel Launcher ANR", flush=True)
    x1, y1, x2, y2 = area
    shell("input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)
    return True


def edge_of_page(area, height):
    # Compose exposes scrolled rows underneath the status bar and large/collapsed app bar.
    # The root also extends behind the navigation bar. Both can intercept an otherwise valid tap.
    y = (area[1] + area[3]) // 2
    if y < height * .16:
        return "up"
    if y > height * .90:
        return "down"
    return None


def locate(options, scroll=False, bottom_tab=False, for_tap=False):
    last_labels = []
    for index in range(22 if scroll else 12):
        try:
            root = tree()
        except (ValueError, subprocess.SubprocessError):
            time.sleep(1)
            continue
        if dismiss_launcher_anr(root):
            time.sleep(1)
            if bottom_tab and PKG not in activity():
                start_main()
            continue
        visible_nodes = [node for node in root.iter("node") if node.get("package") == PKG and bounds(node)]
        if bottom_tab:
            visible_nodes.sort(key=lambda node: bounds(node)[3], reverse=True)
        last_labels = [node.get("text") or node.get("content-desc") for node in visible_nodes]
        viewport_height = max((bounds(node)[3] for node in visible_nodes), default=2400)
        reveal = None
        for option in options:
            for node in visible_nodes:
                values = [node.get("text", ""), node.get("content-desc", "")]
                # A merged Compose button sometimes exposes the role and sample on separate lines.
                if not any(option == line.strip() for value in values for line in value.splitlines()):
                    continue
                area = bounds(node)
                edge = edge_of_page(area, viewport_height) if scroll and for_tap else None
                if edge:
                    reveal = edge
                    continue
                return area
        if reveal:
            swipe(reveal, short=True)
        elif scroll and index >= 1:
            swipe("up" if scroll == "up" else "down")
        time.sleep(0.5)
    raise AssertionError(f"Could not locate {options} in {activity()}: {list(filter(None, last_labels))}")


def tap(options, scroll=False, bottom_tab=False):
    x1, y1, x2, y2 = locate(options, scroll, bottom_tab, for_tap=True)
    shell("input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)
    time.sleep(0.45)


def activity():
    lines = shell("dumpsys", "activity", "activities").splitlines()
    return next((line for line in lines if "topResumedActivity=" in line or "mResumedActivity:" in line
                 or "mResumedActivity=" in line or "ResumedActivity:" in line), "")


def start_main():
    result = shell("am", "start", "-W", "-n", MAIN)
    if "Error" in result or "Exception" in result:
        raise AssertionError(result)


def swipe(direction, short=False):
    # A slower nudge moves an already located edge row out from behind the bars without
    # flinging it past the other end. The long swipe is for searching through the list.
    lo, hi = (1060, 1530) if short else (1010, 1840)
    start, end = (hi, lo) if direction == "down" else (lo, hi)
    shell("input", "swipe", 1050, start, 1050, end, 540 if short else 380)
    time.sleep(0.25)


def open_lyrics_tab():
    start_main()
    tap(labels("tab_features"), bottom_tab=True)
    tap(labels("features_cover_title"), scroll=True)
    tap(labels("lyrics_section"), scroll=True)
    locate(labels("lyrics_style_title"), scroll=True)
    assert "CoverActivity" in activity(), activity()


def enter_editor():
    for attempt in range(2):
        tap(labels("lyrics_style_title"), scroll="up")
        for _ in range(8):
            current = activity()
            if "LyricStyleActivity" in current:
                locate(labels("lyric_editor_sample_lead"))
                return
            time.sleep(.25)
        if "CoverActivity" not in current:
            raise AssertionError(f"Unexpected activity opening lyric colours: {current}")
        if attempt == 0:
            print("Cover screen still resumed; locating its entry again", flush=True)
    raise AssertionError(f"Lyric colour entry did not open the editor: {current}")


def return_to_lyrics():
    shell("input", "keyevent", "KEYCODE_BACK")
    locate(labels("lyrics_style_title"), scroll="up")
    assert "CoverActivity" in activity(), activity()


def editor_shot(name):
    locate(labels("lyric_editor_sample_lead"), scroll="up")
    swipe("up")
    locate(labels("lyric_editor_sample_duet"))
    shot(name)


def seed(file, xml):
    command = shlex.join(["run-as", PKG, "sh", "-c",
                          f"mkdir -p {shlex.quote(str(Path(file).parent))} && cat > {shlex.quote(file)}"])
    adb("shell", "-T", command, data=xml.encode())


def appearance():
    # Before the first migration the setting may never have been written at all.
    command = f"if test -f {shlex.quote(DE)}; then cat {shlex.quote(DE)}; else echo '<map/>'; fi"
    prefs = ET.fromstring(shell("run-as", PKG, "sh", "-c", command))
    item = prefs.find("./string[@name='lyricappearance']")
    return json.loads(item.text) if item is not None and item.text else {}


def locate_hue_bar():
    """miuix 0.9.4's HSV bars are drawn on Canvas without accessibility nodes."""
    raw = adb("exec-out", "screencap", timeout=20)
    width, height, fmt = struct.unpack_from("<III", raw)
    offset = len(raw) - width * height * 4
    if fmt not in (1, 2) or offset not in (12, 16):
        raise AssertionError(f"Unexpected raw screenshot format {width}x{height} {fmt} header {offset}")
    rows = []
    for y in range(height // 6, height * 9 // 10, 3):
        hues, xs = set(), []
        for x in range(width // 8, 7 * width // 8, 9):
            pos = offset + 4 * (y * width + x)
            red, green, blue = raw[pos:pos+3]
            if max(red, green, blue) - min(red, green, blue) < 125:
                continue
            hue = colorsys.rgb_to_hsv(red / 255, green / 255, blue / 255)[0]
            hues.add(int(hue * 12))
            xs.append(x)
        if len(hues) >= 6 and len(xs) >= width / 25:
            rows.append((y, min(xs), max(xs)))
    if not rows:
        raise AssertionError("No hue gradient visible in miuix ColorPicker")
    # The longest band with a full spectrum is the HSV hue slider.
    groups = [[]]
    for row in rows:
        if groups[-1] and row[0] != groups[-1][-1][0] + 3:
            groups.append([])
        groups[-1].append(row)
    group = max(groups, key=len)
    return group[len(group)//2]


def choose_nonwhite(hue=0.58):
    y, left, right = locate_hue_bar()
    dpi = int(re.findall(r"\d+", shell("wm", "density"))[-1])
    for index, fraction in [(0, hue), (1, 0.74)]:
        # The pinned miuix implementation lays out 26dp sliders 12dp apart.
        center = y + round(index * (26 + 12) * dpi / 160)
        shell("input", "swipe", (left + right) // 2, center,
              left + round((right - left) * fraction), center, 240)
    time.sleep(0.3)


ROLES = {
    "bg": ("lyric_editor_sample_backing", "lyrics_style_backing", .78),
    "main": ("lyric_editor_sample_lead", "lyrics_style_main", .40),
    "trans": ("lyric_editor_sample_trans", "lyrics_style_trans", .13),
    "duet": ("lyric_editor_sample_duet", "lyric_editor_duet_voice", .60),
}
# Exact binary fractions avoid conflating float serialization with a changed setting.
LEGACY = {
    "mainMode": 2, "mainColor": -4888511, "mainOpacity": .875, "mainIntensity": .5,
    "bgMode": 0, "bgColor": -1, "bgOpacity": .75, "bgIntensity": .625,
    "transMode": 0, "transColor": -1, "transOpacity": .625, "transIntensity": .75,
}


def formatted(key, role):
    return STRINGS[key].replace("%1$s", STRINGS[ROLES[role][1]])


def pick(row, edited_role=None):
    tap(labels(ROLES[row][0]), scroll="up")
    locate([formatted("lyric_editor_adjust_color", edited_role or row)])
    locate(labels("lyrics_style_done"))


def wait_persisted(predicate, what):
    for _ in range(24):
        saved = appearance()
        if predicate(saved):
            return saved
        time.sleep(.25)
    raise AssertionError(f"{what}: {appearance()}")


def semantic_field(doc, role, field):
    source = "main" if role == "duet" and role + field not in doc else role
    return doc[source + field]


def check_roles(before, after, ignore=()):
    for role in ROLES:
        if role in ignore:
            continue
        for field in ("Mode", "Color", "Opacity", "Intensity"):
            expected = semantic_field(before, role, field)
            actual = semantic_field(after, role, field)
            assert abs(actual - expected) < .00001, (role, field, actual, expected)


def assert_only_colours():
    # The editor is short. Inspect its complete list, including controls below the fold.
    forbidden = set(labels("lyrics_enabled", "lyrics_hdr", "lyrics_align", "lyrics_trans",
                           "lyrics_roma", "lyrics_max_lines", "lyrics_height", "lyrics_network",
                           "lyrics_clock_independent", "lyrics_bt_sync", "lyric_editor_duet_enabled",
                           "lyrics_size", "lyrics_weight", "lyrics_opacity"))
    for role in ROLES:
        for field in ("size", "weight", "opacity"):
            forbidden.add(formatted("lyric_text_voice_" + field, role))
    found = set()
    last = None
    for _ in range(8):
        root = tree()
        nodes = tuple((n.get("text", ""), n.get("content-desc", ""), n.get("bounds", ""))
                      for n in root.iter("node") if n.get("package") == PKG and bounds(n))
        for txt, desc, _ in nodes:
            found.update(txt.splitlines())
            found.update(desc.splitlines())
        if nodes == last:
            break
        last = nodes
        swipe("down")
    assert STRINGS["lyrics_color"] in found, found
    assert not (forbidden & found), f"Non-colour settings in colour editor: {forbidden & found}"
    locate(labels("lyric_editor_sample_lead"), scroll="up")


def check_main_settings():
    # Follow the actual order so every long-list lookup starts above the requested control.
    locate(labels("lyric_editor_duet_enabled"), scroll=True)
    shot("lyrics-settings")
    locate(labels("lyrics_network"), scroll=True)
    locate(labels("lyrics_max_lines"), scroll=True)
    for role in ("main", "bg", "trans"):
        for field in ("size", "weight", "opacity"):
            locate([formatted("lyric_text_voice_" + field, role)], scroll=True)
    locate([formatted("lyric_text_voice_opacity", "duet")], scroll=True)
    shot("lyrics-typography")
    for key in ("lyrics_height", "lyrics_clock_independent", "lyrics_bt_sync",
                "lyrics_align", "lyrics_trans", "lyrics_roma", "lyrics_hdr"):
        locate(labels(key), scroll=True)
    locate(labels("lyrics_style_title"), scroll="up")


def set_duet(enabled):
    before = appearance()
    assert before.get("duetEnabled", True) != enabled, before
    tap(labels("lyric_editor_duet_enabled"), scroll=True)
    after = wait_persisted(lambda doc: doc.get("duetEnabled") is enabled, "Duet switch")
    check_roles(before, after)



def run():
    OUT.mkdir(exist_ok=True)
    serial = adb("get-serialno").decode().strip()
    if not serial.startswith("emulator-") or "1" not in (shell("getprop", "ro.boot.qemu"), shell("getprop", "ro.kernel.qemu")):
        raise RuntimeError("This fixture is for a disposable emulator only")
    adb("install", "-r", str(next(Path("app/build/outputs/apk/debug").glob("*.apk"))), timeout=100)
    shell("input", "keyevent", "KEYCODE_WAKEUP")
    shell("wm", "dismiss-keyguard")
    shell("wm", "size", "1080x2400")
    shell("wm", "density", "320")
    shell("cmd", "uimode", "night", "no")
    shell("am", "force-stop", PKG)
    seed("shared_prefs/app_settings.xml", '<map><boolean name="welcome_shown" value="true"/></map>')
    seed("shared_prefs/app_prefs.xml", '<map><string name="language_code">zh-CN</string></map>')
    # A fresh installation can try the editor before the first SystemUI settings migration.
    adb("logcat", "-c")
    open_lyrics_tab()
    enter_editor()
    pick("main")
    choose_nonwhite()
    tap(labels("lyrics_style_done"))
    assert appearance() == {}, "Preview-only editing wrote an activation-free preference"
    tap(labels("lyric_editor_replay"), scroll=True)
    editor_shot("editor-preview-only")
    shell("am", "force-stop", PKG)
    prefs = ET.Element("map")
    ET.SubElement(prefs, "boolean", name="_migrated", value="true")
    ET.SubElement(prefs, "string", name="lyricappearance").text = json.dumps(LEGACY)
    seed(DE, ET.tostring(prefs, encoding="unicode"))
    open_lyrics_tab()
    check_main_settings()
    enter_editor()
    editor_shot("editor-light")
    assert_only_colours()

    # Materialise the old document through a backing edit first: duet inherits the
    # old main colour, then each later edit has four explicit independent roles.
    for role, (_, _, hue) in ROLES.items():
        before = appearance()
        pick(role)
        if role == "main":
            shot("native-colour-picker")
            choose_nonwhite(hue)
            tap(labels("rules_cancel"))
            assert appearance() == before, "Cancel persisted the draft"
            pick(role)
        choose_nonwhite(hue)
        tap(labels("lyrics_style_done"))
        saved = wait_persisted(lambda doc: doc.get(role + "Mode") == 2
                              and doc.get(role + "Color", -1) not in (-1, semantic_field(before, role, "Color")),
                              f"Edited RGB for {role}")
        check_roles(before, saved, ignore=(role,))
        assert abs(saved[role + "Opacity"] - semantic_field(before, role, "Opacity")) < .00001
        if role == "bg":
            assert all("duet" + field in saved for field in ("Mode", "Color", "Opacity", "Intensity")), saved
        print(f"Verified {role} colour and peer voices", flush=True)

    final = appearance()
    assert len({final[role + "Color"] for role in ROLES}) == 4, final
    editor_shot("editor-edited")
    return_to_lyrics()
    set_duet(False)
    enter_editor()
    pick("duet", edited_role="main")
    tap(labels("rules_cancel"))
    editor_shot("editor-duet-off")
    return_to_lyrics()
    set_duet(True)
    enter_editor()
    pick("duet")
    tap(labels("rules_cancel"))
    assert appearance() == final, "Switching the duet off and on changed its configured colour"
    shell("am", "force-stop", PKG)
    shell("cmd", "uimode", "night", "yes")
    open_lyrics_tab()
    enter_editor()
    editor_shot("editor-dark")
    assert appearance() == final, "Opening in dark mode lost settings"
    (OUT / "verified.json").write_text(json.dumps({
        "commit": os.environ.get("GITHUB_SHA"), "roles": list(ROLES),
        "cancelVerified": True, "coldStartVerified": True, "unmigratedPreviewVerified": True,
        "legacyThreeVoiceUpgrade": True, "duetSwitchBothWays": True, "mainSettingsPlacement": True,
        "fixture": "Migrated device-protected app preferences on AOSP; no SystemUI host",
    }, indent=2))


if __name__ == "__main__":
    try:
        run()
    except Exception:
        OUT.mkdir(exist_ok=True)
        try:
            shot("failure")
            (OUT / "crash-log.txt").write_bytes(adb("logcat", "-d", "-b", "crash"))
            (OUT / "foreground.txt").write_text(activity())
            (OUT / "activity-log.txt").write_bytes(adb("logcat", "-d", "-v", "brief",
                "ActivityManager:I", "ActivityTaskManager:I", "AndroidRuntime:E", "*:S"))
        except Exception as diagnostic_error:
            print(f"Additional failure diagnostics unavailable: {diagnostic_error}", flush=True)
        raise
