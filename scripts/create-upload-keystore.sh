#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
keystore="$root/upload-keystore.jks"
properties="$root/keystore.properties"

if [ -f "$keystore" ]; then
    printf '%s\n' "Keystore already exists: $keystore"
    exit 0
fi

store_password="${UPLOAD_STORE_PASSWORD:-Fcitx5SenseVoiceUpload}"
key_password="${UPLOAD_KEY_PASSWORD:-$store_password}"

keytool -genkeypair -v \
    -keystore "$keystore" \
    -alias upload \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$store_password" \
    -keypass "$key_password" \
    -dname "CN=Fcitx5 SenseVoice, OU=Mobile, O=Lingxiao Li, L=Unknown, ST=Unknown, C=CN"

cat >"$properties" <<EOF
storeFile=upload-keystore.jks
storePassword=$store_password
keyAlias=upload
keyPassword=$key_password
EOF

chmod 600 "$properties"
printf '%s\n' "Created $keystore and $properties"
printf '%s\n' "Back up the keystore and passwords before publishing to Google Play."
