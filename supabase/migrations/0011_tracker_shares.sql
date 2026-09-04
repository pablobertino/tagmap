-- 0011: compartir tags con otros usuarios (invitados).
-- El dueño comparte un tag por email. El invitado ve el tag, su posición e historial, y puede
-- definir sus propios lugares y alarmas sobre él (las reglas ya se evalúan por tracker_id, y
-- cada evento pertenece al dueño de la regla → la push llega al invitado). No puede renombrar,
-- hacer sonar ni borrar historial. Al quitar el acceso se eliminan sus reglas sobre ese tag.
set search_path = tagmap, public, extensions;

create table if not exists tracker_shares (
  tracker_id uuid not null references trackers(id) on delete cascade,
  user_id    uuid not null references auth.users(id) on delete cascade,
  owner_id   uuid not null references auth.users(id) on delete cascade,  -- quién compartió
  created_at timestamptz not null default now(),
  primary key (tracker_id, user_id)
);
create index if not exists tracker_shares_user_idx on tracker_shares(user_id);
alter table tracker_shares enable row level security;

-- Lectura: dueño e invitado ven la fila. Escritura solo vía RPC (security definer).
create policy shares_select on tracker_shares for select
  using (owner_id = auth.uid() or user_id = auth.uid());
grant select on tracker_shares to authenticated;
grant all on tracker_shares to service_role;

-- ¿Puede el usuario actual ver este tag? (security definer para no recursar en RLS)
create or replace function tracker_visible(p_tracker_id uuid)
returns boolean language sql stable security definer set search_path = tagmap, public, extensions as $$
  select exists (select 1 from trackers t where t.id = p_tracker_id and t.owner_id = auth.uid())
      or exists (select 1 from tracker_shares s where s.tracker_id = p_tracker_id and s.user_id = auth.uid());
$$;
revoke all on function tracker_visible(uuid) from public, anon;
grant execute on function tracker_visible(uuid) to authenticated;

-- trackers: el invitado también lee (update sigue siendo solo del dueño)
drop policy if exists trackers_owner_select on trackers;
create policy trackers_visible_select on trackers for select using (tracker_visible(id));

-- locations: lectura para invitados; borrado sigue solo del dueño
drop policy if exists locations_owner_select on locations;
create policy locations_visible_select on locations for select using (tracker_visible(tracker_id));

-- reglas: el invitado crea reglas sobre tags visibles, con SUS lugares
drop policy if exists rules_owner_insert on geofence_rules;
create policy rules_owner_insert on geofence_rules for insert
  with check (owner_id = auth.uid()
    and tracker_visible(tracker_id)
    and exists (select 1 from places p where p.id = place_id and p.owner_id = auth.uid()));

-- vista de tags: is_owner + "en lugares" calculado con los lugares del usuario que consulta
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
       (t.owner_id = auth.uid()) as is_owner   -- al final: una vista solo admite columnas nuevas al final
from trackers t
left join lateral (
  select x.id, x.latitude, x.longitude, x.accuracy_m, x.quality, x.source, x.observed_at, x.received_at
  from locations x where x.tracker_id = t.id order by x.observed_at desc limit 1
) l on true;
grant select on app_trackers to authenticated;

-- ---------------------------------------------------------------- RPC
-- Compartir por email. Devuelve el user_id del invitado.
create or replace function app_share_tracker(p_tracker_id uuid, p_email text)
returns uuid language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare v_user uuid;
begin
  if not exists (select 1 from trackers where id = p_tracker_id and owner_id = auth.uid()) then
    raise exception 'Solo el dueño puede compartir este tag' using errcode = '42501';
  end if;
  select id into v_user from auth.users where lower(email) = lower(trim(p_email)) limit 1;
  if v_user is null then
    raise exception 'No existe un usuario con el email %', trim(p_email) using errcode = 'P0002';
  end if;
  if v_user = auth.uid() then
    raise exception 'Ese sos vos' using errcode = 'P0001';
  end if;
  insert into tracker_shares (tracker_id, user_id, owner_id) values (p_tracker_id, v_user, auth.uid())
  on conflict do nothing;
  insert into audit_log (owner_id, action, entity, entity_id, details)
  values (auth.uid(), 'share', 'tracker', p_tracker_id::text, jsonb_build_object('user_id', v_user));
  return v_user;
end $$;

-- Quitar acceso (el dueño) o irse (el invitado). Borra las reglas del invitado sobre ese tag.
create or replace function app_unshare_tracker(p_tracker_id uuid, p_user_id uuid default null)
returns void language plpgsql security definer set search_path = tagmap, public, extensions as $$
declare v_target uuid := coalesce(p_user_id, auth.uid());
begin
  if v_target <> auth.uid()
     and not exists (select 1 from trackers where id = p_tracker_id and owner_id = auth.uid()) then
    raise exception 'Solo el dueño puede quitar el acceso' using errcode = '42501';
  end if;
  delete from tracker_shares where tracker_id = p_tracker_id and user_id = v_target;
  delete from geofence_rules where tracker_id = p_tracker_id and owner_id = v_target;
  insert into audit_log (owner_id, action, entity, entity_id, details)
  values (auth.uid(), 'unshare', 'tracker', p_tracker_id::text, jsonb_build_object('user_id', v_target));
end $$;

-- Lista de invitados de un tag (solo dueño), con email.
create or replace function app_tracker_shares(p_tracker_id uuid)
returns table (user_id uuid, email text, created_at timestamptz)
language sql stable security definer set search_path = tagmap, public, extensions as $$
  select s.user_id, u.email::text, s.created_at
  from tracker_shares s join auth.users u on u.id = s.user_id
  where s.tracker_id = p_tracker_id
    and exists (select 1 from trackers t where t.id = p_tracker_id and t.owner_id = auth.uid())
  order by s.created_at;
$$;

-- Para el invitado: quién le compartió cada tag.
create or replace function app_shared_by()
returns table (tracker_id uuid, owner_email text)
language sql stable security definer set search_path = tagmap, public, extensions as $$
  select s.tracker_id, u.email::text
  from tracker_shares s join auth.users u on u.id = s.owner_id
  where s.user_id = auth.uid();
$$;

revoke all on function app_share_tracker(uuid, text), app_unshare_tracker(uuid, uuid),
  app_tracker_shares(uuid), app_shared_by() from public, anon;
grant execute on function app_share_tracker(uuid, text), app_unshare_tracker(uuid, uuid),
  app_tracker_shares(uuid), app_shared_by() to authenticated;
