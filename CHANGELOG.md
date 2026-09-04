# Changelog — TagMap

## 0.5.0 — 2026-09-03

- Compartir tags con otros usuarios (`tracker_shares`, migración 0011): RPC `app_share_tracker` / `app_unshare_tracker` / `app_tracker_shares` / `app_shared_by`; `app_trackers.is_owner`.
- RLS: invitados leen tracker y posiciones; crean reglas sobre tags visibles con sus propios lugares; "en lugares" se calcula con los lugares de quien consulta.
- App: diálogo Compartir (dueño), aviso "compartido por" y acciones ocultas para invitados.

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
