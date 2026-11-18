# Arquitectura de Capas e Infraestructura — UPIICSA-Connect

Este documento detalla la organización estructural de **UPIICSA-Connect**, justificando la separación de responsabilidades entre el núcleo de alto rendimiento en **Rust**, la capa común en **Kotlin Multiplatform**, la arquitectura **Ports & Adapters (Hexagonal)** en escritorio (**Windows** y **GNU/Linux**) y el patrón **MVVM** en **Android**.

---

## 1. Visión Global de Módulos e Infraestructura

```mermaid
flowchart TB
    subgraph Android["Módulo Android (MVVM - SDK 26..34)"]
        AUI["View: AndroidConnectionPanelScreen (Compose UPIICSA Light/Dark)"]
        AVM["ViewModel: AndroidConnectionViewModel (StateFlow)"]
        AMOD["Model: DeviceRepo / NotificationBridgeRepo / MediaStreamRepo"]
        AUI --> AVM --> AMOD
    end

    subgraph Desktop["Módulo Escritorio Windows y GNU/Linux (Ports & Adapters)"]
        DUI["Driving Adapter UI: DesktopControlPanelScreen (Compose UPIICSA Light/Dark)"]
        PIN["Inbound Ports: DesktopUseCasePorts"]
        APP["Application Service: DesktopConnectionService"]
        DOM["Domain: DesktopSessionAggregate"]
        POUT["Outbound Ports Segregados (6 interfaces independientes)"]
        DUI --> PIN --> APP
        APP --> DOM
        APP --> POUT

        subgraph AdaptadoresLinux["Adaptadores GNU/Linux (5 Clases Independientes)"]
            L1["LinuxFreedesktopNotificationAdapter (DBus)"]
            L2["LinuxClipboardAdapter (Wayland/X11)"]
            L3["LinuxVirtualDisplayAdapter (EVDI/PipeWire)"]
            L4["LinuxV4l2CameraMicAdapter (v4l2loopback)"]
            L5["LinuxPipeWireAudioSinkAdapter (PipeWire/ALSA)"]
        end

        subgraph AdaptadoresWindows["Adaptadores Windows (5 Clases Independientes)"]
            W1["WindowsWinRtNotificationAdapter (WinRT Toast)"]
            W2["WindowsWin32ClipboardAdapter (User32)"]
            W3["WindowsIddCxDisplayAdapter (IddCx)"]
            W4["WindowsDirectShowCameraMicAdapter (DirectShow/WASAPI)"]
            W5["WindowsWasapiAudioSinkAdapter (WASAPI)"]
        end

        TAD["Outbound Transport Adapter: MultiChannelTransportAdapter"]
        POUT --> AdaptadoresLinux
        POUT --> AdaptadoresWindows
        POUT --> TAD
    end

    subgraph Shared["Módulo Compartido KMP (shared/)"]
        SM["Modelos de Dominio y Contratos de Protocolo"]
        ST["UpiicsaTheme (Light / Dark - Paleta UPIICSA IPN)"]
        SB["RustCoreBridgeContract"]
    end

    subgraph RustCore["Core Nativo Rust (core-rust/)"]
        FFI["ffi_bridge.rs (Interfaz C-ABI / JNI)"]
        TR["transport.rs (Multiplexor USB -> Wi-Fi/BT + CRC32)"]
        DSP["signal_dsp.rs (DSP Audio Music Mode / Mic & Video Framing)"]
        FFI --> TR
        FFI --> DSP
    end

    AMOD --> SB
    TAD --> SB
    SB --> FFI
    DUI -.-> ST
    AUI -.-> ST
    DOM -.-> SM
    AVM -.-> SM
```

---

## 2. Arquitectura Ports & Adapters en Escritorio (`desktopApp`)

### Regla Anti-Clases-Dios (*Anti-God-Class Policy*)
Para evitar concentrar llamadas del sistema operativo Windows y GNU/Linux en una única clase monolítica, cada capacidad del sistema operativo define un **Puerto de Salida (`Outbound Port`) propio** y exactamente **un adaptador dedicado por cada sistema operativo**:

| Puerto de Salida (`ports/outbound/OsAndTransportPorts.kt`) | Adaptador GNU/Linux (`adapters/os/linux/`) | Adaptador Windows (`adapters/os/windows/`) | Responsabilidad Única |
| :--- | :--- | :--- | :--- |
| `OsNotificationOutboundPort` | `LinuxFreedesktopNotificationAdapter` | `WindowsWinRtNotificationAdapter` | Mostrar notificaciones del teléfono y capturar acciones/respuestas rápidas del usuario en el escritorio. |
| `OsClipboardOutboundPort` | `LinuxClipboardAdapter` | `WindowsWin32ClipboardAdapter` | Leer y escribir texto en el portapapeles del sistema operativo anfitrión. |
| `OsVirtualDisplayOutboundPort` | `LinuxVirtualDisplayAdapter` | `WindowsIddCxDisplayAdapter` | Crear, redimensionar y capturar frames del monitor virtual secundario enviado a Android. |
| `OsVirtualCameraMicOutboundPort` | `LinuxV4l2CameraMicAdapter` | `WindowsDirectShowCameraMicAdapter` | Inyectar video y audio provenientes de la cámara/micrófono del móvil en dispositivos virtuales para videollamadas. |
| `OsAudioSinkOutboundPort` | `LinuxPipeWireAudioSinkAdapter` | `WindowsWasapiAudioSinkAdapter` | Reproducir en la tarjeta de sonido del PC el flujo de audio de *Music Mode* con latencia mínima. |
| `DeviceTransportOutboundPort` | `MultiChannelTransportAdapter` (compartido sobre `core-rust`) | `MultiChannelTransportAdapter` (compartido sobre `core-rust`) | Negociar enlace priorizando **Cable USB (1ª opción)** y respaldando en **Wi-Fi / Bluetooth (2ª opción)**. |

