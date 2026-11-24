# Plan de Ejecución por Fases y Estado de Cumplimiento — UPIICSA-Connect

Este documento controla el avance real del proyecto **UPIICSA-Connect**, detalla los entregables de cada fase y establece el porcentaje verificado de cumplimiento. Toda modificación al código fuente o a los contratos debe reflejarse aquí conforme a [`CONTRIBUTING.md`](../CONTRIBUTING.md).

> **Última actualización:** 2026-11-24 — intervenciones **IA-007** a **IA-011** (ver [`BITACORA_IA.md`](BITACORA_IA.md)). Primera liberación: **`v0.1.0-alpha`** (ver [`CHANGELOG.md`](../CHANGELOG.md)).

---

## Resumen Ejecutivo del Estado Actual

| Fase | Nombre de la Fase | Estado | Cumplimiento |
| :--- | :--- | :--- | :---: |
| **Fase 1** | Cimientos, Gobernanza, Arquitectura Base, Tema UPIICSA y Contratos | **Completada** | **100%** |
| **Fase 2** | Core Nativo Rust (Transporte Priorizado, Framing CRC32, Protocolo y DSP *Music Mode*) | **En Progreso (Core 0.3.0; binding JNA en escritorio y JNI en Android empaquetados en los instaladores; códecs Opus/H.264 pendientes)** | **90%** |
| **Fase 3** | Conectividad Real USB (AOAv2/ADB) y Fallback Inalámbrico (Wi-Fi/Bluetooth) | **En Progreso (Enlace real TCP por cable USB con `adb reverse`, reensamblado y latidos en escritorio y Android, probado E2E en bucle local; saludo con versión de protocolo; Wi-Fi/mTLS, Bluetooth y AOAv2 pendientes)** | **65%** |
| **Fase 4** | Resolución de Notificaciones y Portapapeles Bidireccional | **En Progreso (Portapapeles y notificaciones reales en GNU/Linux, Windows y Android con el enlace real; en Android, compilado y probado en la JVM pero sin probar en un dispositivo; botones de acción en la notificación del sistema del escritorio pendientes)** | **75%** |
| **Fase 5** | Music Mode y Cámara/Micrófono Virtual para Videollamadas | **En Progreso (Cadena PCM -> DSP -> tarjeta de sonido real de la PC con Java Sound; captura en Android y cámara/micrófono virtuales pendientes)** | **50%** |
| **Fase 6** | Monitor Secundario (Tablet/Android), Pruebas Integrales E2E y Empaquetado | **En Progreso (CI en GNU/Linux, Windows y Android; liberación automatizada `.deb`/`.AppImage`/`.msi`/portables/APK firmado; drivers de pantalla y E2E con dispositivos pendientes)** | **45%** |

**Avance Global (promedio simple de las 6 fases):** **71%** (antes 69%).

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

### Fase 2: Core Nativo Rust (Señales y Conectividad) — **90% Completada**
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
- [x] *(IA-007)* Binding **JNI** en Android: `core-rust/src/jni_bridge.rs` (solo `target_os = "android"`, crate `jni` 0.21) + `JniRustCoreBridge` / `AndroidNativeCoreLoader`; `cargo ndk` (arm64-v8a, armeabi-v7a, x86, x86_64) integrado en Gradle con `-Pupiicsa.buildRustCore=true` y verificación automática de símbolos (`scripts/verificar-simbolos-jni.sh`). Crate 0.3.0.
- [x] *(IA-007)* El binario del core se empaqueta en los instaladores de escritorio (`appResources`) y `NativeCoreLoader` lo localiza en `compose.application.resources.dir`.
- [ ] Códec Opus embebido y compresión H.264/HEVC por hardware para flujos de video de alta tasa de cuadros.

