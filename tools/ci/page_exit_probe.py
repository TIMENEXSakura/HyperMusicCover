"""Capture a production page-exit mask through Android HWUI and verify its displayed pixels."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import time


PKG = "com.github.zyl6932.HyperMusicCover"
ACTIVITY = PKG + "/com.os4.musiccover.lyrics.PageExitProbeActivity"
OUT = Path("page-exit-diagnostics")
FIXTURE = Path(__file__).resolve().parent / "page-exit-fixture"
FRAMES = "files/page-exit-frames"


def prepare():
    debug = Path("app/src/debug")
    for name, dest in (
        ("AndroidManifest.xml", debug / "AndroidManifest.xml"),
        ("PageExitProbeActivity.java", debug / "java/com/os4/musiccover/lyrics/PageExitProbeActivity.java"),
    ):
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.exists() and dest.read_bytes() != (FIXTURE / name).read_bytes():
            raise RuntimeError(f"An existing debug source conflicts with the CI fixture: {dest}")
        shutil.copyfile(FIXTURE / name, dest)


def adb(*args, timeout=30, check=True):
    return subprocess.run(["adb", *args], capture_output=True, timeout=timeout, check=check)


def app_file(name):
    return adb("exec-out", "run-as", PKG, "cat", f"{FRAMES}/{name}", check=False)


def collect():
    OUT.mkdir(exist_ok=True)
    if not adb("get-serialno").stdout.decode().strip().startswith("emulator-") or not any(
        adb("shell", "getprop", key).stdout.strip() == b"1" for key in ("ro.kernel.qemu", "ro.boot.qemu")
    ):
        raise RuntimeError("Page exit fixture requires a disposable emulator")
    apk, = Path("app/build/outputs/apk/debug").glob("*.apk")
    adb("install", "-r", str(apk), timeout=150)
    adb("shell", "pm", "clear", PKG)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    started = adb("shell", "am", "start", "-W", "-n", ACTIVITY, timeout=45)
    if b"Error" in started.stdout or b"Exception" in started.stdout:
        raise RuntimeError(started.stdout.decode())
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        # exec-out transports raw bytes and need not propagate the remote exit code. Shell v2
        # does, so test for the atomically published marker before reading any payload.
        if adb("shell", "run-as", PKG, "test", "-f", f"{FRAMES}/error.txt", check=False).returncode == 0:
            raise RuntimeError(app_file("error.txt").stdout.decode())
        if adb("shell", "run-as", PKG, "test", "-f", f"{FRAMES}/capture.json", check=False).returncode == 0:
            completed = app_file("capture.json").stdout
            json.loads(completed)
            (OUT / "capture.json").write_bytes(completed)
            break
        time.sleep(.5)
    else:
        raise TimeoutError("HWUI did not commit and capture the full exit sequence")
    meta = json.loads((OUT / "capture.json").read_bytes())
    for name in ["background", "reference", *(f"step-{i:02d}" for i in range(meta["steps"] + 1))]:
        result = app_file(name + ".png")
        if result.returncode or not result.stdout.startswith(b"\x89PNG"):
            raise RuntimeError(f"Missing PixelCopy frame {name}")
        (OUT / (name + ".png")).write_bytes(result.stdout)


def analyse():
    import numpy as np
    from PIL import Image, ImageDraw

    meta = json.loads((OUT / "capture.json").read_bytes())
    assert meta["hardwareCanvas"] and meta["snapshotRecordings"] == 1, meta
    assert max(meta["glyphs"]) >= 2, "Large-text fixture did not wrap"

    def rgb(name):
        with Image.open(OUT / (name + ".png")) as im:
            assert im.size == (meta["width"], meta["height"])
            return np.asarray(im.convert("RGB"), dtype=np.float32)

    cuts = meta["cuts"]
    assert 0 < cuts[0] == meta["clipTop"] < meta["clipBottom"] == cuts[-1] < meta["height"]
    background = rgb("background")
    # Compare against the unmasked hardware reference. The non-uniform background is sampled
    # independently so erasing or darkening the underlying window also fails the check.
    ref = rgb("reference")
    ref_ink = np.maximum((ref - background).mean(axis=2), 0)
    row_strength = np.sum(ref_ink * ref_ink, axis=1)
    bands = [slice(a, b) for a, b in zip(cuts, cuts[1:])]
    assert all(np.count_nonzero(ref_ink[band] > 24) > 120 for band in bands), "Missing sample text"

    def bbox(img):
        y, x = np.where(img > 24)
        return [int(x.min()), int(y.min()), int(x.max()), int(y.max())]

    def matching_pixels(a, b, message):
        # Permit small GPU rounding differences across a transparent offscreen layer.
        assert np.max(np.abs(a - b)) <= 4, message

    metrics = []
    rows = []
    for i in range(meta["steps"] + 1):
        frame = rgb(f"step-{i:02d}")
        if i == 0:
            matching_pixels(frame, ref, "Exit start changed the pose or the glyphs")
        if i == meta["steps"]:
            matching_pixels(frame, background, "Exit end did not restore the underlying scene")
        untouched = np.max(np.abs(ref - background), axis=2) < 1
        matching_pixels(frame[untouched], background[untouched], "New pixels outside reference glyphs")
        ink = np.maximum((frame - background).mean(axis=2), 0)
        # The opacity of a horizontal scanline can change. Its normalised glyph profile must
        # still match the corresponding reference row, including its antialiased edges.
        current_strength = np.sum(ink * ink, axis=1)
        dot = np.sum(ink * ref_ink, axis=1)
        scale = dot / np.maximum(row_strength, 1)
        sampled = (row_strength > 5000) & (scale > .10)
        if np.any(sampled):
            cosines = dot[sampled] / np.sqrt(current_strength[sampled] * row_strength[sampled])
            assert float(np.min(cosines)) >= .992, f"Glyph shape or position changed in frame {i}"
        opacity = [float(ink[b].sum() / ref_ink[b].sum()) for b in bands]
        bounds = []
        for j, band in enumerate(bands):
            # Check full glyph bounds only on rows still entirely below the moving feather.
            # A legitimate sweep may change the first antialiased pixel long before its row's
            # integrated ink noticeably decreases. Partially covered scanlines are checked above.
            if i == 0 or (j == len(bands) - 1 and i <= meta["steps"] // 2):
                matching_pixels(frame[band], ref[band], f"Visible text moved or resized in frame {i}")
                box = bbox(ink[band])
                assert box == bbox(ref_ink[band]), f"Ink bounds changed in frame {i}"
                bounds.append(box)
            else:
                bounds.append(None)
        rows.append(opacity)
        metrics.append({"progress": i / meta["steps"], "bandInk": opacity,
                        "unchangedInkBounds": bounds, "sampledScanlines": int(np.count_nonzero(sampled))})

    rows = np.asarray(rows)
    assert np.max(np.diff(rows, axis=0)) <= .012, "Previously erased glyphs reappeared"
    assert np.min(np.diff(rows, axis=1)) >= -.012, "Lower lyrics disappeared before upper lyrics"
    middle = rows[meta["steps"] // 2]
    assert middle[0] <= .02 and middle[-1] >= .9985, f"Midpoint lost the top-to-bottom wipe: {middle}"
    halves = [int(np.flatnonzero(rows[:, k] <= .5)[0]) for k in range(rows.shape[1])]
    assert all(a < b for a, b in zip(halves, halves[1:])), f"Row half-fades did not travel downward: {halves}"
    # Build an overview directly from the captured PNGs, without redrawing the Android text.
    indices = (0, 2, 4, 6, 8)
    strip = Image.new("RGB", (216 * len(indices), 334), "#121622")
    draw = ImageDraw.Draw(strip)
    for slot, i in enumerate(indices):
        with Image.open(OUT / f"step-{i:02d}.png") as frame:
            strip.paste(frame.resize((216, 304), Image.Resampling.LANCZOS), (216 * slot, 30))
        draw.text((216 * slot + 10, 8), f"{100 * i // meta['steps']}%", fill="white")
    strip.save(OUT / "sequence.png")
    (OUT / "verified.json").write_text(json.dumps({"renderer": "Android HWUI / Window PixelCopy",
        "fixture": "Frozen multi-line StaticLayout display list through production LyricPageExitMask.draw",
        "halfFadeFrames": halves, "rows": metrics}, indent=2))
    print("Verified top-to-bottom wipe and stationary visible glyphs", halves, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("prepare", "run", "analyse"))
    mode = parser.parse_args().command
    if mode == "prepare":
        prepare()
    elif mode == "analyse":
        analyse()
    else:
        try:
            collect()
            analyse()
        except Exception:
            OUT.mkdir(exist_ok=True)
            log = adb("logcat", "-d", "-v", "brief", "PageExitProbe:V", "AndroidRuntime:E", "*:S", check=False)
            (OUT / "emulator-log.txt").write_bytes(log.stdout)
            raise
