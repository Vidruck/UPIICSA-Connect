# AVISO TEMPORAL DE SINCRONIZACIÓN PARA CLAUDE (CLAUDE CODE)

> **ORDEN OBLIGATORIA:** **BORRA ESTE ARCHIVO (`MENSAJE_PARA_CLAUDE.md`) DESPUÉS DE LEERLO** y confirma su eliminación en tu siguiente commit (`git rm MENSAJE_PARA_CLAUDE.md`). Este archivo es temporal y únicamente sirve para coordinar la sincronización entre agentes sobre `dev`.

---

## Estado Actual de la Rama `dev`

1. **Tu rama `origin/feat/ajustes-modo-enlace` (`8944733`) ya fue fusionada limpiamente en `dev`**, incluyendo:
   - `FileDesktopPreferencesAdapter`, `DesktopPreferencesOutboundPort`, `DesktopLinkMode.resolve`, interruptor persistente en `SettingsScreen` y `DesktopPreferencesTest`.
   - Tu actualización de `docs/PLAN_DE_FASES.md`, `docs/ARQUITECTURA_Y_CAPAS.md`, `CHANGELOG.md`, `README.md` y la ampliación de **`IA-010`** en `docs/BITACORA_IA.md`.
2. **También está integrada en `dev` la intervención `IA-011` (sobre `androidApp/`):**
   - `UpiicsaNotificationListenerService.sendQuietly`: captura también `SecurityException` además de `PendingIntent.CanceledException`.
   - `AndroidConnectionViewModel`: agrega `sendSystemClipboardToDesktop()`, relectura automática del portapapeles y reactivación de flujos al regresar a `onResume()` (`refreshSystemAccess`), y parámetro `notificationRepository: NotificationBridgeSource`.
   - `PortapapelesTab` (`AndroidTabScreens.kt`): agrega botón **«Enviar portapapeles del teléfono»** en modo enlace real.
   - `AndroidConnectionViewModelRealTest`: prueba de integración JVM contra socket TCP loopback.
3. **Bitácora (`docs/BITACORA_IA.md`):**
   - El último registro existente es **`IA-012`**. Por favor numera tu siguiente intervención a partir de **`IA-013`**.

---

## Qué Debes Hacer Antes de Seguir Trabajando

Ejecuta estos comandos para sincronizar tu copia local con el remoto unificado:

```bash
git fetch --all --prune
git checkout dev
git pull origin dev
```

Si vas a crear una nueva rama de trabajo, parte siempre desde `origin/dev` actualizado:

```bash
git checkout -b feat/tu-siguiente-tarea dev
```

> **Nota sobre ramas ya integradas en `dev`:** `feat/primera-liberacion`, `feat/transporte-real`, `feat/integraciones-so-escritorio`, `feat/version-protocolo`, `feat/android-notificaciones-portapapeles` y `feat/ajustes-modo-enlace` ya están fusionadas en `dev`; evita seguir enviando commits nuevos sobre esas ramas antiguas.

**Recuerda:** una vez leído este mensaje, ejecuta `git rm MENSAJE_PARA_CLAUDE.md` antes de continuar con tus cambios.
