// التحقق من الاشتراك وإصدار القسيمة.
//
// الطلب (POST، مع رمز دخول المستخدم في Authorization):
//   { action: "verify" | "refresh", device, ts, integrity, purchaseToken? }
// الرد:
//   200 { grant }  أو  4xx { error: "auth" | "bad_request" | "unlicensed" | "unrecognized" | "device"
//                               | "stale" | "no_subscription" | "owned_by_other" }
//
// لا قسيمة إلا إذا:
//   1. حكم Play Integrity يقول: نسختنا الأصلية (PLAY_RECOGNIZED)، مثبّتة من Google Play (LICENSED)،
//      على جهاز سليم، وحكم مربوط بهذا الطلب نفسه (requestHash) وحديث.
//   2. Google Play تؤكد اشتراكًا فعّالًا مربوطًا بحساب هذا المستخدم.

import { createClient } from "jsr:@supabase/supabase-js@2";
import { decodeIntegrity, type IntegrityVerdict } from "../_shared/google.ts";
import { sha256Hex, signGrant } from "../_shared/grant.ts";
import { entitled, OwnershipError, PKG, type Row, syncFromGoogle } from "../_shared/subscriptions.ts";

const GRANT_DAYS = Number(Deno.env.get("GRANT_DAYS") ?? "7");
const DAY = 24 * 60 * 60 * 1000;
const MAX_SKEW = 10 * 60 * 1000;
// "strong" | "device" | "basic": مستوى سلامة الجهاز المطلوب. "device" يرفض الأجهزة المخترقة والمحاكيات.
const DEVICE_LEVEL = Deno.env.get("DEVICE_INTEGRITY") ?? "device";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function checkVerdict(v: IntegrityVerdict, expectedHash: string): string | null {
  const req = v.requestDetails ?? {};
  if (req.requestPackageName !== PKG || req.requestHash !== expectedHash) return "stale";
  if (Math.abs(Date.now() - Number(req.timestampMillis ?? 0)) > MAX_SKEW) return "stale";
  if (v.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED") return "unrecognized";
  if (v.accountDetails?.appLicensingVerdict !== "LICENSED") return "unlicensed";
  const device = v.deviceIntegrity?.deviceRecognitionVerdict ?? [];
  const need = DEVICE_LEVEL === "strong" ? "MEETS_STRONG_INTEGRITY"
    : DEVICE_LEVEL === "basic" ? "MEETS_BASIC_INTEGRITY" : "MEETS_DEVICE_INTEGRITY";
  if (!device.includes(need) && !device.includes("MEETS_STRONG_INTEGRITY")) return "device";
  return null;
}

async function logFailure(userId: string, device: string, reason: string, detail: unknown) {
  await db.from("integrity_failures").insert({ user_id: userId, device, reason, detail });
}

async function issue(row: Row, userId: string, device: string): Promise<Response> {
  const now = Date.now();
  const exp = Date.parse(row.expires_at!);
  // يعمل دون إنترنت حتى GRANT_DAYS، ولا يتجاوز نهاية الاشتراك إن أُلغي التجديد
  const until = row.auto_renewing ? now + GRANT_DAYS * DAY : Math.min(now + GRANT_DAYS * DAY, exp);
  const grant = await signGrant({
    sub: userId, dev: device, pkg: PKG, iat: now, until, exp,
    trial: row.in_trial, renew: row.auto_renewing,
  });
  return json({ grant });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);

  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  const { data: auth } = await db.auth.getUser(jwt);
  const user = auth?.user;
  if (!user) return json({ error: "auth" }, 401);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }
  const action = body.action;
  const device = body.device;
  const ts = body.ts;
  const integrity = body.integrity;
  const purchaseToken = typeof body.purchaseToken === "string" ? body.purchaseToken : "";
  if (
    (action !== "verify" && action !== "refresh") || typeof device !== "string" || !/^[0-9a-f]{32}$/.test(device) ||
    typeof ts !== "number" || typeof integrity !== "string" || (action === "verify" && !purchaseToken)
  ) return json({ error: "bad_request" }, 400);
  if (Math.abs(Date.now() - ts) > MAX_SKEW) return json({ error: "stale" }, 400);

  // 1) سلامة التطبيق والجهاز
  const expected = await sha256Hex([action, user.id, device, String(ts), purchaseToken].join("|"));
  let verdict: IntegrityVerdict;
  try {
    verdict = await decodeIntegrity(PKG, integrity);
  } catch (e) {
    console.error(e);
    return json({ error: "integrity_unavailable" }, 503);
  }
  const problem = checkVerdict(verdict, expected);
  if (problem) {
    await logFailure(user.id, device, problem, verdict);
    return json({ error: problem }, 403);
  }

  // 2) الاشتراك
  try {
    let row: Row | null = null;
    if (action === "verify") {
      row = await syncFromGoogle(db, purchaseToken, user.id);
    } else {
      const { data } = await db.from("subscriptions").select("*").eq("user_id", user.id).eq("revoked", false)
        .order("expires_at", { ascending: false }).limit(1).maybeSingle();
      row = data as Row | null;
      // إشعارات Google تحدّث الجدول عادة؛ وإن قارب الانتهاء أو قدم التحديث نسأل Google مباشرة
      if (row && (Date.parse(row.expires_at ?? "0") - Date.now() < 2 * DAY ||
        Date.now() - Date.parse(row.updated_at ?? "0") > 3 * DAY)) {
        row = (await syncFromGoogle(db, row.purchase_token, user.id)) ?? row;
      }
    }
    if (!row || !entitled(row)) return json({ error: "no_subscription" }, 404);
    return await issue(row, user.id, device);
  } catch (e) {
    if (e instanceof OwnershipError) {
      await logFailure(user.id, device, "owned_by_other", { purchaseToken: purchaseToken.slice(0, 12) });
      return json({ error: "owned_by_other" }, 403);
    }
    console.error(e);
    return json({ error: "server" }, 500);
  }
});
