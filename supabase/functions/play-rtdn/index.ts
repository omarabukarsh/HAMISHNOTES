// إشعارات Google Play الفورية (Real-time developer notifications) عبر Pub/Sub push.
// تحدّث جدول الاشتراكات عند التجديد والإلغاء والانتهاء والاسترداد، فلا يحتفظ من استرد ماله بالاشتراك.
//
// التحقق من المرسل (تُنشر الدالة بلا تحقق JWT من Supabase لأن Google لا ترسل رمز Supabase):
//   المفضّل: مصادقة OIDC في اشتراك Pub/Sub ("Enable authentication" بحساب خدمة).
//     ضع في أسرار Supabase: RTDN_PUSH_SERVICE_ACCOUNT=<بريد حساب الخدمة>
//     و RTDN_AUDIENCE=<الـ audience المضبوط في الاشتراك، افتراضيًا رابط الدالة>.
//     عندها يُرفض أي طلب بلا رمز Google صالح، ولا حاجة إلى السر في الرابط.
//   القديم (يبقى يعمل إن لم يُضبط OIDC): سر في الرابط
//     https://<project>.supabase.co/functions/v1/play-rtdn?secret=<RTDN_SECRET>
//     عيبه أن الروابط تُحفظ في سجلات الخوادم.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { PKG, syncFromGoogle } from "../_shared/subscriptions.ts";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

function same(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

/** رمز OIDC من Pub/Sub: تتحقق Google نفسها من توقيعه (tokeninfo)، ونتحقق نحن من المرسل والجمهور والمدة. */
async function oidcOk(req: Request, account: string): Promise<boolean> {
  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!jwt) return false;
  try {
    const res = await fetch("https://oauth2.googleapis.com/tokeninfo?id_token=" + encodeURIComponent(jwt));
    if (!res.ok) return false;
    const t = await res.json();
    // req.url داخل Supabase قد لا يكون الرابط العام، فالافتراضي مبني من SUPABASE_URL
    const audience = Deno.env.get("RTDN_AUDIENCE") ??
      (Deno.env.get("SUPABASE_URL") ?? "").replace(/\/+$/, "") + "/functions/v1/play-rtdn";
    return t.email === account && (t.email_verified === true || t.email_verified === "true") &&
      t.aud === audience && (t.iss === "https://accounts.google.com" || t.iss === "accounts.google.com") &&
      Number(t.exp) * 1000 > Date.now();
  } catch {
    return false;
  }
}

Deno.serve(async (req) => {
  const account = Deno.env.get("RTDN_PUSH_SERVICE_ACCOUNT") ?? "";
  let allowed: boolean;
  if (account) {
    allowed = await oidcOk(req, account);
  } else {
    const secret = Deno.env.get("RTDN_SECRET") ?? "";
    const given = new URL(req.url).searchParams.get("secret") ?? "";
    allowed = !!secret && same(given, secret);
  }
  if (!allowed) return new Response("forbidden", { status: 403 });
  if (req.method !== "POST") return new Response("method", { status: 405 });

  let note: Record<string, any>;
  try {
    const push = await req.json();
    note = JSON.parse(atob(push.message.data));
  } catch {
    // رسالة لا تُقرأ: نردّ بنجاح كي لا يعيد Pub/Sub إرسالها بلا نهاية
    return new Response("ignored", { status: 200 });
  }
  if (note.packageName && note.packageName !== PKG) return new Response("other package", { status: 200 });

  try {
    if (note.subscriptionNotification?.purchaseToken) {
      // أيًا كان نوع الإشعار، نقرأ الحالة الحقيقية من Google
      await syncFromGoogle(db, note.subscriptionNotification.purchaseToken);
    } else if (note.voidedPurchaseNotification?.purchaseToken) {
      // استرداد أو إلغاء من Google: يُسحب الاشتراك فورًا
      const { error } = await db.from("subscriptions")
        .update({ revoked: true, state: "REVOKED", updated_at: new Date().toISOString() })
        .eq("purchase_token", note.voidedPurchaseNotification.purchaseToken);
      if (error) throw error;
    }
  } catch (e) {
    console.error(e);
    // خطأ مؤقت (Google أو قاعدة البيانات): 500 فيعيد Pub/Sub المحاولة لاحقًا
    return new Response("retry", { status: 500 });
  }
  return new Response("ok", { status: 200 });
});
