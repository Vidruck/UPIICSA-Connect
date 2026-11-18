# Inventario de Contratos Dummy y Datos Hardcodeados para Pruebas — UPIICSA-Connect

En cumplimiento de las reglas de transparencia del proyecto, todo contrato temporal, simulador o dato hardcodeado utilizado para validar la arquitectura y la interfaz de usuario antes de conectar los drivers nativos del sistema operativo está marcado en el código fuente con la etiqueta **`// [DUMMY-PRUEBAS]:`** y documentado en este archivo.

---

## 1. Módulo `core-rust/`

| Archivo | Símbolo / Función | Qué Simula / Dato Hardcodeado | Reemplazo en Fase Futura |
| :--- | :--- | :--- | :--- |
| `core-rust/src/ffi_bridge.rs` | `upiicsa_probe_usb_device` | Simula la detección de un dispositivo Android por cable USB (`1` si el parámetro `simulate_usb_attached != 0`) en lugar de consultar `libusb` o el demonio ADB real. | Fase 3: Detección real de interfaces USB AOAv2 (`0x18D1`) y sockets locales ADB. |

---

## 2. Módulo `shared/`

| Archivo | Símbolo / Función | Qué Simula / Dato Hardcodeado | Reemplazo en Fase Futura |
| :--- | :--- | :--- | :--- |
| `shared/.../protocol/RustCoreBridgeContract.kt` | `DummyRustCoreBridge` | Implementación pura en Kotlin que replica la prioridad de transporte (`USB -> Wi-Fi -> Bluetooth`), el checksum determinista y el procesamiento de paquetes de *Music Mode* sin cargar todavía el binario compilado `.so`/`.dll` de `core-rust`. | Fase 2/3: Implementación `JniRustCoreBridge` cargando `System.loadLibrary("upiicsa_core_rs")` vía JNI/JNA. |

---

## 3. Módulo `desktopApp/`

