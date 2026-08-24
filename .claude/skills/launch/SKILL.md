---
name: launch
description: Build Spotter and get it running on the emulator, then hand it to the user to drive by hand. Use when asked to run, start, open, or install the app.
argument-hint: [word-to-open-on]
arguments: word
disable-model-invocation: true
allowed-tools: Read Glob Grep Bash
---

Get the app running in front of the user and leave it there. **They drive it
from here** — do not screenshot it, do not narrate what is on screen, and do not
shut the emulator down. To drive it yourself and report findings, that is
`/inspect`, not this.

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
   `input keyevent KEYCODE_WAKEUP` — a freshly booted emulator sleeps, and the
   user cannot tap a sleeping screen.

2. **Build and install:** `./gradlew :app:installDebug`.

   The dictionary must exist first. If `:app:stageDictionaryAsset` fails, it says
   which file is stale or missing — run `python build.py` in `tools/dictbuild/`
   (~45 s, no network needed) rather than working around it.

3. **Launch:** `adb shell am start -n com.spotterkanji.app/.MainActivity`.

   If the user named a word, open on it — `adb shell input text` is ASCII-only,
   so the intent extra is the only way to get Japanese into the field without
   typing it on the device:

   ```
   adb shell am start -n com.spotterkanji.app/.MainActivity --es query 上手
   ```

   The seeded text arrives **selected**, which pops Android's text-selection
   toolbar over the top of the screen. Harmless — the user taps once to dismiss
   it — but say so rather than letting it look like a glitch.

4. **Confirm it came up**, without screenshotting: check that the activity is
   resumed,

   ```
   adb shell dumpsys activity activities | grep ResumedActivity
   ```

   and that nothing landed in `adb logcat -d -b crash`. If it did crash, say so
   and stop; otherwise tell the user it is ready, in a sentence.

## Leave it running

The emulator stays up — the whole point is that the user pokes at it afterwards.
Never call `adb emu kill` in this skill, and do not offer to.

## Notes

- **The first launch after install is slow.** Room extracts a ~100 MB dictionary
  out of the APK on first query, so the first tap can hang for ~15 seconds. Warn
  the user instead of assuming it wedged.
- **`am force-stop` then relaunch still shows the splash for several seconds.**
  Allow ~20 s after a force-stop, ~40 s on the first launch after an install.
- **`connectedAndroidTest` uninstalls the app when it finishes** — so it also
  takes away whatever the user was about to poke at. Anything that depends on an
  existing install must be driven with `adb install -r` plus
  `adb shell am instrument`.
