# TagMap

App Android privada para ver la última posición e historial de tags Xiaomi vinculados a Google Find Hub, con lugares (geocercas) y avisos de llegada/salida.

Especificación completa: [ESPECIFICACION_APP_ANDROID_FIND_HUB.md](ESPECIFICACION_APP_ANDROID_FIND_HUB.md).

## Arquitectura (decisión: todo en la nube)

```
Google Find Hub ──> collector/ (Python, GitHub Actions cada 15 min) ──> Supabase · schema `tagmap` (proyecto Naima)
                                                        │  trigger evaluate_geofences()
                                                        ├──> supabase/functions/notify (FCM)
                                                        └──> android-app/ (Kotlin, Compose, supabase-kt)
```

Cambio respecto a la spec §4.2: **no hay FastAPI**. Supabase (proyecto Naima, schema `tagmap`) provee la API (PostgREST + RPC), autenticación, RLS y funciones serverless. Ver [docs/ADR-001-supabase-en-lugar-de-fastapi.md](docs/ADR-001-supabase-en-lugar-de-fastapi.md).

## Estructura

```
.github/workflows/  collector.yml (cron 15 min) · actions.yml (hacer sonar, disparado por Supabase)
collector/          Recolector Python: TrackerProvider + adaptador GoogleFindMyTools, phase0/
supabase/
  migrations/       Esquema, RLS, RPC, geocercas, action_requests
  functions/notify  Edge Function → Firebase Cloud Messaging
android-app/        App Kotlin/Compose (MapLibre, supabase-kt)
docs/               ADRs, guías por fase, contrato de datos
CHANGELOG.md        Versiones de la app
```

## Estado

| Fase | Estado |
|---|---|
| 0 Prueba técnica | Aprobada 2026-09-02 (`docs/FASE0-resultado.md`) |
| 1 Backend | Supabase (Naima/`tagmap`) + recolector en GitHub Actions (`docs/FASE1-despliegue.md`) |
| 2 App | v0.3.x: lista, mapa, detalle, historial, lugares |
| 3 Alarmas | Geocercas y eventos operativos; push pendiente de Firebase (`docs/FASE3-notificaciones.md`) |
| 4 Mejoras | Hacer sonar implementado (requiere `github_token` en `tagmap.app_settings`) |

## Seguridad

`collector/secrets.json`, `collector/.env`, `google-services.json` y `keys.txt` están en `.gitignore`. Los secretos del recolector viajan como GitHub Secrets; los de la Edge Function como Supabase Secrets; el PAT de GitHub para "hacer sonar" vive solo en `tagmap.app_settings` (tabla sin acceso para la app).
