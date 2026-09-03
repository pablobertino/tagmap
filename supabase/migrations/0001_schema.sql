-- Schema: tagmap (convive con Naima en el mismo proyecto; nada en public)
create schema if not exists tagmap;
set search_path = tagmap, public, extensions;

-- TagMap — esquema base (spec §9). Postgres 15+ / Supabase.
-- Aplicar con `supabase db push` o desde el SQL Editor.

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------- enums
create type location_source as enum ('find_hub_network', 'own_device', 'unknown');
create type location_quality as enum ('good', 'medium', 'low');            -- spec §13
create type geofence_state as enum ('UNKNOWN', 'INSIDE', 'OUTSIDE', 'ENTRY_PENDING', 'EXIT_PENDING');
create type geofence_event_type as enum ('ENTRY', 'EXIT');
create type geofence_event_status as enum ('CREATED', 'SENT', 'READ', 'SUPPRESSED');
create type collector_status as enum ('ok', 'error', 'auth_expired');

-- ---------------------------------------------------------------- users
-- Perfil 1:1 con auth.users. Se crea por trigger al registrarse.
create table profiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  email       text not null,
  created_at  timestamptz not null default now(),
  -- preferencias de la app (spec §6.7)
  recent_threshold_minutes int not null default 30,
  retention_days           int not null default 90 check (retention_days in (30, 90, 180, 365)),
  quiet_hours              jsonb,          -- {"from":"23:00","to":"07:00","tz":"America/Argentina/Buenos_Aires"}
  notify_first_detection   boolean not null default false
);

-- Sin trigger sobre auth.users (el proyecto se comparte con Naima): el perfil se crea
-- bajo demanda cuando la app llama a ensure_profile() tras iniciar sesión.
create or replace function ensure_profile() returns profiles
language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare p profiles;
begin
  if auth.uid() is null then raise exception 'not authenticated'; end if;
  insert into profiles (id, email)
  select u.id, u.email from auth.users u where u.id = auth.uid()
  on conflict (id) do nothing;
  select * into p from profiles where id = auth.uid();
  return p;
end $$;

-- ---------------------------------------------------------------- collectors
-- Cada instancia del recolector pertenece a un usuario. La service_role key
-- solo vive en el contenedor; las RPC internas validan auth.role() = 'service_role'.
create table collectors (
  id            text primary key,                       -- COLLECTOR_ID del .env
  owner_id      uuid not null references profiles(id) on delete cascade,
  created_at    timestamptz not null default now(),
  last_seen_at  timestamptz,
  status        collector_status not null default 'ok',
  message       text,
  interval_minutes int not null default 15 check (interval_minutes >= 5)
);

-- ---------------------------------------------------------------- trackers
create table trackers (
  id                  uuid primary key default gen_random_uuid(),
  owner_id            uuid not null references profiles(id) on delete cascade,
  provider_device_id  text not null,      -- ID del conector; nunca se expone a la app (ver vista app_trackers)
  name                text not null,
  icon                text not null default 'tag',
  color               text not null default '#1E88E5',
  enabled             boolean not null default true,
  supports_sound      boolean not null default false,
  created_at          timestamptz not null default now(),
  last_synced_at      timestamptz,
  unique (owner_id, provider_device_id)
);
create index trackers_owner_idx on trackers(owner_id);

-- ---------------------------------------------------------------- locations
create table locations (
  id                  bigint generated always as identity primary key,
  tracker_id          uuid not null references trackers(id) on delete cascade,
  latitude            double precision not null check (latitude between -90 and 90),
  longitude           double precision not null check (longitude between -180 and 180),
  accuracy_m          real,
  observed_at         timestamptz not null,
  received_at         timestamptz not null default now(),
  source              location_source not null default 'unknown',
  provider_report_id  text,
  raw                 jsonb,               -- dato original sin secretos (spec §13)
  quality             location_quality generated always as (
                        case
                          when accuracy_m is null then 'low'::location_quality
                          when accuracy_m <= 100 then 'good'::location_quality
                          when accuracy_m <= 300 then 'medium'::location_quality
                          else 'low'::location_quality
                        end) stored
);
-- Deduplicación (spec §9): mismo tag, misma hora, mismo punto.
create unique index locations_dedup_idx
  on locations (tracker_id, observed_at, round(latitude::numeric, 6), round(longitude::numeric, 6));
