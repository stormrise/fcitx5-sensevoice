# Third-party notices

The Apache License 2.0 in [`LICENSE`](LICENSE) covers this repository's original source and documentation. It does **not** relicense third-party runtimes, models, model weights, names, or marks.

The binary inputs below are downloaded by `scripts/prepare-local-asr.sh` into ignored paths. They are not part of the Git source distribution.

## Dependency summary

| Component | Pinned artifact | License | Notes |
|---|---|---|---|
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.6) | Android AAR `v1.13.6` | Apache-2.0 | The upstream AAR does not contain a `LICENSE` or `NOTICE` entry. Preserve the Apache-2.0 license when redistributing it. |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime/tree/v1.27.1) | `1.27.1`, inside the sherpa-onnx AAR | MIT | The version is fixed by sherpa-onnx's tagged Android build and is also present in the native binary. Binary redistribution must include ONNX Runtime's `LICENSE` and its exact [`ThirdPartyNotices.txt`](https://github.com/microsoft/onnxruntime/blob/v1.27.1/ThirdPartyNotices.txt). |
| [Silero VAD](https://github.com/snakers4/silero-vad) | `silero_vad.onnx` | MIT | Copyright © 2020-present Silero Team. |
| [SenseVoice source](https://github.com/FunAudioLLM/SenseVoice) | Upstream source only | MIT | Copyright © 2025 FunASR. The source-code license does not determine the model-weight license. |
| SenseVoice-derived WSYue model weights and tokens | sherpa-onnx conversion dated `2025-09-09` | Apache-2.0 on the source model card | Converted from `ASLP-lab/WSYue-ASR/sensevoice_small_yue`. The sherpa archive itself contains no license file, so preserve the source and model attribution when redistributing it. |

Exact artifact URLs and SHA-256 values are recorded in the preparation script and in [`README.md`](README.md).

## SenseVoice-derived WSYue model weights

The local development build uses:

```text
sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2
```

The archive's bundled README identifies its source as [`ASLP-lab/WSYue-ASR/sensevoice_small_yue`](https://huggingface.co/ASLP-lab/WSYue-ASR), a Cantonese fine-tune of the SenseVoice architecture. The source model card is marked Apache-2.0. This is not a newer release of the original FunAudioLLM SenseVoiceSmall checkpoint, whose weights have separate FunASR model terms. The sherpa conversion archive contains no license file, so the exact source-model attribution and applicable Apache-2.0 text must accompany any redistributed binary.

Consequently:

- the model, tokens, and generated APKs are intentionally ignored by Git;
- source publication does not grant rights to redistribute those model files;
- before publishing any APK, confirm that the exact converted artifact may be redistributed, capture the applicable license version, and include all required attribution in both the APK and release materials.

See [`docs/release-checklist.md`](docs/release-checklist.md). This is an engineering compliance boundary, not legal advice.

## MIT license — ONNX Runtime

```text
MIT License

Copyright (c) Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## MIT license — Silero VAD

```text
MIT License

Copyright (c) 2020-present Silero Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Names, marks, and artwork

This is an unofficial, independent Android Voice IME. It is not an Fcitx addon and is not affiliated with the Fcitx, SenseVoice, FunASR, Google, Android, Linux, or Tux projects or their owners. Apache-2.0 grants no trademark rights.

The launcher foreground is an original penguin-inspired vector drawn for this project; no upstream Fcitx, Tux, Google Voice Input, or Typeless artwork is embedded. References to other products describe interoperability or design comparison only.
