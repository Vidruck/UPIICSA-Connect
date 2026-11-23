# Plan de Ejecución por Fases y Estado de Cumplimiento — UPIICSA-Connect

Este documento controla el avance real del proyecto **UPIICSA-Connect**, detalla los entregables de cada fase y establece el porcentaje verificado de cumplimiento. Toda modificación al código fuente o a los contratos debe reflejarse aquí conforme a [`CONTRIBUTING.md`](../CONTRIBUTING.md).

> **Última actualización:** 2026-11-23 — intervención **IA-006** (ver [`BITACORA_IA.md`](BITACORA_IA.md)).

---

## Resumen Ejecutivo del Estado Actual

| Fase | Nombre de la Fase | Estado | Cumplimiento |
| :--- | :--- | :--- | :---: |
| **Fase 1** | Cimientos, Gobernanza, Arquitectura Base, Tema UPIICSA y Contratos | **Completada** | **100%** |
| **Fase 2** | Core Nativo Rust (Transporte Priorizado, Framing CRC32, Protocolo y DSP *Music Mode*) | **En Progreso (Core 0.2.0 con protocolo, reensamblado, supervisor y jitter; binding JNA en escritorio verificado; JNI Android y códecs pendientes)** | **80%** |
| **Fase 3** | Conectividad Real USB (AOAv2/ADB) y Fallback Inalámbrico (Wi-Fi/Bluetooth) | **En Progreso (Protocolo tipado, reensamblado de flujo, conmutación por latidos y PIN dummy listos; sockets/USB nativos pendientes)** | **40%** |
| **Fase 4** | Resolución de Notificaciones y Portapapeles Bidireccional | **En Progreso (Flujo extremo a extremo con eventos simulados, códec y UIs completas; DBus/WinRT/Listener real pendientes)** | **45%** |
| **Fase 5** | Music Mode y Cámara/Micrófono Virtual para Videollamadas | **En Progreso (Cadena PCM -> DSP -> sumidero con niveles en vivo, jitter buffer y vistas previas; drivers PipeWire/v4l2/WASAPI/DirectShow pendientes)** | **40%** |
| **Fase 6** | Monitor Secundario (Tablet/Android), Pruebas Integrales E2E y Empaquetado | **En Progreso (Configuración y vista previa del monitor, envío de cuadros por vsync, suite de pruebas automatizadas, APK debug y empaquetado declarado)** | **30%** |

**Avance Global (promedio simple de las 6 fases):** **56%** (antes 43%).

---

## Desglose Detallado por Fase

### Fase 1: Cimientos, Gobernanza, Arquitectura Base, Tema UPIICSA y Contratos — **100% Completada**
- [x] Creación de `LICENSE` sin licencia pública (todos los derechos reservados al equipo desarrollador).
- [x] Creación de `CONTRIBUTING.md` con reglas obligatorias para IAs, prohibición de autoría no autorizada, idioma español obligatorio en commits y reglas estrictas de comentarios.
- [x] Estructura modular Kotlin Multiplatform (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `shared`, `desktopApp`, `androidApp`).
- [x] Esquema de colores institucional **UPIICSA - IPN** (`UpiicsaTheme.kt`) con adaptabilidad Light / Dark según preferencia del sistema o selección manual.
- [x] Definición de arquitectura **Ports & Adapters** sin Clases Dios en `desktopApp` (separación de 5 adaptadores independientes para GNU/Linux y 5 para Windows).
- [x] Definición de arquitectura **MVVM** estricta en `androidApp` (`model/`, `viewmodel/`, `ui/`).
- [x] Documentación de capas e infraestructura (`docs/ARQUITECTURA_Y_CAPAS.md`) y catálogo de contratos dummy (`docs/CONTRATOS_DUMMY.md`).
- [x] Creación de bitácora obligatoria de auditoría para intervenciones de IA (`docs/BITACORA_IA.md`).
- [x] *(IA-006)* Gradle Wrapper 8.10.2 versionado (`gradlew`, `gradlew.bat`) y script de entorno GNU/Linux (`scripts/setup-cloud-env.sh`).
- [x] *(IA-006)* Sistema visual ampliado: tipografía, formas, colores semánticos y biblioteca de componentes Compose compartidos (`shared/.../ui/components/`).

