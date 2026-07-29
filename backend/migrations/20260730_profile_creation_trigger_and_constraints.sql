-- Fills two gaps in account/database setup:
--
-- 1. No `profiles` row was ever created for a new user. AuthRepository's
--    requestAccountDeletion() does a PATCH on profiles?id=eq.{userId},
--    which only updates an existing row — with no row there, that PATCH
--    silently affects zero rows and deletion_due_at/deletion_requested_at
--    never get recorded. This adds the standard Supabase trigger that
--    creates a profiles row the moment a user signs up (email/password or
--    OAuth), so every account has one from the start.
--
-- 2. account_deletion_requests is upserted via
--    `?on_conflict=user_id`, which requires a UNIQUE constraint on
--    user_id — otherwise Postgres rejects the upsert with "no unique or
--    exclusion constraint matching the ON CONFLICT specification". Add it
--    defensively in case it isn't already there.
--
-- Run this against your Supabase project (SQL editor, or `supabase db
-- push`). Safe to re-run.

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, email)
  values (new.id, new.email)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Backfill: create profiles rows for any existing users that predate this
-- trigger (e.g. accounts created while this gap existed).
insert into public.profiles (id, email)
select u.id, u.email
from auth.users u
left join public.profiles p on p.id = u.id
where p.id is null
on conflict (id) do nothing;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'account_deletion_requests_user_id_key'
  ) then
    alter table public.account_deletion_requests
      add constraint account_deletion_requests_user_id_key unique (user_id);
  end if;
end $$;
