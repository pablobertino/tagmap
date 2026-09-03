-- 0009: collector_finish_action fallaba con 42804 (text → action_status). Cast explícito.
set search_path = tagmap, public;

create or replace function collector_finish_action(p_id uuid, p_ok boolean, p_result text default null)
returns void language plpgsql security definer set search_path to 'tagmap','public','extensions' as $$
begin
  perform assert_service_role();
  update action_requests
     set status = (case when p_ok then 'done' else 'failed' end)::action_status,
         result = left(p_result, 500), done_at = now()
   where id = p_id;
end $$;
