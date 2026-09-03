-- Prueba de la máquina de estados de geocercas (spec §7, §16).
-- Ejecutar en el SQL Editor de Supabase (se hace ROLLBACK al final; no deja datos).
-- Cada bloque `assert` falla con mensaje si el comportamiento no es el esperado.

begin;
set local search_path = tagmap, public, extensions;

-- Usuario de prueba (auth.users es necesario por la FK de profiles)
insert into auth.users (id, email, instance_id, aud, role, encrypted_password, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000001', 'test@tagmap.local', '00000000-0000-0000-0000-000000000000',
        'authenticated', 'authenticated', '', now(), now())
on conflict (id) do nothing;
insert into profiles (id, email) values ('00000000-0000-0000-0000-000000000001', 'test@tagmap.local')
on conflict (id) do nothing;

insert into collectors (id, owner_id) values ('test-collector', '00000000-0000-0000-0000-000000000001');

insert into trackers (id, owner_id, provider_device_id, name)
values ('00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-000000000001', 'dev-1', 'Daniel');

-- Casa en Obelisco, radio 200 m → histéresis 50 m
insert into places (id, owner_id, name, latitude, longitude, radius_m)
values ('00000000-0000-0000-0000-0000000000bb', '00000000-0000-0000-0000-000000000001', 'Casa', -34.6037, -58.3816, 200);

insert into geofence_rules (id, owner_id, tracker_id, place_id, confirmation_minutes, cooldown_minutes)
values ('00000000-0000-0000-0000-0000000000cc', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-0000000000aa', '00000000-0000-0000-0000-0000000000bb', 10, 60);

-- helper: insertar posición con offset en metros hacia el norte y minutos desde t0
create function pg_temp.ins(m_north double precision, minutes int, acc real default 50)
returns void language sql as $$
  insert into locations (tracker_id, latitude, longitude, accuracy_m, observed_at)
  values ('00000000-0000-0000-0000-0000000000aa',
          -34.6037 + m_north / 111320.0, -58.3816, acc,
          timestamptz '2026-09-02 12:00:00-03' + make_interval(mins => minutes));
$$;

-- 1. Haversine
do $$ begin
  assert abs(haversine_m(-34.6037, -58.3816, -34.6037 + 100/111320.0, -58.3816) - 100) < 1,
    'haversine 100 m';
end $$;

-- 2. UNKNOWN -> INSIDE sin evento
select pg_temp.ins(0, 0);
do $$ declare s geofence_state; n int; begin
  select current_state into s from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  select count(*) into n from geofence_events;
  assert s = 'INSIDE', 'estado inicial INSIDE, es ' || s;
  assert n = 0, 'sin evento en primera detección';
end $$;

-- 3. Zona gris (220 m: > radio, <= radio+histéresis) no cambia estado
select pg_temp.ins(220, 15);
do $$ declare s geofence_state; begin
  select current_state into s from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  assert s = 'INSIDE', 'zona gris mantiene INSIDE';
end $$;

-- 4. Precisión pésima (1000 m) fuera del radio: ignorada
select pg_temp.ins(2000, 30, 1000);
do $$ declare s geofence_state; begin
  select current_state into s from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  assert s = 'INSIDE', 'precisión mala no cambia estado';
end $$;

-- 5. Salida: primer reporte fuera → EXIT_PENDING, sin evento
select pg_temp.ins(600, 45);
do $$ declare s geofence_state; n int; begin
  select current_state into s from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  select count(*) into n from geofence_events;
  assert s = 'EXIT_PENDING', 'primer fuera = EXIT_PENDING, es ' || s;
  assert n = 0, 'pendiente no genera evento';
end $$;

-- 6. Segundo reporte fuera → OUTSIDE + evento EXIT CREATED
select pg_temp.ins(650, 60);
do $$ declare s geofence_state; t geofence_event_type; st geofence_event_status; begin
  select current_state into s from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  select event_type, status into t, st from geofence_events order by observed_at desc limit 1;
  assert s = 'OUTSIDE', 'confirmado OUTSIDE';
  assert t = 'EXIT' and st = 'CREATED', 'evento EXIT CREATED';
end $$;

-- 7. Reporte fuera de orden (observed_at anterior) se ignora
select pg_temp.ins(0, 50);
do $$ declare s geofence_state; begin
  select current_state into s from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  assert s = 'OUTSIDE', 'fuera de orden ignorado';
end $$;

-- 8. Entrada dudosa: dentro y luego fuera → vuelve a OUTSIDE sin evento
select pg_temp.ins(0, 75);   -- ENTRY_PENDING
select pg_temp.ins(700, 90); -- cancela
do $$ declare s geofence_state; n int; begin
  select current_state into s from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  select count(*) into n from geofence_events;
  assert s = 'OUTSIDE', 'entrada cancelada';
  assert n = 1, 'sin evento por entrada dudosa';
end $$;

-- 9. Entrada confirmada (dos reportes) → ENTRY,
--    pero dentro del cooldown de 60 min desde el EXIT (min 60; 111-60=51) → SUPPRESSED
select pg_temp.ins(0, 100);  -- ENTRY_PENDING desde min 100
select pg_temp.ins(10, 111); -- 11 min después → confirmado
do $$ declare s geofence_state; st geofence_event_status; r text; begin
  select current_state into s from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  select status, suppress_reason into st, r from geofence_events order by observed_at desc limit 1;
  assert s = 'INSIDE', 'INSIDE por permanencia';
  assert st = 'SUPPRESSED' and r = 'cooldown', 'ENTRY suprimido por cooldown, fue ' || st || '/' || coalesce(r,'');
end $$;

-- 10. Duplicado exacto no se inserta
do $$ declare before int; after int; begin
  select count(*) into before from locations;
  insert into locations (tracker_id, latitude, longitude, accuracy_m, observed_at)
  select tracker_id, latitude, longitude, accuracy_m, observed_at from locations order by id desc limit 1
  on conflict do nothing;
  select count(*) into after from locations;
  assert before = after, 'dedup';
end $$;

-- 11. Borrar el lugar elimina la regla (spec §15.10)
delete from places where id = '00000000-0000-0000-0000-0000000000bb';
do $$ declare n int; begin
  select count(*) into n from geofence_rules where id = '00000000-0000-0000-0000-0000000000cc';
  assert n = 0, 'regla eliminada en cascada';
end $$;

select 'TODAS LAS PRUEBAS PASARON' as resultado;
rollback;
