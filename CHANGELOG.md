# Registro de Cambios — UPIICSA-Connect

Todas las versiones se publican en la sección *Releases* del repositorio. Las versiones con sufijo (`-alpha`, `-beta`) se marcan como *pre-release*.

---

## [Sin publicar]

### Agregado
- Enlace real por cable USB (`adb reverse`) entre escritorio y Android sobre TCP en loopback, seleccionable con `UPIICSA_ENLACE=real` en el escritorio y el interruptor "Enlace real por cable USB" en Android.
- Mensaje de protocolo `NotificationPosted` (`0x13`) para espejar notificaciones del teléfono por el enlace real.
- `FrameReassembler` (Kotlin) y `FrameSocketChannel` compartidos por escritorio y Android; prueba extremo a extremo en bucle local.
- Portapapeles real del escritorio: `wl-clipboard`, `xclip`, `xsel` o AWT en GNU/Linux y `user32.dll` en Windows. Lo que se copia en la PC durante la sesión se envía al teléfono automáticamente.
- Notificaciones del teléfono en el escritorio: D-Bus (`org.freedesktop.Notifications`) en GNU/Linux y toasts en Windows; se retiran al responderlas, marcarlas o descartarlas desde la PC.
- *Music Mode* suena en la tarjeta de sonido de la PC (Java Sound sobre ALSA/PipeWire o DirectSound/WASAPI).
- El saludo de enlace incluye la versión de protocolo; versiones distintas se desconectan con un aviso para actualizar ambas apps.
- Interruptor «Enlace real por cable USB» en Ajustes del escritorio (se aplica al reiniciar; `UPIICSA_ENLACE` tiene prioridad).
- Android (con el enlace real): espejo de las notificaciones reales del teléfono con respuesta en línea, «marcar como leída» y descarte desde la PC (requiere conceder el acceso a notificaciones), y portapapeles real del sistema. Aún sin probar en un dispositivo físico.

### Cambiado
- El portapapeles y las notificaciones del sistema solo se usan con el enlace real; con el teléfono simulado siguen en memoria para no sobrescribir el portapapeles del usuario.

---

## [0.1.0-alpha] — 2026-11-24

Primera liberación preliminar para revisión del equipo. **No apta para uso diario:** la interfaz, el protocolo y el core nativo son reales, pero los servicios del sistema operativo y la conexión con el teléfono todavía funcionan con **simuladores** (ver [`docs/CONTRATOS_DUMMY.md`](https://github.com/Vidruck/UPIICSA-Connect/blob/dev/docs/CONTRATOS_DUMMY.md)).

### Artefactos

| Plataforma | Archivo | Notas |
| :--- | :--- | :--- |
| GNU/Linux (Debian/Ubuntu) | `UPIICSA-Connect-0.1.0-linux-amd64.deb` | `sudo apt install ./UPIICSA-Connect-0.1.0-linux-amd64.deb`; se instala en `/opt/upiicsa-connect`. |
| GNU/Linux (cualquier distribución) | `UPIICSA-Connect-0.1.0-x86_64.AppImage` | `chmod +x` y ejecutar. |
| GNU/Linux (portable) | `UPIICSA-Connect-0.1.0-linux-x64.tar.gz` | Extraer y ejecutar `UPIICSA-Connect/bin/UPIICSA-Connect`. |
| Windows 10/11 | `UPIICSA-Connect-0.1.0-windows-x64.msi` | Instalación por usuario (no requiere administrador). Sin firma de código: Windows SmartScreen puede pedir confirmación. |
| Windows (portable) | `UPIICSA-Connect-0.1.0-windows-x64.zip` | Extraer y ejecutar `UPIICSA-Connect.exe`. |
| Android 8.0+ (API 26) | `UPIICSA-Connect-0.1.0-alpha.apk` | APK firmado; instalar permitiendo "orígenes desconocidos". |
| Todas | `SHA256SUMS.txt` | Verificar con `sha256sum -c SHA256SUMS.txt`. |

Los instaladores de escritorio incluyen su propio entorno Java y el core nativo Rust (`libupiicsa_core_rs.so` / `upiicsa_core_rs.dll`). El APK incluye el core para `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`.

### Funciona con componentes reales

- Core nativo Rust 0.3.0: prioridad de transporte USB -> Wi-Fi -> Bluetooth, tramas `UpiicsaFrame` con CRC32, protocolo de mensajes, reensamblado de flujo, supervisor de enlace, *jitter buffer* y DSP de *Music Mode*.
- Escritorio: carga del core vía **JNA**; Android: carga del core vía **JNI** (con respaldo automático a la simulación Kotlin si el binario no está disponible). Ajustes (escritorio) y la pestaña Enlace (Android) indican si el core es "Nativo" o "Simulado".
- Interfaz completa con tema institucional UPIICSA-IPN claro/oscuro: 7 secciones en escritorio y 5 pestañas en Android.

### Todavía simulado

- Descubrimiento y conexión con el teléfono (USB/ADB, Wi-Fi, Bluetooth) y PIN de emparejamiento (no criptográfico).
- Notificaciones, portapapeles, *Music Mode*, cámara/micrófono virtual y monitor secundario: los datos provienen de simuladores y no llegan al sistema operativo.

### Verificación de esta versión

- 24 pruebas del core Rust, 21 pruebas Kotlin (incluye reglas de arquitectura y capturas de pantalla), en GNU/Linux y Windows mediante CI.
- La app Android **no se probó en emulador ni en dispositivo físico**; solo se verificó su compilación, lint, firma y el contenido del APK.

### Próximas versiones

Consulte la *Hoja de Ruta de Pendientes por Versión* en [`docs/PLAN_DE_FASES.md`](https://github.com/Vidruck/UPIICSA-Connect/blob/dev/docs/PLAN_DE_FASES.md). La siguiente meta (v0.2.0) es el enlace real por TCP con túnel USB `adb reverse`.
