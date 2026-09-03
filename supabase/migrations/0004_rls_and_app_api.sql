-- Schema: tagmap (convive con Naima en el mismo proyecto; nada en public)
create schema if not exists tagmap;
set search_path = tagmap, public, extensions;

-- TagMap — RLS y API para la app Android (spec §10 "Aplicación Android", §11).
-- La app usa la anon key + JWT de Supabase Auth. Todo se filtra por auth.uid().

alter table profiles        enable row level security;
alter table collectors      enable row level security;
alter table trackers        enable row level security;
alter table locations       enable row level security;
alter table places          enable row level security;
alter table geofence_rules  enable row level security;
alter table geofence_events enable row level security;
alter table mobile_devices  enable row level security;
alter table audit_log       enable row level security;

-- profiles: leer y editar el propio
create policy profiles_self_select on profiles for select using (id = auth.uid());
create policy profiles_self_update on profiles for update using (id = auth.uid());

-- collectors: solo lectura del estado (para "Estado del sistema", spec §6.6)
create policy collectors_owner_select on collectors for select using (owner_id = auth.uid());

-- trackers: el usuario puede renombrar/colorear/deshabilitar, no crear (los crea el recolector)
create policy trackers_owner_select on trackers for select using (owner_id = auth.uid());
create policy trackers_owner_update on trackers for update using (owner_id = auth.uid());
-- provider_device_id no se expone a la app: privilegios a nivel columna.
-- (Un REVOKE de columna no restringe si existe GRANT de tabla; hay que revocar tabla y otorgar columnas.)
revoke select, update on trackers from anon, authenticated;
grant select (id, owner_id, name, icon, color, enabled, supports_sound, created_at, last_synced_at)
  on trackers to authenticated;
grant update (name, icon, color, enabled) on trackers to authenticated;

-- locations: solo lectura; borrado por tag permitido (spec §11 "eliminar historial por tag")
create policy locations_owner_select on locations for select
  using (exists (select 1 from trackers t where t.id = locations.tracker_id and t.owner_id = auth.uid()));
create policy locations_owner_delete on locations for delete
  using (exists (select 1 from trackers t where t.id = locations.tracker_id and t.owner_id = auth.uid()));
revoke select on locations from anon, authenticated;
grant select (id, tracker_id, latitude, longitude, accuracy_m, observed_at, received_at, source, quality)
  on locations to authenticated;

-- places: CRUD completo
create policy places_owner_all on places for all
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());

-- geofence_rules: CRUD, pero el estado interno solo lo escribe el trigger
create policy rules_owner_select on geofence_rules for select using (owner_id = auth.uid());
create policy rules_owner_insert on geofence_rules for insert
  with check (owner_id = auth.uid()
    and exists (select 1 from trackers t where t.id = tracker_id and t.owner_id = auth.uid())
    and exists (select 1 from places  p where p.id = place_id   and p.owner_id = auth.uid()));
create policy rules_owner_update on geofence_rules for update
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy rules_owner_delete on geofence_rules for delete using (owner_id = auth.uid());
revoke update on geofence_rules from anon, authenticated;
grant update (enabled, notify_entry, notify_exit, active_schedule, confirmation_minutes,
              cooldown_minutes, muted_until)
  on geofence_rules to authenticated;

-- events: leer y marcar leído
create policy events_owner_select on geofence_events for select using (owner_id = auth.uid());
create policy events_owner_update on geofence_events for update using (owner_id = auth.uid());
revoke update on geofence_events from anon, authenticated;
grant update (read_at) on geofence_events to authenticated;

-- mobile devices: cada usuario administra los suyos
create policy devices_owner_all on mobile_devices for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- audit: solo lectura propia
create policy audit_owner_select on audit_log for select using (owner_id = auth.uid());

-- ---------------------------------------------------------------- vistas para la app
-- GET /v1/trackers  → última posición + lugar reconocido + antigüedad
create or replace view app_trackers with (security_invoker = true) as
select t.id, t.owner_id, t.name, t.icon, t.color, t.enabled, t.supports_sound, t.last_synced_at,
       l.id          as last_location_id,
       l.latitude, l.longitude, l.accuracy_m, l.quality, l.source,
       l.observed_at as last_observed_at,
       l.received_at as last_received_at,
       extract(epoch from (now() - l.observed_at))::int / 60 as age_minutes,
       (select string_agg(p.name, ', ' order by p.name)
          from places p
         where p.owner_id = t.owner_id
           and l.id is not null
           and haversine_m(l.latitude, l.longitude, p.latitude, p.longitude) <= p.radius_m) as at_places
from trackers t
left join lateral (
  select x.id, x.latitude, x.longitude, x.accuracy_m, x.quality, x.source, x.observed_at, x.received_at
  from locations x where x.tracker_id = t.id order by x.observed_at desc limit 1
) l on true;

grant select on app_trackers to authenticated;

-- GET /v1/events → con nombres para renderizar "Daniel llegó a Casa"
create or replace view app_events with (security_invoker = true) as
select e.*, t.name as tracker_name, p.name as place_name
from geofence_events e
join trackers t on t.id = e.tracker_id
join places p on p.id = e.place_id;