### Fase 2: Core Nativo Rust (Señales y Conectividad) — **80% Completada**
- [x] Crate `core-rust` sin dependencias externas (`std`) para compilación determinista y segura.
- [x] Multiplexor de transporte (`transport.rs`) con regla de prioridad: **Cable USB (Opción 1)** sobre **Wi-Fi / Bluetooth (Opción 2)**.
- [x] Empaquetado binario `UpiicsaFrame` con verificación de integridad CRC32 para los canales de servicio.
- [x] Procesador DSP (`signal_dsp.rs`) para normalización de ganancia y empaquetado de buffers PCM en *Music Mode* y encabezados de baja latencia para cámara/micrófono y pantalla.
- [x] Interfaz C-ABI (`ffi_bridge.rs`) probada con `cargo test`.
- [x] *(IA-006)* Canal `SessionControl`, errores tipados `WireFrameError` con códigos FFI estables y límite de 4 MiB por carga útil (protección contra reservas abusivas).
- [x] *(IA-006)* Protocolo de mensajes tipados (`protocol.rs`) compatible byte a byte con `ChannelMessageCodec` de Kotlin (vectores compartidos).
- [x] *(IA-006)* Nuevas funciones FFI: `upiicsa_core_version`, `upiicsa_encode_frame`, `upiicsa_validate_wire_frame`, `upiicsa_derive_pairing_pin` (crate 0.2.0).
- [x] *(IA-006)* Artefacto `.so` / `.dll` cargado en tiempo de ejecución desde JVM Desktop vía **JNA** (`JnaRustCoreBridge`), con prueba de paridad contra la simulación Kotlin.
- [x] *(IA-006)* Pruebas: 24 pruebas unitarias en Rust, `clippy -D warnings` y `rustfmt` sin observaciones.
- [ ] Binding **JNI** en Android (`cargo ndk` -> `jniLibs` + `JniRustCoreBridge`).
- [ ] Códec Opus embebido y compresión H.264/HEVC por hardware para flujos de video de alta tasa de cuadros.

### Fase 3: Conectividad Real USB y Wi-Fi / Bluetooth — **40% Completada**
- [x] Contrato `DeviceTransportOutboundPort` y adaptador `MultiChannelTransportAdapter` con fallback automático.
- [x] Contrato de emparejamiento bidireccional (`DESKTOP_TO_MOBILE` y `MOBILE_TO_DESKTOP`) en modelos compartidos y UIs de control.
- [x] *(IA-006)* `FrameStreamDecoder` (Rust): reensamblado de tramas desde lecturas parciales con resincronización ante basura o corrupción.
- [x] *(IA-006)* `LinkSupervisor` (Rust): conmutación USB -> Wi-Fi -> Bluetooth por latidos vencidos y regreso al medio prioritario con histéresis.
- [x] *(IA-006)* Mensaje `HandshakeHello` y PIN de emparejamiento de 6 dígitos mostrado en escritorio y Android (**derivación dummy no criptográfica**).
- [x] *(IA-006)* El transporte de escritorio serializa cada carga útil como `UpiicsaFrame` real mediante el core (validación en bucle local).
- [ ] Reenvío real de puertos sobre cable USB mediante protocolo `ADB reverse/forward` y `USB Accessory (AOAv2)` en Android.
- [ ] Descubrimiento mDNS/NSD en LAN Wi-Fi con cifrado TLS mutuo (mTLS) y emparejamiento por código PIN/QR criptográfico (ECDH + SAS).
- [ ] Canal de respaldo Bluetooth RFCOMM / L2CAP para control y notificaciones sin red Wi-Fi.
- [ ] Integración de `LinkSupervisor` y `FrameStreamDecoder` en los adaptadores de transporte reales.

### Fase 4: Notificaciones Resolvibles y Portapapeles — **45% Completada**
- [x] Modelos `PhoneNotificationItem` y `NotificationResolutionAction` (Responder en línea, Marcar como leído, Descartar).
- [x] Casos de uso en `DesktopConnectionService` y bandeja interactiva en el panel de escritorio.
- [x] Repositorio `AndroidNotificationBridgeRepository` y ViewModel `AndroidConnectionViewModel`.
- [x] Adaptadores separados `LinuxFreedesktopNotificationAdapter` / `WindowsWinRtNotificationAdapter` y `LinuxClipboardAdapter` / `WindowsWin32ClipboardAdapter`.
- [x] *(IA-006)* Flujo de eventos entrantes del teléfono (`PhoneEventStreamOutboundPort`) procesado por el servicio respetando los interruptores de espejo y sincronización.
- [x] *(IA-006)* Resoluciones y portapapeles viajan codificados con `ChannelMessageCodec` (sin cadenas `"REPLY|id|texto"` vulnerables a separadores).
- [x] *(IA-006)* Pantallas dedicadas de Avisos y Portapapeles (historial por origen) en escritorio y Android.
- [ ] Conexión real al demonio `org.freedesktop.Notifications` (DBus) en GNU/Linux y `Windows.UI.Notifications` (WinRT) en Windows.
- [ ] Implementación de `NotificationListenerService` y despacho real de `PendingIntent` + `RemoteInput` en Android SDK >= 26.
- [ ] Listener real del portapapeles de X11/Wayland, Win32 y `ClipboardManager` de Android.

