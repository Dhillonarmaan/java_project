#!/bin/sh
# Creates a throwaway SoftHSM token for the PKCS#11 tests, isolated from any
# system SoftHSM config. Everything lives under backend/target/softhsm/, so
# `mvn clean` removes it. Test PINs only - never use these for real keys.
#
# Usage (from backend/):  sh scripts/softhsm-init.sh
# Then run the command it prints.
set -eu

cd "$(dirname "$0")/.."
BASE="$PWD/target/softhsm"
LABEL="${SOFTHSM_LABEL:-kb-test}"
PIN="${SOFTHSM_PIN:-123456}"
SO_PIN="${SOFTHSM_SO_PIN:-12345678}"
LIB="${PKCS11_LIBRARY:-}"

if [ -z "$LIB" ]; then
  for candidate in /opt/homebrew/lib/softhsm/libsofthsm2.so /usr/local/lib/softhsm/libsofthsm2.so /usr/lib/softhsm/libsofthsm2.so; do
    [ -f "$candidate" ] && LIB="$candidate" && break
  done
fi
[ -n "$LIB" ] && [ -f "$LIB" ] || { echo "SoftHSM library not found; install it (brew install softhsm) or set PKCS11_LIBRARY" >&2; exit 1; }
command -v softhsm2-util >/dev/null || { echo "softhsm2-util not on PATH" >&2; exit 1; }

rm -rf "$BASE"
mkdir -p "$BASE/tokens"
cat > "$BASE/softhsm2.conf" <<CONF
directories.tokendir = $BASE/tokens
objectstore.backend = file
log.level = ERROR
CONF
export SOFTHSM2_CONF="$BASE/softhsm2.conf"

softhsm2-util --init-token --free --label "$LABEL" --pin "$PIN" --so-pin "$SO_PIN" >/dev/null
SLOT=$(softhsm2-util --show-slots | awk -v label="$LABEL" '
  /^Slot [0-9]+/ { slot = $2 }
  /Label:/ { sub(/^[ \t]*Label:[ \t]*/, ""); sub(/[ \t]+$/, ""); if ($0 == label) { print slot; exit } }')
[ -n "$SLOT" ] || { echo "Could not find slot for token $LABEL" >&2; exit 1; }

echo "SoftHSM token '$LABEL' ready in slot $SLOT"
echo
echo "Run the PKCS#11 tests with:"
echo "  SOFTHSM2_CONF=$SOFTHSM2_CONF sh mvnw test -Dtest='Pkcs11*' \\"
echo "    -Dkeybridge.pkcs11.library=$LIB -Dkeybridge.pkcs11.slot=$SLOT -Dkeybridge.pkcs11.pin=$PIN"
