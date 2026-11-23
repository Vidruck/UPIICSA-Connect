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

---

## Compilación, Ejecución y Pruebas

El repositorio incluye el **Gradle Wrapper 8.10.2**. Requisitos: JDK 17, Android SDK 34 (para configurar los módulos KMP) y Rust estable. En GNU/Linux puede prepararse todo con [`scripts/setup-cloud-env.sh`](scripts/setup-cloud-env.sh); en Windows consulte [`docs/STACK_E_INSTALACION_WINDOWS.md`](docs/STACK_E_INSTALACION_WINDOWS.md).

```bash
# Core nativo Rust (24 pruebas, sin dependencias externas)
cargo test --manifest-path core-rust/Cargo.toml

# Compilar el core como biblioteca compartida y ejecutar el escritorio (lo carga vía JNA)
./gradlew :desktopApp:buildRustCore
./gradlew :desktopApp:run

# Pruebas Kotlin: códec, paridad con Rust, servicio, reglas de arquitectura y capturas de pantalla
./gradlew :shared:desktopTest :desktopApp:desktopTest

# APK debug de Android
./gradlew :androidApp:assembleDebug
```

Las pruebas de escritorio guardan capturas de las 7 secciones en modo claro y oscuro en `desktopApp/build/screenshots/`. Mientras no existan los transportes y drivers reales, los datos en vivo (notificaciones, audio, cámara, latidos) provienen de simuladores documentados en [`docs/CONTRATOS_DUMMY.md`](docs/CONTRATOS_DUMMY.md).
