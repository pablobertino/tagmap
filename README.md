# TagMap

App Android privada para ver la última posición e historial de tags Xiaomi vinculados a Google Find Hub, con lugares (geocercas) y avisos de llegada/salida.

Especificación completa: [ESPECIFICACION_APP_ANDROID_FIND_HUB.md](ESPECIFICACION_APP_ANDROID_FIND_HUB.md).

## Arquitectura (decisión: todo en la nube)

```
Google Find Hub ──> collector/ (Python, Fly.io) ──> Supabase (Postgres + Auth + RLS)
                                                        │  trigger evaluate_geofences()
                                                        ├──> supabase/functions/notify (FCM)
                                                        └──> android-app/ (Kotlin, Compose, supabase-kt)
```

Cambio respecto a la spec §4.2: **no hay FastAPI**. Supabase (proyecto Naima, schema `tagmap`) provee la API (PostgREST + RPC), autenticación, RLS y funciones serverless. Ver [docs/ADR-001-supabase-en-lugar-de-fastapi.md](docs/ADR-001-supabase-en-lugar-de-fastapi.md).

## Estructura

```
collector/          Recolector Python (Fase 0 y Fase 1)
  providers/        Interfaz TrackerProvider + adaptador GoogleFindMyTools
  phase0/           Scripts de prueba técnica (48-72 h)
supabase/
  migrations/       Esquema, RLS, RPC, geocercas
  functions/notify  Edge Function → Firebase Cloud Messaging
android-app/        Fase 2 (pendiente hasta congelar contrato de datos)
docs/               ADRs y guías
```

## Orden de trabajo

1. **Fase 0** — [docs/FASE0-prueba-tecnica.md](docs/FASE0-prueba-tecnica.md). Obligatoria. Sin esto no se construye la app.
2. **Fase 1** — Crear proyecto Supabase, aplicar migraciones, desplegar recolector en Fly.io. [docs/FASE1-despliegue.md](docs/FASE1-despliegue.md).
3. **Fase 2** — App Android.

## Seguridad

`collector/secrets.json` y `collector/.env` están en `.gitignore`. Nunca se suben al repo, al APK ni a Supabase. En Fly.io viajan como secretos (`fly secrets set`).
