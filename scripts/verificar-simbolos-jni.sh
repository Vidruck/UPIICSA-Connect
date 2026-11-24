#!/usr/bin/env bash
# =============================================================================
# UPIICSA-Connect — Verificación de símbolos JNI
#
# Comprueba que cada `external fun` declarada en JniRustCoreBridge.kt tenga su símbolo
# `Java_<paquete>_JniRustCoreBridge_<método>` exportado en la biblioteca Android compilada.
#
# Uso:
#   bash scripts/verificar-simbolos-jni.sh <ruta/a/libupiicsa_core_rs.so>
# =============================================================================
set -euo pipefail

LIB="${1:?Indique la ruta de libupiicsa_core_rs.so}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLIN_FILE="$REPO_ROOT/androidApp/src/main/kotlin/mx/ipn/upiicsa/connect/android/model/nativecore/JniRustCoreBridge.kt"
PREFIX="Java_mx_ipn_upiicsa_connect_android_model_nativecore_JniRustCoreBridge_"

NM="$(command -v llvm-nm || command -v nm)"
EXPORTED="$("$NM" -D --defined-only "$LIB" | awk '{print $NF}')"
METHODS="$(grep -oE 'external fun [A-Za-z0-9]+' "$KOTLIN_FILE" | awk '{print $3}')"

[[ -n "$METHODS" ]] || { echo "No se encontraron métodos external en $KOTLIN_FILE" >&2; exit 1; }

missing=0
for method in $METHODS; do
    if grep -qx "$PREFIX$method" <<< "$EXPORTED"; then
        echo "OK      $method"
    else
        echo "FALTA   $method" >&2
        missing=1
    fi
done

exit "$missing"
