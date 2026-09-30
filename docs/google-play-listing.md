# Google Play listing

Use this document when filling in Google Play Console. Assets are generated under `docs/play-store/`.

## Internal testing

Testers can join your test on the web. Use this link:

[![Get it on Google Play](play-store/google-play-badge-en.png)](https://play.google.com/apps/internaltest/4701482031936983435)

中文版徽章：[![在 Google Play 上加入内测](play-store/google-play-badge-zh-cn.png)](https://play.google.com/apps/internaltest/4701482031936983435)

| Field | Value |
|---|---|
| Track | Internal testing |
| Version | `0.1.0` (version code `1`) |
| Tester link | https://play.google.com/apps/internaltest/4701482031936983435 |

Testers must be added in Play Console **Internal testing → Testers** before the link works on device.

## App access

| Field | Value |
|---|---|
| Package name | `com.fcitx5sensevoice` |
| Version name | `0.1.0` |
| Version code | `1` |
| Default language | Chinese (Simplified) |
| Category | Tools or Productivity |
| Contact email | `lingxiaoli@trip.com` |
| Website | https://github.com/stormrise/fcitx5-sensevoice |
| Privacy policy URL | https://github.com/stormrise/fcitx5-sensevoice/blob/main/PRIVACY.md |

## Short description (80 chars)

```text
纯离线中文语音输入法，本地 SenseVoice 识别，无需联网，保护隐私。
```

## Full description

```text
Fcitx5 SenseVoice 是一款纯离线的 Android 语音输入法。

通过 sherpa-onnx、Silero VAD 和 SenseVoice 模型，在设备本地完成语音识别，无需网络连接，不上传录音，不包含广告或遥测。

主要特性：
• 完全离线：不申请网络权限，录音仅在本地处理
• 多语言识别：支持普通话、粤语、英语、日语、韩语及自动识别
• 与 Fcitx5 配合：Fcitx5 可通过语音输入按钮切换到此输入法
• 可配置：识别语言、智能格式化（ITN）、收音灵敏度、停顿结束时长
• 分段输入：检测到语音停顿后自动识别并插入文字

使用方法：
1. 在系统设置中启用并授予麦克风权限
2. 在 Fcitx5 中开启「显示语音输入按钮」
3. 在输入框中点击 Fcitx5 的语音按钮即可使用

重要说明：
本项目为非官方项目，与 Fcitx、SenseVoice、FunASR、Google、Android 及其所有者无隶属或关联关系。产品名称与商标属于各自所有者。

已知限制：
• 仅支持 arm64-v8a 设备
• 应用体积较大（内置离线模型）
• 按语音停顿分段提交，非逐字流式显示
```

## Release notes (v0.1.0)

```text
• 首次公开发布
• 纯离线 SenseVoice 中文语音输入法
• 支持普通话、粤语、英语、日语、韩语
• 可配置收音灵敏度与停顿结束时长
• 与 Fcitx5 Android 语音输入按钮配合使用
```

## Graphics

| Asset | Path |
|---|---|
| Hi-res icon (512x512) | `docs/play-store/icon-512.png` |
| Feature graphic (1024x500) | `docs/play-store/feature-graphic.png` |
| Phone screenshots | `docs/play-store/phone/*.png` |
| Google Play badge (zh-CN) | `docs/play-store/google-play-badge-zh-cn.png` |
| Google Play badge (en) | `docs/play-store/google-play-badge-en.png` |

Regenerate with:

```bash
python3 scripts/generate-play-store-assets.py
```

## Data safety

| Question | Answer |
|---|---|
| Collect or share user data? | No |
| Encrypted in transit | Not applicable |
| Request data deletion | Not applicable |
| Microphone | Used for voice input; processed on device only; not stored or shared |
| Analytics / ads / accounts | No |

## Content rating

Expected result: Everyone / 全年龄.

## Device targeting

- Phones: yes
- Tablets: not specifically optimized
- ABIs: `arm64-v8a` only
- Min SDK: 28 (Android 9)
- Target SDK: 36

## Upload artifact

Build a signed Android App Bundle:

```bash
./scripts/create-upload-keystore.sh
./scripts/package-release.sh
```

Upload:

```text
app/build/outputs/bundle/release/app-release.aab
```

Checksum:

```text
app/build/outputs/bundle/release/SHA256SUMS
```

## Pre-upload checklist

- [ ] Replace the upload keystore passwords if you used the script defaults
- [ ] Back up `upload-keystore.jks` and `keystore.properties`
- [ ] Complete the manual matrix in `docs/manual-test.md`
- [x] Confirm Play App Signing enrollment during first upload
- [x] Upload `app-release.aab` to Internal testing
