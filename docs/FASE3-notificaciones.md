# Fase 3 — Notificaciones push

Cadena: `locations` INSERT → trigger `evaluate_geofences` → `geofence_events` → trigger `notify_geofence_event` (pg_net) → Edge Function `notify` → FCM → app.

Ya hecho en Supabase: tablas, triggers, `app_settings` con `notify_url` y `notify_secret`, `notify_enabled = false`.

## 1. Firebase (gratis, sin tarjeta)

1. https://console.firebase.google.com → **Crear proyecto** → nombre `tagmap` → desactivá Analytics → Crear.
2. En la portada del proyecto, ícono **Android** (Agregar app):
   - Nombre del paquete: `com.pablobertino.tagmap`
   - Registrar app → **Descargar google-services.json**.
3. Copiá `google-services.json` a `C:\CLAUDE\XIAOMI_TAG\android-app\app\google-services.json` (está en `.gitignore`).
4. Configuración del proyecto (engranaje) → **Cuentas de servicio** → **Generar nueva clave privada** → se descarga un JSON. Guardalo fuera del repo (por ejemplo `C:\CLAUDE\firebase-tagmap.json`).

## 2. Edge Function `notify` (desde el Dashboard, sin CLI)

1. Supabase → **Edge Functions** → **Deploy a new function** → *Via Editor*.
2. Nombre: `notify`. Pegá el contenido de `supabase/functions/notify/index.ts`. Deploy.
3. En la función → **Details**: desactivá **Verify JWT** (la autentica el header `x-notify-secret`).
4. **Edge Functions → Secrets** → agregar:
   - `NOTIFY_SECRET` = `R8VRgS4dep0QH2IkRdIFAtwY7FshE4MJNcmwqhswM6A`
   - `FCM_SERVICE_ACCOUNT_JSON` = contenido completo del JSON de la cuenta de servicio (abrilo con el Bloc de notas, copiá todo).
5. Activar el envío (SQL Editor):
   ```sql
   update tagmap.app_settings set value = 'true' where key = 'notify_enabled';
   ```

## 3. App

Con `google-services.json` en su lugar, `Build APK`. La app pide permiso de notificaciones al abrir y registra el token en `tagmap.mobile_devices`. Verificar:

```sql
select device_name, notifications_enabled, last_seen_at from tagmap.mobile_devices;
```

## 4. Prueba

1. En la app: mapa → mantener pulsado sobre tu casa → nombre `Casa`, radio 250 m → Guardar.
2. Lugares → en `Casa` activá *Llega* y *Sale* para `Pablo 1`.
3. La primera posición fija el estado (dentro/fuera) sin avisar (spec §7.3). El próximo cambio real de dentro→fuera o viceversa, confirmado por dos reportes o 10 min, genera el evento y la push.
4. Sin moverte no hay forma de provocar un evento real; el estado actual por tag aparece bajo cada switch ("ahora: dentro / fuera") y cambia con el primer movimiento verdadero.

## Diagnóstico

- `select * from tagmap.geofence_events order by created_at desc limit 20;` → si hay filas `CREATED` que nunca pasan a `SENT`, la Edge Function no está recibiendo: revisar logs en Edge Functions → notify → Logs.
- `select * from net._http_response order by created desc limit 10;` → respuestas de pg_net a la función.
