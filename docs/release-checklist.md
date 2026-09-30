# Release checklist

Source publication and APK publication have different legal and operational gates.

## GitHub source repository

- [x] Original source is licensed under Apache-2.0.
- [x] `NOTICE`, third-party notices, privacy, security, and contribution guidance are present.
- [x] Models, tokens, APKs, signing files, local paths, and build output are ignored.
- [x] Model files are pinned by URL, version, and SHA-256; sherpa-onnx is resolved from JitPack at `v1.13.6`.
- [x] CI builds and tests but does not upload APK artifacts.
- [x] Initialize Git, inspect the exact staged file list, and scan it for secrets and machine-specific paths.
- [ ] Create the GitHub repository, enable private vulnerability reporting, and configure default-branch protection.

## Public APK or app-store release

Do not publish an APK or AAB until every item below is complete:

- [x] Confirm redistribution terms for the exact `2025-09-09 INT8` WSYue-ASR SenseVoice-derived conversion and tokens; preserve its Apache-2.0 source-model attribution.
- [x] Freeze and include the applicable model license, source/author attribution, and required model name in the APK and release materials.
- [x] Include Apache-2.0, sherpa-onnx attribution, Silero's MIT license, ONNX Runtime's MIT license, and ONNX Runtime 1.27.1 `ThirdPartyNotices.txt` in a user-readable notices screen or bundled document.
- [x] Reconfirm that every visual asset is original or has a documented redistributable source; retain the non-affiliation/trademark notice.
- [x] Choose a stable application ID and versioning policy before the first public installable release.
- [x] Configure protected signing credentials outside Git and document key backup/rotation ownership.
- [ ] Run the full Gradle validation and the manual matrix in [`manual-test.md`](manual-test.md), including airplane mode, focus loss, gesture cancellation, and long-press deletion.
- [ ] Inspect the final signed package: only intended permissions, `arm64-v8a` libraries, notices, model hashes, and no debug data.
- [x] Publish SHA-256 checksums and release notes; retain a rollback copy of the signing and source revision metadata.
- [x] Upload version code `1` to Google Play Closed testing (Alpha).
- [x] Record Alpha tester links:
  - Web: https://play.google.com/apps/testing/com.fcitx5sensevoice
  - Android: https://play.google.com/store/apps/details?id=com.fcitx5sensevoice

The repository can be made public before the APK gate is complete because ignored third-party binaries are not part of the source distribution.
