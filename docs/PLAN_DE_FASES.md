# Plan de Ejecución por Fases y Estado de Cumplimiento — UPIICSA-Connect

Este documento controla el avance real del proyecto **UPIICSA-Connect**, detalla los entregables de cada fase y establece el porcentaje verificado de cumplimiento. Toda modificación al código fuente o a los contratos debe reflejarse aquí conforme a [`CONTRIBUTING.md`](../CONTRIBUTING.md).

---

## Resumen Ejecutivo del Estado Actual

| Fase | Nombre de la Fase | Estado | Cumplimiento |
| :--- | :--- | :--- | :---: |
| **Fase 1** | Cimientos, Gobernanza, Arquitectura Base, Tema UPIICSA y Contratos | **Completada** | **100%** |
| **Fase 2** | Core Nativo Rust (Transporte Priorizado, Framing CRC32 y DSP *Music Mode*) | **En Progreso (Núcleo lógico y FFI verificados; binding JNI real pendiente)** | **65%** |
| **Fase 3** | Conectividad Real USB (AOAv2/ADB) y Fallback Inalámbrico (Wi-Fi/Bluetooth) | **En Progreso (Contratos y simuladores dummy listos; sockets/USB nativos pendientes)** | **25%** |
| **Fase 4** | Resolución de Notificaciones y Portapapeles Bidireccional | **En Progreso (Flujos de dominio, MVVM y adaptadores base listos; DBus/WinRT/Listener real pendientes)** | **30%** |
| **Fase 5** | Music Mode y Cámara/Micrófono Virtual para Videollamadas | **En Progreso (DSP PCM en Rust y puertos/adaptadores definidos; drivers PipeWire/v4l2/WASAPI/DirectShow pendientes)** | **25%** |
| **Fase 6** | Monitor Secundario (Tablet/Android), Pruebas Integrales E2E y Empaquetado | **Planificada (Contratos de dominio y adaptadores IddCx/EVDI estructurados)** | **15%** |

**Avance Global Ponderado del Proyecto (Base Arquitectónica + Core Rust + Contratos):** **43%**

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

### Fase 2: Core Nativo Rust (Señales y Conectividad) — **65% Completada**
- [x] Crate `core-rust` sin dependencias externas (`std`) para compilación determinista y segura.
- [x] Multiplexor de transporte (`transport.rs`) con regla de prioridad: **Cable USB (Opción 1)** sobre **Wi-Fi / Bluetooth (Opción 2)**.
- [x] Empaquetado binario `UpiicsaFrame` con verificación de integridad CRC32 para los 5 canales de servicio.
- [x] Procesador DSP (`signal_dsp.rs`) para normalización de ganancia y empaquetado de buffers PCM en *Music Mode* y encabezados de baja latencia para cámara/micrófono y pantalla.
- [x] Interfaz C-ABI (`ffi_bridge.rs`) probada con `cargo test`.
- [ ] Generación del artefacto compilado `.so` / `.dll` integrado con `JNI` / `JNA` en tiempo de ejecución desde JVM Desktop y Android NDK.
- [ ] Códec Opus embebido y compresión H.264/HEVC por hardware para flujos de video de alta tasa de cuadros.

### Fase 3: Conectividad Real USB y Wi-Fi / Bluetooth — **25% Completada**
- [x] Contrato `DeviceTransportOutboundPort` y adaptador `MultiChannelTransportAdapter` con fallback automático.
- [x] Contrato de emparejamiento bidireccional (`DESKTOP_TO_MOBILE` y `MOBILE_TO_DESKTOP`) en modelos compartidos y UIs de control.
- [ ] Reenvío real de puertos sobre cable USB mediante protocolo `ADB reverse/forward` y `USB Accessory (AOAv2)` en Android.
- [ ] Descubrimiento mDNS/NSD en LAN Wi-Fi con cifrado TLS mutuo (mTLS) y emparejamiento por código PIN/QR.
- [ ] Canal de respaldo Bluetooth RFCOMM / L2CAP para control y notificaciones sin red Wi-Fi.

