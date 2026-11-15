#!/usr/bin/env bash
# Re-signs a release APK with APK Signature Scheme v3 key rotation, so it also installs over
# builds signed with the original development key (Android 9+), keeping the user's data.
# Usage: tools/sign-with-rotation.sh <apk>   (needs keystore.properties, the debug keystore and
# the lineage file referenced below; none of these are in git).
set -euo pipefail
APK=$1
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
AS=$(ls -d "$SDK"/build-tools/*/apksigner | sort -V | tail -1)
prop() { grep "^$1=" keystore.properties | cut -d= -f2-; }
KS=$(prop storeFile); PASS=$(prop storePassword); ALIAS=$(prop keyAlias)
DBG=${CUSTO_OLD_KEYSTORE:-$HOME/.config/.android/debug.keystore}
LIN=${CUSTO_LINEAGE:-$HOME/.config/custo/custo-lineage.bin}
"$AS" sign --ks "$DBG" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
  --next-signer --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --lineage "$LIN" --rotation-min-sdk-version 28 "$APK"
"$AS" verify --print-certs "$APK" | grep "certificate DN"
