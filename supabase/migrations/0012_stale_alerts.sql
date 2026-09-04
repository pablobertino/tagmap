-- 0012: alarma "sin señal": avisar cuando un tag lleva más de N horas sin reportar.
-- Configurable por tag (stale_alert_hours; null = apagada; default 12 h). La evalúa el recolector en
-- cada ciclo (dentro de collector_heartbeat). Se avisa una sola vez por "silencio": al volver a
-- reportar se reinicia y, si vuelve a callarse, se avisa de nuevo. El aviso queda en system_alerts
-- (visible en la pantalla Eventos) y se manda por push (trigger → Edge Function notify).
set search_path = tagmap, public, extensions;

alter table trackers
  add column if not exists stale_alert_hours int check (stale_alert_hours is null or stale_alert_hours between 1 and 720),
  add column if not exists stale_alerted_for timestamptz;   -- observed_at del último reporte por el que ya se avisó
update trackers set stale_alert_hours = 12 where stale_alert_hours is null;

alter table system_alerts add column if not exists tracker_id uuid references trackers(id) on delete cascade;

-- la app puede leer y cambiar el umbral
grant select (stale_alert_hours) on trackers to authenticated;
grant update (stale_alert_hours) on trackers to authenticated;

drop view if exists app_trackers;
create or replace view app_trackers with (security_invoker = true) as
select t.id, t.owner_id, t.name, t.icon, t.color, t.kind, t.enabled, t.supports_sound, t.last_synced_at,
       l.id          as last_location_id,
       l.latitude, l.longitude, l.accuracy_m, l.quality, l.source,
       l.observed_at as last_observed_at,
       l.received_at as last_received_at,
       extract(epoch from (now() - l.observed_at))::int / 60 as age_minutes,
       (select string_agg(p.name, ', ' order by p.name)
          from places p
         where p.owner_id = auth.uid()
           and l.id is not null
           and haversine_m(l.latitude, l.longitude, p.latitude, p.longitude) <= p.radius_m) as at_places,
       (t.owner_id = auth.uid()) as is_owner,
       t.stale_alert_hours
from trackers t
left join lateral (
  select x.id, x.latitude, x.longitude, x.accuracy_m, x.quality, x.source, x.observed_at, x.received_at
  from locations x where x.tracker_id = t.id order by x.observed_at desc limit 1
) l on true;
grant select on app_trackers to authenticated;

-- Evaluación (solo service_role, la llama collector_heartbeat)
create or replace function check_stale_trackers() returns int
language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare r record; n int := 0; v_hours int;
begin
  for r in
    select t.id, t.owner_id, t.name, t.stale_alert_hours, t.stale_alerted_for,
           (select max(observed_at) from locations l where l.tracker_id = t.id) as last_seen
      from trackers t
     where t.enabled and t.stale_alert_hours is not null
  loop
    if r.last_seen is null then continue; end if;
    if r.last_seen > now() - make_interval(hours => r.stale_alert_hours) then continue; end if;
    if r.stale_alerted_for is not distinct from r.last_seen then continue; end if;  -- ya avisado
    v_hours := extract(epoch from (now() - r.last_seen))::int / 3600;
    insert into system_alerts (owner_id, kind, tracker_id, message)
    values (r.owner_id, 'tracker_stale', r.id,
            format('%s no reporta desde hace %s h', r.name, v_hours));  -- la hora local la agrega la Edge Function
    update trackers set stale_alerted_for = r.last_seen where id = r.id;
    n := n + 1;
  end loop;
  return n;
end $$;
revoke all on function check_stale_trackers() from public, anon, authenticated;
grant execute on function check_stale_trackers() to service_role;

-- Engancharla al heartbeat del recolector (cada ciclo)
create or replace function collector_heartbeat(p_collector_id text, p_status collector_status, p_message text default null)
returns void language plpgsql security definer set search_path = tagmap, public, extensions as $$
begin
  perform assert_service_role();
  update collectors
     set last_seen_at = now(), status = p_status, message = left(p_message, 500)
   where id = p_collector_id;
  if not found then
    raise exception 'collector % no registrado', p_collector_id;
  end if;
  perform check_stale_trackers();
end $$;

-- Push del aviso: trigger en system_alerts → Edge Function notify ({"alert_id": ...})
create or replace function notify_system_alert() returns trigger
language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare url text; secret text; enabled text;
begin
  select value into enabled from app_settings where key = 'notify_enabled';
  if enabled is distinct from 'true' then return null; end if;
  select value into url from app_settings where key = 'notify_url';
  select value into secret from app_settings where key = 'notify_secret';
  if url is null or secret is null then return null; end if;
  perform net.http_post(
    url := url,
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-notify-secret', secret),
    body := jsonb_build_object('alert_id', new.id),
    timeout_milliseconds := 5000);
  return null;
end $$;
drop trigger if exists system_alerts_notify on system_alerts;
create trigger system_alerts_notify after insert on system_alerts
  for each row execute function notify_system_alert();

-- La app marca alertas leídas
create or replace function app_mark_alerts_read(p_ids uuid[]) returns int
language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare n int;
begin
  update system_alerts set read_at = now() where id = any(p_ids) and owner_id = auth.uid() and read_at is null;
  get diagnostics n = row_count;
  return n;
end $$;
revoke all on function app_mark_alerts_read(uuid[]) from public, anon;
grant execute on function app_mark_alerts_read(uuid[]) to authenticated;
