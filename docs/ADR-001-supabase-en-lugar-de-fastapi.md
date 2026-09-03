# ADR-001 — Supabase como backend en lugar de FastAPI

**Estado:** Aceptada — 2026-09-02

## Contexto

La spec §4.2 propone FastAPI + SQLite (MVP local) y luego PostgreSQL/Supabase. Pablo pidió que nada dependa de una máquina local: desarrollo local, ejecución en la nube.

## Decisión

Usar Supabase como backend completo:

| Responsabilidad (spec §4.2) | Implementación |
|---|---|
| Registrar tags y posiciones | Tablas `trackers`, `locations`; RPC `ingest_locations` para el recolector |
| Eliminar duplicados | Índice único `(tracker_id, observed_at, latitude, longitude)` + `ON CONFLICT DO NOTHING` |
| Evaluar geocercas | Función SQL `evaluate_geofences()` disparada por trigger `AFTER INSERT ON locations` |
| Registrar eventos y preferencias | Tablas `geofence_rules`, `geofence_events`, `mobile_devices` |
| Entregar datos a la app | PostgREST con RLS por `owner_id = auth.uid()` |
| Notificaciones push | Edge Function `notify` (Deno) → FCM HTTP v1, invocada por Database Webhook en `geofence_events` |
| Auth de la app | Supabase Auth (email/password o magic link); JWT breves + refresh revocable |
| Credencial del recolector | `service_role` key **solo** en el contenedor Fly.io; los RPC internos exigen `auth.role() = 'service_role'` |

El recolector Python corre en Fly.io (contenedor pequeño, siempre encendido, ~US$2-3/mes) con `secrets.json` cargado como secreto en base64.

## Consecuencias

- Menos código: desaparece la capa REST propia, autenticación, migraciones manuales y despliegue de un servidor.
- La app Android usa `supabase-kt` (PostgREST + Auth + Realtime) en lugar de Retrofit.
- Los endpoints de la spec §10 se mapean a tablas/RPC; el contrato se documenta en `docs/CONTRATO-DATOS.md`.
- Lock-in moderado: la lógica de geocercas está en SQL plano y es portable a cualquier Postgres.
- Costo: plan Free de Supabase alcanza para un usuario; se pausa tras 7 días sin actividad, por lo que el heartbeat del recolector lo mantiene activo. Plan Pro (US$25/mes) si se quiere evitar la pausa y tener backups.

## Alternativas descartadas

- **FastAPI en Fly.io + Postgres**: más control, más código y dos servicios que mantener.
- **Home Assistant + GoogleFindMy-HA**: resuelve la captura pero no la app privada ni las geocercas server-side con histéresis.
- **Traccar + google-find-hub-sync**: buena opción si se quisiera solo mapa/historial; no cubre la UX pedida ni FCM propio.
