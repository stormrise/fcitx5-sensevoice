# Contributing

Thanks for improving Fcitx5 SenseVoice. Keep changes focused and preserve the project's offline and input-session safety boundaries.

## Development setup

1. Install JBR/JDK 25, Android SDK platform 36, and Build Tools 36.0.0.
2. Point `JAVA_HOME` and `ANDROID_SDK_ROOT` at those installations.
3. Download the pinned local model files. Gradle resolves sherpa-onnx from JitPack at build time:

   ```bash
   ./scripts/prepare-local-asr.sh
   ```

   Running this script downloads third-party model artifacts under their own terms. Read [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) first.

4. Validate the project:

   ```bash
   ./gradlew --no-daemon test lintDebug assembleDebug assembleRelease
   ```

Use an `arm64-v8a` Android device for IME behavior and speech testing. Follow [`docs/manual-test.md`](docs/manual-test.md) for device acceptance.

## Change guidelines

- Keep microphone capture, VAD, and decode off the Android main thread.
- Never add network speech recognition, telemetry, or an `INTERNET` permission without an explicit project decision and privacy review.
- Preserve input-session and recording-generation checks so delayed text cannot reach a different field.
- Do not log PCM, full transcripts, credentials, or user-entered text.
- Add or update the smallest relevant test for behavior changes.
- Update documentation when changing pinned artifacts, hashes, permissions, ABIs, or model behavior.

## Files that must not be committed

Do not commit models, tokens, APKs, AABs, signing material, local SDK paths, captured audio, or build output. The preparation script must remain the reproducible source of local model files.

Contributions are submitted under the repository's [Apache License 2.0](LICENSE), as described by Section 5 of that license. Third-party contributions must retain their own notices and be license-compatible.