### Fase 4: Notificaciones Resolvibles y Portapapeles — **30% Completada**
- [x] Modelos `PhoneNotificationItem` y `NotificationResolutionAction` (Responder en línea, Marcar como leído, Descartar).
- [x] Casos de uso en `DesktopConnectionService` y bandeja interactiva en `DesktopControlPanelScreen`.
- [x] Repositorio `AndroidNotificationBridgeRepository` y ViewModel `AndroidConnectionViewModel`.
- [x] Adaptadores separados `LinuxFreedesktopNotificationAdapter` / `WindowsWinRtNotificationAdapter` y `LinuxClipboardAdapter` / `WindowsWin32ClipboardAdapter`.
- [ ] Conexión real al demonio `org.freedesktop.Notifications` (DBus) en GNU/Linux y `Windows.UI.Notifications` (WinRT) en Windows.
- [ ] Implementación de `NotificationListenerService` y despacho real de `PendingIntent` + `RemoteInput` en Android SDK >= 26.
- [ ] Listener real del portapapeles de X11/Wayland, Win32 y `ClipboardManager` de Android.

### Fase 5: Music Mode y Cámara/Micrófono Virtual — **25% Completada**
- [x] Flujo *Music Mode* en dominio, DSP Rust (`process_music_mode_pcm_block`), ViewModel Android y panel de escritorio.
- [x] Adaptadores segregados `LinuxPipeWireAudioSinkAdapter`, `WindowsWasapiAudioSinkAdapter`, `LinuxV4l2CameraMicAdapter` y `WindowsDirectShowCameraMicAdapter`.
- [ ] Captura real de audio interno Android mediante `AudioPlaybackCaptureConfiguration` (Android 10+) o proyección de sesión multimedia.
- [ ] Reproducción nativa en tarjeta de sonido PC vía **PipeWire / PulseAudio** (GNU/Linux) y **WASAPI** (Windows).
- [ ] Creación de dispositivo virtual `/dev/video*` (`v4l2loopback`) en Linux y fuente virtual MediaFoundation/DirectShow en Windows usando `CameraX` + `AudioRecord` desde Android.

### Fase 6: Monitor Secundario, Pruebas E2E y Empaquetado — **15% Completada**
- [x] Modelos `SecondaryDisplayConfig`, puertos `OsVirtualDisplayOutboundPort` y adaptadores `LinuxVirtualDisplayAdapter` / `WindowsIddCxDisplayAdapter`.
- [ ] Integración con driver virtual de pantalla (**EVDI / PipeWire Virtual Screen** en Linux; **IddCx** en Windows).
- [ ] Decodificación y renderizado táctil a pantalla completa en tablet/teléfono Android con retorno de eventos táctiles/lápiz al escritorio.
- [ ] Suite completa de pruebas de integración y empaquetado de instaladores (`.AppImage` / `.deb`, `.msi` y `.apk`).

---

## Criterios de Verificación de Cumplimiento Arquitectónico

| Verificación | Estado Actual | Evidencia |
| :--- | :---: | :--- |
| Separación Ports & Adapters en Escritorio sin Clases Dios | **Cumple** | `desktopApp/.../ports/` y `adapters/os/{linux,windows}/*.kt` |
| Separación MVVM en Android | **Cumple** | `androidApp/.../{model,viewmodel,ui}/*.kt` |
| Prioridad USB (1ª) sobre Wi-Fi/BT (2ª) | **Cumple** | `core-rust/src/transport.rs`, `DesktopSessionAggregate.kt` |
| Tema UPIICSA-IPN Light y Dark adaptable | **Cumple** | `shared/.../ui/theme/UpiicsaTheme.kt` |
| Comentarios en español solo por clase/función + etiquetas dummy/técnicas | **Cumple** | Auditoría sobre `.rs` y `.kt` |
| Pruebas unitarias del core Rust (`cargo test`) | **Cumple** | Ejecutado satisfactoriamente en el entorno |
