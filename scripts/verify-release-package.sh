#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
aab="$root/app/build/outputs/bundle/release/app-release.aab"
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

if [ ! -f "$aab" ]; then
    printf '%s\n' "Missing release AAB. Run ./scripts/package-release.sh first." >&2
    exit 1
fi

unzip -q "$aab" -d "$workdir"
manifest="$workdir/base/manifest/AndroidManifest.xml"

printf '%s\n' "Permissions:"
strings "$manifest" | grep 'android.permission.' || true
if strings "$manifest" | grep -q 'android.permission.INTERNET'; then
    printf '%s\n' "ERROR: INTERNET permission must not be present." >&2
    exit 1
fi
if ! strings "$manifest" | grep -q 'android.permission.RECORD_AUDIO'; then
    printf '%s\n' "ERROR: RECORD_AUDIO permission is missing." >&2
    exit 1
fi

printf '\n%s\n' "Native libraries:"
unzip -l "$aab" | awk '/base\/lib\// {print $4}' | sort -u

abis=$(unzip -l "$aab" | awk '/base\/lib\// {split($4, parts, "/"); print parts[3]}' | sort -u)
if [ "$abis" != "arm64-v8a" ]; then
    printf '%s\n' "ERROR: Expected only arm64-v8a, found: $abis" >&2
    exit 1
fi

printf '\n%s\n' "Bundled legal assets:"
unzip -l "$aab" | awk '/base\/assets\/legal\// {print $4}' | sort

for asset in \
    base/assets/legal/privacy_policy.txt \
    base/assets/legal/third_party_notices.txt \
    base/assets/legal/model_attribution.txt \
    base/assets/legal/onnxruntime_third_party_notices.txt
do
    if ! unzip -l "$aab" | awk '{print $4}' | grep -qx "$asset"; then
        printf 'ERROR: Missing bundled asset: %s\n' "$asset" >&2
        exit 1
    fi
done

printf '\n%s\n' "Release bundle checksum:"
shasum -a 256 "$aab"
