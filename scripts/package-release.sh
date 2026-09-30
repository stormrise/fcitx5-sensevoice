#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"

if [ ! -f "$root/keystore.properties" ]; then
    printf '%s\n' "Missing keystore.properties. Run ./scripts/create-upload-keystore.sh first." >&2
    exit 1
fi

./scripts/prepare-local-asr.sh
./scripts/prepare-legal-assets.sh
./gradlew --no-daemon test lintDebug bundleRelease

aab="$root/app/build/outputs/bundle/release/app-release.aab"
if [ ! -f "$aab" ]; then
    printf '%s\n' "Release AAB was not produced: $aab" >&2
    exit 1
fi

checksums="$root/app/build/outputs/bundle/release/SHA256SUMS"
shasum -a 256 "$aab" >"$checksums"
printf '%s\n' "Release AAB: $aab"
cat "$checksums"
