-- has_premium تطابق entitled() في الخادم (_shared/subscriptions.ts):
-- الحالة يجب أن تكون فعّالة، لا تاريخ الانتهاء وحده. بدون هذا كان صف SUPERSEDED أو PENDING
-- بتاريخ مستقبلي يُعدّ اشتراكًا فعّالًا.
create or replace function public.has_premium(uid uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.subscriptions s
    where s.user_id = uid
      and not s.revoked
      and s.state in (
        'SUBSCRIPTION_STATE_ACTIVE',
        'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
        'SUBSCRIPTION_STATE_CANCELED'
      )
      and s.expires_at > now()
  );
$$;

revoke all on function public.has_premium(uuid) from public, anon;
grant execute on function public.has_premium(uuid) to authenticated, service_role;
