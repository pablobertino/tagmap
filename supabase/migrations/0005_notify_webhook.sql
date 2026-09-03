-- Schema: tagmap (convive con Naima en el mismo proyecto; nada en public)
create schema if not exists tagmap;
set search_path = tagmap, public, extensions;

-- TagMap — disparo de la Edge Function `notify` al crear un evento (spec §8).
-- Requiere la extensión pg_net (Dashboard > Database > Extensions > pg_net) y dos settings:
--   alter database postgres set app.notify_url = 'https://<ref>.supabase.co/functions/v1/notify';
--   alter database postgres set app.notify_secret = '<mismo valor que NOTIFY_SECRET en la función>';
-- Alternativa sin SQL: Dashboard > Database > Webhooks sobre geofence_events INSERT → función notify.

create extension if not exists pg_net with schema extensions;

create or replace function notify_geofence_event() returns trigger
language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare url text; secret text;
begin
  if new.status <> 'CREATED' then return null; end if;
  url := current_setting('app.notify_url', true);
  secret := current_setting('app.notify_secret', true);
  if url is null or secret is null then
    raise warning 'app.notify_url/app.notify_secret no configurados; evento % sin enviar', new.id;
    return null;
  end if;
  perform net.http_post(
    url := url,
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-notify-secret', secret),
    body := jsonb_build_object('event_id', new.id),
    timeout_milliseconds := 5000
  );
  return null;
end $$;

create trigger geofence_events_notify
  after insert on geofence_events
  for each row execute function notify_geofence_event();

-- Alerta administrativa: recolector con error / auth vencida (spec §6.6)
create table system_alerts (
  id          uuid primary key default gen_random_uuid(),
  owner_id    uuid not null references profiles(id) on delete cascade,
  kind        text not null,       -- collector_error | auth_expired | tracker_stale
  message     text,
  created_at  timestamptz not null default now(),
  read_at     timestamptz
);
alter table system_alerts enable row level security;
create policy alerts_owner_select on system_alerts for select using (owner_id = auth.uid());
create policy alerts_owner_update on system_alerts for update using (owner_id = auth.uid());

create or replace function collector_status_alert() returns trigger
language plpgsql security definer set search_path = tagmap, public, extensions as $$
begin
  if new.status <> 'ok' and (old.status is distinct from new.status) then
    insert into system_alerts (owner_id, kind, message)
    values (new.owner_id, case new.status when 'auth_expired' then 'auth_expired' else 'collector_error' end, new.message);
  end if;
  return null;
end $$;

create trigger collectors_status_alert
  after update of status on collectors
  for each row execute function collector_status_alert();

grant select, update on system_alerts to authenticated;
grant all on system_alerts to service_role;
