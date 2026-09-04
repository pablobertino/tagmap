# Changelog — TagMap

## 0.5.0 — 2026-09-03

- Compartir tags con otros usuarios (`tracker_shares`, migración 0011): RPC `app_share_tracker` / `app_unshare_tracker` / `app_tracker_shares` / `app_shared_by`; `app_trackers.is_owner`.
- RLS: invitados leen tracker y posiciones; crean reglas sobre tags visibles con sus propios lugares; "en lugares" se calcula con los lugares de quien consulta.
- App: diálogo Compartir (dueño), aviso "compartido por" y acciones ocultas para invitados.
- Alarma "sin señal" (migración 0012): `trackers.stale_alert_hours` (default 12, null = apagada); `check_stale_trackers()` corre en cada `collector_heartbeat`; crea `system_alerts(kind='tracker_stale')` → trigger → Edge Function `notify` (`{"alert_id"}`) → push canal `stale_trackers` con hora local. Una sola alerta por silencio; se reinicia al volver a reportar.
- App: umbral editable en "Editar tag"; sección Alertas en Eventos; `app_mark_alerts_read`.
- Historial: presets por días de calendario; un color por día en el mapa (el más reciente con el color del tag), hora en cada punto (zoom ≥ 12); sin línea entre reportes separados por más de 3 h.
- Recolector multi-cuenta: `python -m tagmap_collector.multi` corre una cuenta de Google por subproceso; los workflows lo usan.
- Cuentas autoservicio (migración 0013): `tagmap.google_accounts` + Supabase Vault; RPC `app_register_google_account` / `app_remove_google_account` / `app_google_account` (usuario) y `collector_list_accounts` (service_role). El recolector recarga las cuentas en cada ciclo. Respaldo: `GFMT_ACCOUNTS_B64` (`phase0/pack_accounts.py`) o `GFMT_SECRETS_B64`.
- Herramienta `tagmap_auth` (`python -m tagmap_auth`, empaquetable con `build_tagmap_auth.ps1`): login TagMap → flujo Chrome del vendor → sube `secrets.json` cifrado y lo borra de la PC.

## 0.4.1 — 2026-09-03

- Push operativas con Firebase; hora local del teléfono (24 h, con zona) en cada aviso (`mobile_devices.tz`, migración 0010).
- Recolector en GitHub Actions en modo run largo: ciclo cada 15 min, se relanza solo; cron de respaldo.
- Fix: permisos `service_role` en `collector_take_actions`/`finish_action` (0008) y cast a `action_status` (0009).
- Fix: un fallo en los pedidos ya no aborta el ciclo de posiciones.

## 0.4.0 · 2026-09-03
- Identidad visual "Carta náutica" (azul + ámbar) e ícono nuevo; temas elegibles: Carta náutica / Claro / Instrumentos.
- Estilo de mapa "Noche" (CARTO dark) y colores de etiquetas según estilo.
- Pin central en lugares; íconos en cápsula en la lista.

## 0.3.1 · 2026-09-03
- Hacer sonar un tag desde el detalle (`action_requests` → workflow `actions.yml` → recolector `--actions-only`).

## 0.3.0 · 2026-09-03
- Lugares favoritos (geocercas) creados manteniendo pulsado el mapa: radio 100–2000 m y color.
- Alarmas de llegada y salida por tag y lugar (pantalla Lugares).
- Centro de eventos.
- Notificaciones push vía FCM (requiere Firebase + Edge Function `notify`).
- Botones de zoom; el encuadre no acerca más de zoom 15,5.
- Pantalla Acerca de con versión y novedades.
- Backend: `trackers.kind`, tabla `app_settings` para la Edge Function.

## 0.2.0 · 2026-09-03
- Login con ver contraseña, recuperación por email (`tagmap://auth`) y registro.
- Ícono de app; íconos y color por tag.
- Estilos de mapa: Calles, Claro, Relieve (OpenTopoMap), Satélite (Esri).
- Rango de fechas a elección y distancia recorrida.
- Mapa general centrado en el tag más reciente.

## 0.1.0 · 2026-09-02
- Primera versión: lista, mapa y detalle con historial.
