-- 0008: permisos faltantes para las RPC de pedidos (hacer sonar).
-- Causa: collector_take_actions / collector_finish_action se crearon después del
-- "grant all on all functions ... to service_role" de 0004, y quedaron sin EXECUTE
-- para service_role → el recolector fallaba con 42501 en cada ciclo.
set search_path = tagmap, public;

revoke all on function collector_take_actions(text) from public, anon, authenticated;
revoke all on function collector_finish_action(uuid, boolean, text) from public, anon, authenticated;
grant execute on function collector_take_actions(text) to service_role;
grant execute on function collector_finish_action(uuid, boolean, text) to service_role;
grant all on action_requests to service_role;

-- Privilegios por defecto para que objetos futuros del schema no repitan el problema.
alter default privileges in schema tagmap grant execute on functions to service_role;
alter default privileges in schema tagmap grant all on tables to service_role;
alter default privileges in schema tagmap grant all on sequences to service_role;