### Fase 3: Conectividad Real USB y Wi-Fi / Bluetooth — **65% Completada**
- [x] Contrato `DeviceTransportOutboundPort` y adaptador `MultiChannelTransportAdapter` con fallback automático.
- [x] Contrato de emparejamiento bidireccional (`DESKTOP_TO_MOBILE` y `MOBILE_TO_DESKTOP`) en modelos compartidos y UIs de control.
- [x] *(IA-006)* `FrameStreamDecoder` (Rust): reensamblado de tramas desde lecturas parciales con resincronización ante basura o corrupción.
- [x] *(IA-006)* `LinkSupervisor` (Rust): conmutación USB -> Wi-Fi -> Bluetooth por latidos vencidos y regreso al medio prioritario con histéresis.
- [x] *(IA-006)* Mensaje `HandshakeHello` y PIN de emparejamiento de 6 dígitos mostrado en escritorio y Android (**derivación dummy no criptográfica**).
- [x] *(IA-006)* El transporte de escritorio serializa cada carga útil como `UpiicsaFrame` real mediante el core (validación en bucle local).
- [x] *(IA-008)* Enlace real por cable USB con `adb reverse tcp:47800`: servidor TCP de escritorio **solo en loopback** (`TcpLinkSession`, `TcpLinkTransportAdapter`, `AdbDeviceBridge`) y cliente Android (`AndroidSocketLinkRepository`), seleccionable con `-Dupiicsa.enlace=real` / `UPIICSA_ENLACE=real` y el interruptor "Enlace real por cable USB" en Android.
- [x] *(IA-008)* Canal compartido `FrameSocketChannel` (conjunto `jvmCommonMain`, mismo código en escritorio y Android) y `FrameReassembler` (paridad Kotlin de `FrameStreamDecoder` con los mismos casos de prueba).
- [x] *(IA-008)* Prueba E2E en bucle local JVM (`TcpLinkLoopbackTest`): saludo + PIN, latidos con latencia, notificación entrante, respuesta, portapapeles en ambos sentidos y cierre; pruebas JVM del repositorio Android sin emulador.
- [x] *(IA-010)* Interruptor «Enlace real por cable USB» en Ajustes del escritorio, guardado en la configuración del usuario (la variable `UPIICSA_ENLACE` sigue teniendo prioridad).
- [ ] Prueba manual con teléfono físico por USB (requiere dispositivo; no ejecutada).
- [ ] `USB Accessory (AOAv2)` en Android como alternativa a ADB.
- [ ] Descubrimiento mDNS/NSD en LAN Wi-Fi con cifrado TLS mutuo (mTLS) y emparejamiento por código PIN/QR criptográfico (ECDH + SAS).
- [ ] Canal de respaldo Bluetooth RFCOMM / L2CAP para control y notificaciones sin red Wi-Fi.
- [x] *(IA-008)* Reensamblado de flujo y vigilancia de latidos (umbral de 3 s de `LinkSupervisorConfig`) integrados en los adaptadores reales; el cierre del enlace emite `PhoneEvent.LinkClosed` y finaliza la sesión.
- [ ] Conmutación entre medios con `LinkSupervisor` (requiere los transportes Wi-Fi/Bluetooth).
- [x] *(IA-010)* Versión de protocolo en `HandshakeHello` (`PROTOCOL_VERSION = 1` en Rust y Kotlin, vector binario compartido): si el otro extremo usa otra versión, el escritorio y Android cierran el enlace con un motivo legible.

