#!/usr/bin/env bash
# =============================================================================
# UPIICSA-Connect — Preparación de entorno de desarrollo en la nube
#
# Instala y configura todo lo necesario para compilar y probar:
#   - core-rust   : Rust estable + rustfmt/clippy + targets Android y Windows + cargo-ndk
#   - shared /
#     desktopApp  : JDK 17 (Temurin) + Gradle 8.10.2 (wrapper) + librerías de Compose Desktop
#   - androidApp  : Android SDK (cmdline-tools, platform-tools, platform 34,
#                   build-tools 34, NDK r26, CMake) + licencias aceptadas
#   - Adaptadores GNU/Linux (PipeWire, DBus, V4L2, portapapeles X11/Wayland, libusb)
#
# Uso:
#   bash scripts/setup-cloud-env.sh [opciones]
#
# Opciones:
#   --skip-apt        No instala paquetes del sistema (sin sudo / imagen ya preparada).
#   --with-emulator   Instala también emulador e imagen de sistema x86_64 API 34.
#   --no-wrapper      No genera el Gradle Wrapper (gradlew) en el repositorio.
#   --verify          Al terminar ejecuta cargo test, compila el .so para Android y
#                     corre ./gradlew assemble de los módulos.
#   -h, --help        Muestra esta ayuda.
#
# Variables sobrescribibles (exportarlas antes de ejecutar):
#   UPIICSA_TOOLS_DIR   (def. $HOME/.upiicsa-tools)   Carpeta de JDK, SDK y Gradle.
#   UPIICSA_REPO_ROOT   (def. autodetección)          Raíz del repositorio clonado.
#   JDK_MAJOR           (def. 17)
#   GRADLE_VERSION      (def. 8.10.2)   Compatible con AGP 8.5.2 y Kotlin 2.0.21.
#   ANDROID_API         (def. 34)
#   ANDROID_BUILD_TOOLS (def. 34.0.0)
#   ANDROID_NDK_VERSION (def. 26.3.11579264)
#   ANDROID_CMAKE       (def. 3.22.1)
#   CMDLINE_TOOLS_BUILD (def. 11076708)
#
# El script es idempotente: puede ejecutarse varias veces sin efectos adversos.
# Las fallas de descarga del Android SDK se reportan como aviso y no abortan el resto.
# =============================================================================
set -euo pipefail

SKIP_APT=0
WITH_EMULATOR=0
GEN_WRAPPER=1
VERIFY=0

for arg in "$@"; do
    case "$arg" in
        --skip-apt)      SKIP_APT=1 ;;
        --with-emulator) WITH_EMULATOR=1 ;;
        --no-wrapper)    GEN_WRAPPER=0 ;;
        --verify)        VERIFY=1 ;;
        -h|--help)       sed -n '2,38p' "$0"; exit 0 ;;
        *) echo "Opción desconocida: $arg" >&2; exit 1 ;;
    esac
done