### Fase 5: Music Mode y Cámara/Micrófono Virtual — **40% Completada**
- [x] Flujo *Music Mode* en dominio, DSP Rust (`process_music_mode_pcm_block`), ViewModel Android y panel de escritorio.
- [x] Adaptadores segregados `LinuxPipeWireAudioSinkAdapter`, `WindowsWasapiAudioSinkAdapter`, `LinuxV4l2CameraMicAdapter` y `WindowsDirectShowCameraMicAdapter`.
- [x] *(IA-006)* `PcmJitterBuffer` (Rust): reordenamiento por secuencia, ocultamiento de pérdidas con silencio y telemetría.
- [x] *(IA-006)* Cadena continua PCM -> DSP del core -> sumidero del S.O. con medidor de nivel en vivo y control de ganancia.
- [x] *(IA-006)* Interruptores independientes de cámara, micrófono y cámara trasera con vista previa y medidor de micrófono.
- [ ] Captura real de audio interno Android mediante `AudioPlaybackCaptureConfiguration` (Android 10+) o proyección de sesión multimedia.
- [ ] Reproducción nativa en tarjeta de sonido PC vía **PipeWire / PulseAudio** (GNU/Linux) y **WASAPI** (Windows).
- [ ] Creación de dispositivo virtual `/dev/video*` (`v4l2loopback`) en Linux y fuente virtual MediaFoundation/DirectShow en Windows usando `CameraX` + `AudioRecord` desde Android.

### Fase 6: Monitor Secundario, Pruebas E2E y Empaquetado — **30% Completada**
- [x] Modelos `SecondaryDisplayConfig`, puertos `OsVirtualDisplayOutboundPort` y adaptadores `LinuxVirtualDisplayAdapter` / `WindowsIddCxDisplayAdapter`.
- [x] *(IA-006)* Pantalla de Monitor Secundario con vista previa, resolución (1280×800 / 1920×1080 / 2560×1600), tasa (30/60/90 fps) y retorno táctil; envío de cuadros por señal `DisplayVsync`.
- [x] *(IA-006)* Suite automatizada: 9 pruebas `shared` (códec y paridad con Rust), 12 pruebas de escritorio (servicio, JNA nativo, **reglas de arquitectura** y **capturas fuera de pantalla** claro/oscuro de las 7 secciones).
- [x] *(IA-006)* App Android ejecutable (`MainActivity`, manifiesto, recursos e ícono adaptable); `assembleDebug` genera el APK (verificado solo por compilación, sin emulador).
- [x] *(IA-006)* Distribuciones nativas declaradas en `desktopApp` (`.deb` y `.msi`).
- [ ] Integración con driver virtual de pantalla (**EVDI / PipeWire Virtual Screen** en Linux; **IddCx** en Windows).
- [ ] Decodificación y renderizado táctil a pantalla completa en tablet/teléfono Android con retorno de eventos táctiles/lápiz al escritorio.
- [ ] Pruebas E2E entre dispositivos físicos, `.AppImage` y firma de `.apk` de liberación.

---

## Criterios de Verificación de Cumplimiento Arquitectónico

| Verificación | Estado Actual | Evidencia |
| :--- | :---: | :--- |
| Separación Ports & Adapters en Escritorio sin Clases Dios | **Cumple** | `ArchitectureRulesTest` (6 reglas automáticas) sobre `desktopApp/src/desktopMain` |
| La UI de escritorio solo depende de puertos de entrada | **Cumple** | `ui/` usa `DesktopUseCases` vía `DesktopPanelController`; la fábrica solo se usa en `bootstrap/` |
| Separación MVVM en Android | **Cumple** | `androidApp/.../{model,viewmodel,ui}/*.kt`; la vista solo observa `StateFlow` y delega eventos |
| Prioridad USB (1ª) sobre Wi-Fi/BT (2ª) | **Cumple** | `transport.rs`, `link_supervisor.rs`, `DesktopSessionAggregate.kt` y pruebas |
| Tema UPIICSA-IPN Light y Dark adaptable | **Cumple** | `UpiicsaTheme.kt` + capturas `desktopApp/build/screenshots/*-{light,dark}.png` |
| Comentarios en español solo por clase/función + etiquetas dummy/técnicas | **Cumple** | Auditoría automatizada de declaraciones sin bloque doc y de comentarios en línea (IA-006) |
| Pruebas unitarias del core Rust (`cargo test`) | **Cumple** | 24 pruebas, `cargo clippy --all-targets -- -D warnings`, `cargo fmt --check` |
| Pruebas Kotlin (`./gradlew :shared:desktopTest :desktopApp:desktopTest`) | **Cumple** | 21 pruebas en verde |
| Compilación Android (`./gradlew :androidApp:assembleDebug`) | **Cumple** | APK debug generado; no se ejecutó en emulador ni dispositivo |
