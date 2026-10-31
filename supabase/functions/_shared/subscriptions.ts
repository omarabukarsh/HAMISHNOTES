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

function toRow(token: string, userId: string, sub: PlaySubscription): Row {
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
    revoked: false,
  };
}

export class OwnershipError extends Error {}

/**
 * يسأل Google عن رمز الشراء ويحفظ النتيجة. إن مُرّر userId يجب أن يطابق الحساب الذي اشترى
 * (obfuscatedAccountId)، فلا يستطيع أحد استخدام رمز شراء شخص آخر.
 */
export async function syncFromGoogle(db: SupabaseClient, token: string, userId?: string): Promise<Row | null> {
  const sub = await getSubscription(PKG, token);
  if (!sub) return null;
  const owner = sub.externalAccountIdentifiers?.obfuscatedExternalAccountId;

  const { data: existing } = await db.from("subscriptions").select("user_id").eq("purchase_token", token).maybeSingle();
  const uid = userId ?? existing?.user_id ?? owner;
  if (!uid) return null;
  if (userId && owner && owner !== userId) throw new OwnershipError("purchase belongs to another account");
  if (userId && existing && existing.user_id !== userId) throw new OwnershipError("purchase linked to another account");

  const row = toRow(token, uid, sub);
  if (sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING" && entitled(row)) {
    await acknowledge(PKG, row.product_id, token);
  }
  const { error } = await db.from("subscriptions").upsert({ ...row, updated_at: new Date().toISOString() });
  if (error) throw error;
  // الرمز القديم حلّ محله هذا (تجديد بعد انقطاع أو تغيير خطة)
  if (row.linked_purchase_token) {
    await db.from("subscriptions").update({ state: "SUPERSEDED", updated_at: new Date().toISOString() })
      .eq("purchase_token", row.linked_purchase_token);
  }
  return row;
}
