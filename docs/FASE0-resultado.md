# Fase 0 — Resultado

**Fecha:** 2026-09-02 · **Vendor:** GoogleFindMyTools commit `d46e952` (2026-05-05) · **Windows 11, Python 3.14, Chrome**

## Dispositivos en la cuenta

| Nombre | Tipo | Reportes por consulta |
|---|---|---|
| POCO X8 Pro | teléfono Android | 1 (propio) |
| Pablo 1 | Xiaomi Tag | 7 (6 red + 1 propio) |
| Daniel | Xiaomi Tag | 3 (2 red + 1 propio) |
| Pablo 2 | Xiaomi Tag | 7 (6 red + 1 propio) |

## Campos confirmados por reporte

| Campo | Disponible | Observación |
|---|---|---|
| latitude / longitude | sí | 7 decimales |
| accuracy (m) | sí | 11 a 454 m en la muestra; el vendor no lo imprime pero está en el protobuf (`geoLocation.accuracy`) |
| observed_at | sí | epoch real de la detección, no hora de consulta; hubo un reporte con `observed_at` 2 s posterior a `received_at` (desfase de reloj, aceptable) |
| is_own_report | sí | `own_device` = último reporte del teléfono propietario; `find_hub_network` = red colaborativa |
| status | sí | LAST_KNOWN / CROWDSOURCED / AGGREGATED; SEMANTIC (sin coordenadas) se descarta |
| altitude | sí | se guarda en `raw` |
| provider_report_id | no | dedup por `(tracker, observed_at, lat, lon)` |
| hacer sonar | pendiente | el vendor tiene `PlaySound`; validar en Fase 4 |

## Autenticación

`Auth/secrets.json` queda con `fcm_credentials`, `username`, `aas_token`, `shared_key`, `owner_key`. Chrome hizo falta dos veces (login inicial y login E2EE); después, ninguna. El adaptador rechaza arrancar si falta alguno de los cinco, para no bloquearse pidiendo `input()` en la nube.

## Latencia

~2 s por dispositivo (request → push FCM → descifrado). 4 dispositivos ≈ 8 s por ciclo.

## Pendiente del criterio de aprobación

48 h sin intervención manual: se mide con el recolector desplegado (Fly.io → Supabase) en lugar de `phase0.sample_loop`, ya que es el mismo código. Verificar el 2026-09-05 con:

```sql
select t.name, count(*) as posiciones, min(l.observed_at), max(l.observed_at),
       percentile_cont(0.5) within group (order by l.accuracy_m) as precision_mediana
from tagmap.locations l join tagmap.trackers t on t.id = l.tracker_id
group by t.name order by t.name;
select id, status, last_seen_at, message from tagmap.collectors;
```

## Decisiones derivadas

- Contrato de datos congelado: `docs/CONTRATO-DATOS.md`.
- Intervalo del recolector: 15 min. La red entregó reportes cada 2–20 min para los tags en movimiento; con 15 min y 6–7 reportes por consulta no se pierde historial.
- El teléfono (`POCO X8 Pro`) se sincroniza como tracker más; se puede deshabilitar desde la app (`trackers.enabled`).
