#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
legal_dir="$root/app/src/main/assets/legal"
onnx_notices="$legal_dir/onnxruntime_third_party_notices.txt"
onnx_url="https://raw.githubusercontent.com/microsoft/onnxruntime/v1.27.1/ThirdPartyNotices.txt"
mkdir -p "$legal_dir"

sha256() {
    shasum -a 256 "$1" | awk '{print $1}'
}

download_if_missing() {
    url=$1
    destination=$2
    if [ -f "$destination" ]; then
        return
    fi
    partial="$destination.part"
    curl --fail --location --retry 3 --output "$partial" "$url"
    mv "$partial" "$destination"
}

download_if_missing "$onnx_url" "$onnx_notices"

if [ ! -s "$onnx_notices" ]; then
    printf 'Failed to download ONNX Runtime ThirdPartyNotices.txt\n' >&2
    exit 1
fi

printf '%s\n' "Prepared legal assets in $legal_dir"
