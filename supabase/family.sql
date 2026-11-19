-- Private family / friends shared spam list. Run once in Supabase: SQL Editor -> New query -> paste -> Run.
-- (Also safe to run again.) Anyone holding the secret share code can read and add to that group's list.

create table if not exists family_spam (
  code text not null,
  num text not null,
  label text,
  updated_at timestamptz not null default now(),
  primary key (code, num)
);
alter table family_spam enable row level security;   -- no policies: table not readable directly

create or replace function family_push(c text, items jsonb) returns void
language plpgsql security definer set search_path = public as $$
begin
  if length(c) < 8 then raise exception 'code too short'; end if;
  if jsonb_array_length(items) > 2000 then raise exception 'too many numbers'; end if;
  insert into family_spam(code, num, label)
  select c, left(e->>'n', 20), left(coalesce(e->>'l',''), 40)
  from jsonb_array_elements(items) e
  where coalesce(e->>'n','') <> ''
  on conflict (code, num) do update set label = excluded.label, updated_at = now();
end $$;

create or replace function family_pull(c text) returns table(num text, label text)
language sql security definer stable set search_path = public as $$
  select f.num, f.label from family_spam f where f.code = c and length(c) >= 8 limit 5000;
$$;

revoke all on function family_push(text, jsonb) from public;
revoke all on function family_pull(text) from public;
grant execute on function family_push(text, jsonb) to anon;
grant execute on function family_pull(text) to anon;
