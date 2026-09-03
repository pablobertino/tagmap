-- Schema: tagmap (convive con Naima en el mismo proyecto; nada en public)
create schema if not exists tagmap;
set search_path = tagmap, public, extensions;

-- TagMap — evaluación de geocercas en servidor (spec §7).
-- Se ejecuta por trigger AFTER INSERT en locations.

-- ---------------------------------------------------------------- haversine
create or replace function haversine_m(lat1 double precision, lon1 double precision,
                                       lat2 double precision, lon2 double precision)
returns double precision language sql immutable parallel safe set search_path = tagmap, public, extensions as $$
  select 2 * 6371008.8 * asin(sqrt(
    power(sin(radians(lat2 - lat1) / 2), 2) +
    cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lon2 - lon1) / 2), 2)
  ));
$$;

-- ---------------------------------------------------------------- horario activo
-- active_schedule: {"days":[0..6] (0=domingo), "from":"HH:MM", "to":"HH:MM", "tz":"..."}.
-- Soporta rangos que cruzan medianoche (from > to). null => siempre activo.
create or replace function schedule_is_active(schedule jsonb, at_ts timestamptz)
returns boolean language plpgsql stable set search_path = tagmap, public, extensions as $$
declare
  tz    text;
  local timestamp;
  dow   int;
  t     time;
  t_from time;
  t_to   time;
begin
  if schedule is null then return true; end if;
  tz := coalesce(schedule->>'tz', 'UTC');
  local := at_ts at time zone tz;
  dow := extract(dow from local);
  if schedule ? 'days' and not (schedule->'days') @> to_jsonb(dow) then
    return false;
  end if;
  if schedule ? 'from' and schedule ? 'to' then
    t := local::time;
    t_from := (schedule->>'from')::time;
    t_to := (schedule->>'to')::time;
    if t_from <= t_to then
      return t between t_from and t_to;
    else
      return t >= t_from or t <= t_to;
    end if;
  end if;
  return true;
end $$;

-- ---------------------------------------------------------------- horario de silencio del usuario
create or replace function in_quiet_hours(owner uuid, at_ts timestamptz)
returns boolean language sql stable set search_path = tagmap, public, extensions as $$
  select coalesce(
    (select case when p.quiet_hours is null then false
                 else schedule_is_active(p.quiet_hours, at_ts) end
     from profiles p where p.id = owner),
    false);
$$;

-- ---------------------------------------------------------------- evaluación
create or replace function evaluate_geofences()
returns trigger language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare
  r            geofence_rules%rowtype;
  p            places%rowtype;
  dist         double precision;
  hyst         double precision;
  raw_state    geofence_state;        -- INSIDE / OUTSIDE / null (zona de histéresis)
  new_state    geofence_state;
  ev_type      geofence_event_type;
  ev_status    geofence_event_status;
  ev_reason    text;
  pending_ok   boolean;
