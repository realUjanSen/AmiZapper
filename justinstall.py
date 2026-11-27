#!/usr/bin/env python3
"""
Install an already-built APK to the first online ADB device and launch the app.
"""

import glob
import os
import subprocess
import sys


PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
PACKAGE_NAME = "com.example.amizapper"
LAUNCH_ACTIVITY = f"{PACKAGE_NAME}/.LoginActivity"


def get_active_device_serial():
    """Return the first online ADB device serial, if any."""
    result = subprocess.run(
        ["adb", "devices"],
        cwd=PROJECT_ROOT,
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        return None

    for line in result.stdout.splitlines():
        if "\tdevice" in line:
            return line.split("\t", 1)[0].strip()

    return None


def find_apk():
    """Find the preferred already-built APK."""
    preferred_apk = os.path.join(
        PROJECT_ROOT,
        "app",
        "build",
        "outputs",
        "apk",
        "debug",
        "app-debug.apk",
    )
    if os.path.exists(preferred_apk):
        return preferred_apk

    apk_pattern = os.path.join(
        PROJECT_ROOT,
        "app",
        "build",
        "outputs",
        "apk",
        "**",
        "*.apk",
    )
    apks = sorted(glob.glob(apk_pattern, recursive=True))
    return apks[0] if apks else None


def main():
    apk_path = find_apk()
    if not apk_path:
        print("No built APK found under app/build/outputs/apk.")
        print("Build the app first, then run this script again.")
        sys.exit(1)

    device_serial = get_active_device_serial()
    if not device_serial:
        print("No online ADB device found.")
        sys.exit(1)

    print(f"Installing: {apk_path}")
    install_result = subprocess.run(
        ["adb", "-s", device_serial, "install", "-r", apk_path],
        cwd=PROJECT_ROOT,
    )
    if install_result.returncode != 0:
        print("APK installation failed.")
        sys.exit(1)

    print("Launching app...")
    launch_result = subprocess.run(
        ["adb", "-s", device_serial, "shell", "am", "start", "-n", LAUNCH_ACTIVITY],
        cwd=PROJECT_ROOT,
    )
    if launch_result.returncode != 0:
        print("APK installed, but the app could not be launched automatically.")
        sys.exit(1)

    print("Install and launch complete.")


if __name__ == "__main__":
    main()
