#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cache="$root/local-deps"
downloads="$cache/downloads"
extract="$cache/sensevoice-2025-09-09"
aar="$downloads/sherpa-onnx-1.13.6.aar"
archive="$downloads/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2"
vad="$downloads/silero_vad.onnx"
model_source="$extract/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
model_target="$root/app/src/main/assets/models/sensevoice"

aar_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/sherpa-onnx-1.13.6.aar"
model_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2"
vad_url="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

aar_sha256="0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698"
archive_sha256="7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63"
model_sha256="12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d"
tokens_sha256="f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"
vad_sha256="9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"

sha256() {
    shasum -a 256 "$1" | awk '{print $1}'
}

verify() {
    expected=$1
    file=$2
    actual=$(sha256 "$file")
    if [ "$actual" != "$expected" ]; then
        printf 'SHA-256 mismatch for %s\nexpected: %s\nactual:   %s\n' "$file" "$expected" "$actual" >&2
        exit 1
    fi
}

download() {
    url=$1
    destination=$2
    expected=$3
    if [ -f "$destination" ]; then
        verify "$expected" "$destination"
        return
    fi
    partial="$destination.part"
    curl --fail --location --retry 3 --continue-at - --output "$partial" "$url"
    verify "$expected" "$partial"
    mv "$partial" "$destination"
}

mkdir -p "$downloads" "$extract" "$root/app/libs" "$model_target"

legacy_vad="$model_target/silero_vad_v5.onnx"
if [ -f "$legacy_vad" ]; then
    mv "$legacy_vad" "$downloads/silero_vad_v5.onnx"
fi

download "$aar_url" "$aar" "$aar_sha256"
download "$model_url" "$archive" "$archive_sha256"
download "$vad_url" "$vad" "$vad_sha256"

if [ ! -f "$model_source/model.int8.onnx" ]; then
    tar -xjf "$archive" -C "$extract"
fi

verify "$model_sha256" "$model_source/model.int8.onnx"
verify "$tokens_sha256" "$model_source/tokens.txt"

cp "$aar" "$root/app/libs/sherpa-onnx-1.13.6.aar"
cp "$model_source/model.int8.onnx" "$model_target/model.int8.onnx"
cp "$model_source/tokens.txt" "$model_target/tokens.txt"
cp "$vad" "$model_target/silero_vad.onnx"
if [ -f "$model_source/LICENSE" ]; then
    cp "$model_source/LICENSE" "$model_target/LICENSE"
elif [ -f "$model_target/LICENSE" ]; then
    unlink "$model_target/LICENSE"
fi

verify "$aar_sha256" "$root/app/libs/sherpa-onnx-1.13.6.aar"
verify "$model_sha256" "$model_target/model.int8.onnx"
verify "$tokens_sha256" "$model_target/tokens.txt"
verify "$vad_sha256" "$model_target/silero_vad.onnx"

printf '%s\n' "Prepared fixed local ASR dependencies:"
printf '  sherpa-onnx AAR: %s\n' "$root/app/libs/sherpa-onnx-1.13.6.aar"
printf '  SenseVoice model: %s\n' "$model_target/model.int8.onnx"
printf '  SenseVoice tokens: %s\n' "$model_target/tokens.txt"
printf '  Silero VAD model: %s\n' "$model_target/silero_vad.onnx"
