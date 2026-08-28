# Architecture

## Boundary

`fcitx5-sensevoice` is an independent Android Voice IME. Its only integration contract with Fcitx5 is the standard Android input-method registration and a subtype whose mode is `voice`.

## Threads

| Thread | Responsibilities |
|---|---|
| Android main | IME lifecycle, touch handling, status rendering, permission launch, `InputConnection` access |
| `fcitx5-sensevoice-audio` | `AudioRecord` creation, blocking reads, stop, release |
| `fcitx5-sensevoice-asr` | model install/verification, Silero VAD JNI, synchronous SenseVoice decode, native resource release |

The main thread never reads microphone PCM or calls sherpa-onnx decode.

## Recording and recognition

```text
tap/hold microphone
  → AudioRecord (16 kHz / mono / PCM16)
  → ordered PCM tasks on the single ASR executor
  → PCM16 normalized to float [-1, 1)
  → Silero probability per 512-sample frame
  → tested Kotlin endpoint state machine
  → speech endpoint after configured silence (about 700 ms by default, or stop + flush)
  → one OfflineStream per completed speech segment
  → SenseVoice OfflineRecognizer.decode()
  → commit non-empty segment and continue recording
```

Silero probability inference is streaming, but SenseVoice is not. The local state machine requires about 256 ms of speech probability, preserves about 224 ms of pre-roll, and closes a segment after the configured silence duration (about 704 ms by default) below the negative threshold. No transcript is emitted until a segment completes; this is pause-delimited incremental recognition rather than word-by-word partial recognition. Each completed segment owns one sherpa offline stream, released in `finally`. If VAD produces no segment, explicit stop falls back to decoding the bounded full recording once. VAD and recognizer live for the IME service lifetime and are explicitly released during destruction.

The audio thread only reads PCM and enqueues work. Every VAD and recognizer JNI call is serialized on `fcitx5-sensevoice-asr`. On explicit stop, `finish()` is queued after all PCM tasks and flushes an in-progress VAD segment. Cancel and focus loss instead queue a reset and invalidate the recording generation, so already-decoded background results cannot reach a new input target.

## Input-session safety

Recognition captures the current input-session number and recording generation before capture starts. A non-empty segment is committed only if:

- the input view is still active;
- the input-session number is unchanged;
- the recording generation is unchanged (not cancelled, replaced, or failed);
- `currentInputConnection` is non-null;
- `commitText()` returns success.

Otherwise the result is discarded and a reason is logged without logging the transcript.

## Model lifecycle

The first initialization copies the bundled SenseVoice model, tokens, and Silero VAD model into an app-private staging directory, verifies fixed SHA-256 values, renames the staging directory atomically, and marks the installed files read-only. Later initializations verify all files before use. Corrupt, missing, or unverified files cannot reach sherpa-onnx.

Recognition language, inverse text normalization, VAD threshold preset, and endpoint silence are stored in app-private `SharedPreferences`. `onStartInputView()` compares the stored settings with the loaded engine configuration. A change queues a replacement recognizer on the single ASR executor; an initialization generation prevents an older, slower initialization from replacing a newer configuration. The previous native engine is released only after its replacement is ready.

## Offline and privacy properties

- The manifest has no `INTERNET` permission.
- No runtime downloader or cloud fallback exists.
- Audio is bounded in memory; it is not written to shared or private storage.
- Logs contain durations, sample counts, result lengths, and error diagnostics, never raw PCM or complete transcript text.
