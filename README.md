# UPIICSA-Connect

Plataforma multiplataforma de convergencia y continuidad entre equipos de escritorio (**Windows** y **GNU/Linux**) y dispositivos **Android** (`minSdk 26` / `targetSdk 34`), diseñada bajo la identidad visual institucional de la **UPIICSA - IPN** con soporte adaptable para **Modo Claro (Light)** y **Modo Oscuro (Dark)**.

> **Aviso Legal:** Todos los derechos reservados al equipo desarrollador. Consulte [`LICENSE`](LICENSE).  
> **Reglas de Contribución:** Consulte [`CONTRIBUTING.md`](CONTRIBUTING.md) antes de realizar cambios o commits.

---

## Capacidades Principales

1. **Panel Bidireccional de Conexión y Opciones:**
   - Conexión iniciada desde el **Ordenador hacia el Móvil** y desde el **Móvil hacia el Ordenador**.
   - Prioridad de transporte determinista:
     1. **Cable USB** (primera opción: mayor ancho de banda y menor latencia vía ADB/AOAv2).
     2. **Wi-Fi LAN / Bluetooth** (segunda opción inalámbrica).
2. **Resolución Remota de Notificaciones:**
   - Espejo de notificaciones del teléfono hacia el escritorio y **resolución directa desde el ordenador** (respuesta rápida en línea vía `RemoteInput`, marcado como leído y descarte) para evitar distracciones y pérdida de flujo de trabajo.
3. **Portapapeles Compartido Bidireccional:**
   - Sincronización inmediata de texto entre PC y Android.
4. **Monitor Secundario:**
   - Permite utilizar un dispositivo Android (por ejemplo, una tablet) como pantalla extendida o secundaria de una PC Windows o GNU/Linux.
5. **Cámara y Micrófono Remotos para Videollamadas:**
   - Convierte la cámara y micrófono del teléfono en webcam y entrada de audio virtual del portátil por cable USB o red inalámbrica.
6. **Music Mode (Audio Offload hacia la PC):**
   - Transmite el flujo de audio del teléfono directamente a la tarjeta de sonido de la PC para aprovechar mejores altavoces o amplificación sin consumir RAM/CPU del ordenador en navegadores o reproductores pesados.

---

## Stack Tecnológico y Arquitectura

- **Core de Señales y Transporte (`core-rust/`):** Implementado en **Rust** (`std`) para procesamiento DSP de baja latencia (*Music Mode*, micrófono, video), empaquetado binario con verificación CRC32 y multiplexing de transporte con prioridad `USB -> Wi-Fi / Bluetooth`, expuesto mediante interfaz FFI (`extern "C"`) para Kotlin Multiplatform.
- **Módulo Compartido (`shared/`):** **Kotlin Multiplatform** con modelos de dominio, códec del protocolo binario, contratos hacia el core Rust, tema oficial **UPIICSA - IPN** (Verde `#006341`, Oro `#F2A900`, Guinda `#6C1D45`) adaptable a Light/Dark y biblioteca de componentes visuales compartidos.
- **Escritorio Windows y GNU/Linux (`desktopApp/`):** Arquitectura **Ports & Adapters (Hexagonal)** con **adaptadores estrictamente segregados por S.O. y por servicio** (evitando Clases Dios), panel con 7 secciones y carga del core Rust vía **JNA**.
- **Móvil Android (`androidApp/`):** Arquitectura **MVVM** (`Model` -> `ViewModel` con `StateFlow` -> `View` en Compose).

---

## Documentación Técnica y Seguimiento

- [`docs/PLAN_DE_FASES.md`](docs/PLAN_DE_FASES.md) — Plan maestro por fases y grado real de cumplimiento actual.
- [`docs/ARQUITECTURA_Y_CAPAS.md`](docs/ARQUITECTURA_Y_CAPAS.md) — Diagramas y especificación de capas, infraestructura y puertos/adaptadores.
- [`docs/CONTRATOS_DUMMY.md`](docs/CONTRATOS_DUMMY.md) — Inventario explícito de contratos dummy y datos hardcodeados para pruebas.
- [`docs/STACK_E_INSTALACION_WINDOWS.md`](docs/STACK_E_INSTALACION_WINDOWS.md) — Stack técnico requerido, manual de instalación en Windows 10/11 y prevención de errores.
- [`docs/BITACORA_IA.md`](docs/BITACORA_IA.md) — Bitácora obligatoria de auditoría para toda intervención de Inteligencia Artificial.
- [`CHANGELOG.md`](CHANGELOG.md) — Registro de cambios y notas de cada liberación.

---

## Descargas e Instalación