# [Decisión técnica]: como script de entorno en la nube se ejecuta desde la carpeta que
# contiene los clones, no desde scripts/; se prueban esas ubicaciones antes de la del script.
resolve_repo_root() {
    if [[ -n "${UPIICSA_REPO_ROOT:-}" ]]; then
        echo "$UPIICSA_REPO_ROOT"
    elif [[ -f "$PWD/UPIICSA-Connect/settings.gradle.kts" ]]; then
        echo "$PWD/UPIICSA-Connect"
    elif [[ -f "$PWD/settings.gradle.kts" ]]; then
        echo "$PWD"
    else
        (cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
    fi
}

REPO_ROOT="$(resolve_repo_root)"
TOOLS_DIR="${UPIICSA_TOOLS_DIR:-$HOME/.upiicsa-tools}"
JDK_MAJOR="${JDK_MAJOR:-17}"
GRADLE_VERSION="${GRADLE_VERSION:-8.10.2}"
ANDROID_API="${ANDROID_API:-34}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-34.0.0}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-26.3.11579264}"
ANDROID_CMAKE="${ANDROID_CMAKE:-3.22.1}"
CMDLINE_TOOLS_BUILD="${CMDLINE_TOOLS_BUILD:-11076708}"

JDK_DIR="$TOOLS_DIR/jdk-$JDK_MAJOR"
GRADLE_DIR="$TOOLS_DIR/gradle-$GRADLE_VERSION"
ANDROID_SDK_DIR="$TOOLS_DIR/android-sdk"
ENV_FILE="$HOME/.upiicsa-connect.env"

RUST_TARGETS=(
    aarch64-linux-android
    armv7-linux-androideabi
    x86_64-linux-android
    i686-linux-android
    x86_64-pc-windows-gnu
)

log()  { printf '\033[1;32m[upiicsa]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[upiicsa][aviso]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[upiicsa][error]\033[0m %s\n' "$*" >&2; exit 1; }

ARCH="$(uname -m)"
[[ "$(uname -s)" == "Linux" ]] || die "Este script está pensado para hosts GNU/Linux."
if [[ "$ARCH" != "x86_64" ]]; then
    warn "Host $ARCH detectado: el NDK y build-tools de Android solo se publican para Linux x86_64."
    warn "Rust y el escritorio funcionarán, pero la compilación Android puede fallar."
fi

if [[ $EUID -eq 0 ]]; then SUDO=""; elif command -v sudo >/dev/null; then SUDO="sudo"; else SUDO=""; fi

mkdir -p "$TOOLS_DIR"

# -----------------------------------------------------------------------------
# 1. Paquetes del sistema
# -----------------------------------------------------------------------------
install_system_packages() {
    if [[ $SKIP_APT -eq 1 ]]; then
        log "Omitiendo paquetes del sistema (--skip-apt)."
        return
    fi
    if ! command -v apt-get >/dev/null; then
        warn "apt-get no disponible: instale manualmente los equivalentes de los paquetes listados en este script."
        return
    fi
    if [[ -z "$SUDO" && $EUID -ne 0 ]]; then
        warn "Sin root ni sudo: se omiten paquetes del sistema."
        return
    fi

    log "Instalando paquetes del sistema (apt)..."
    export DEBIAN_FRONTEND=noninteractive
    $SUDO apt-get update -y
    $SUDO apt-get install -y --no-install-recommends \
        ca-certificates curl wget git unzip zip xz-utils tar file jq \
        build-essential pkg-config cmake ninja-build clang lld libssl-dev

    local optional=(
        libpipewire-0.3-dev libspa-0.2-dev pipewire-bin
        libdbus-1-dev libnotify-bin
        libv4l-dev v4l-utils
        libusb-1.0-0-dev
        xclip wl-clipboard
        mingw-w64
        libxrender1 libxtst6 libxi6 libxext6 libx11-6 libxrandr2
        libfontconfig1 libfreetype6 libgl1
        xvfb xauth
    )
    local pkg
    for pkg in "${optional[@]}"; do
        $SUDO apt-get install -y --no-install-recommends "$pkg" >/dev/null 2>&1 \
            || warn "No se pudo instalar '$pkg' (se omite)."
    done
    $SUDO apt-get install -y --no-install-recommends libasound2t64 >/dev/null 2>&1 \
        || $SUDO apt-get install -y --no-install-recommends libasound2 >/dev/null 2>&1 \
        || warn "No se pudo instalar libasound2."
}

# -----------------------------------------------------------------------------
# 2. JDK 17 (Eclipse Temurin) — independiente de la distro
# -----------------------------------------------------------------------------
install_jdk() {
    if [[ -x "$JDK_DIR/bin/java" ]]; then
        log "JDK $JDK_MAJOR ya instalado en $JDK_DIR."
        return
    fi
    local jarch
    case "$ARCH" in
        x86_64)  jarch="x64" ;;
        aarch64) jarch="aarch64" ;;
        *) die "Arquitectura no soportada para el JDK: $ARCH" ;;
    esac
    log "Descargando Eclipse Temurin JDK $JDK_MAJOR ($jarch)..."
    local tmp; tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/jdk.tar.gz" \
        "https://api.adoptium.net/v3/binary/latest/$JDK_MAJOR/ga/linux/$jarch/jdk/hotspot/normal/eclipse"
    mkdir -p "$JDK_DIR"
    tar -xzf "$tmp/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
    rm -rf "$tmp"
}

