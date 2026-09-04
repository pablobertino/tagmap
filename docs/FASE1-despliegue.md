# Fase 1 — Despliegue en la nube

Prerrequisito: Fase 0 aprobada (`docs/FASE0-resultado.md`).

## 1. Supabase — proyecto **Naima**, schema `tagmap`

TagMap comparte el proyecto `Naima` (`rlaxxavhzrrrmjrkymlm`) pero vive íntegramente en el schema `tagmap`. No hay nada en `public` ni triggers sobre `auth.users`; los usuarios sí se comparten (Supabase Auth es por proyecto).

- URL: `https://rlaxxavhzrrrmjrkymlm.supabase.co`
- Anon/publishable key (app Android): Dashboard > Settings > API. Solo sirve con un JWT de usuario; sin sesión no puede leer nada de `tagmap`.
- Service role key (solo recolector y Edge Function): Dashboard > Settings > API. Nunca en la app.

### Estado actual (aplicado el 2026-09-02)

Migraciones `tagmap_0001` … `tagmap_0005` aplicadas y `supabase/tests/geofence_state_machine.sql` verificado (11/11).

### Pasos manuales pendientes (una vez)

1. **Exponer el schema:** Dashboard > Settings > API > *Exposed schemas* → agregar `tagmap`. Sin esto, PostgREST responde 404 a la app y al recolector.
2. **Crear tu usuario:** Authentication > Users > *Add user* con `pablobertino@gmail.com` y contraseña. Luego, en SQL Editor:
   ```sql
   insert into tagmap.profiles (id, email)
   select id, email from auth.users where email = 'pablobertino@gmail.com'
   on conflict (id) do nothing;
   insert into tagmap.collectors (id, owner_id)
   select 'fly-tagmap-1', id from auth.users where email = 'pablobertino@gmail.com';
   ```
3. **Notificaciones** (puede esperar a Fase 3):
   - Firebase Console > proyecto nuevo > Project settings > Service accounts > *Generate new private key* → `firebase-service-account.json` (ignorado por git).
   - ```powershell
     supabase link --project-ref rlaxxavhzrrrmjrkymlm
     supabase secrets set NOTIFY_SECRET=<cadena aleatoria larga>
     supabase secrets set FCM_SERVICE_ACCOUNT_JSON="$(Get-Content firebase-service-account.json -Raw)"
     supabase functions deploy notify --no-verify-jwt
     ```
   - Activar: `update tagmap.app_settings set value = 'true' where key = 'notify_enabled';` (URL y secreto ya están en `app_settings`). Detalle en `FASE3-notificaciones.md`.
4. **Retención diaria** (opcional): habilitar `pg_cron` y
   ```sql
   select cron.schedule('tagmap_purge', '15 4 * * *', $$select tagmap.purge_old_locations()$$);
   ```

## 2. Recolector en GitHub Actions (sin tarjeta)

Repo público https://github.com/pablobertino/tagmap. `.github/workflows/collector.yml` funciona en modo **run largo**: cada ejecución corre ~5 h 40 min con un ciclo cada 15 min (`--max-minutes 340`) y al terminar se relanza a sí misma con `gh workflow run` (GITHUB_TOKEN, permiso `actions: write`). Motivo: el cron de GitHub en repos gratuitos se ejecutaba cada ~4 h en vez de cada 15 min. El cron (cada 2 h) queda como respaldo para reiniciar la cadena; la concurrencia (`group: collector`) garantiza un solo recolector activo. Minutos: ilimitados en repos públicos.

Si la cadena se corta (p. ej. GitHub cancela un run), basta con Actions → collector → Run workflow.

Secretos del repo (Settings → Secrets and variables → Actions): `SUPABASE_SERVICE_ROLE_KEY` (obligatorio); `GFMT_SECRETS_B64` / `GFMT_ACCOUNTS_B64` solo como respaldo (las cuentas normales viven en Supabase, ver abajo).

