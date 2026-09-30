#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
out="$root/docs/copyright/源程序鉴别材料.txt"
src_dir="$root/app/src/main/java/com/fcitx5sensevoice"
lines_per_page=50
software_name="Fcitx5 SenseVoice安卓离线语音输入法软件 V1.0"

mkdir -p "$(dirname "$out")"
: >"$out.tmp"

files=(
    AsrEngine.kt
    AudioBuffer.kt
    AudioRecorder.kt
    AppSettings.kt
    LegalDocumentActivity.kt
    ModelManager.kt
    PcmAudioRecorder.kt
    SenseVoiceEngine.kt
    SettingsActivity.kt
    SileroVadSegmenter.kt
    Transcript.kt
    VoiceInputMethodService.kt
    VoiceState.kt
    VoiceWaveView.kt
)

for file in "${files[@]}"; do
    path="$src_dir/$file"
    if [ ! -f "$path" ]; then
        printf 'Missing source file: %s\n' "$path" >&2
        exit 1
    fi
    {
        printf '/* ===== File: %s ===== */\n' "$file"
        cat "$path"
        printf '\n\n'
    } >>"$out.tmp"
done

total_lines=$(wc -l <"$out.tmp" | tr -d ' ')
page=1
line_in_page=0

{
    printf '%s\n' "$software_name"
    printf '%s\n\n' "源程序鉴别材料（共 $total_lines 行）"
} >"$out"

while IFS= read -r line || [ -n "$line" ]; do
    if [ "$line_in_page" -eq 0 ]; then
        printf '\n---------- 第 %d 页 ----------\n' "$page" >>"$out"
    fi
    line_in_page=$((line_in_page + 1))
    printf '%s\n' "$line" >>"$out"
    if [ "$line_in_page" -ge "$lines_per_page" ]; then
        page=$((page + 1))
        line_in_page=0
    fi
done <"$out.tmp"

total_pages=$page
if [ "$line_in_page" -gt 0 ]; then
    total_pages=$page
else
    total_pages=$((page - 1))
fi

rm -f "$out.tmp"

printf '%s\n' "Generated $out"
printf 'Total lines: %s, pages (@%s lines): %s\n' "$total_lines" "$lines_per_page" "$total_pages"
if [ "$total_pages" -le 60 ]; then
    printf '%s\n' "Under 60 pages: submit the full file."
else
    printf '%s\n' "Over 60 pages: submit first 30 and last 30 pages in PDF."
fi
