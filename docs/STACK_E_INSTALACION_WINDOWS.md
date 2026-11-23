# Stack Técnico y Manual de Instalación en Windows 10 / 11 — UPIICSA-Connect

Este documento define las tecnologías que el equipo desarrollador debe conocer y tener instaladas en su ordenador, junto con la guía de instalación paso a paso para **Windows 10 y Windows 11**, y una sección de prevención y solución de errores frecuentes.

---

## 1. Stack Técnico Requerido

| Tecnología / Herramienta | Versión Recomendada | Uso en el Proyecto |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | **17** o **21** (Temurin / OpenJDK) | Compilación de módulos Kotlin Multiplatform, Desktop JVM y Android Gradle Plugin (`8.5.2`). |
| **Kotlin Multiplatform (KMP)** | `2.0.21` | Lógica compartida (`shared`), aplicación de escritorio (`desktopApp`) y móvil (`androidApp`). |
| **Compose Multiplatform** | `1.7.1` | Interfaz gráfica declarativa Material 3 con paleta **UPIICSA-IPN** (Light/Dark). |
| **Android Studio + Android SDK** | SDK `34` (`minSdk 26`) | Compilación, emulación y depuración de `androidApp` vía **ADB**. |
| **Rust + Cargo (`rustup`)** | Estable (`1.80+`, MSVC) | Compilación y pruebas del núcleo nativo de señales y transporte (`core-rust`). |
| **Visual Studio C++ Build Tools** | 2022 (`Desktop development with C++`) | Linker (`link.exe`) y SDK de Windows requeridos por Rust (`x86_64-pc-windows-msvc`). |
| **JNA (Java Native Access)** | `5.15.0` (dependencia Gradle) | Carga de `upiicsa_core_rs.dll` / `.so` desde la app de escritorio. |
| **Git** | `2.40+` | Control de versiones bajo el flujo `main` / `dev` y commits en español. |

### Conocimientos Recomendados para el Equipo
* **Arquitectura:** **Ports & Adapters (Hexagonal)** para `desktopApp` (adaptadores separados por S.O. sin Clases Dios) y **MVVM** (`StateFlow` + Repositorios) para `androidApp`.
* **Programación Reactiva:** Corrutinas de Kotlin (`kotlinx-coroutines`) y `StateFlow`.
* **Interoperabilidad:** Fundamentos de FFI (`extern "C"`) entre Rust y Kotlin/JVM.

---

## 2. Manual de Instalación Paso a Paso (Windows 10 / 11)

> **Recomendación:** Abrir **PowerShell** o **Terminal de Windows** como Administrador para ejecutar los comandos de `winget`.

### Paso 1: Instalar Git y configurar línea de comandos
```powershell
winget install --id Git.Git -e --source winget
```
Configurar nombre, correo y soporte de rutas largas en Windows:
```powershell
git config --global user.name "Tu Nombre"
git config --global user.email "tu.correo@ejemplo.com"
git config --global core.longpaths true
```

### Paso 2: Instalar JDK 17 (o JDK 21)
```powershell
winget install --id EclipseAdoptium.Temurin.17.JDK -e --source winget
```
Verificar instalación:
```powershell
java -version
javac -version
```

### Paso 3: Instalar Visual Studio C++ Build Tools (Requisito para Rust en Windows)
1. Descargar **Visual Studio Build Tools 2022** o instalar vía `winget`:
   ```powershell
   winget install --id Microsoft.VisualStudio.2022.BuildTools -e --source winget
   ```
2. En el instalador gráfico, seleccionar la carga de trabajo **"Desarrollo para el escritorio con C++" (*Desktop development with C++*)** asegurándose de incluir **MSVC v143** y **Windows 10/11 SDK**.

### Paso 4: Instalar Rust y Cargo (`rustup`)
```powershell
winget install --id Rustlang.Rustup -e --source winget
```
Cerrar y volver a abrir PowerShell, luego verificar:
```powershell
rustc --version
cargo --version
```

### Paso 5: Instalar Android Studio, Android SDK 34 y ADB
```powershell
winget install --id Google.AndroidStudio -e --source winget
```
1. Abrir **Android Studio** -> **More Actions** -> **SDK Manager**.
2. En **SDK Platforms**, instalar **Android 14.0 (API Level 34)**.
3. En **SDK Tools**, instalar **Android SDK Build-Tools 34**, **Android SDK Platform-Tools** (incluye `adb`) y **NDK (Side by side)** (necesario en Fase 2/3 para compilar `core-rust` para Android).
4. Configurar la variable de entorno `ANDROID_HOME` (ejemplo por defecto: `C:\Users\<TuUsuario>\AppData\Local\Android\Sdk`) y agregar `%ANDROID_HOME%\platform-tools` al `PATH`.