### Fase 4: Notificaciones Resolvibles y Portapapeles — **75% Completada**
- [x] Modelos `PhoneNotificationItem` y `NotificationResolutionAction` (Responder en línea, Marcar como leído, Descartar).
- [x] Casos de uso en `DesktopConnectionService` y bandeja interactiva en el panel de escritorio.
- [x] Repositorio `AndroidNotificationBridgeRepository` y ViewModel `AndroidConnectionViewModel`.
- [x] Adaptadores separados `LinuxFreedesktopNotificationAdapter` / `WindowsWinRtNotificationAdapter` y `LinuxClipboardAdapter` / `WindowsWin32ClipboardAdapter`.
- [x] *(IA-006)* Flujo de eventos entrantes del teléfono (`PhoneEventStreamOutboundPort`) procesado por el servicio respetando los interruptores de espejo y sincronización.
- [x] *(IA-006)* Resoluciones y portapapeles viajan codificados con `ChannelMessageCodec` (sin cadenas `"REPLY|id|texto"` vulnerables a separadores).
- [x] *(IA-006)* Pantallas dedicadas de Avisos y Portapapeles (historial por origen) en escritorio y Android.
- [x] *(IA-008)* Mensaje de protocolo `NotificationPosted` (`0x13`, canal 1) en Rust y Kotlin con vector binario compartido: las notificaciones del teléfono, las respuestas y el portapapeles viajan por el enlace real.
- [x] *(IA-009)* Publicación real en el escritorio: `org.freedesktop.Notifications` por D-Bus con `gdbus` (GNU/Linux) y toasts `Windows.UI.Notifications` con PowerShell (Windows); la notificación se reemplaza si llega de nuevo y se retira al resolverla desde la PC. El contenido del teléfono viaja escapado y sin shell.
- [ ] Botones de acción (Responder / Marcar como leído / Descartar) dentro de la notificación del sistema (señal `ActionInvoked` de D-Bus y activación de toasts).
- [x] *(IA-010)* `UpiicsaNotificationListenerService` (Android): captura con filtros (app propia, persistentes, resúmenes de grupo), respuesta en línea con `RemoteInput` (incluidas acciones `WearableExtender`), «marcar como leída» semántica (Android 9+) y descarte; tarjeta para conceder el acceso a notificaciones. **Compilado, con lint y pruebas JVM; sin probar en dispositivo.**
- [x] *(IA-009)* Portapapeles real del escritorio: `wl-clipboard` (Wayland), `xclip`/`xsel` (X11) o AWT como respaldo en GNU/Linux; `user32.dll` vía JNA en Windows. Vigilancia cada 1 s que envía al teléfono lo copiado en la PC durante la sesión (sin reenviar lo copiado antes de conectar ni lo recibido del teléfono).
- [x] *(IA-010)* `ClipboardManager` real en Android con el enlace real: el texto de la PC se copia al teléfono y lo copiado en el teléfono (con la app en primer plano, límite de Android 10+) se envía a la PC sin eco. **Sin probar en dispositivo.**
- [x] *(IA-011)* Relectura automática del portapapeles de Android al regresar la app a primer plano (`onResume`), botón explícito **«Enviar portapapeles del teléfono»**, captura segura de `SecurityException` en `PendingIntent` y prueba de integración JVM del `AndroidConnectionViewModel` sobre socket loopback.

### Fase 5: Music Mode y Cámara/Micrófono Virtual — **50% Completada**
- [x] Flujo *Music Mode* en dominio, DSP Rust (`process_music_mode_pcm_block`), ViewModel Android y panel de escritorio.
- [x] Adaptadores segregados `LinuxPipeWireAudioSinkAdapter`, `WindowsWasapiAudioSinkAdapter`, `LinuxV4l2CameraMicAdapter` y `WindowsDirectShowCameraMicAdapter`.
- [x] *(IA-006)* `PcmJitterBuffer` (Rust): reordenamiento por secuencia, ocultamiento de pérdidas con silencio y telemetría.
- [x] *(IA-006)* Cadena continua PCM -> DSP del core -> sumidero del S.O. con medidor de nivel en vivo y control de ganancia.
- [x] *(IA-006)* Interruptores independientes de cámara, micrófono y cámara trasera con vista previa y medidor de micrófono.
- [ ] Captura real de audio interno Android mediante `AudioPlaybackCaptureConfiguration` (Android 10+) o proyección de sesión multimedia.
- [x] *(IA-009)* Reproducción real en la tarjeta de sonido de la PC con `javax.sound.sampled` (ALSA/PipeWire en GNU/Linux, DirectSound/WASAPI en Windows) en un hilo propio con cola acotada que descarta bloques atrasados sin frenar el enlace.
- [ ] Creación de dispositivo virtual `/dev/video*` (`v4l2loopback`) en Linux y fuente virtual MediaFoundation/DirectShow en Windows usando `CameraX` + `AudioRecord` desde Android.

