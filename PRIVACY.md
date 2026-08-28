# Privacy

Fcitx5 SenseVoice performs speech recognition locally on the Android device.

## Data handled by the app

- The app requests microphone access only while the user starts voice input.
- Audio is held in bounded memory for VAD and recognition. The app does not save or upload recordings.
- Recognized text is sent only to the currently active Android `InputConnection`, which belongs to the app and field selected by the user.
- Diagnostic logs contain operational metadata such as durations, sample counts, result lengths, and errors. They do not contain raw PCM or complete transcripts.

## Collection and sharing

The app contains no analytics, advertising, telemetry, account system, cloud ASR, or runtime model downloader. It does not request Android's `INTERNET` permission. The project operator therefore does not collect or share personal data through the app.

The destination app, Android system services, keyboard framework, and device vendor operate under their own privacy terms. This document describes only Fcitx5 SenseVoice itself.

Changes that add networking, persistent audio/transcript storage, telemetry, or new sensitive permissions require this document and the implementation to be reviewed before release.
