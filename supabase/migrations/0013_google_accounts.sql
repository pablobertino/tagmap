-- 0013: cuentas de Google autoservicio.
-- Cada usuario registra su propia cuenta de Find Hub con la herramienta `tagmap-auth` (PC + Chrome),
-- que sube secrets.json vía RPC. Se guarda cifrado en Supabase Vault; el recolector (service_role)
-- lo lee en cada arranque. Ya no hace falta tocar secretos de GitHub para sumar personas.
set search_path = tagmap, public, extensions;

create table if not exists google_accounts (
  owner_id      uuid primary key references auth.users(id) on delete cascade,
  collector_id  text not null unique references collectors(id) on delete cascade,
  google_email  text,
  secret_id     uuid not null,                 -- vault.secrets.id
  registered_at timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
alter table google_accounts enable row level security;
create policy google_accounts_owner_select on google_accounts for select using (owner_id = auth.uid());
revoke all on google_accounts from anon, authenticated;
grant select (owner_id, collector_id, google_email, registered_at, updated_at) on google_accounts to authenticated;
grant all on google_accounts to service_role;

-- Registrar / renovar la cuenta del usuario actual (la llama tagmap-auth con el JWT del usuario)
create or replace function app_register_google_account(p_secrets jsonb)
returns text language plpgsql security definer set search_path = tagmap, public, extensions, vault as $$
declare
  v_uid uuid := auth.uid();
  v_cid text;
  v_sid uuid;
  v_email text;
  k text;
begin
  if v_uid is null then raise exception 'sin sesión' using errcode = '42501'; end if;
  foreach k in array array['fcm_credentials','username','aas_token','shared_key','owner_key'] loop
    if p_secrets->k is null then
      raise exception 'secrets.json incompleto: falta %', k using errcode = '22023';
    end if;
  end loop;
  v_email := p_secrets->>'username';

  -- perfil y recolector propios (id estable por usuario)
  insert into profiles (id, email) select id, email from auth.users where id = v_uid on conflict (id) do nothing;
  select collector_id into v_cid from google_accounts where owner_id = v_uid;
  if v_cid is null then
    -- reutilizar un recolector gha-* ya existente del usuario (cuentas migradas), si no crear uno
    select id into v_cid from collectors where owner_id = v_uid and id like 'gha-%' order by created_at limit 1;
    if v_cid is null then
      v_cid := 'gha-' || left(replace(v_uid::text, '-', ''), 8);
      insert into collectors (id, owner_id, status, interval_minutes) values (v_cid, v_uid, 'ok', 15)
      on conflict (id) do nothing;
    end if;
    v_sid := vault.create_secret(p_secrets::text, 'tagmap_gfmt_' || v_cid, 'secrets.json de Find Hub, usuario ' || v_uid);
    insert into google_accounts (owner_id, collector_id, google_email, secret_id)
    values (v_uid, v_cid, v_email, v_sid);
    update collectors set status = 'ok', message = null where id = v_cid;
  else
    select secret_id into v_sid from google_accounts where owner_id = v_uid;
    perform vault.update_secret(v_sid, p_secrets::text);
    update google_accounts set google_email = v_email, updated_at = now() where owner_id = v_uid;
    update collectors set status = 'ok', message = null where id = v_cid;
  end if;
  insert into audit_log (owner_id, action, entity, entity_id) values (v_uid, 'register_google_account', 'collector', v_cid);
  return v_cid;
end $$;

-- Quitar la cuenta (borra el secreto; los tags e historial quedan)
create or replace function app_remove_google_account()
returns void language plpgsql security definer set search_path = tagmap, public, extensions, vault as $$
declare v_sid uuid;
begin
  select secret_id into v_sid from google_accounts where owner_id = auth.uid();
  if v_sid is null then return; end if;
  delete from google_accounts where owner_id = auth.uid();
  delete from vault.secrets where id = v_sid;
  insert into audit_log (owner_id, action, entity, entity_id) values (auth.uid(), 'remove_google_account', 'collector', '');
end $$;

-- Estado para la app: ¿tengo cuenta registrada? ¿cómo está el recolector?
create or replace function app_google_account()
returns table (collector_id text, google_email text, registered_at timestamptz, updated_at timestamptz,
               status collector_status, last_seen_at timestamptz, message text)
language sql stable security invoker set search_path = tagmap, public, extensions as $$
  select g.collector_id, g.google_email, g.registered_at, g.updated_at, c.status, c.last_seen_at, c.message
  from google_accounts g join collectors c on c.id = g.collector_id
  where g.owner_id = auth.uid();
$$;

-- Para el recolector: todas las cuentas con su secrets.json descifrado
create or replace function collector_list_accounts()
returns table (collector_id text, secrets jsonb)
language plpgsql security definer set search_path = tagmap, public, extensions, vault as $$
begin
  perform assert_service_role();
  return query
  select g.collector_id, s.decrypted_secret::jsonb
  from google_accounts g join vault.decrypted_secrets s on s.id = g.secret_id
  order by g.registered_at;
end $$;

revoke all on function app_register_google_account(jsonb), app_remove_google_account(), app_google_account(),
  collector_list_accounts() from public, anon;
grant execute on function app_register_google_account(jsonb), app_remove_google_account(), app_google_account()
  to authenticated;
grant execute on function collector_list_accounts() to service_role;