| Archivo | Símbolo / Función | Qué Simula / Dato Hardcodeado | Reemplazo en Fase Futura |
| :--- | :--- | :--- | :--- |
| `desktopApp/.../application/DesktopConnectionService.kt` | `toggleMusicMode` | Genera un bloque PCM sintético de prueba (`shortArrayOf(900, -900, 1800, -1800)`) para comprobar la cadena DSP hacia el sumidero del sistema operativo. | Fase 5: Recepción continua del flujo de audio real enviado desde `AudioPlaybackCapture` en Android. |
| `desktopApp/.../adapters/transport/MultiChannelTransportAdapter.kt` | `discoverDevices`, `connect` | Retorna una lista hardcodeada de dispositivos de prueba (`Pixel 8 Pro (USB ADB)`, `Galaxy Tab S9 (Wi-Fi 192.168.1.45)`, `Redmi Note 13 (Bluetooth)`) y registra el enlace simulado en memoria. | Fase 3: Escaneo real de `adb devices`, descubrimiento mDNS (`_upiicsa-connect._tcp`) y sockets Bluetooth RFCOMM. |
| `desktopApp/.../adapters/os/linux/LinuxFreedesktopNotificationAdapter.kt` | `publishPhoneNotification`, `dispatchResolutionFeedback` | Registra en memoria (`publishedLog`) las notificaciones espejadas y las confirmaciones de resolución para validar el flujo de escritorio. | Fase 4: Conexión real a D-Bus (`org.freedesktop.Notifications`) en GNU/Linux. |
| `desktopApp/.../adapters/os/linux/LinuxClipboardAdapter.kt` | `writeClipboardText`, `readClipboardText` | Mantiene el texto en una variable en memoria simulando el portapapeles de Wayland/X11. | Fase 4: Integración real con `wl-copy`/`wl-paste`, `xclip` o API nativa X11/Wayland. |
| `desktopApp/.../adapters/os/linux/LinuxVirtualDisplayAdapter.kt` | `startVirtualDisplay`, `captureCurrentFrame` | Simula la creación de un monitor virtual EVDI/PipeWire y genera un encabezado sintético de frame. | Fase 6: Creación de salida virtual DRM/EVDI o PipeWire ScreenCast con codificación H.264. |
| `desktopApp/.../adapters/os/linux/LinuxV4l2CameraMicAdapter.kt` | `enableVirtualDevices`, `pushVideoFrame`, `pushMicAudio` | Simula la escritura hacia `/dev/video*` (`v4l2loopback`) y fuente virtual de micrófono PipeWire contabilizando tramas en memoria. | Fase 5: Escritura real en dispositivo `/dev/video*` y nodo virtual `pw-loopback`. |
| `desktopApp/.../adapters/os/linux/LinuxPipeWireAudioSinkAdapter.kt` | `openMusicModeSink`, `writePcmSamples` | Simula el envío de muestras PCM de *Music Mode* al sumidero de PipeWire/ALSA contabilizando muestras en memoria. | Fase 5: Reproducción real mediante `libpipewire` o `javax.sound.sampled` / ALSA. |
| `desktopApp/.../adapters/os/windows/WindowsWinRtNotificationAdapter.kt` | `publishPhoneNotification`, `dispatchResolutionFeedback` | Simula notificaciones Toast WinRT y acuses en memoria (`toastHistory`) para validar la paridad funcional en Windows. | Fase 4: Invocación nativa WinRT `ToastNotificationManager`. |
| `desktopApp/.../adapters/os/windows/WindowsWin32ClipboardAdapter.kt` | `writeClipboardText`, `readClipboardText` | Simula el portapapeles Win32 en memoria dentro del adaptador de Windows. | Fase 4: Llamadas `OpenClipboard` / `SetClipboardData` mediante JNA/JNI en `user32.dll`. |
| `desktopApp/.../adapters/os/windows/WindowsIddCxDisplayAdapter.kt` | `startVirtualDisplay`, `captureCurrentFrame` | Simula el adaptador de pantalla indirecta `IddCx` y genera un frame sintético de prueba. | Fase 6: Driver Windows `IddCx` y captura Desktop Duplication API (`IDXGIOutputDuplication`). |
| `desktopApp/.../adapters/os/windows/WindowsDirectShowCameraMicAdapter.kt` | `enableVirtualDevices`, `pushVideoFrame`, `pushMicAudio` | Simula la cámara virtual DirectShow/MediaFoundation y micrófono virtual en memoria. | Fase 5: Fuente virtual MediaFoundation Frame Server y controlador de audio virtual. |
| `desktopApp/.../adapters/os/windows/WindowsWasapiAudioSinkAdapter.kt` | `openMusicModeSink`, `writePcmSamples` | Simula el flujo de audio hacia la tarjeta de sonido WASAPI de Windows. | Fase 5: Cliente WASAPI nativo de baja latencia (`IAudioRenderClient`). |
| `desktopApp/.../ui/DesktopControlPanelScreen.kt` | `crearNotificacionesDummy` | Precarga notificaciones dummy de ejemplo (WhatsApp, Classroom IPN) para demostrar la resolución inmediata desde el panel del ordenador. | Fase 4: Alimentación exclusiva desde el flujo en vivo del teléfono conectado. |

---

## 4. Módulo `androidApp/`

| Archivo | Símbolo / Función | Qué Simula / Dato Hardcodeado | Reemplazo en Fase Futura |
| :--- | :--- | :--- | :--- |
| `androidApp/.../model/AndroidDeviceRepository.kt` | `scanAvailableDesktops`, `connectToDesktop`, `syncClipboardFromMobile` | Lista de equipos de escritorio dummy (`Laptop-UPIICSA-Linux (Cable USB)`, `Workstation-Win11 (Wi-Fi / BT)`), registro en memoria de sesión y lectura/escritura simulada de `ClipboardManager`. | Fase 3 y 4: Servicio Android `UsbManager` (AOAv2), descubrimiento `NsdManager`, Bluetooth `BluetoothAdapter` y `ClipboardManager` real. |
| `androidApp/.../model/AndroidNotificationBridgeRepository.kt` | `cachedNotifications`, `loadActiveNotifications`, `resolveNotificationFromDesktop` | Lista inicial de notificaciones móviles simuladas y confirmación local en memoria de respuestas enviadas desde la PC. | Fase 4: `NotificationListenerService` real de Android y disparo de `RemoteInput` / `PendingIntent`. |
| `androidApp/.../model/AndroidMediaStreamRepository.kt` | `configureMusicModeCapture`, `configureCameraMicStream`, `configureSecondaryDisplayReceiver` | Genera bloque PCM sintético de prueba para *Music Mode* y confirma en memoria los estados de cámara/micrófono y receptor de pantalla secundaria sin invocar hardware ni permisos Android en vivo. | Fase 5 y 6: `MediaProjection` + `AudioPlaybackCaptureConfiguration`, `CameraX` + `AudioRecord` y `SurfaceView` decodificando tramas de video. |