### Fase 6: Monitor Secundario, Pruebas E2E y Empaquetado — **45% Completada**
- [x] Modelos `SecondaryDisplayConfig`, puertos `OsVirtualDisplayOutboundPort` y adaptadores `LinuxVirtualDisplayAdapter` / `WindowsIddCxDisplayAdapter`.
- [x] *(IA-006)* Pantalla de Monitor Secundario con vista previa, resolución (1280×800 / 1920×1080 / 2560×1600), tasa (30/60/90 fps) y retorno táctil; envío de cuadros por señal `DisplayVsync`.
- [x] *(IA-006)* Suite automatizada: 9 pruebas `shared` (códec y paridad con Rust), 12 pruebas de escritorio (servicio, JNA nativo, **reglas de arquitectura** y **capturas fuera de pantalla** claro/oscuro de las 7 secciones).
- [x] *(IA-006)* App Android ejecutable (`MainActivity`, manifiesto, recursos e ícono adaptable); `assembleDebug` genera el APK (verificado solo por compilación, sin emulador).
- [x] *(IA-006)* Distribuciones nativas declaradas en `desktopApp` (`.deb` y `.msi`).
- [ ] Integración con driver virtual de pantalla (**EVDI / PipeWire Virtual Screen** en Linux; **IddCx** en Windows).
- [ ] Decodificación y renderizado táctil a pantalla completa en tablet/teléfono Android con retorno de eventos táctiles/lápiz al escritorio.
- [x] *(IA-007)* Integración continua (`.github/workflows/ci.yml`): core Rust (fmt, clippy, pruebas, clippy y símbolos JNI para Android), pruebas Kotlin en **GNU/Linux y Windows** con el binario nativo, APK debug y lint de Android.
- [x] *(IA-007)* Liberación automatizada (`.github/workflows/release.yml`) por etiqueta `v*`: `.deb`, `.AppImage`, `.tar.gz` (GNU/Linux), `.msi`, `.zip` (Windows), **APK firmado** con almacén en secretos del repositorio y `SHA256SUMS.txt`.
- [x] *(IA-007)* Íconos de instaladores (`packaging/icons/`) y entrada de escritorio GNU/Linux; pruebas de escritorio en modo `headless` (sin servidor X).
- [ ] Pruebas E2E entre dispositivos físicos (teléfono/tablet reales) y prueba de humo de la app Android en dispositivo.

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
| Pruebas unitarias del core Rust (`cargo test`) | **Cumple** | 24 pruebas, `cargo clippy --all-targets -- -D warnings` (anfitrión y `aarch64-linux-android`), `cargo fmt --check` |
| Pruebas Kotlin (`./gradlew :shared:desktopTest :desktopApp:desktopTest`) | **Cumple** | 21 pruebas en verde |
| Compilación Android (`./gradlew :androidApp:assembleDebug`) | **Cumple** | APK debug y de liberación firmado con `libupiicsa_core_rs.so` en 4 ABI; lint sin errores; no se ejecutó en emulador ni dispositivo |
| Instaladores con core nativo | **Cumple** | `.deb`/`.AppImage` verificados localmente (binario incluido y cargado: `native=true version=0.3.0`); `.msi` verificado en el runner Windows de CI |

---

## Hoja de Ruta de Pendientes por Versión

Ordena los entregables aún sin marcar (`[ ]`) en versiones incrementales. Cada versión se libera con una etiqueta `vX.Y.Z[-alpha|-beta]` sobre `dev` (ver `.github/workflows/release.yml`). Los simuladores (`DummyPhoneEventStreamAdapter`, repositorios Android) se conservan como modo por omisión hasta que el reemplazo real se verifique.