Las versiones compiladas se publican en la sección [*Releases*](https://github.com/Vidruck/UPIICSA-Connect/releases) del repositorio. La versión actual es **`v0.1.0-alpha`** (*pre-release*: interfaz y core reales, servicios del sistema y conexión con el teléfono aún simulados; ver [`CHANGELOG.md`](CHANGELOG.md)).

| Plataforma | Artefacto | Instalación |
| :--- | :--- | :--- |
| Debian / Ubuntu | `.deb` | `sudo apt install ./UPIICSA-Connect-<versión>-linux-amd64.deb` |
| Cualquier GNU/Linux | `.AppImage` o `.tar.gz` | `chmod +x UPIICSA-Connect-<versión>-x86_64.AppImage && ./UPIICSA-Connect-<versión>-x86_64.AppImage` |
| Windows 10/11 | `.msi` o `.zip` | Ejecutar el `.msi` (instalación por usuario) o extraer el `.zip` y abrir `UPIICSA-Connect.exe`. |
| Android 8.0+ | `.apk` firmado | Copiar al teléfono e instalar permitiendo orígenes desconocidos. |

Verifique la integridad con `sha256sum -c SHA256SUMS.txt`.

### Proceso de Liberación

1. Integrar los cambios en `dev` mediante Pull Request (la CI de `.github/workflows/ci.yml` debe estar en verde).
2. Actualizar `CHANGELOG.md` con una sección `## [X.Y.Z]` y las versiones de `desktopApp/build.gradle.kts` y `androidApp/build.gradle.kts`.
3. Crear y empujar una etiqueta firmada sobre `dev`: `git tag -s vX.Y.Z -m "..." && git push origin vX.Y.Z`.
4. `.github/workflows/release.yml` compila en GNU/Linux, Windows y Android, firma el APK con los secretos `UPIICSA_*` del repositorio y publica la liberación (las etiquetas con sufijo `-alpha`/`-beta` se marcan como *pre-release*).

Sin publicar, el mismo flujo compila todos los artefactos en cada Pull Request que modifique el empaquetado (`packaging/`, `build.gradle.kts` de las apps, `core-rust/Cargo.toml` o el propio flujo) y puede ejecutarse manualmente desde la pestaña *Actions* una vez que esté en la rama por omisión.

## Enlace Real por Cable USB (experimental)

Por omisión ambas apps usan el teléfono/ordenador simulado. Para probar el enlace real (rama `dev`, aún no incluido en una liberación):

1. Active la **depuración USB** en el teléfono, conéctelo por cable y autorice el equipo (`adb devices` debe mostrarlo como `device`).
2. Inicie el escritorio en modo real: `UPIICSA_ENLACE=real ./gradlew :desktopApp:run` (o `-Dupiicsa.enlace=real`). En **Ajustes** se indica el modo activo.
3. En el escritorio pulse **Buscar** y **Conectar** sobre el teléfono detectado: se crea el túnel `adb reverse tcp:47800 tcp:47800` y el servidor espera 30 s.
4. En Android, active **Enlace real por cable USB** (pestaña Enlace) y pulse **Conectar**. Ambos equipos muestran el mismo PIN.
5. Para espejar las notificaciones reales, en la pestaña **Avisos** pulse **Abrir ajustes de acceso** y conceda el «Acceso a notificaciones» a UPIICSA-Connect.

El servidor escucha únicamente en `127.0.0.1`; el enlace por Wi-Fi se habilitará junto con mTLS (v0.4.0).

Con el enlace real, el escritorio usa además el portapapeles y las notificaciones del sistema:

- **GNU/Linux:** instale `wl-clipboard` (Wayland) o `xclip`/`xsel` (X11) para el portapapeles; sin ellos se usa el de AWT. Las notificaciones requieren `gdbus` (paquete `libglib2.0-bin`) y un demonio de notificaciones (GNOME, KDE, etc.).
- **Windows:** portapapeles por `user32.dll`; las notificaciones aparecen como toasts de *Windows PowerShell*.

*Music Mode* reproduce el audio en la tarjeta de sonido de la PC en ambos modos.

## Compilación, Ejecución y Pruebas

El repositorio incluye el **Gradle Wrapper 8.10.2**. Requisitos: JDK 17, Android SDK 34 (para configurar los módulos KMP) y Rust estable. En GNU/Linux puede prepararse todo con [`scripts/setup-cloud-env.sh`](scripts/setup-cloud-env.sh); en Windows consulte [`docs/STACK_E_INSTALACION_WINDOWS.md`](docs/STACK_E_INSTALACION_WINDOWS.md).

```bash
# Core nativo Rust (24 pruebas; sin dependencias externas en escritorio)
cargo test --manifest-path core-rust/Cargo.toml

# Compilar el core como biblioteca compartida y ejecutar el escritorio (lo carga vía JNA)
./gradlew :desktopApp:buildRustCore
./gradlew :desktopApp:run

# Pruebas Kotlin: códec, paridad con Rust, servicio, reglas de arquitectura y capturas de pantalla
./gradlew :shared:desktopTest :desktopApp:desktopTest

# Además prueba el portapapeles, las notificaciones y el audio reales del equipo (muestra un aviso breve)
UPIICSA_PRUEBAS_SO_REAL=1 ./gradlew :desktopApp:desktopTest

# APK debug de Android (agregue -Pupiicsa.buildRustCore=true para incluir el core vía cargo-ndk)
./gradlew :androidApp:assembleDebug

# Instaladores de escritorio del sistema anfitrión (incluyen el core nativo)
./gradlew :desktopApp:packageDeb          # GNU/Linux
./gradlew :desktopApp:packageMsi          # Windows
bash packaging/linux/construir-appimage.sh 0.1.0   # .AppImage (tras createDistributable)
```

Las pruebas de escritorio guardan capturas de las 7 secciones en modo claro y oscuro en `desktopApp/build/screenshots/`. Mientras no existan los transportes y drivers reales, los datos en vivo (notificaciones, audio, cámara, latidos) provienen de simuladores documentados en [`docs/CONTRATOS_DUMMY.md`](docs/CONTRATOS_DUMMY.md).
