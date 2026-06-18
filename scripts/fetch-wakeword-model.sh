#!/usr/bin/env bash
# Downloads the Vosk Spanish model used by the "Hey Atom" wake word into
# src/main/assets/model-es/ (gitignored — too large to commit).
set -euo pipefail
URL="https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
DEST="$(cd "$(dirname "$0")/.." && pwd)/src/main/assets/model-es"
TMP="$(mktemp -d)"
echo "Downloading Vosk ES model..."
curl -fsSL -o "$TMP/model.zip" "$URL"
unzip -q "$TMP/model.zip" -d "$TMP"
INNER="$(ls -d "$TMP"/*/ | head -1)"
rm -rf "$DEST"; mkdir -p "$DEST"
cp -r "$INNER"* "$DEST"/
rm -rf "$TMP"
echo "vosk-model-small-es-0.42" > "$DEST/uuid"
echo "Model ready at $DEST"