create index locations_tracker_observed_idx on locations (tracker_id, observed_at desc);

-- ---------------------------------------------------------------- places
create table places (
  id          uuid primary key default gen_random_uuid(),
  owner_id    uuid not null references profiles(id) on delete cascade,
  name        text not null,
  icon        text not null default 'place',
  color       text not null default '#43A047',
  latitude    double precision not null check (latitude between -90 and 90),
  longitude   double precision not null check (longitude between -180 and 180),
  radius_m    int not null default 200 check (radius_m >= 100),   -- spec §6.5
  created_at  timestamptz not null default now()
);
create index places_owner_idx on places(owner_id);

-- ---------------------------------------------------------------- geofence rules
create table geofence_rules (
  id                    uuid primary key default gen_random_uuid(),
  owner_id              uuid not null references profiles(id) on delete cascade,
  tracker_id            uuid not null references trackers(id) on delete cascade,
  place_id              uuid not null references places(id) on delete cascade,   -- spec §15.10
  enabled               boolean not null default true,
  notify_entry          boolean not null default true,
  notify_exit           boolean not null default true,
  -- {"days":[1,2,3,4,5],"from":"08:00","to":"20:00","tz":"America/Argentina/Buenos_Aires"}; null = siempre
  active_schedule       jsonb,
  confirmation_minutes  int not null default 10 check (confirmation_minutes between 0 and 60),
  cooldown_minutes      int not null default 30 check (cooldown_minutes between 0 and 1440),
  muted_until           timestamptz,               -- silencio temporal (spec §2.1)
  -- estado de la máquina (spec §7.3)
  current_state         geofence_state not null default 'UNKNOWN',
  pending_since         timestamptz,
  pending_reports       int not null default 0,
  last_observed_at      timestamptz,               -- evita procesar reportes fuera de orden
  last_event_at         timestamptz,
  created_at            timestamptz not null default now(),
  unique (tracker_id, place_id)
);
create index geofence_rules_tracker_idx on geofence_rules(tracker_id) where enabled;

-- ---------------------------------------------------------------- events
create table geofence_events (
  id            uuid primary key default gen_random_uuid(),
  owner_id      uuid not null references profiles(id) on delete cascade,
  rule_id       uuid not null references geofence_rules(id) on delete cascade,
  tracker_id    uuid not null references trackers(id) on delete cascade,
  place_id      uuid not null references places(id) on delete cascade,
  location_id   bigint references locations(id) on delete set null,
  event_type    geofence_event_type not null,
  observed_at   timestamptz not null,
  created_at    timestamptz not null default now(),
  notified_at   timestamptz,
  read_at       timestamptz,
  status        geofence_event_status not null default 'CREATED',
  suppress_reason text,
  -- copia de lo que provocó la transición (spec §7.2)
  latitude      double precision,
  longitude     double precision,
  accuracy_m    real,
  distance_m    real
);
create index geofence_events_owner_created_idx on geofence_events(owner_id, created_at desc);
create index geofence_events_pending_idx on geofence_events(status) where status = 'CREATED';

-- ---------------------------------------------------------------- mobile devices
create table mobile_devices (
  id                     uuid primary key default gen_random_uuid(),
  user_id                uuid not null references profiles(id) on delete cascade,
  fcm_token              text not null,
  device_name            text,
  notifications_enabled  boolean not null default true,
  created_at             timestamptz not null default now(),
  last_seen_at           timestamptz not null default now(),
  unique (user_id, fcm_token)
);

-- ---------------------------------------------------------------- auditoría (spec §11)
create table audit_log (
  id          bigint generated always as identity primary key,
  owner_id    uuid,
  action      text not null,
  entity      text,
  entity_id   text,
  at          timestamptz not null default now(),
  details     jsonb
);
create index audit_log_owner_idx on audit_log(owner_id, at desc);
