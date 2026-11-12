-- Run once in Supabase: SQL Editor -> New query -> paste -> Run.
-- Then add the project URL and the "anon public" key as GitHub secrets SPAM_API_URL and SPAM_API_KEY.

create table if not exists spam_reports (
  id bigserial primary key,
  num_hash text not null,          -- SHA-256 of the number, never the number itself
  reporter text not null,          -- random per-install id (one vote per install per number)
  label text,
  created_at timestamptz not null default now(),
  unique (num_hash, reporter)
);
create index if not exists spam_reports_hash on spam_reports(num_hash);

alter table spam_reports enable row level security;   -- no policies: the table is not readable directly

create or replace function spam_report(h text, r text, lbl text) returns void
language sql security definer set search_path = public as $$
  insert into spam_reports(num_hash, reporter, label) values (h, r, left(lbl, 40))
  on conflict (num_hash, reporter) do update set label = excluded.label, created_at = now();
$$;

create or replace function spam_lookup(h text) returns table(reports int, label text)
language sql security definer stable set search_path = public as $$
  select count(*)::int,
         (select s.label from spam_reports s where s.num_hash = h and coalesce(s.label,'') <> ''
          group by s.label order by count(*) desc limit 1)
  from spam_reports where num_hash = h;
$$;

revoke all on function spam_report(text,text,text) from public;
revoke all on function spam_lookup(text) from public;
grant execute on function spam_report(text,text,text) to anon;
grant execute on function spam_lookup(text) to anon;
