# Inventario de Contratos Dummy y Datos Hardcodeados para Pruebas — UPIICSA-Connect

En cumplimiento de las reglas de transparencia del proyecto, todo contrato temporal, simulador o dato hardcodeado utilizado para validar la arquitectura y la interfaz de usuario antes de conectar los drivers nativos del sistema operativo está marcado en el código fuente con la etiqueta **`// [DUMMY-PRUEBAS]:`** y documentado en este archivo.

> **Última auditoría:** 2026-11-24 (IA-007). Se verificó con `grep -rn "DUMMY-PRUEBAS"` que cada etiqueta del código tiene una fila en este inventario.

---

## 1. Módulo `core-rust/`

| Archivo | Símbolo / Función | Qué Simula / Dato Hardcodeado | Reemplazo en Fase Futura |
| :--- | :--- | :--- | :--- |
| `core-rust/src/ffi_bridge.rs` | `upiicsa_probe_usb_device` | Simula la detección de un dispositivo Android por cable USB (`1` si el parámetro `simulate_usb_attached != 0`) en lugar de consultar `libusb` o el demonio ADB real. | Fase 3: Detección real de interfaces USB AOAv2 (`0x18D1`) y sockets locales ADB. |
| `core-rust/src/pairing.rs` | `derive_pairing_pin` | PIN de 6 dígitos **no criptográfico**: CRC32 de los identificadores ordenados de ambos equipos + nonce de 30 s. Sirve para validar el flujo visual de emparejamiento. | Fase 3: Intercambio ECDH + mTLS y verificación SAS (Short Authentication String) derivada del secreto compartido. |

---

## 2. Módulo `shared/`

| Archivo | Símbolo / Función | Qué Simula / Dato Hardcodeado | Reemplazo en Fase Futura |
| :--- | :--- | :--- | :--- |
| `shared/.../protocol/RustCoreBridgeContract.kt` | `DummyRustCoreBridge` (`coreVersion`, `selectBestTransport`, `processMusicModePcm`, `computeFrameChecksum`, `encodeFrame`, `validateWireFrame`, `derivePairingPin`) | Implementación pura en Kotlin que replica byte a byte el core Rust 0.2.0 (prioridad de transporte, DSP, CRC32, serialización/validación de tramas y PIN). Sus resultados están fijados por vectores de referencia compartidos con `core-rust/src/lib.rs`. Se usa como respaldo en escritorio y Android cuando no se encuentra el binario nativo. | **Ya reemplazado en ambas plataformas:** `JnaRustCoreBridge` (escritorio) y `JniRustCoreBridge` (Android, IA-007). Se conserva únicamente como respaldo automático cuando el binario no está presente (compilaciones de desarrollo sin `cargo`/NDK) y como referencia de paridad en pruebas. |

---

## 3. Módulo `desktopApp/`

