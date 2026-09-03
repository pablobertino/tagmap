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
   - SQL Editor:
     ```sql
     alter database postgres set app.notify_url = 'https://rlaxxavhzrrrmjrkymlm.supabase.co/functions/v1/notify';
     alter database postgres set app.notify_secret = '<el mismo NOTIFY_SECRET>';
     ```
4. **Retención diaria** (opcional): habilitar `pg_cron` y
   ```sql
   select cron.schedule('tagmap_purge', '15 4 * * *', $$select tagmap.purge_old_locations()$$);
   ```

## 2. Recolector en Fly.io

```powershell
winget install flyctl
fly auth login
cd collector
fly launch --copy-config --no-deploy
```

Secretos:

```powershell
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("secrets.json"))
fly secrets set SUPABASE_URL=https://rlaxxavhzrrrmjrkymlm.supabase.co `
                SUPABASE_SERVICE_ROLE_KEY=<service_role> `
                GFMT_SECRETS_B64=$b64
fly deploy
fly logs
```

Debe verse `Ciclo ok: N tags, M posiciones, K nuevas` cada 15 min. En Supabase: `select * from tagmap.app_trackers;`.

### Cuando venza la autenticación de Google

`tagmap.collectors.status` pasa a `auth_expired` y se crea un `system_alerts`. Reautenticar en Windows (`python main.py` en el vendor), regenerar `$b64`, `fly secrets set GFMT_SECRETS_B64=$b64`.

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
| Fly.io shared-cpu-1x 512 MB | ~US$3 |
| Firebase FCM | US$0 |
| Google Maps SDK Android | US$0 (crédito mensual) |
