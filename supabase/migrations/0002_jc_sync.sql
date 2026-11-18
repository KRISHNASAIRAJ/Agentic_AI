-- Full Jupiter Connect cloud sync in the shared Meridian Supabase project.
-- Run in the Supabase SQL Editor for project rkmouoglorsnijmemmcd:
--   supabase db push   (or paste this file into the SQL editor)
-- Only authenticated Meridian users can touch their own rows (RLS).

-- Ride graphs and any ride detail added after 0001.
alter table public.jc_rides
  add column if not exists speed_samples integer[] not null default '{}',
  add column if not exists throttle_samples integer[] not null default '{}',
  add column if not exists maps_eta_min integer not null default -1;

-- Petrol fills. The client id is stable (fill number + IST date) so retries
-- merge instead of duplicating rows.
create table if not exists public.jc_fuel_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  client_id text not null,
  date_ist date not null,
  odometer_km double precision not null default 0,
  distance_km double precision not null default 0,
  liters double precision not null default 0,
  amount_paise bigint not null default 0,
  mileage_kmpl double precision not null default 0,
  station text not null default '',
  created_at timestamptz not null default now(),
  unique (user_id, client_id)
);
alter table public.jc_fuel_logs enable row level security;
drop policy if exists "jc fuel logs select own" on public.jc_fuel_logs;
create policy "jc fuel logs select own" on public.jc_fuel_logs
  for select to authenticated using (auth.uid() = user_id);
drop policy if exists "jc fuel logs insert own" on public.jc_fuel_logs;
create policy "jc fuel logs insert own" on public.jc_fuel_logs
  for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "jc fuel logs update own" on public.jc_fuel_logs;
create policy "jc fuel logs update own" on public.jc_fuel_logs
  for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- One JSON document per user for settings and cumulative counters that are not
-- rows of their own (carbon saved, all-time top speed, parked spot, toggles).
create table if not exists public.jc_app_state (
  user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  details jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.jc_app_state enable row level security;
drop policy if exists "jc app state select own" on public.jc_app_state;
create policy "jc app state select own" on public.jc_app_state
  for select to authenticated using (auth.uid() = user_id);
drop policy if exists "jc app state insert own" on public.jc_app_state;
create policy "jc app state insert own" on public.jc_app_state
  for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "jc app state update own" on public.jc_app_state;
create policy "jc app state update own" on public.jc_app_state
  for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
