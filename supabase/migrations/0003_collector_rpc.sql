-- Schema: tagmap (convive con Naima en el mismo proyecto; nada en public)
create schema if not exists tagmap;
set search_path = tagmap, public, extensions;

-- TagMap — RPC internas del recolector (spec §10 "Recolector").
-- Solo invocables con la service_role key (auth.role() = 'service_role').

create or replace function assert_service_role() returns void
language plpgsql stable set search_path = tagmap, public, extensions as $$
begin
  if coalesce(auth.role(), '') <> 'service_role' then
    raise exception 'forbidden' using errcode = '42501';
  end if;
end $$;

-- POST /internal/v1/trackers/sync
-- p_trackers: [{"provider_device_id":"...","name":"Daniel","supports_sound":false}, ...]
create or replace function collector_sync_trackers(p_collector_id text, p_trackers jsonb)
returns table (tracker_id uuid, device_id text, tracker_name text)
language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare owner uuid;
begin
  perform assert_service_role();
  select owner_id into owner from collectors where collectors.id = p_collector_id;
  if owner is null then
    raise exception 'collector % no registrado', p_collector_id;
  end if;

  return query
  insert into trackers (owner_id, provider_device_id, name, supports_sound, last_synced_at)
  select owner, t.value->>'provider_device_id', t.value->>'name',
         coalesce((t.value->>'supports_sound')::boolean, false), now()
  from jsonb_array_elements(p_trackers) t
  on conflict (owner_id, provider_device_id) do update
    set last_synced_at = now(),
        supports_sound = excluded.supports_sound
        -- el nombre lo controla el usuario en la app; no se pisa
  returning trackers.id, trackers.provider_device_id, trackers.name;
end $$;

-- POST /internal/v1/locations/batch
-- p_locations: [{"provider_device_id":"...","latitude":..,"longitude":..,"accuracy_m":..,
--                "observed_at":"iso","received_at":"iso","source":"find_hub_network",
--                "provider_report_id":null,"raw":{}}]
-- Devuelve cantidad insertada (duplicados ignorados).
create or replace function collector_ingest_locations(p_collector_id text, p_locations jsonb)
returns int
language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare owner uuid; n int;
begin
  perform assert_service_role();
  select owner_id into owner from collectors where collectors.id = p_collector_id;
  if owner is null then
    raise exception 'collector % no registrado', p_collector_id;
  end if;

  with ins as (
    insert into locations (tracker_id, latitude, longitude, accuracy_m, observed_at, received_at,
                           source, provider_report_id, raw)
    select t.id,
           (l->>'latitude')::double precision,
           (l->>'longitude')::double precision,
           (l->>'accuracy_m')::real,
           (l->>'observed_at')::timestamptz,
           coalesce((l->>'received_at')::timestamptz, now()),
           coalesce((l->>'source')::location_source, 'unknown'),
           l->>'provider_report_id',
           l->'raw'
    from jsonb_array_elements(p_locations) l
    join trackers t on t.owner_id = owner and t.provider_device_id = l->>'provider_device_id'
    where t.enabled
    order by (l->>'observed_at')::timestamptz          -- orden cronológico para la máquina de estados
    on conflict do nothing
    returning 1)
  select count(*) into n from ins;

  update collectors set last_seen_at = now(), status = 'ok', message = null
  where collectors.id = p_collector_id;
  return n;
end $$;

-- POST /internal/v1/collector/heartbeat  y  /errors
create or replace function collector_heartbeat(p_collector_id text, p_status collector_status, p_message text default null)
returns void
language plpgsql security definer set search_path = tagmap, public, extensions as $$
begin
  perform assert_service_role();
  update collectors
     set last_seen_at = now(), status = p_status, message = left(p_message, 500)
   where id = p_collector_id;
  if not found then
    raise exception 'collector % no registrado', p_collector_id;
  end if;
end $$;

revoke all on function collector_sync_trackers(text, jsonb) from public, anon, authenticated;
revoke all on function collector_ingest_locations(text, jsonb) from public, anon, authenticated;
revoke all on function collector_heartbeat(text, collector_status, text) from public, anon, authenticated;