# -----------------------------------------------------------------------------
# 3. Gradle (distribución local, usada solo para generar el wrapper)
# -----------------------------------------------------------------------------
install_gradle() {
    if [[ -x "$GRADLE_DIR/bin/gradle" ]]; then
        log "Gradle $GRADLE_VERSION ya instalado."
        return
    fi
    log "Descargando Gradle $GRADLE_VERSION..."
    local tmp; tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/gradle.zip" \
        "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    unzip -q "$tmp/gradle.zip" -d "$TOOLS_DIR"
    rm -rf "$tmp"
}

# -----------------------------------------------------------------------------
# 4. Android SDK + NDK
# -----------------------------------------------------------------------------
# [Decisión técnica]: sdkmanager es una aplicación Java que ignora HTTPS_PROXY; detrás de un
# proxy corporativo o de la nube se le pasan host/puerto explícitos y el truststore del sistema.
sdkmanager_proxy_args() {
    local proxy="${HTTPS_PROXY:-${https_proxy:-}}"
    [[ -n "$proxy" ]] || return 0
    local hostport="${proxy#*://}"
    hostport="${hostport%%/*}"
    hostport="${hostport##*@}"
    local host="${hostport%%:*}"
    local port="${hostport##*:}"
    [[ "$port" != "$host" ]] || port=80
    printf '%s\n' "--proxy=http" "--proxy_host=$host" "--proxy_port=$port"
}

