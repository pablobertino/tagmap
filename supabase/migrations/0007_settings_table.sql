-- Aplicada en Naima el 2026-09-03 (tagmap_0007_settings_table)
set search_path = tagmap, public, extensions;

-- Sin permiso para ALTER DATABASE en Supabase: la URL y el secreto de la Edge Function
-- viven en una tabla privada que solo leen las funciones security definer.
create table if not exists app_settings (key text primary key, value text not null);
revoke all on app_settings from anon, authenticated;
-- claves: notify_url, notify_secret, notify_enabled ('true'/'false')
-- notify_geofence_event() lee de esta tabla en lugar de current_setting()