begin
  for r in
    select * from geofence_rules
    where tracker_id = new.tracker_id and enabled
    for update
  loop
    -- Reportes fuera de orden: no retroceder la máquina de estados.
    if r.last_observed_at is not null and new.observed_at <= r.last_observed_at then
      continue;
    end if;

    select * into p from places where id = r.place_id;
    dist := haversine_m(new.latitude, new.longitude, p.latitude, p.longitude);
    hyst := greatest(50, 0.2 * p.radius_m);                              -- spec §7.2

    -- Precisión claramente peor que el radio: no cambiar de estado.
    if new.accuracy_m is not null and new.accuracy_m > 2 * p.radius_m then
      update geofence_rules set last_observed_at = new.observed_at where id = r.id;
      continue;
    end if;

    raw_state := case
      when dist <= p.radius_m then 'INSIDE'::geofence_state
      when dist > p.radius_m + hyst then 'OUTSIDE'::geofence_state
      else null
    end;

    new_state := r.current_state;
    ev_type := null;

    if raw_state is null then
      -- Zona gris: mantener estado, pero un pendiente sin confirmar se cancela.
      if r.current_state in ('ENTRY_PENDING', 'EXIT_PENDING') then
        new_state := case r.current_state when 'ENTRY_PENDING' then 'OUTSIDE' else 'INSIDE' end;
        r.pending_since := null; r.pending_reports := 0;
      end if;

    elsif r.current_state = 'UNKNOWN' then
      -- Estado inicial: no alerta, salvo notify_first_detection (spec §7.3).
      new_state := raw_state;
      if (select notify_first_detection from profiles where id = r.owner_id) then
        ev_type := case raw_state when 'INSIDE' then 'ENTRY'::geofence_event_type else 'EXIT'::geofence_event_type end;
      end if;

    elsif (r.current_state = 'INSIDE' and raw_state = 'OUTSIDE') then
      new_state := 'EXIT_PENDING'; r.pending_since := new.observed_at; r.pending_reports := 1;
    elsif (r.current_state = 'OUTSIDE' and raw_state = 'INSIDE') then
      new_state := 'ENTRY_PENDING'; r.pending_since := new.observed_at; r.pending_reports := 1;

    elsif r.current_state = 'EXIT_PENDING' then
      if raw_state = 'INSIDE' then
        new_state := 'INSIDE'; r.pending_since := null; r.pending_reports := 0;   -- falsa alarma
      else
        r.pending_reports := r.pending_reports + 1;
        pending_ok := r.pending_reports >= 2
                   or (new.observed_at - r.pending_since) >= make_interval(mins => r.confirmation_minutes);
        if pending_ok then
          new_state := 'OUTSIDE'; ev_type := 'EXIT'; r.pending_since := null; r.pending_reports := 0;
        end if;
      end if;
    elsif r.current_state = 'ENTRY_PENDING' then
      if raw_state = 'OUTSIDE' then
        new_state := 'OUTSIDE'; r.pending_since := null; r.pending_reports := 0;
      else
        r.pending_reports := r.pending_reports + 1;
        pending_ok := r.pending_reports >= 2
                   or (new.observed_at - r.pending_since) >= make_interval(mins => r.confirmation_minutes);
        if pending_ok then
          new_state := 'INSIDE'; ev_type := 'ENTRY'; r.pending_since := null; r.pending_reports := 0;
        end if;
      end if;
    end if;

    -- Confirmación inmediata cuando confirmation_minutes = 0 (un solo reporte).
    if r.confirmation_minutes = 0 and new_state = 'EXIT_PENDING' then
      new_state := 'OUTSIDE'; ev_type := 'EXIT'; r.pending_since := null; r.pending_reports := 0;
    elsif r.confirmation_minutes = 0 and new_state = 'ENTRY_PENDING' then
      new_state := 'INSIDE'; ev_type := 'ENTRY'; r.pending_since := null; r.pending_reports := 0;
    end if;

    -- Evento
    if ev_type is not null then
      ev_status := 'CREATED'; ev_reason := null;
      if (ev_type = 'ENTRY' and not r.notify_entry) or (ev_type = 'EXIT' and not r.notify_exit) then
        ev_status := 'SUPPRESSED'; ev_reason := 'notify_disabled';
      elsif r.muted_until is not null and now() < r.muted_until then
        ev_status := 'SUPPRESSED'; ev_reason := 'muted';
      elsif r.last_event_at is not null
            and new.observed_at - r.last_event_at < make_interval(mins => r.cooldown_minutes) then
        ev_status := 'SUPPRESSED'; ev_reason := 'cooldown';
      elsif not schedule_is_active(r.active_schedule, new.observed_at) then
        ev_status := 'SUPPRESSED'; ev_reason := 'schedule';
      elsif in_quiet_hours(r.owner_id, now()) then
        ev_status := 'SUPPRESSED'; ev_reason := 'quiet_hours';
      end if;

      insert into geofence_events (owner_id, rule_id, tracker_id, place_id, location_id, event_type,
                                   observed_at, status, suppress_reason,
                                   latitude, longitude, accuracy_m, distance_m)
      values (r.owner_id, r.id, r.tracker_id, r.place_id, new.id, ev_type,
              new.observed_at, ev_status, ev_reason,
              new.latitude, new.longitude, new.accuracy_m, dist);
      r.last_event_at := new.observed_at;
    end if;

    update geofence_rules
       set current_state = new_state,
           pending_since = r.pending_since,
           pending_reports = r.pending_reports,
           last_observed_at = new.observed_at,
           last_event_at = r.last_event_at
     where id = r.id;
  end loop;
  return new;
end $$;

create trigger locations_evaluate_geofences
  after insert on locations
  for each row execute function evaluate_geofences();

-- ---------------------------------------------------------------- retención (spec §11)
create or replace function purge_old_locations()
returns int language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare n int;
begin
  with del as (
    delete from locations l
    using trackers t, profiles p
    where l.tracker_id = t.id and t.owner_id = p.id
      and l.observed_at < now() - make_interval(days => p.retention_days)
    returning 1)
  select count(*) into n from del;
  return n;
end $$;
-- Programar diario con pg_cron (habilitar extensión en Dashboard > Database > Extensions):
-- select cron.schedule('tagmap_purge', '15 4 * * *', $$select purge_old_locations()$$);
