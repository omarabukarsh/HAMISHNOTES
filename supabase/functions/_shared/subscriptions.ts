import type { SupabaseClient } from "jsr:@supabase/supabase-js@2";
import { acknowledge, getSubscription, type PlaySubscription } from "./google.ts";

export const PKG = Deno.env.get("ANDROID_PACKAGE") ?? "com.hamishnotes.app";
export const PRODUCT = Deno.env.get("PREMIUM_PRODUCT") ?? "hamish_premium";

/** حالات Google التي يبقى فيها الاشتراك مفتوحًا حتى تاريخ انتهائه. */
const ENTITLED = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  // ألغى التجديد التلقائي، لكنه دفع حتى نهاية المدة
  "SUBSCRIPTION_STATE_CANCELED",
]);

export type Row = {
  purchase_token: string;
  user_id: string;
  product_id: string;
  state: string;
  expires_at: string | null;
  auto_renewing: boolean;
  in_trial: boolean;
  test_purchase: boolean;
  linked_purchase_token: string | null;
  revoked: boolean;
  updated_at?: string;
};

export function entitled(row: Pick<Row, "state" | "expires_at" | "revoked">): boolean {
  return !row.revoked && ENTITLED.has(row.state) && row.expires_at != null && Date.parse(row.expires_at) > Date.now();
}

function toRow(token: string, userId: string, sub: PlaySubscription, revoked: boolean): Row {
  const item = sub.lineItems?.find((l) => l.productId === PRODUCT) ?? sub.lineItems?.[0] ?? {};
  return {
    purchase_token: token,
    user_id: userId,
    product_id: item.productId ?? PRODUCT,
    state: sub.subscriptionState ?? "SUBSCRIPTION_STATE_UNSPECIFIED",
    expires_at: item.expiryTime ?? null,
    auto_renewing: item.autoRenewingPlan?.autoRenewEnabled === true,
    in_trial: item.offerPhase?.freeTrial != null,
    test_purchase: sub.testPurchase != null,
    linked_purchase_token: sub.linkedPurchaseToken ?? null,
    revoked,
  };
}

export class OwnershipError extends Error {}

/**
 * يسأل Google عن رمز الشراء ويحفظ النتيجة. إن مُرّر userId يجب أن يطابق الحساب الذي اشترى
 * (obfuscatedAccountId)، فلا يستطيع أحد استخدام رمز شراء شخص آخر.
 *
 * علامة السحب (revoked) لا يغيّرها هذا التحديث أبدًا: من استرد ماله لا يعود اشتراكه بإشعار لاحق.
 */
export async function syncFromGoogle(db: SupabaseClient, token: string, userId?: string): Promise<Row | null> {
  const sub = await getSubscription(PKG, token);
  if (!sub) return null;
  const owner = sub.externalAccountIdentifiers?.obfuscatedExternalAccountId;

  const { data: existing, error: readError } = await db.from("subscriptions").select("user_id, revoked")
    .eq("purchase_token", token).maybeSingle();
  if (readError) throw readError;
  if (userId && owner && owner !== userId) throw new OwnershipError("purchase belongs to another account");
  if (userId && existing && existing.user_id !== userId) throw new OwnershipError("purchase linked to another account");
  // شراء بلا معرّف حساب (رمز ترويجي من المتجر مثلًا): يُربط بأول حساب يقدّمه فقط، ولا يُنقل بعدها
  if (userId && !owner && !existing) {
    await db.from("integrity_failures").insert({ user_id: userId, reason: "ownerless_claim", detail: { token: token.slice(0, 12) } });
  }

  const uid = userId ?? existing?.user_id ?? owner;
  if (!uid) return null;
  // إشعار لحساب حُذف (لا صف ولا مستخدم): لا شيء يُحفظ، ولا نطلب من Pub/Sub إعادة الإرسال
  if (!userId && !existing) {
    const { data: u } = await db.auth.admin.getUserById(uid);
    if (!u?.user) return null;
  }

  const row = toRow(token, uid, sub, existing?.revoked === true);
  if (sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING" && entitled(row)) {
    await acknowledge(PKG, row.product_id, token);
  }
  // revoked لا يُرسل: يبقى كما هو في الصف الموجود، ويأخذ القيمة الافتراضية (false) في الصف الجديد
  const { revoked: _keep, ...fields } = row;
  const { error } = await db.from("subscriptions").upsert({ ...fields, updated_at: new Date().toISOString() });
  if (error) {
    // 23503: المستخدم حُذف بين الفحص والحفظ
    if (error.code === "23503") return null;
    throw error;
  }
  // الرمز القديم حلّ محله هذا (تجديد بعد انقطاع أو تغيير خطة)
  if (row.linked_purchase_token) {
    await db.from("subscriptions").update({ state: "SUPERSEDED", updated_at: new Date().toISOString() })
      .eq("purchase_token", row.linked_purchase_token);
  }
  return row;
}
