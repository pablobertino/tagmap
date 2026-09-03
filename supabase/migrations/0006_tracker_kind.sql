-- Aplicada en Naima el 2026-09-03 (tagmap_0006_tracker_kind)
set search_path = tagmap, public, extensions;

alter table trackers add column if not exists kind text not null default 'tag'
  check (kind in ('tag', 'phone', 'other'));
grant select (kind) on trackers to authenticated;

-- collector_sync_trackers acepta "kind"; app_trackers expone t.kind (ver tagmap_0006 en el historial de migraciones)
