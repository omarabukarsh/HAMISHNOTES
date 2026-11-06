-- اشتراكات هامش Premium عبر Google Play.
-- لا يكتب فيها إلا الخادم (Edge Functions بمفتاح service_role)؛ المستخدم يقرأ صفه فقط.

create table if not exists public.subscriptions (
  purchase_token        text primary key,
  user_id               uuid not null references auth.users (id) on delete cascade,
  product_id            text not null,
  state                 text not null,
  expires_at            timestamptz,
  auto_renewing         boolean not null default false,
  in_trial              boolean not null default false,
  test_purchase         boolean not null default false,
  linked_purchase_token text,
  revoked               boolean not null default false,
  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now()
);

create index if not exists subscriptions_user_idx on public.subscriptions (user_id);

alter table public.subscriptions enable row level security;

drop policy if exists "read own subscriptions" on public.subscriptions;
create policy "read own subscriptions" on public.subscriptions
  for select to authenticated
  using ((select auth.uid()) = user_id);

-- لا سياسات insert/update/delete: العميل لا يستطيع منح نفسه اشتراكًا.

-- هل للمستخدم اشتراك فعّال؟ للاستخدام في سياسات RLS لاحقًا (مثل المزامنة السحابية الحصرية للمشتركين).
create or replace function public.has_premium(uid uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.subscriptions s
    where s.user_id = uid and not s.revoked and s.expires_at > now()
  );
$$;

revoke all on function public.has_premium(uuid) from public, anon;
grant execute on function public.has_premium(uuid) to authenticated, service_role;

-- محاولات فشلت في فحص السلامة (نسخ معدّلة أو من خارج المتجر): لمراقبة القرصنة.
create table if not exists public.integrity_failures (
  id         bigint generated always as identity primary key,
  user_id    uuid references auth.users (id) on delete cascade,
  device     text,
  reason     text not null,
  detail     jsonb,
  created_at timestamptz not null default now()
);

create index if not exists integrity_failures_created_idx on public.integrity_failures (created_at desc);

alter table public.integrity_failures enable row level security;
-- بلا سياسات: لا يقرؤها ولا يكتبها إلا service_role.