### Paso 6: Clonar el repositorio en la rama `dev` y verificar
```powershell
git clone -b dev git@github.com:Vidruck/UPIICSA-Connect.git
cd UPIICSA-Connect
cargo test --manifest-path core-rust/Cargo.toml
```
Si `cargo test` muestra `24 passed; 0 failed`, el entorno Rust está listo. Posteriormente, abrir la carpeta raíz `UPIICSA-Connect` en **Android Studio** para sincronizar Gradle.

### Paso 7: Compilar el core nativo y ejecutar la app de escritorio
El repositorio incluye el **Gradle Wrapper** (`gradlew.bat`), por lo que no es necesario instalar Gradle por separado:
```powershell
.\gradlew.bat :desktopApp:buildRustCore
.\gradlew.bat :desktopApp:run
```
* `buildRustCore` genera `core-rust\target\release\upiicsa_core_rs.dll`, que el escritorio carga automáticamente vía **JNA**. Si la DLL no existe, la app arranca igual con la simulación Kotlin (en **Ajustes** se indica "Nativo" o "Simulado").
* Para otra ubicación de la DLL: `-Dupiicsa.core.lib.path=C:\ruta\upiicsa_core_rs.dll` o la variable `UPIICSA_CORE_LIB`.

### Paso 8: Ejecutar las pruebas y generar el APK
```powershell
.\gradlew.bat :shared:desktopTest :desktopApp:desktopTest
.\gradlew.bat :androidApp:assembleDebug
```
Las pruebas de escritorio generan capturas PNG de cada sección (modo claro y oscuro) en `desktopApp\build\screenshots\`. El APK queda en `androidApp\build\outputs\apk\debug\`.

---

## 3. Prevención y Solución de Fallas o Errores Comunes

### 1. Error en Rust: `linker 'link.exe' not found`
* **Causa:** Falta instalar las herramientas de compilación C++ de Visual Studio.
* **Solución:** Ejecutar el Instalador de Visual Studio, modificar **Build Tools 2022** y marcar **Desarrollo para el escritorio con C++** + **Windows SDK**. Reiniciar la terminal.

### 2. Error en Gradle / Android Studio: `JAVA_HOME is not set` o versión incompatible
* **Causa:** `JAVA_HOME` apunta a un JRE antiguo o no está definido.
* **Solución:** En Variables de Entorno del Sistema, definir `JAVA_HOME` apuntando a la carpeta raíz del JDK 17 (ej. `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`) y añadir `%JAVA_HOME%\bin` al `PATH`.

### 3. Error de rutas largas o caracteres especiales en Windows (`Filename too long` / fallo de Gradle)
* **Causa:** Clonar el proyecto en rutas profundas o con espacios/acentos (ej. `C:\Users\José\OneDrive\Escritorio\...`).
* **Solución / Prevención:** Clonar siempre en una ruta corta sin espacios ni acentos, por ejemplo `C:\dev\UPIICSA-Connect`, y mantener `git config --global core.longpaths true`.

### 4. `adb devices` no detecta el teléfono Android por cable USB
* **Causa:** Depuración USB desactivada, cable de solo carga o falta de driver USB del fabricante (Xiaomi, Samsung, Motorola, etc.).
* **Solución:**
  1. Activar **Opciones de desarrollador** y **Depuración por USB** en el teléfono.
  2. Cambiar el modo USB a **Transferencia de archivos (MTP)**.
  3. Instalar el driver USB OEM desde el Administrador de dispositivos de Windows o Google USB Driver en SDK Manager.

### 5. Bloqueo de conexión Wi-Fi LAN por Firewall de Windows Defender
* **Causa:** Windows bloquea puertos entrantes en redes marcadas como *Pública*.
* **Solución:** Cambiar el perfil de red local a **Privada** o crear una regla de entrada en el Firewall de Windows para permitir el tráfico de la aplicación JVM / puerto de desarrollo de UPIICSA-Connect.

### 6. Conflictos de rama al trabajar en equipo
* **Prevención:** Nunca trabajar ni hacer push directo a `main`. Trabajar siempre sobre `dev` o crear una rama derivada (`git checkout -b feat/mi-tarea dev`) y abrir un **Pull Request** hacia `dev` siguiendo [`CONTRIBUTING.md`](../CONTRIBUTING.md).
