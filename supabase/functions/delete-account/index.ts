// حذف الحساب من داخل التطبيق (شرط Google Play و Apple).
//
// الطلب: POST مع رمز دخول المستخدم في Authorization.
// 1. يلغي تجديد كل اشتراك فعّال للمستخدم في Google Play، فلا يُخصم منه مال على حساب محذوف.
//    إن تعذّر الإلغاء لا يُحذف شيء ويُرد cancel_failed، ليلغيه المستخدم من Play ثم يعيد المحاولة.
// 2. يحذف المستخدم من Supabase Auth؛ صفوف subscriptions و integrity_failures تُحذف معه (on delete cascade).

import { createClient } from "jsr:@supabase/supabase-js@2";
import { cancelSubscription } from "../_shared/google.ts";
import { PKG } from "../_shared/subscriptions.ts";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);

  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: auth } = await db.auth.getUser(jwt);
  const user = auth?.user;
  if (!user) return json({ error: "auth" }, 401);

  const { data: subs, error } = await db.from("subscriptions")
    .select("purchase_token, product_id, expires_at, auto_renewing, revoked")
    .eq("user_id", user.id);
  if (error) {
    console.error(error);
    return json({ error: "server" }, 500);
  }

  const renewing = (subs ?? []).filter((s) =>
    !s.revoked && s.auto_renewing && s.expires_at != null && Date.parse(s.expires_at) > Date.now()
  );
  try {
    for (const s of renewing) await cancelSubscription(PKG, s.product_id, s.purchase_token);
  } catch (e) {
    console.error(e);
    return json({ error: "cancel_failed" }, 502);
  }

  const { error: delError } = await db.auth.admin.deleteUser(user.id);
  if (delError) {
    console.error(delError);
    return json({ error: "server" }, 500);
  }
  return json({ ok: true, cancelled: renewing.length });
});
