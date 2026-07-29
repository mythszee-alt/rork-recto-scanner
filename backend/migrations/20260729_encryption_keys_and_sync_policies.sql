-- Fixes cloud backup/restore so it actually works across devices.
--
-- Previously, document backups were encrypted with an Android Keystore key
-- that never leaves the originating device — meaning a restore on a new
-- phone (or after reinstall) could never decrypt anything, even though
-- uploads "succeeded". This migration adds a per-account encryption key,
-- generated once on-device and stored here (RLS-protected so only the
-- owning authenticated user can read it), so any device signed into the
-- same account can fetch it and decrypt that account's backups.
--
-- Run this against your Supabase project (SQL editor, or `supabase db push`
-- if you use the CLI). It's written to be safe to re-run.

create table if not exists public.encryption_keys (
  user_id uuid primary key references auth.users(id) on delete cascade,
  wrapped_key text not null,
  created_at timestamptz not null default now()
);

alter table public.encryption_keys enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'encryption_keys' and policyname = 'encryption_keys_select_own'
  ) then
    create policy "encryption_keys_select_own" on public.encryption_keys
      for select using (auth.uid() = user_id);
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'encryption_keys' and policyname = 'encryption_keys_insert_own'
  ) then
    create policy "encryption_keys_insert_own" on public.encryption_keys
      for insert with check (auth.uid() = user_id);
  end if;
end $$;

-- The app also needs to read its own document rows back (to list what's in
-- the cloud) and update deleted_at/updated_at when a document is deleted or
-- restored locally, so that state matches between devices. Add these if
-- they aren't already present — check pg_policies first if you already
-- manage documents policies elsewhere, to avoid duplicating grants.
do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'documents' and policyname = 'documents_select_own'
  ) then
    create policy "documents_select_own" on public.documents
      for select using (auth.uid() = user_id);
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'documents' and policyname = 'documents_update_own'
  ) then
    create policy "documents_update_own" on public.documents
      for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
  end if;
end $$;

-- Storage: the app already uploads to the `encrypted-documents` bucket, so
-- an insert/write policy for that bucket must already exist. Restore also
-- needs to *read* those objects back. Verify (via Storage > Policies in the
-- dashboard) that authenticated users can SELECT objects in
-- `encrypted-documents` where the first path segment equals their own
-- auth.uid() — e.g.:
--
--   create policy "encrypted_documents_read_own"
--     on storage.objects for select
--     using (
--       bucket_id = 'encrypted-documents'
--       and (storage.foldername(name))[1] = auth.uid()::text
--     );
--
-- This is left commented out because bucket policies are easy to
-- accidentally duplicate/conflict with an existing one — add it manually
-- if read-back for a user's own objects isn't already permitted.