grant select on app_events to authenticated;

-- ---------------------------------------------------------------- RPC de la app
-- GET /v1/trackers/{id}/locations?from=&to=&accuracy_max=
create or replace function app_locations(p_tracker_id uuid, p_from timestamptz, p_to timestamptz,
                                         p_accuracy_max real default null, p_limit int default 2000)
returns table (id bigint, latitude double precision, longitude double precision, accuracy_m real,
               quality location_quality, source location_source, observed_at timestamptz)
language sql stable security invoker set search_path = tagmap, public, extensions as $$
  select l.id, l.latitude, l.longitude, l.accuracy_m, l.quality, l.source, l.observed_at
  from locations l
  where l.tracker_id = p_tracker_id
    and l.observed_at between p_from and p_to
    and (p_accuracy_max is null or l.accuracy_m is null or l.accuracy_m <= p_accuracy_max)
  order by l.observed_at
  limit p_limit;
$$;

-- POST /v1/mobile-devices/register
create or replace function app_register_device(p_fcm_token text, p_device_name text default null)
returns uuid language plpgsql security invoker set search_path = tagmap, public, extensions as $$
declare d uuid;
begin
  insert into mobile_devices (user_id, fcm_token, device_name)
  values (auth.uid(), p_fcm_token, p_device_name)
  on conflict (user_id, fcm_token) do update set last_seen_at = now(), device_name = excluded.device_name
  returning id into d;
  return d;
end $$;

-- Marcar eventos leídos
create or replace function app_mark_events_read(p_event_ids uuid[])
returns int language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare n int;
begin
  update geofence_events set status = 'READ', read_at = now()
   where id = any(p_event_ids) and owner_id = auth.uid() and status in ('CREATED', 'SENT');
  get diagnostics n = row_count;
  return n;
end $$;

-- Silenciar temporalmente una regla (spec §2.1)
create or replace function app_mute_rule(p_rule_id uuid, p_minutes int)
returns void language plpgsql security definer set search_path = tagmap, public, extensions as $$
begin
  update geofence_rules set muted_until = now() + make_interval(mins => p_minutes)
   where id = p_rule_id and owner_id = auth.uid();
end $$;

-- Eliminar historial de un tag
create or replace function app_delete_tracker_history(p_tracker_id uuid)
returns int language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare n int;
begin
  delete from locations l using trackers t
   where l.tracker_id = t.id and t.id = p_tracker_id and t.owner_id = auth.uid();
  get diagnostics n = row_count;
  update geofence_rules set current_state = 'UNKNOWN', pending_since = null, pending_reports = 0,
                            last_observed_at = null
   where tracker_id = p_tracker_id and owner_id = auth.uid();
  insert into audit_log (owner_id, action, entity, entity_id)
  values (auth.uid(), 'delete_history', 'tracker', p_tracker_id::text);
  return n;
end $$;

-- ---------------------------------------------------------------- auditoría automática
create or replace function audit_row_change() returns trigger
language plpgsql security definer set search_path = tagmap, public, extensions as $$
begin
  insert into audit_log (owner_id, action, entity, entity_id, details)
  values (coalesce(auth.uid(), (case when tg_op = 'DELETE' then old.owner_id else new.owner_id end)),
          lower(tg_op), tg_table_name,
          (case when tg_op = 'DELETE' then old.id else new.id end)::text,
          case when tg_op = 'DELETE' then null else to_jsonb(new) - 'current_state' - 'pending_since' end);
  return null;
end $$;

create trigger places_audit after insert or update or delete on places
  for each row execute function audit_row_change();
-- pg_trigger_depth() = 0 excluye las actualizaciones de estado hechas por evaluate_geofences()
create trigger rules_audit after insert or update or delete on geofence_rules
  for each row when (pg_trigger_depth() = 0) execute function audit_row_change();

-- ---------------------------------------------------------------- privilegios del schema
-- Exponer `tagmap` en Dashboard > Settings > API > Exposed schemas (además de public).
grant usage on schema tagmap to authenticated, service_role;
grant all on all tables in schema tagmap to service_role;
grant all on all sequences in schema tagmap to service_role;
grant all on all functions in schema tagmap to service_role;
-- authenticated: lo no cubierto arriba por grants de columna
grant select, update on profiles to authenticated;
grant select on collectors to authenticated;
grant delete on locations to authenticated;
grant select, insert, update, delete on places to authenticated;
grant select, insert, delete on geofence_rules to authenticated;
grant select on geofence_events to authenticated;
grant select, insert, update, delete on mobile_devices to authenticated;
grant select on audit_log to authenticated;
grant usage, select on all sequences in schema tagmap to authenticated;
grant execute on function ensure_profile(), app_locations(uuid, timestamptz, timestamptz, real, int),
  app_register_device(text, text), app_mark_events_read(uuid[]), app_mute_rule(uuid, int),
  app_delete_tracker_history(uuid), haversine_m(double precision, double precision, double precision, double precision)
  to authenticated;
-- anon: nada
revoke all on all tables in schema tagmap from anon;
revoke all on all functions in schema tagmap from anon;
revoke usage on schema tagmap from anon;