| Archivo | Símbolo / Función | Qué Simula / Dato Hardcodeado | Reemplazo en Fase Futura |
| :--- | :--- | :--- | :--- |
| `desktopApp/.../adapters/simulation/DummyPhoneEventStreamAdapter.kt` | `events`, `backlogNotifications`, `rotatingNotification`, `rotatingClipboard`, `synthesize*` | Simulador completo del teléfono conectado: bandeja inicial de 3 notificaciones (WhatsApp, Classroom IPN, Telegram), notificación nueva cada ~18 s, texto de portapapeles cada ~31 s, bloques PCM de 440 Hz con envolvente, nivel de micrófono, cuadros de cámara `CAMF`, señal de vsync del monitor secundario y latidos con latencia/calidad característicos de cada medio. | Fase 3–6: Lectura del `FrameStreamDecoder` sobre el socket/endpoint USB real, decodificando cada `ChannelMessage` enviado por la app Android. |
| `desktopApp/.../adapters/transport/MultiChannelTransportAdapter.kt` | `discoverDevices` | Retardo artificial de 450 ms y catálogo hardcodeado de dispositivos (`Pixel 8 Pro (USB ADB)`, `Galaxy Tab S9 (Wi-Fi 192.168.1.45)`, `Redmi Note 13 (Bluetooth)`). | Fase 3: Escaneo real de `adb devices`, descubrimiento mDNS (`_upiicsa-connect._tcp`) y sockets Bluetooth RFCOMM. |
| `desktopApp/.../adapters/transport/MultiChannelTransportAdapter.kt` | `connect` | Registra el enlace activo en memoria simulando el handshake AOAv2/TLS/RFCOMM. | Fase 3: Apertura real de socket TCP con mTLS, túnel `adb reverse` o canal RFCOMM. |
| `desktopApp/.../adapters/transport/MultiChannelTransportAdapter.kt` | `sendChannelPayload` | Serializa la trama real con el core (`encodeFrame`) pero la valida en bucle local (`validateWireFrame`) en lugar de escribirla al socket. | Fase 3: Escritura al socket/endpoint y supervisión con `LinkSupervisor`. |
| `desktopApp/.../adapters/os/linux/LinuxFreedesktopNotificationAdapter.kt` | `publishPhoneNotification`, `dispatchResolutionFeedback` | Registra en memoria (`publishedLog`) las notificaciones espejadas y las confirmaciones de resolución. | Fase 4: Conexión real a D-Bus (`org.freedesktop.Notifications`) en GNU/Linux. |
| `desktopApp/.../adapters/os/linux/LinuxClipboardAdapter.kt` | `writeClipboardText`, `readClipboardText` | Mantiene el texto en una variable en memoria simulando el portapapeles de Wayland/X11. | Fase 4: Integración real con `wl-copy`/`wl-paste`, `xclip` o API nativa X11/Wayland. |
| `desktopApp/.../adapters/os/linux/LinuxVirtualDisplayAdapter.kt` | `startVirtualDisplay`, `captureCurrentFrame` | Simula la creación de un monitor virtual EVDI/PipeWire y genera un encabezado sintético de frame. | Fase 6: Salida virtual DRM/EVDI o PipeWire ScreenCast con codificación H.264. |
| `desktopApp/.../adapters/os/linux/LinuxV4l2CameraMicAdapter.kt` | `enableVirtualDevices`, `pushVideoFrame`, `pushMicAudio` | Simula la escritura hacia `/dev/video*` (`v4l2loopback`) y la fuente virtual de micrófono PipeWire contabilizando tramas en memoria. | Fase 5: Escritura real en `/dev/video*` y nodo virtual `pw-loopback`. |
| `desktopApp/.../adapters/os/linux/LinuxPipeWireAudioSinkAdapter.kt` | `openMusicModeSink`, `writePcmSamples` | Simula el envío de muestras PCM de *Music Mode* al sumidero de PipeWire/ALSA contabilizando muestras en memoria. | Fase 5: Reproducción real mediante `libpipewire` o `javax.sound.sampled` / ALSA. |
| `desktopApp/.../adapters/os/windows/WindowsWinRtNotificationAdapter.kt` | `publishPhoneNotification`, `dispatchResolutionFeedback` | Simula notificaciones Toast WinRT y acuses en memoria (`toastHistory`). | Fase 4: Invocación nativa WinRT `ToastNotificationManager`. |
| `desktopApp/.../adapters/os/windows/WindowsWin32ClipboardAdapter.kt` | `writeClipboardText`, `readClipboardText` | Simula el portapapeles Win32 en memoria. | Fase 4: `OpenClipboard` / `SetClipboardData` mediante JNA en `user32.dll`. |
| `desktopApp/.../adapters/os/windows/WindowsIddCxDisplayAdapter.kt` | `startVirtualDisplay`, `captureCurrentFrame` | Simula el adaptador de pantalla indirecta `IddCx` y genera un frame sintético. | Fase 6: Driver `IddCx` y Desktop Duplication API (`IDXGIOutputDuplication`). |
| `desktopApp/.../adapters/os/windows/WindowsDirectShowCameraMicAdapter.kt` | `enableVirtualDevices`, `pushVideoFrame`, `pushMicAudio` | Simula la cámara virtual DirectShow/MediaFoundation y el micrófono virtual en memoria. | Fase 5: Fuente virtual MediaFoundation Frame Server y controlador de audio virtual. |
| `desktopApp/.../adapters/os/windows/WindowsWasapiAudioSinkAdapter.kt` | `openMusicModeSink`, `writePcmSamples` | Simula el flujo de audio hacia WASAPI. | Fase 5: Cliente WASAPI nativo de baja latencia (`IAudioRenderClient`). |