install_android_sdk() {
    local sdkmanager="$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
    if [[ ! -x "$sdkmanager" ]]; then
        log "Descargando Android command-line tools ($CMDLINE_TOOLS_BUILD)..."
        local tmp; tmp="$(mktemp -d)"
        curl -fsSL -o "$tmp/cmdline-tools.zip" \
            "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
        unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
        mkdir -p "$ANDROID_SDK_DIR/cmdline-tools"
        rm -rf "$ANDROID_SDK_DIR/cmdline-tools/latest"
        mv "$tmp/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
        rm -rf "$tmp"
    fi

    export JAVA_HOME="$JDK_DIR"
    export ANDROID_HOME="$ANDROID_SDK_DIR"
    export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
    if [[ -f /etc/ssl/certs/java/cacerts ]]; then
        export SDKMANAGER_OPTS="${SDKMANAGER_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts -Djavax.net.ssl.trustStoreType=JKS"
    fi

    local proxy_args=()
    mapfile -t proxy_args < <(sdkmanager_proxy_args)
    [[ ${#proxy_args[@]} -eq 0 ]] || log "sdkmanager usará el proxy ${proxy_args[*]}"

    log "Aceptando licencias del Android SDK..."
    yes | "$sdkmanager" --sdk_root="$ANDROID_SDK_DIR" "${proxy_args[@]}" --licenses >/dev/null || true

    local packages=(
        "platform-tools"
        "platforms;android-$ANDROID_API"
        "build-tools;$ANDROID_BUILD_TOOLS"
        "ndk;$ANDROID_NDK_VERSION"
        "cmake;$ANDROID_CMAKE"
    )
    if [[ $WITH_EMULATOR -eq 1 ]]; then
        packages+=("emulator" "system-images;android-$ANDROID_API;google_apis;x86_64")
    fi

    log "Instalando paquetes del SDK: ${packages[*]}"
    "$sdkmanager" --sdk_root="$ANDROID_SDK_DIR" "${proxy_args[@]}" --install "${packages[@]}" \
        || warn "No se pudieron instalar todos los paquetes del Android SDK; Rust y escritorio siguen disponibles."

    if [[ $WITH_EMULATOR -eq 1 ]]; then
        local avdmanager="$ANDROID_SDK_DIR/cmdline-tools/latest/bin/avdmanager"
        if ! "$avdmanager" list avd 2>/dev/null | grep -q "upiicsa_api$ANDROID_API"; then
            log "Creando AVD upiicsa_api$ANDROID_API..."
            echo "no" | "$avdmanager" create avd -n "upiicsa_api$ANDROID_API" \
                -k "system-images;android-$ANDROID_API;google_apis;x86_64" --force \
                || warn "No se pudo crear el AVD."
        fi
        [[ -e /dev/kvm ]] || warn "/dev/kvm no disponible: el emulador será muy lento o no arrancará en esta VM."
    fi
}

# -----------------------------------------------------------------------------
# 5. Rust (rustup) + targets + cargo-ndk
# -----------------------------------------------------------------------------
install_rust() {
    if ! command -v rustup >/dev/null && [[ ! -x "$HOME/.cargo/bin/rustup" ]]; then
        log "Instalando rustup + toolchain estable..."
        curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs \
            | sh -s -- -y --default-toolchain stable --profile minimal
    fi
    # shellcheck disable=SC1091
    source "$HOME/.cargo/env"

    rustup toolchain install stable --profile minimal
    rustup default stable
    rustup component add rustfmt clippy
    log "Agregando targets de Rust: ${RUST_TARGETS[*]}"
    rustup target add "${RUST_TARGETS[@]}"

    if ! command -v cargo-ndk >/dev/null; then
        log "Instalando cargo-ndk..."
        cargo install cargo-ndk --locked || warn "No se pudo instalar cargo-ndk."
    fi
}

# -----------------------------------------------------------------------------
# 6. Variables de entorno persistentes
# -----------------------------------------------------------------------------
write_env_file() {
    local ndk="$ANDROID_SDK_DIR/ndk/$ANDROID_NDK_VERSION"
    local ndk_bin="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
    local min_sdk=26

    log "Escribiendo variables de entorno en $ENV_FILE"
    cat > "$ENV_FILE" <<EOF
# Generado por UPIICSA-Connect/scripts/setup-cloud-env.sh — no editar a mano.
export UPIICSA_TOOLS_DIR="$TOOLS_DIR"
export UPIICSA_CONNECT_HOME="$REPO_ROOT"

# Java / Gradle
export JAVA_HOME="$JDK_DIR"
export GRADLE_HOME="$GRADLE_DIR"
export GRADLE_OPTS="\${GRADLE_OPTS:--Dorg.gradle.daemon=true -Dorg.gradle.parallel=true}"

# Android SDK / NDK
export ANDROID_HOME="$ANDROID_SDK_DIR"
export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
export ANDROID_NDK_HOME="$ndk"
export ANDROID_NDK_ROOT="$ndk"
export ANDROID_NDK="$ndk"
export ANDROID_USER_HOME="\$HOME/.android"

# Rust → Android: linkers del NDK para 'cargo build --target <triple>' sin cargo-ndk (minSdk $min_sdk)
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ndk_bin/aarch64-linux-android${min_sdk}-clang"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$ndk_bin/armv7a-linux-androideabi${min_sdk}-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$ndk_bin/x86_64-linux-android${min_sdk}-clang"
export CARGO_TARGET_I686_LINUX_ANDROID_LINKER="$ndk_bin/i686-linux-android${min_sdk}-clang"
export CC_aarch64_linux_android="$ndk_bin/aarch64-linux-android${min_sdk}-clang"
export CC_armv7_linux_androideabi="$ndk_bin/armv7a-linux-androideabi${min_sdk}-clang"
export CC_x86_64_linux_android="$ndk_bin/x86_64-linux-android${min_sdk}-clang"
export CC_i686_linux_android="$ndk_bin/i686-linux-android${min_sdk}-clang"
export AR_aarch64_linux_android="$ndk_bin/llvm-ar"
export AR_armv7_linux_androideabi="$ndk_bin/llvm-ar"
export AR_x86_64_linux_android="$ndk_bin/llvm-ar"
export AR_i686_linux_android="$ndk_bin/llvm-ar"

# Rust → Windows (cross-compilación del core para desktop Windows)
export CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER="x86_64-w64-mingw32-gcc"

# Compose Desktop sin pantalla física (usar con: Xvfb :99 &)
export DISPLAY="\${DISPLAY:-:99}"

# PATH
[ -f "\$HOME/.cargo/env" ] && . "\$HOME/.cargo/env"
export PATH="\$JAVA_HOME/bin:\$GRADLE_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator:\$PATH"
EOF

    local line="[ -f \"$ENV_FILE\" ] && . \"$ENV_FILE\""
    for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
        [[ -f "$rc" || "$rc" == "$HOME/.bashrc" ]] || continue
        touch "$rc"
        grep -qF "$ENV_FILE" "$rc" || printf '\n# UPIICSA-Connect\n%s\n' "$line" >> "$rc"
    done
}

# -----------------------------------------------------------------------------
# 7. Configuración del repositorio (local.properties + Gradle Wrapper)
# -----------------------------------------------------------------------------
configure_repo() {
    if [[ ! -f "$REPO_ROOT/settings.gradle.kts" ]]; then
        warn "No se encontró el repositorio en $REPO_ROOT; se omite local.properties y gradlew."
        return
    fi
    log "Escribiendo $REPO_ROOT/local.properties (ignorado por git)."
    printf 'sdk.dir=%s\n' "$ANDROID_SDK_DIR" > "$REPO_ROOT/local.properties"

    if [[ $GEN_WRAPPER -eq 1 && ! -x "$REPO_ROOT/gradlew" ]]; then
        log "Generando Gradle Wrapper $GRADLE_VERSION..."
        local tmp; tmp="$(mktemp -d)"
        touch "$tmp/settings.gradle.kts"
        JAVA_HOME="$JDK_DIR" "$GRADLE_DIR/bin/gradle" -q -p "$tmp" wrapper \
            --gradle-version "$GRADLE_VERSION" --distribution-type bin
        cp "$tmp/gradlew" "$tmp/gradlew.bat" "$REPO_ROOT/"
        mkdir -p "$REPO_ROOT/gradle/wrapper"
        cp "$tmp/gradle/wrapper/"* "$REPO_ROOT/gradle/wrapper/"
        chmod +x "$REPO_ROOT/gradlew"
        rm -rf "$tmp"
    fi
}

# -----------------------------------------------------------------------------
# 8. Verificación opcional
# -----------------------------------------------------------------------------
verify() {
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    log "Versiones instaladas:"
    java -version 2>&1 | head -1
    rustc --version
    cargo --version
    cargo ndk --version 2>/dev/null || true
    "$ANDROID_HOME/platform-tools/adb" version 2>/dev/null | head -1 \
        || warn "adb no disponible (platform-tools sin instalar)."

    [[ $VERIFY -eq 1 ]] || return 0

    log "cargo test (core-rust)..."
    cargo test --manifest-path "$REPO_ROOT/core-rust/Cargo.toml"

    log "Compilando core-rust para Android (arm64-v8a, x86_64)..."
    (cd "$REPO_ROOT/core-rust" && cargo ndk -t arm64-v8a -t x86_64 -P 26 build)

    if [[ -x "$REPO_ROOT/gradlew" ]]; then
        log "Gradle: compilando shared, desktopApp y androidApp..."
        (cd "$REPO_ROOT" && ./gradlew --no-configuration-cache \
            :shared:assemble :desktopApp:desktopJar :androidApp:assembleDebug)
    else
        warn "No hay gradlew; se omite la compilación Gradle."
    fi
}

main() {
    install_system_packages
    install_jdk
    install_gradle
    install_android_sdk
    install_rust
    write_env_file
    configure_repo
    verify

    cat <<EOF

$(log "Entorno listo.")
  Cargue las variables en esta sesión:   source $ENV_FILE
  (Las nuevas shells las cargan automáticamente desde ~/.bashrc / ~/.zshrc)

  Comandos útiles:
    cargo test --manifest-path core-rust/Cargo.toml
    (cd core-rust && cargo ndk -t arm64-v8a -t x86_64 -P 26 -o ../androidApp/src/main/jniLibs build --release)
    ./gradlew :androidApp:assembleDebug
    Xvfb :99 & ./gradlew :desktopApp:run
EOF
}

main
