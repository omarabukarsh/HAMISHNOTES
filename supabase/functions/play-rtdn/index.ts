// إشعارات Google Play الفورية (Real-time developer notifications) عبر Pub/Sub push.
// تحدّث جدول الاشتراكات عند التجديد والإلغاء والانتهاء والاسترداد، فلا يحتفظ من استرد ماله بالاشتراك.
//
// رابط الاشتراك في Pub/Sub:
//   https://<project>.supabase.co/functions/v1/play-rtdn?secret=<RTDN_SECRET>
// تُنشر هذه الدالة بلا تحقق JWT (Google لا ترسل رمز Supabase)، والحماية بالسر في الرابط.

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

Deno.serve(async (req) => {
  const secret = Deno.env.get("RTDN_SECRET") ?? "";
  const given = new URL(req.url).searchParams.get("secret") ?? "";
  if (!secret || !same(given, secret)) return new Response("forbidden", { status: 403 });
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
      await db.from("subscriptions")
        .update({ revoked: true, state: "REVOKED", updated_at: new Date().toISOString() })
        .eq("purchase_token", note.voidedPurchaseNotification.purchaseToken);
    }
  } catch (e) {
    console.error(e);
    // خطأ مؤقت (Google أو قاعدة البيانات): 500 فيعيد Pub/Sub المحاولة لاحقًا
    return new Response("retry", { status: 500 });
  }
  return new Response("ok", { status: 200 });
});