| Versión | Fase(s) | Entregables | Puertos / Adaptadores / Repositorios afectados | Criterio de verificación |
| :--- | :--- | :--- | :--- | :--- |
| **v0.1.0-alpha** *(liberada en IA-007)* | 2, 6 | Binding JNI en Android, core nativo empaquetado, CI y liberación automatizada multiplataforma, APK firmado. | `JniRustCoreBridge`, `NativeCoreLoader`, `desktopApp/build.gradle.kts`, `.github/workflows/*` | CI en verde; artefactos publicados con `SHA256SUMS.txt`. |
| **v0.2.0** *(código completo en IA-008 e IA-010; falta la prueba con teléfono físico)* | 3 | Enlace real por TCP: servidor en escritorio, túnel USB con `adb reverse`, cliente en Android; reensamblado y latidos *(hecho)*; saludo con versión de protocolo *(hecho en IA-010)*; prueba con teléfono físico *(pendiente)*. El descubrimiento mDNS/NSD en LAN se mueve a v0.4.0 para no exponer el puerto sin mTLS. | `DeviceTransportOutboundPort` (nuevo `TcpLinkServerAdapter`, `AdbReverseTunnelAdapter`), `PhoneEventStreamOutboundPort` (`SocketPhoneEventStreamAdapter`), `AndroidDeviceRepository` (+ `AndroidSocketLinkRepository`) | Prueba E2E en bucle local JVM (escritorio + cliente que actúa como teléfono): saludo, PIN, latidos, notificación, respuesta, portapapeles y desconexión; prueba manual con teléfono físico por USB. |
| **v0.3.0** *(escritorio en IA-009, Android en IA-010; falta validar en dispositivo)* | 4 | Portapapeles real (`wl-clipboard`/`xclip`, Win32 vía JNA, `ClipboardManager`) y notificaciones reales (D-Bus `org.freedesktop.Notifications`, toasts WinRT, `NotificationListenerService` + `RemoteInput`/`PendingIntent`). | `OsClipboardOutboundPort`, `OsNotificationOutboundPort` (adaptadores Linux y Windows), `AndroidNotificationBridgeRepository` | Pruebas con ejecutor de procesos simulado; viaje de ida y vuelta real del portapapeles en GNU/Linux y en el runner Windows; validación manual en dispositivo físico. |
| **v0.4.0** | 3 (seguridad) | Emparejamiento criptográfico ECDH + SAS (sustituye el PIN CRC32 dummy), mTLS sobre TCP, escucha en LAN Wi-Fi con descubrimiento mDNS/NSD, identificador por instalación en Android Keystore, respaldo Bluetooth RFCOMM/L2CAP. | `pairing.rs`, `RustCoreBridgeContract`, `AndroidPairingRepository`, adaptadores de transporte | Vectores de prueba del intercambio en Rust y Kotlin; rechazo de pares no emparejados; prueba manual Bluetooth con dispositivo. |
| **v0.5.0** *(reproducción en escritorio hecha en IA-009)* | 5 | *Music Mode* real (`AudioPlaybackCaptureConfiguration` en Android; reproducción PipeWire/`javax.sound` y WASAPI en escritorio), códec Opus, cámara/micrófono virtual (`v4l2loopback` + nodo PipeWire; fuente virtual MediaFoundation). | `OsAudioSinkOutboundPort`, `OsVirtualCameraMicOutboundPort`, `AndroidMediaStreamRepository`, `signal_dsp.rs` | Reproducción audible del tono del simulador; latencia medida con `PcmJitterBuffer`; dispositivo `/dev/video*` visible en GNU/Linux. |
| **v0.6.0** | 6 | Monitor secundario real (EVDI / PipeWire ScreenCast en GNU/Linux; IddCx + Desktop Duplication en Windows), codificación H.264/HEVC por hardware, decodificación `MediaCodec` a pantalla completa y retorno táctil/lápiz. | `OsVirtualDisplayOutboundPort`, `AndroidMediaStreamRepository` | Cuadros a 60 fps en tablet física; latencia extremo a extremo < 50 ms por USB. |
| **v1.0.0** | 6 | Pruebas E2E con dispositivos físicos, R8/minificación del APK, firma de código del `.msi`, publicación del `.AppImage` con actualización delta y revisión completa de seguridad. | Todos | Lista de verificación E2E firmada por el equipo; liberación estable (no *pre-release*). |
