---
name: device
description: Run the instrumented journey tests (FakeLlm) on the attached phone and save a screenshot and logcat as evidence. Use after UI, lifecycle or ChatViewModel changes, for spec rows verified by `device`, or when the user asks for /device.
---

# Device check

1. If more than one device is attached (`adb devices`), set `ANDROID_SERIAL` to the phone (the Fairphone, not
   the emulator) in every command.
2. Run `scripts/device-check.sh`. It refuses when the phone is asleep or locked: ask the user to unlock it.
3. Report pass or fail, the test count and time, and on failure the crash or assertion from
   `build/reports/device/instrument.txt` and `logcat.txt`. Look at `screen.png` when the failure is visual.

Never uninstall the app, run `pm clear`, or run `connectedDebugAndroidTest`: each deletes the model in the app's
`files/models/` dir. The script fails loudly if the model files change; if one is gone, say so and restore it with
`adb push <model>.litertlm /sdcard/Android/data/dev.stoa.droid/files/models/`.

The tests use `FakeLlm` (via `FakeLlmRunner` in `app/src/androidTest`), never the real model. They do not run in
`scripts/validate.sh` or CI, which have no phone.