### Capas Internas del Hexágono de Escritorio
1. **Dominio (`domain/DesktopSessionAggregate.kt`):** Contiene las invariantes de negocio puras (por ejemplo, que el cable USB siempre prevalece en prioridad automática sobre canales inalámbricos y la validación de estados activos de los servicios). No depende de UI, redes ni librerías del sistema operativo.
2. **Puertos (`ports/inbound/` y `ports/outbound/`):** Interfaces Kotlin puras que aíslan la aplicación tanto de la interfaz Compose como de los detalles de Windows/Linux y Rust.
3. **Aplicación (`application/DesktopConnectionService.kt`):** Implementa los 6 casos de uso de entrada orquestando llamadas entre el agregado de dominio, el transporte y el adaptador del S.O. inyectado por `OsAdapterFactory`.
4. **Adaptadores (`adapters/`):** Contienen el código específico de plataforma y el enlace con el núcleo Rust.
5. **Interfaz Gráfica (`ui/DesktopControlPanelScreen.kt`):** Actúa exclusivamente como *Driving Adapter* invocando los puertos de entrada.

---

## 3. Arquitectura MVVM en Android (`androidApp`)

1. **Model (`model/`):**
   - `AndroidDeviceRepository`: Administra el ciclo de vida de conexión (USB AOAv2/ADB como prioridad 1, Wi-Fi/Bluetooth como prioridad 2) y la lectura/escritura del `ClipboardManager`.
   - `AndroidNotificationBridgeRepository`: Se enlaza conceptualmente con `NotificationListenerService` para capturar notificaciones entrantes y ejecutar acciones `RemoteInput` (responder, marcar como leído, descartar) cuando el usuario las resuelve desde la PC.
   - `AndroidMediaStreamRepository`: Orquesta el envío de audio en *Music Mode* (`AudioPlaybackCapture`), la captura de cámara/micrófono para videollamadas en el portátil y la recepción de frames de video cuando el teléfono o tablet funciona como monitor secundario.
2. **ViewModel (`viewmodel/AndroidConnectionViewModel.kt`):**
   - Expone un único estado reactivo inmutable `StateFlow<AndroidConnectionUiState>`.
   - Procesa las acciones de la UI sin mantener referencias a vistas ni contextos Android visuales.
3. **View (`ui/AndroidConnectionPanelScreen.kt`):**
   - Panel Compose declarativo que solo observa el `StateFlow` del ViewModel y delega eventos del usuario hacia métodos del ViewModel.

---

## 4. Core de Procesamiento en Rust (`core-rust`) y Prioridad de Transporte

```mermaid
sequenceDiagram
    participant PC as Escritorio (DesktopConnectionService)
    participant TR as Rust Transport Multiplexer
    participant DSP as Rust Signal DSP
    participant Phone as Android (Repositories)

    PC->>TR: select_best_medium(usb_ok=true, wifi_ok=true, bt_ok=true)
    TR-->>PC: TransportMedium::UsbCable (Prioridad 1)
    Note over Phone,PC: Si el cable USB se desconecta, conmutación automática a Wi-Fi LAN o Bluetooth (Prioridad 2)

    Phone->>DSP: Audio PCM 48kHz (Music Mode)
    DSP->>DSP: Normalizar ganancia y empaquetar UpiicsaFrame + CRC32
    DSP->>PC: Entregar a OsAudioSinkOutboundPort (PipeWire / WASAPI)

    Phone->>PC: PhoneNotificationItem (ej. WhatsApp / Correo)
    PC->>Phone: NotificationResolutionAction (Responder desde escritorio)
    Phone->>Phone: Ejecutar RemoteInput sin desbloquear el móvil
```

---

## 5. Esquema Cromático Institucional (UPIICSA - IPN)

Definido de forma centralizada en `shared/src/commonMain/kotlin/mx/ipn/upiicsa/connect/shared/ui/theme/UpiicsaTheme.kt`:

- **Modo Claro (`Light`):**
  - Primario (Verde UPIICSA): `#006341`
  - Secundario (Oro / Amarillo UPIICSA): `#F2A900`
  - Terciario (Guinda IPN): `#6C1D45`
  - Fondo y Superficie: `#F5F8F6` / `#FFFFFF`
- **Modo Oscuro (`Dark`):**
  - Primario (Verde UPIICSA brillante para alto contraste): `#4CD99B`
  - Secundario (Oro UPIICSA cálido): `#FFD166`
  - Terciario (Guinda IPN aclarado): `#E07A9F`
  - Fondo y Superficie: `#0D1512` / `#14201B`
