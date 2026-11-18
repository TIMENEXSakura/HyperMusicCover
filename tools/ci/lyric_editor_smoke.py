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


def locate(options, scroll=False, bottom_tab=False):
    last_labels = []
    for index in range(12):
        try:
            root = tree()
        except (ValueError, subprocess.TimeoutExpired):
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
        for option in options:
            for node in visible_nodes:
                values = [node.get("text", ""), node.get("content-desc", "")]
                # A merged Compose button sometimes exposes the role and sample on separate lines.
                if not any(option == line.strip() for value in values for line in value.splitlines()):
                    continue
                return bounds(node)
        if scroll and index >= 1:
            shell("input", "swipe", 430, 1820, 430, 1020, 320)
        time.sleep(0.5)
    raise AssertionError(f"Could not locate {options} in {activity()}: {list(filter(None, last_labels))}")


def tap(options, scroll=False, bottom_tab=False):
    x1, y1, x2, y2 = locate(options, scroll, bottom_tab)
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


def open_editor():
    start_main()
    tap(labels("tab_features"), bottom_tab=True)
    tap(labels("features_cover_title"), scroll=True)
    tap(labels("lyrics_section"), scroll=True)
    tap(labels("lyrics_style_title"), scroll=True)
    assert "LyricStyleActivity" in activity(), activity()


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


def choose_nonwhite():
    y, left, right = locate_hue_bar()
    dpi = int(re.findall(r"\d+", shell("wm", "density"))[-1])
    for index, fraction in [(0, 0.58), (1, 0.74)]:
        # The pinned miuix implementation lays out 26dp sliders 12dp apart.
        center = y + round(index * (26 + 12) * dpi / 160)
        shell("input", "swipe", (left + right) // 2, center,
              left + round((right - left) * fraction), center, 240)
    time.sleep(0.3)


def wait_saved(role):
    for _ in range(20):
        saved = appearance()
        if saved.get(role + "Mode") == 2 and saved.get(role + "Color", -1) != -1:
            return saved
        time.sleep(0.25)
    raise AssertionError(f"Custom RGB for {role} was not persisted: {appearance()}")


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
    open_editor()
    tap(labels("lyric_editor_sample_lead"))
    locate(labels("lyrics_style_done"))
    choose_nonwhite()
    tap(labels("lyrics_style_done"))
    assert appearance() == {}, "Preview-only editing wrote an activation-free preference"
    tap(labels("lyric_editor_replay"), scroll=True)
    shot("editor-preview-only")
    shell("am", "force-stop", PKG)
    seed(DE, '<map><boolean name="_migrated" value="true"/></map>')
    open_editor()
    shot("editor-light")
    for role, key in [("main", "lyric_editor_sample_lead"), ("trans", "lyric_editor_sample_trans"),
                      ("bg", "lyric_editor_sample_backing")]:
        before = appearance()
        tap(labels(key))
        locate(labels("lyrics_style_done"))
        if role == "main":
            shot("native-colour-picker")
        choose_nonwhite()
        if role == "main":
            tap(labels("rules_cancel"))
            assert appearance() == before, "Cancel persisted the draft"
            tap(labels(key))
            locate(labels("lyrics_style_done"))
            choose_nonwhite()
        tap(labels("lyrics_style_done"))
        saved = wait_saved(role)
        for other in {"main", "trans", "bg"} - {role}:
            for field, default in [("Mode", 0), ("Color", -1)]:
                assert saved.get(other + field, default) == before.get(other + field, default), (role, other, field)
        print(f"Saved {role} only", flush=True)
    final = appearance()
    shot("editor-edited")
    shell("input", "keyevent", "KEYCODE_BACK")
    locate(labels("lyrics_style_title"))
    assert "CoverActivity" in activity(), activity()
    shell("am", "force-stop", PKG)
    shell("cmd", "uimode", "night", "yes")
    open_editor()
    locate(labels("lyric_editor_sample_lead"))
    assert appearance() == final, "Opening in dark mode lost settings"
    shot("editor-dark")
    (OUT / "verified.json").write_text(json.dumps({
        "commit": os.environ.get("GITHUB_SHA"), "roles": ["main", "trans", "bg"],
        "cancelVerified": True, "coldStartVerified": True, "unmigratedPreviewVerified": True,
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
