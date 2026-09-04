# Contrato de datos — app Android ↔ Supabase

**Estado: CONGELADO 2026-09-02** tras Fase 0 (ver `FASE0-resultado.md`). Find Hub entrega lat/lon, precisión en metros, hora real de observación y origen (red/propio) para los Xiaomi Tag.

La app usa `supabase-kt` con la anon key + sesión de Supabase Auth. Todo está protegido por RLS; la app solo ve datos de `auth.uid()`.

## Mapeo de la spec §10

| Endpoint spec | Supabase |
|---|---|
| `POST /v1/auth/login`, `/refresh` | `supabase.auth.signInWith(Email)` / refresh automático |
| `GET /v1/trackers` | `from("app_trackers").select()` |
| `GET /v1/trackers/{id}` | `from("app_trackers").select().eq("id", id).single()` |
| `GET /v1/trackers/{id}/locations?from=&to=&accuracy_max=` | `rpc("app_locations", {p_tracker_id, p_from, p_to, p_accuracy_max})` |
| `POST /v1/trackers/{id}/refresh` | Fase 4 (requiere canal recolector ← app; propuesta: tabla `refresh_requests` + Realtime) |
| `POST /v1/trackers/{id}/sound` | Fase 4, idem |
| `GET/POST/PUT/DELETE /v1/places` | `from("places")` CRUD directo |
| `GET/POST/PUT/DELETE /v1/geofence-rules` | `from("geofence_rules")` CRUD (columnas de estado son de solo lectura) |
| `GET /v1/events` | `from("app_events").select().order("created_at", descending)` |
| `POST /v1/mobile-devices/register` | `rpc("app_register_device", {p_fcm_token, p_device_name})` |
| marcar leído | `rpc("app_mark_events_read", {p_event_ids})` |
| silenciar regla | `rpc("app_mute_rule", {p_rule_id, p_minutes})` |
| borrar historial | `rpc("app_delete_tracker_history", {p_tracker_id})` |
| estado del sistema | `from("collectors")`, `from("system_alerts")` |
| preferencias | `from("profiles")` update |

## `app_trackers`

| Campo | Tipo | Nota |
|---|---|---|
| id | uuid | |
| name, icon, color, enabled, supports_sound | | editables vía `trackers` |
| last_location_id | bigint? | null si nunca reportó |
| latitude, longitude | double? | |
| accuracy_m | real? | |
| quality | `good` / `medium` / `low` | spec §13 |
| source | `find_hub_network` / `own_device` / `unknown` | |
| last_observed_at | timestamptz? | **hora de detección**; usar esta para "hace X min" |
| last_received_at | timestamptz? | hora de ingreso al sistema |
| age_minutes | int? | calculado en servidor |
| at_places | text? | "Casa, Oficina" si está dentro de algún lugar |

Estado visual (spec §6.1): verde si `age_minutes <= profiles.recent_threshold_minutes`, amarillo si mayor, gris si `last_location_id is null`.

## `app_events`

Todos los campos de `geofence_events` + `tracker_name`, `place_name`. Título sugerido: `"{tracker_name} llegó a {place_name}"` / `"salió de"`; siempre mostrar `observed_at`.

## Realtime

Suscribirse a `postgres_changes` en `locations` (INSERT) y `geofence_events` (INSERT/UPDATE) filtrando por owner para refrescar mapa y centro de notificaciones sin polling. Habilitar en Dashboard > Database > Replication.

## Canales Android (spec §8)

`arrivals`, `departures`, `stale_trackers`, `system`. La Edge Function envía `channel_id` en `android.notification`.

## Adenda 0.5.0 — compartir tags

- `app_trackers` suma la columna final `is_owner boolean`; `at_places` se calcula con los lugares de quien consulta.
- `tracker_shares(tracker_id, user_id, owner_id, created_at)`: solo lectura para dueño e invitado; escritura vía RPC.
- RPC (`authenticated`): `app_share_tracker(p_tracker_id, p_email) → uuid`, `app_unshare_tracker(p_tracker_id, p_user_id default null)`, `app_tracker_shares(p_tracker_id) → (user_id, email, created_at)`, `app_shared_by() → (tracker_id, owner_email)`.
- Invitado: SELECT en `trackers`/`locations` de tags compartidos; INSERT en `geofence_rules` sobre tags visibles con lugares propios. Sin UPDATE de tracker, sin `action_requests`, sin borrar historial.
