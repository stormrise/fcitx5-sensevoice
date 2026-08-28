# Manual device acceptance

Run these checks on an authorized `arm64-v8a` Android device before distributing a binary. Keep results per Android and device-software version.

## Prerequisite

Connect and authorize the device, then confirm it is visible:

```bash
adb devices -l
```

The output must contain one device whose state is `device`, not `unauthorized` or `offline`.

## Phase 1 installation and IME registration

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime list -a
```

Expected component:

```text
com.fcitx5sensevoice/.VoiceInputMethodService
```

Enable the IME from Android's keyboard/input-method settings. Do not replace the user's default input method through ADB without explicit confirmation.

## Phase 2 Fcitx5 discovery

1. Open Fcitx5 Android settings.
2. Enable **显示语音输入按钮**.
3. If the version exposes a preferred voice-input selector, choose **Fcitx5 SenseVoice**.
4. Open a normal, non-password text field.
5. Confirm Fcitx5 shows the voice-input button.
6. Tap it and confirm Android switches to **Fcitx5 SenseVoice**.

Capture diagnostics if discovery or switching fails:

```bash
adb shell ime list -a
adb shell dumpsys input_method > /tmp/fcitx5-sensevoice-input-method.txt
adb logcat -d -v threadtime > /tmp/fcitx5-sensevoice-logcat.txt
```

Do not continue to microphone or ASR implementation if this integration check fails.

## Settings

1. Open **Fcitx5 SenseVoice** from the Android launcher.
2. Confirm language offers automatic, Mandarin, Cantonese, English, Japanese, and Korean.
3. Select **Automatic**, disable smart formatting, choose a different sensitivity and endpoint silence, then leave the page.
4. Reopen the page and confirm all values persisted.
5. Return to a text field, open the voice panel, and confirm logcat reports `MODEL_INIT_SUCCESS language=auto` with the selected VAD values.
6. Restore defaults and confirm the next voice-panel initialization reports `language=zh`, ITN enabled, threshold `0.25`, and endpoint silence `0.7`.
7. Confirm the microphone section shows the current permission state and can launch the system permission request when access is missing.

## Voice UI and recognition

1. Switch from Fcitx5 to Fcitx5 SenseVoice using the Fcitx5 voice button.
2. Confirm the Voice IME window has the same visible height as Fcitx5 and has balanced top/bottom spacing.
3. Tap the center microphone, speak one Chinese phrase, pause for about one second, then speak another phrase. Confirm the first phrase is inserted during the pause, recording remains active, and the second phrase is inserted after the next pause.
    While speaking, confirm the three mirrored waveform bars follow the live microphone level: louder speech produces taller peaks, silence falls back toward the minimum, and stopping recording resets the waveform.
4. Tap again to stop. If speech is still in progress without enough trailing silence, confirm the final segment is flushed and inserted once.
5. Hold the microphone, speak, and release. Confirm the final segment is flushed and inserted once.
6. Tap the top-right backspace icon. Confirm the selection or previous character is deleted.
7. Hold the top-right backspace icon. Confirm deletion begins after the long-press threshold, proceeds at a controllable pace (about 10 characters per second), and stops immediately when the finger is released.
8. Tap the top-left keyboard icon. Confirm Android returns to the IME that opened the Voice IME. If the Voice IME was selected through ADB and no history exists, confirm the system IME picker opens.

## Failure paths

- Revoke microphone permission, tap the microphone, and confirm the permission Activity appears without a crash.
- Deny permission and confirm a readable permission-required state remains.
- Start recording and switch applications/input fields. Confirm recording stops and no transcript is committed to the new field.
- Start speaking, cancel the hold gesture before the VAD segment is committed, and confirm no delayed segment appears.
- Corrupt or remove the installed private model in a debug environment, restart the IME, and confirm `MODEL_INIT_FAILED` plus a readable model error. Restore by reinstalling/clearing app data afterward.
- Record silence and confirm no empty text is committed.

## Offline test

This is a manual acceptance step because wireless ADB depends on Wi-Fi:

1. Enable airplane mode.
2. Disable Wi-Fi and mobile data.
3. Open a local text field.
4. Use Fcitx5 to open Fcitx5 SenseVoice.
5. Record a Chinese phrase and confirm it is committed.

The APK must also declare no `INTERNET` permission:

```bash
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions \
  app/build/outputs/apk/debug/app-debug.apk
```

## Multi-application matrix

Record PASS/FAIL separately for:

- an OEM notes application
- a Chromium-based browser address/text field
- a messaging application
- a terminal or plain-text editor
- a standard Android `EditText` test application

For each application, test tap-to-toggle, hold-to-record, backspace, return-to-previous-IME, empty audio, and focus loss.
