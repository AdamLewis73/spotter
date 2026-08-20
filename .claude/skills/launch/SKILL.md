---
name: launch
description: Build Spotter and run it on a device or emulator, then report what is on screen. Use when asked to run, launch, install, or look at the app.
disable-model-invocation: true
allowed-tools: Read Glob Grep Bash
---

Build the app, get it onto a device, and report what actually happened.

## Environment

`JAVA_HOME` must point at Android Studio's bundled JBR — there is no other JDK
on this machine:

```
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/sword/AppData/Local/Android/Sdk"
```

`adb` and `emulator` are not on `PATH`; call them at
`$ANDROID_HOME/platform-tools/adb.exe` and `$ANDROID_HOME/emulator/emulator.exe`.

## Steps

1. **Check for a device.** `adb devices`. If none, start the `Pixel_9` AVD in the
   **background** — it never exits on its own, so a foreground run blocks
   forever:

   ```
   "$ANDROID_HOME/emulator/emulator.exe" -avd Pixel_9 -no-boot-anim
   ```

   Then wait for `getprop sys.boot_completed` to return `1`, and send
   `input keyevent KEYCODE_WAKEUP` — a freshly booted emulator sleeps, and a
   screenshot of a sleeping device is solid black rather than an error.

2. **Build and install:** `./gradlew :app:installDebug`.

   The dictionary must exist first. If `:app:stageDictionaryAsset` fails, it says
   which file is stale or missing — run `python build.py` in `tools/dictbuild/`
   (~45 s, no network needed) rather than working around it.

3. **Launch:** `adb shell am start -n com.spotterkanji.app/.MainActivity`.

   To open on a **particular word**, seed the query:

   ```
   adb shell am start -n com.spotterkanji.app/.MainActivity --es query 上手
   ```

   This is the only way to get Japanese into the text field from a script.
   `adb shell input text` is ASCII-only and this emulator image has no
   `cmd clipboard`, so without the extra the screen can only be driven by hand —
   which is no use on a screen whose bugs are silent. Every fault found in this
   phase was found by looking at a *specific* word: 上手 for archaic readings,
   中国 for search-only ones, 生 for the routing.

4. **Look at it.** `adb exec-out screencap -p > <scratch>/shot.png` and read the
   image. Do not report that something works without looking.

5. **Check the log** for anything the screen does not show:
   `adb logcat -d -s DictionaryProvider` and `adb logcat -d -b crash`.

## Report

What is on screen, whether it matches what was expected, and any crash or
warning from the log. Attach the screenshot.

## Notes

- **`am force-stop` then relaunch still shows the splash for several seconds.**
  A screenshot at 9 s caught the Android robot and looked like a hang. Allow
  ~20 s after a force-stop, and ~40 s on the first launch after an install.
- **The first launch after install is slow.** Room extracts a ~100 MB dictionary
  out of the APK on first query; allow 15 seconds before screenshotting or you
  will capture a spinner and think it hung.
- **`adb shell input text` cannot type Japanese.** To see a populated screen,
  seed the query in `WordLookupViewModel`'s `init`, screenshot, then revert —
  and revert before committing.
- **`connectedAndroidTest` uninstalls the app when it finishes.** Anything that
  depends on an existing install — testing a dictionary refresh, or an upgrade
  across a schema change — must be driven with `adb install -r` plus
  `adb shell am instrument`, or it silently tests a fresh install and proves
  nothing.
- **Shut the emulator down when finished:** `adb emu kill`. It is a background
  process that outlives the task otherwise.
