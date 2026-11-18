-- Jupiter Connect ride history in the shared Meridian Supabase project.
-- Run with: supabase db push (from D:\KrishnaTrackrer, project rkmouoglorsnijmemmcd)
-- Only authenticated Meridian users can see their own rides.

create table if not exists public.jc_rides (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  client_id text not null,
  started_at timestamptz not null,
  ended_at timestamptz not null,
  distance_km double precision not null,
  avg_speed_kmh double precision not null,
  top_speed_kmh integer not null,
  duration_min integer not null,
  band_ms bigint[] not null default '{}',
  idle_ms bigint not null default 0,
  iss_stops integer not null default 0,
  fuel_bars_start integer not null default 0,
  fuel_bars_end integer not null default 0,
  max_rpm integer not null default 0,
  created_at timestamptz not null default now(),
  unique (user_id, client_id)
);

alter table public.jc_rides enable row level security;

drop policy if exists "jc rides select own" on public.jc_rides;
create policy "jc rides select own"
  on public.jc_rides for select to authenticated
  using (auth.uid() = user_id);

drop policy if exists "jc rides insert own" on public.jc_rides;
create policy "jc rides insert own"
  on public.jc_rides for insert to authenticated
  with check (auth.uid() = user_id);

drop policy if exists "jc rides update own" on public.jc_rides;
create policy "jc rides update own"
  on public.jc_rides for update to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