> **Modo de enlace (IA-008):** `MultiChannelTransportAdapter` y `DummyPhoneEventStreamAdapter` solo se usan en el modo simulado (valor por omisión). Con `-Dupiicsa.enlace=real` / `UPIICSA_ENLACE=real` el escritorio usa los adaptadores reales `TcpLinkTransportAdapter` + `SocketPhoneEventStreamAdapter` (sin datos simulados). En Android, `AndroidDeviceRepository.scanAvailableDesktops` / `connectToDesktop` / `linkTelemetryStream` / `clipboardFromDesktopStream` solo se usan con el interruptor "Enlace real" apagado; con el enlace real se usa `AndroidSocketLinkRepository`. Las notificaciones de `AndroidNotificationBridgeRepository` siguen siendo simuladas, pero ahora viajan al ordenador por el enlace real.

> **Retirados en IA-006:** el bloque PCM sintético de `DesktopConnectionService.toggleMusicMode` y `crearNotificacionesDummy` de la interfaz fueron eliminados; ahora esos datos provienen exclusivamente de `DummyPhoneEventStreamAdapter` (capa de adaptadores), de modo que el servicio de aplicación y la UI no contienen datos simulados.

---

## 4. Módulo `androidApp/`

| Archivo | Símbolo / Función | Qué Simula / Dato Hardcodeado | Reemplazo en Fase Futura |
| :--- | :--- | :--- | :--- |
| `androidApp/.../model/AndroidDeviceRepository.kt` | `scanAvailableDesktops` | Retardo de 400 ms y lista de equipos dummy (`Laptop-UPIICSA-Linux (Cable USB)`, `Workstation-Win11 (Wi-Fi / BT)`). | Fase 3: `UsbManager` (AOAv2), `NsdManager` y `BluetoothAdapter`. |
| `androidApp/.../model/AndroidDeviceRepository.kt` | `connectToDesktop`, `syncClipboardFromMobile` | Serializa el saludo y el portapapeles con el core sin abrir sockets; el portapapeles se guarda en memoria en lugar de `ClipboardManager`. | Fase 3 y 4: Socket mTLS/ADB/RFCOMM real y `ClipboardManager`. |
| `androidApp/.../model/AndroidDeviceRepository.kt` | `linkTelemetryStream`, `clipboardFromDesktopStream` | Latencia/calidad sintéticas por medio y textos simulados enviados desde el ordenador cada 30 s. | Fase 3–4: Latidos reales del `LinkSupervisor` y mensajes `ClipboardText` recibidos. |
| `androidApp/.../model/AndroidPairingRepository.kt` | `mobileDeviceId` | Identificador fijo `android-upiicsa-movil` para derivar el PIN. | Fase 3: Identificador por instalación persistido en Android Keystore. |
| `androidApp/.../model/AndroidNotificationBridgeRepository.kt` | `cachedNotifications`, `resolveNotificationFromDesktop`, `incomingNotifications` | Lista inicial de notificaciones simuladas, confirmación local de respuestas enviadas desde la PC y notificación nueva cada 25 s. | Fase 4: `NotificationListenerService` real y disparo de `RemoteInput` / `PendingIntent`. |
| `androidApp/.../model/AndroidMediaStreamRepository.kt` | `configureMusicModeCapture`, `configureCameraMicStream`, `configureSecondaryDisplayReceiver` | Bloque PCM sintético y confirmación en memoria de cámara/micrófono y receptor de pantalla sin hardware ni permisos. | Fase 5 y 6: `MediaProjection` + `AudioPlaybackCaptureConfiguration`, `CameraX` + `AudioRecord` y `SurfaceView` con `MediaCodec`. |
| `androidApp/.../model/AndroidMediaStreamRepository.kt` | `musicLevelStream`, `microphoneLevelStream`, `frameTicks` | Tono de 440 Hz procesado por el DSP del core, nivel de voz simulado y reloj de cuadros que anima las vistas previas. | Fase 5 y 6: Callbacks reales de `AudioRecord`, `CameraX` y `MediaCodec`. |
