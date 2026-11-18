# Guía de Contribución y Gobernanza — UPIICSA-Connect

Este documento define las normas obligatorias de colaboración para desarrolladores humanos y para **cualquier sistema de Inteligencia Artificial (IA)** que asista en el diseño, implementación, refactorización o documentación de **UPIICSA-Connect**.

---

## 1. Cláusulas Obligatorias e Inviolables para Inteligencias Artificiales

Toda Inteligencia Artificial que colabore en este repositorio **DEBE** acatar sin excepción los siguientes lineamientos:

1. **PROHIBICIÓN DE AUTORÍA O COLABORACIÓN SIN PERMISO:**
   - **ESTÁ ESTRICTAMENTE PROHIBIDO** que cualquier IA se agregue a sí misma, a su modelo o a su proveedor como colaborador, autor, coautor (`Co-Authored-By`), mantenedor o propietario en commits, metadatos, encabezados de archivo, `README.md`, `LICENSE` o cualquier otro documento, **sin el permiso explícito y por escrito del líder de proyecto**.
2. **ACTUALIZACIÓN OBLIGATORIA DEL DOCUMENTO DE FASES:**
   - En cada intervención o entrega, la IA **deberá ajustar y actualizar el documento** [`docs/PLAN_DE_FASES.md`](docs/PLAN_DE_FASES.md), reflejando con precisión qué fase se trabajó, qué entregables se completaron y cuál es el porcentaje real de cumplimiento de dicha fase.
3. **CUMPLIMIENTO ESTRICTO DE LA ARQUITECTURA:**
   - Antes de dar por concluida cualquier tarea, la IA **deberá comprobar** que sus contratos, puertos, adaptadores, ViewModels, repositorios y funciones **NO violan la arquitectura del proyecto**:
     - **Versión de Escritorio (`desktopApp`)**: Arquitectura **Ports & Adapters (Hexagonal)** con separación estricta por sistema operativo (**Windows** y **GNU/Linux**) y por servicio independiente, prohibiendo terminantemente la creación de **Clases Dios (*God Classes*)** o interfaces sobrecargadas.
     - **Versión Android (`androidApp`)**: Esquema **MVVM** estricto (`Model` -> `ViewModel` con `StateFlow` -> `View` en Compose sin lógica de negocio).
     - **Core Nativo (`core-rust`) y Compartido (`shared`)**: Separación clara de contratos FFI/JNI y modelos de dominio transversales sin acoplamiento directo a drivers de un único S.O.
4. **IDIOMA DE COMMITS EXCLUSIVAMENTE EN ESPAÑOL:**
   - **ESTÁ PROHIBIDO realizar commits en idiomas ajenos al ESPAÑOL**. Tanto el título como el cuerpo de cada mensaje de commit deben redactarse en español claro y técnico (por ejemplo: `feat(desktop): implementar adaptador de notificaciones para GNU/Linux`).
5. **PROHIBICIÓN DE ALTERAR LA LICENCIA:**
   - **ESTÁ PROHIBIDO modificar, suavizar, reemplazar o eliminar los términos del archivo `LICENSE`**. El proyecto no posee licencia abierta; todos los derechos están reservados al equipo desarrollador.

---

## 2. Estándares de Código y Comentarios

Todo código fuente agregado al proyecto (`Kotlin` y `Rust`) debe respetar estrictamente las siguientes reglas:

1. **Indentación uniforme:** 4 espacios por nivel en archivos `.kt`, `.kts` y `.rs`.
2. **Regla de Comentarios en Español:**
   - Se debe documentar **en español** el funcionamiento de **cada clase/struct/enum/interface** y **cada función/método** mediante bloques doc (`/** ... */` en Kotlin, `/// ...` en Rust).
   - **PROHIBIDO colocar comentarios en cada renglón o línea por línea** dentro del cuerpo de las funciones.
   - **Únicas excepciones permitidas dentro del cuerpo de una función:**
     - Señalar datos dummy o contratos hardcodeados para pruebas usando el prefijo exacto:
       `// [DUMMY-PRUEBAS]: <descripción del dato o contrato simulado>`
     - Justificar una decisión técnica puntual y no trivial usando el prefijo exacto:
       `// [DECISIÓN TÉCNICA]: <motivo arquitectónico o de rendimiento>`
3. **Registro de Contratos Dummy:**
   - Todo dato simulado o contrato temporal etiquetado con `// [DUMMY-PRUEBAS]:` debe quedar inventariado en [`docs/CONTRATOS_DUMMY.md`](docs/CONTRATOS_DUMMY.md).

---

## 3. Flujo de Ramas y Pull Requests

El repositorio se organiza bajo **dos ramas principales**:

1. **`main` (Original / Producción):** Rama principal estable. **Ningún agente ni colaborador debe trabajar directamente sobre `main`.**
2. **`dev` (De Trabajo / Integración):** Rama base de desarrollo activo. **Todo agente debe trabajar sobre `dev`.**

### Regla de Ramas Derivadas y Pull Requests
* Si el agente detecta un flujo alto de cambios, múltiples tareas paralelas o riesgo de conflictos sobre `dev`, **debe crear una rama de trabajo derivada a partir de `dev`** (por ejemplo: `feat/nombre-modulo` o `fix/descripcion-breve`).
* Una vez concluidos y verificados sus cambios, el agente debe abrir un **Pull Request (PR) hacia `dev`** para análisis y aprobación del equipo desarrollador.

---

## 4. Flujo de Trabajo y Verificación Previa al Commit

Antes de confirmar cambios en el repositorio:

1. Validar que las pruebas del núcleo Rust pasen correctamente:
   ```bash
   cargo test --manifest-path core-rust/Cargo.toml
   ```
2. Verificar que no existan referencias de acoplamiento cruzado entre `adapters/os/linux` y `adapters/os/windows` ni acceso directo de las vistas (`ui/`) a los adaptadores del sistema operativo.
3. Confirmar que el mensaje de commit está redactado íntegramente en **ESPAÑOL**, sin trailers de autoría automática de IA.
4. Confirmar que `docs/PLAN_DE_FASES.md` y `docs/CONTRATOS_DUMMY.md` reflejan el estado real del código.
