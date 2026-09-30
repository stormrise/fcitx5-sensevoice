# Fcitx5 SenseVoice

[English](README.md) | [简体中文](README.zh-CN.md)

## Closed testing (Alpha)

[![Get it on Google Play](docs/play-store/google-play-badge-en.png)](https://play.google.com/apps/testing/com.fcitx5sensevoice)

| Join method | Link |
|---|---|
| **Join on the web** | https://play.google.com/apps/testing/com.fcitx5sensevoice |
| **Join on Android** | https://play.google.com/store/apps/details?id=com.fcitx5sensevoice |

> Add tester Google accounts in Play Console **Closed testing → Alpha → Testers** first. `arm64-v8a` devices only.

Offline Chinese Voice IME for Android, powered by sherpa-onnx, Silero VAD, and SenseVoice.

This project is **not a Fcitx5 addon** and does not modify Fcitx5 Android. It is an independent standard Android `InputMethodService`. Fcitx5 discovers it through `android:imeSubtypeMode="voice"` and can switch to it from its voice-input button.

> [!IMPORTANT]
> This is an unofficial project and is not affiliated with Fcitx, SenseVoice, FunASR, Google, Android, Linux, or their owners. The repository source is Apache-2.0, but downloaded runtimes and model weights remain under separate terms. Do not redistribute a generated APK until the [binary release checklist](docs/release-checklist.md#public-apk-or-app-store-release) is complete.

![Voice input panel](docs/screenshots/sensevoice-ime-panel.png)

## Architecture

```text
Fcitx5 / Android IME switcher
        ↓
VoiceInputMethodService (main thread: lifecycle, UI, InputConnection)
        ├── PcmAudioRecorder (dedicated AudioRecord thread)
        └── SenseVoiceEngine (single ASR executor: Silero VAD + offline decode)
                ↓
        sherpa-onnx v1.13.6 + Silero VAD + SenseVoice INT8
                ↓
        InputConnection.commitText() per completed speech segment
```

- Audio: 16 kHz, mono, signed PCM16, bounded to 30 seconds.
- ASR: Silero VAD probabilities are evaluated every 512 samples and passed through a tested local endpoint state machine, followed by non-streaming SenseVoice decode for each completed speech segment. This provides pause-delimited incremental commits, not model-level partial results.
- ABI: `arm64-v8a` only.
- Android: `minSdk 28`, `targetSdk 36`.
- Network: the APK does not request `android.permission.INTERNET`.

See [docs/architecture.md](docs/architecture.md) for lifecycle and resource details.

## Supported languages

The released upstream SenseVoiceSmall checkpoint supports speech recognition and language identification for:

| Language | Code |
|---|---|
| Mandarin Chinese | `zh` |
| Cantonese | `yue` |
| English | `en` |
| Japanese | `ja` |
| Korean | `ko` |

Upstream also exposes `auto` for language identification and `nospeech` as a control option. This five-language list describes the released base checkpoint; the current `2025-09-09` app model is a Cantonese fine-tune described below. The app defaults to Mandarin (`zh`), but its launcher settings page can select `auto`, `zh`, `yue`, `en`, `ja`, or `ko`. The Android voice-IME subtype remains `zh_CN` for Fcitx5 discovery and does not restrict the recognizer setting.

## Requirements

- JBR 25.0.2 (the Android bytecode target remains JVM 17)
- Gradle Wrapper 9.5.0
- Android Gradle Plugin 9.3.2
- Android SDK platform 36 and Build Tools 36.0.0
- Android `arm64-v8a` device
- ADB for installation and diagnostics

Use JBR 25.0.2 as the Gradle JVM in IntelliJ IDEA and for command-line builds.

## Prepare fixed local ASR dependencies

Gradle resolves sherpa-onnx `v1.13.6` from [JitPack](https://k2-fsa.github.io/sherpa/onnx/java-api/anroid-java.html) at build time. Models and tokens are deliberately not tracked in Git. Review [third-party terms](THIRD_PARTY_NOTICES.md), then prepare the exact pinned model artifacts before building:

```bash
./scripts/prepare-local-asr.sh
```

The script downloads only local model inputs and verifies all hashes before copying them into ignored directories:

| Artifact | Pinned version | SHA-256 |
|---|---|---|
| SenseVoice archive | `2025-09-09 INT8` | `7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63` |
| `model.int8.onnx` | `2025-09-09 INT8` | `12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d` |
| `tokens.txt` | `2025-09-09` | `f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc` |
| Silero VAD | `silero_vad.onnx` | `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` |

Version status was checked against the official upstream releases on 2026-08-28. [`v1.13.6`](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.6) is the newest stable semantic sherpa-onnx release. The app uses sherpa-onnx's newest dated standard CPU INT8 SenseVoice-format archive, `2025-09-09`. Its model is the Apache-2.0 [ASLP-lab WSYue-ASR `sensevoice_small_yue`](https://huggingface.co/ASLP-lab/WSYue-ASR) Cantonese fine-tune, not a newer release of the original FunAudioLLM SenseVoiceSmall checkpoint. Because these are derived weights, all exposed recognition languages require device regression testing when the model is updated.

## Build

```bash
export JAVA_HOME="/path/to/jbr-25.0.2"
export ANDROID_SDK_ROOT="/path/to/Android/Sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"

./scripts/prepare-local-asr.sh
./gradlew --no-daemon test lintDebug assembleDebug assembleRelease
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` creates an unsigned local release build. Public binary distribution is intentionally disabled in CI until the model and native-runtime notice requirements are complete.

## Install and enable

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable com.fcitx5sensevoice/.VoiceInputMethodService
```

Grant microphone permission from the launcher permission screen or when prompted. Then enable the IME in Android keyboard settings if required.

## Settings

Open **Fcitx5 SenseVoice** from the Android launcher. The settings page provides:

- recognition language: automatic, Mandarin, Cantonese, English, Japanese, or Korean;
- inverse text normalization (ITN) for written-form numbers and similar text;
- three voice-activity sensitivity presets;
- endpoint silence of 0.5, 0.7, 1.0, or 1.5 seconds;
- microphone permission status and request action.

Settings are stored in app-private preferences. They are applied by safely rebuilding the local recognizer the next time the voice input panel opens; no network access is involved.

## Enable Fcitx5 voice input

1. Open Fcitx5 Android settings.
2. Enable **显示语音输入按钮**.
3. Select **Fcitx5 SenseVoice** as the voice input method if the preference is shown.
4. Open a non-password text field and tap the Fcitx5 voice button.

The voice panel supports:

- tap microphone to start, tap again to stop;
- hold microphone to record, release to stop;
- while recording, a speech segment is recognized and inserted after the configured endpoint silence (about 700 ms by default); recording then continues for the next segment;
- top-left keyboard icon to return to the previous IME (or open the IME picker when Android has no previous-IME history);
- top-right backspace icon to delete the current selection or previous character.

## Model installation

The pinned SenseVoice, token, and VAD files are packaged as build-time assets for the development APK. On first use they are copied through a staging directory into app-private storage, verified against fixed SHA-256 values, atomically installed, and marked read-only. A missing or modified file prevents ASR initialization and produces a visible error instead of falling back to a network service.

## Offline guarantee

- No `INTERNET` permission.
- No HTTP, WebSocket, telemetry, cloud ASR, or runtime model download path.
- Audio remains in bounded in-memory VAD and 30-second fallback buffers and is discarded after recognition.
- Transcripts are committed only to the active Android `InputConnection` and are not logged verbatim.

## Known limitations

- The development APK is large because it embeds the roughly 228 MiB INT8 model.
- Mandarin is the default language. Automatic and explicit five-language modes are available in settings, but recognition quality depends on the current Cantonese-fine-tuned weights.
- SenseVoice remains an offline model: there are no word-by-word partial results or composing text. Incremental commits occur only after VAD finds a speech endpoint.
- VAD minimum speech (about 256 ms), pre-roll (about 224 ms), and maximum segment (15 seconds) remain fixed. Sensitivity and endpoint silence are configurable. If VAD produces no segment, explicit stop falls back to one full-recording SenseVoice decode so captured speech is not lost.
- No QNN/NPU or custom dictionary.
- Only `arm64-v8a` is packaged.
- Public binary release still requires the documented legal, signing, package-inspection, and manual-device gates.

## Google Play release

Store listing copy, graphics, and upload steps live in [`docs/google-play-listing.md`](docs/google-play-listing.md). Build a signed AAB with:

```bash
./scripts/create-upload-keystore.sh   # once before the first release
./scripts/package-release.sh
./scripts/verify-release-package.sh   # permission and ABI inspection
```

Upload `app/build/outputs/bundle/release/app-release.aab`. Complete the manual device matrix in [`docs/manual-test.md`](docs/manual-test.md) before publishing. See **Closed testing (Alpha)** at the top for the install links.

## License

Original source and documentation are licensed under the [Apache License 2.0](LICENSE), copyright 2026 Lingxiao Li. Third-party runtimes and models are not relicensed; see [`NOTICE`](NOTICE) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

The launcher icon uses original vector artwork. Product names and marks belong to their respective owners; Apache-2.0 grants no trademark rights.

## Project policies

- [Contributing](CONTRIBUTING.md)
- [Privacy](PRIVACY.md)
- [Security reporting](SECURITY.md)
- [Release checklist](docs/release-checklist.md)
