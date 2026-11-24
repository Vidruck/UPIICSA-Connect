#!/usr/bin/env bash
# =============================================================================
# UPIICSA-Connect — Construcción del .AppImage a partir del distribuible de Compose Desktop
#
# Uso:
#   bash packaging/linux/construir-appimage.sh <versión> [directorio-salida]
#
# Requiere haber ejecutado antes: ./gradlew :desktopApp:createDistributable
# Descarga appimagetool fijado por versión y verificado con SHA-256.
# =============================================================================
set -euo pipefail

VERSION="${1:?Indique la versión, por ejemplo 0.1.0}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${2:-$REPO_ROOT/desktopApp/build/compose/binaries/main/appimage}"
DIST_DIR="$REPO_ROOT/desktopApp/build/compose/binaries/main/app/UPIICSA-Connect"
APPIMAGETOOL_VERSION="1.9.1"
APPIMAGETOOL_SHA256="ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

[[ -x "$DIST_DIR/bin/UPIICSA-Connect" ]] || {
    echo "No existe $DIST_DIR; ejecute ./gradlew :desktopApp:createDistributable" >&2
    exit 1
}

TOOL="$WORK_DIR/appimagetool-x86_64.AppImage"
curl -fsSL -o "$TOOL" \
    "https://github.com/AppImage/appimagetool/releases/download/$APPIMAGETOOL_VERSION/appimagetool-x86_64.AppImage"
echo "$APPIMAGETOOL_SHA256  $TOOL" | sha256sum -c -
chmod +x "$TOOL"

APPDIR="$WORK_DIR/UPIICSA-Connect.AppDir"
mkdir -p "$APPDIR"
cp -a "$DIST_DIR/." "$APPDIR/"
cp "$REPO_ROOT/packaging/linux/upiicsa-connect.desktop" "$APPDIR/"
cp "$REPO_ROOT/packaging/icons/upiicsa-connect.png" "$APPDIR/upiicsa-connect.png"
ln -s upiicsa-connect.png "$APPDIR/.DirIcon"
cat > "$APPDIR/AppRun" <<'APPRUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/bin/UPIICSA-Connect" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"

mkdir -p "$OUT_DIR"
TARGET="$OUT_DIR/UPIICSA-Connect-$VERSION-x86_64.AppImage"
APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 "$TOOL" --no-appstream "$APPDIR" "$TARGET"
echo "AppImage generado: $TARGET"