Verificar: pestaña Actions (círculo verde) y en Supabase `select id, status, last_seen_at from tagmap.collectors;` → `gha-tagmap-1`.

Notas:
- GitHub desactiva los crons tras 60 días sin commits en el repo; cualquier push los reactiva.
- Cuando venza la autenticación de Google (`status = auth_expired`): el usuario vuelve a correr `tagmap-auth` (ver abajo).

### Varias cuentas de Google (una por usuario de TagMap) — autoservicio

El recolector corre `python -m tagmap_collector.multi`: una cuenta de Google por subproceso, en secuencia. Las cuentas viven en Supabase (`tagmap.google_accounts`, secreto cifrado en **Vault**), las registra cada usuario y el recolector las recarga en cada ciclo. No hay que tocar GitHub para sumar personas.

**Para el usuario nuevo** (una vez, en cualquier PC con Windows y Google Chrome):

1. Que exista su usuario de TagMap (Supabase → Authentication → Users → Add user, *Auto confirm*).
2. Ejecutar `tagmap-auth.exe` (o `python -m tagmap_auth` desde `collector\` con el venv activado). Pide email/contraseña de TagMap, abre Chrome dos veces (login de Google + bloqueo de pantalla del teléfono para las claves de Find Hub), verifica que la cuenta responde y sube `secrets.json` cifrado. Lo borra de la PC al terminar.
3. En ≤15 min sus dispositivos aparecen en su app. Puede compartirlos con el botón Compartir.

Cuando Google vence la sesión, su fila en `collectors` pasa a `auth_expired`, le llega la alerta y repite el paso 2. `tagmap-auth --status` muestra el estado; `--remove` borra la cuenta; `--file secrets.json` sube un archivo ya generado (útil para migrar la cuenta original sin abrir Chrome).

**Generar el .exe** (en tu PC, una vez por versión): `collector\build_tagmap_auth.ps1` → `collector\dist\tagmap-auth.exe` (~80 MB). Repartilo por el medio que quieras; no contiene secretos (solo la URL y la clave pública de Supabase).

Respaldo manual (sin Vault): secreto de GitHub `GFMT_ACCOUNTS_B64` generado con `phase0\pack_accounts.py`; o `GFMT_SECRETS_B64` + `COLLECTOR_ID` para una sola cuenta. Solo se usan si Supabase no devuelve cuentas.

### Hacer sonar (workflow `actions.yml`)

La app inserta en `tagmap.action_requests`; un trigger llama a la API de GitHub (`workflow_dispatch`) y el workflow ejecuta `--actions-only`. Necesita un token:

1. GitHub → Settings → Developer settings → Personal access tokens → **Fine-grained tokens** → Generate: Repository access = *Only select repositories* → `tagmap`; Permissions → Repository → **Actions: Read and write**. Expiración: 1 año.
2. Supabase → SQL Editor (no pegar el token en otro lado):
   ```sql
   insert into tagmap.app_settings (key, value) values ('github_token', 'github_pat_XXXX')
   on conflict (key) do update set value = excluded.value;
   ```

Fly.io (`Dockerfile`, `fly.toml`) queda como alternativa si algún día hay tarjeta: recolector siempre encendido, acciones instantáneas.

## 3. Probar sin Google

```powershell
cd collector
Copy-Item .env.example .env   # completar SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY
$env:PROVIDER="fake"; python -m tagmap_collector --once
```

Inserta dos tags falsos (`Daniel`, `Mochila`) cerca del Obelisco. Útil para desarrollar la app antes de terminar Fase 0.

## 4. Costos

| Servicio | Mensual |
|---|---|
| Supabase | ya cubierto por Naima |
| GitHub Actions (repo público) | US$0 |
| Firebase FCM | US$0 |
| Mapas (OpenFreeMap / OpenTopoMap / Esri) | US$0, sin clave |
