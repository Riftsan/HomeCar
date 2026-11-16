#!/usr/bin/env python3
"""Check the release UI on a disposable Android emulator and preserve evidence."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.renanespinar.homecar.auto"
ACTIVITY = PACKAGE + "/me.aap.fermata.ui.activity.MainActivity"


def adb(*args, timeout=45, binary=False, check=True):
    result = subprocess.run(
        ["adb", "-e", *args], capture_output=True, timeout=timeout, check=check
    )
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def wait_for_boot():
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        if adb("shell", "getprop", "sys.boot_completed", check=False).strip() == "1":
            adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            adb("shell", "wm", "dismiss-keyguard")
            return
        time.sleep(2)
    raise RuntimeError("Android emulator did not finish booting")


def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/homecar-smoke.xml")
    return adb("exec-out", "cat", "/sdcard/homecar-smoke.xml")


def find_id(xml, resource_id):
    for node in ET.fromstring(xml).iter("node"):
        if node.get("resource-id", "").endswith(":id/" + resource_id):
            return node
    return None


def bounds(node):
    values = [int(value) for value in re.findall(r"\d+", node.get("bounds", ""))]
    if len(values) != 4 or values[2] <= values[0] or values[3] <= values[1]:
        raise RuntimeError("The requested UI control has no visible bounds")
    return values


def wait_for_id(resource_id):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        xml = hierarchy()
        node = find_id(xml, resource_id)
        if node is not None:
            return xml, node
        time.sleep(1)
    raise RuntimeError("UI control did not appear: " + resource_id)


def capture(destination, name, xml=None):
    (destination / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    (destination / (name + ".xml")).write_text(xml or hierarchy(), encoding="utf-8")


def assert_running():
    if not adb("shell", "pidof", PACKAGE, check=False).strip():
        raise RuntimeError("HomeCar stopped while exercising settings")
    crashes = adb("logcat", "-d", "-b", "crash")
    if "Process: " + PACKAGE + "," in crashes or ">>> " + PACKAGE + " <<<" in crashes:
        raise RuntimeError("Android recorded a HomeCar runtime crash")


def check_mode(destination, name, night_mode, font_scale):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "cmd", "uimode", "night", night_mode)
    adb("shell", "settings", "put", "system", "font_scale", font_scale)
    launch = adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    (destination / (name + "-launch.txt")).write_text(launch, encoding="utf-8")
    xml, button = wait_for_id("homeCarSettingsButton")
    capture(destination, name + "-dashboard", xml)
    left, top, right, bottom = bounds(button)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    xml, _ = wait_for_id("homeCarServerInput")
    if find_id(xml, "homeCarCloseSettings") is None:
        raise RuntimeError("Settings header did not inflate")
    capture(destination, name + "-settings-top", xml)
    for _ in range(6):
        if find_id(xml, "homeCarCheckUpdates") is not None:
            break
        scroll = find_id(xml, "homeCarSettingsScroll")
        if scroll is None:
            raise RuntimeError("Settings scroll container is unavailable")
        left, top, right, bottom = bounds(scroll)
        x = str((left + right) // 2)
        adb("shell", "input", "swipe", x, str(bottom - 80), x, str(top + 80), "350")
        xml = hierarchy()
    for resource_id in ("homeCarSaveServer", "homeCarReloadDashboard", "homeCarCheckUpdates"):
        node = find_id(xml, resource_id)
        if node is None:
            raise RuntimeError("Settings action is not reachable: " + resource_id)
        bounds(node)
    capture(destination, name + "-settings-bottom", xml)
    assert_running()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, default=Path("smoke-artifacts"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    result = {"package": PACKAGE, "status": "failed", "completed_modes": []}
    try:
        wait_for_boot()
        adb("install", "-r", "-g", str(args.apk.resolve()), timeout=120)
        adb("logcat", "-c")
        for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            adb("shell", "settings", "put", "global", key, "0")
        for name, night_mode, font_scale in (
            ("light", "no", "1.0"),
            ("dark", "yes", "1.0"),
            ("large-font", "no", "1.5"),
        ):
            check_mode(args.output, name, night_mode, font_scale)
            result["completed_modes"].append(name)
        result["status"] = "passed"
        print("Release settings opened successfully in light, dark, and 150% font modes.")
    except Exception as error:
        result["error"] = str(error)
        try:
            capture(args.output, "failure")
        except Exception:
            pass
        raise
    finally:
        (args.output / "smoke-result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        for name, log_args in (("logcat.txt", ("logcat", "-d")), ("crash-log.txt", ("logcat", "-d", "-b", "crash"))):
            try:
                (args.output / name).write_text(adb(*log_args, check=False), encoding="utf-8")
            except Exception:
                pass


if __name__ == "__main__":
    main()
