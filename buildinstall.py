#!/usr/bin/env python3
"""
Build APK, install to ADB device, and launch MainActivity
"""

import subprocess
import os
import sys
import glob

# Configuration
PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
PACKAGE_NAME = "com.example.amizapper"
ACTIVITY_NAME = "MainActivity"
FULL_ACTIVITY = f"{PACKAGE_NAME}.{ACTIVITY_NAME}"

def get_active_device_serial():
    """Return the first online adb device serial, if any."""
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

def run_command(cmd, description):
    """Run a shell command and print output"""
    print(f"\n{'='*60}")
    print(f"▶ {description}")
    print(f"{'='*60}")
    print(f"Command: {cmd}\n")
    
    try:
        result = subprocess.run(cmd, shell=True, cwd=PROJECT_ROOT, capture_output=False, text=True)
        if result.returncode != 0:
            print(f"\n✗ Error: {description} failed with exit code {result.returncode}")
            return False
        print(f"\n✓ {description} completed successfully")
        return True
    except Exception as e:
        print(f"\n✗ Exception during {description}: {e}")
        return False

def find_apk():
    """Find the built APK file"""
    print("\n" + "="*60)
    print("🔍 Looking for APK file...")
    print("="*60)
    
    apk_patterns = [
        os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
        os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk"),
    ]
    
    for pattern in apk_patterns:
        if os.path.exists(pattern):
            print(f"✓ Found APK: {pattern}")
            return pattern
    
    # Try glob pattern
    glob_pattern = os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "**", "*.apk")
    apks = glob.glob(glob_pattern, recursive=True)
    
    if apks:
        apk_path = apks[0]
        print(f"✓ Found APK: {apk_path}")
        return apk_path
    
    print("✗ No APK file found!")
    return None

def main():
    print("\n" + "="*60)
    print("🚀 ShuttleHARGetter Build & Install Script")
    print("="*60)
    
    # Step 1: Build APK
    if not run_command("gradlew.bat build", "Building APK"):
        sys.exit(1)
    
    # Step 2: Find APK
    apk_path = find_apk()
    if not apk_path:
        sys.exit(1)

    # Pick the active online device explicitly so adb does not fail when
    # stale/offline serials are still present.
    device_serial = get_active_device_serial()
    if not device_serial:
        print("\n✗ No online adb device found!")
        sys.exit(1)
    
    # Step 3: Install APK
    if not run_command(f"adb -s {device_serial} install -r \"{apk_path}\"", "Installing APK to device"):
        sys.exit(1)
    
    # Step 4: Launch LoginActivity (entry point)
    # Note: Try to launch but don't fail if it doesn't work - app is installed and can be opened from home screen
    try:
        result = subprocess.run(
            f"adb -s {device_serial} shell am start -n {PACKAGE_NAME}/.LoginActivity",
            shell=True,
            cwd=PROJECT_ROOT,
            capture_output=True,
            text=True
        )
        if result.returncode == 0:
            print("\n✓ App launched successfully")
        else:
            print("\n⚠ Could not auto-launch app via adb (this is normal on some devices)")
            print(f"   Just tap the '{PACKAGE_NAME}' app icon on your device home screen")
    except Exception as e:
        print(f"\n⚠ Could not auto-launch app: {e}")
        print(f"   Just tap the '{PACKAGE_NAME}' app icon on your device home screen")
    
    print("\n" + "="*60)
    print("✅ Build & install complete!")
    print("="*60 + "\n")

if __name__ == "__main__":
    main()
