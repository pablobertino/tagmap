-- 0010: zona horaria por dispositivo, para que la push muestre la hora local del teléfono que la recibe.
set search_path = tagmap, public;
alter table mobile_devices add column if not exists tz text;

create or replace function app_register_device(p_fcm_token text, p_device_name text default null, p_tz text default null)
returns uuid language plpgsql set search_path to 'tagmap','public','extensions' as $$
declare d uuid;
begin
  insert into mobile_devices (user_id, fcm_token, device_name, tz)
  values (auth.uid(), p_fcm_token, p_device_name, p_tz)
  on conflict (user_id, fcm_token) do update
    set last_seen_at = now(), device_name = excluded.device_name, tz = coalesce(excluded.tz, mobile_devices.tz)
  returning id into d;
  return d;
end $$;
revoke all on function app_register_device(text, text, text) from public, anon;
grant execute on function app_register_device(text, text, text) to authenticated;
drop function if exists app_register_device(text, text);
