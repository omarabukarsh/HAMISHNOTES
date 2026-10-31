// الاتصال بواجهات Google بحساب خدمة (service account):
// Play Developer API للاشتراكات، و Play Integrity لفك حكم السلامة.
// مفتاح حساب الخدمة في أسرار Supabase فقط (GOOGLE_SERVICE_ACCOUNT_JSON).

const SCOPES = [
  "https://www.googleapis.com/auth/androidpublisher",
  "https://www.googleapis.com/auth/playintegrity",
].join(" ");

type ServiceAccount = { client_email: string; private_key: string; token_uri?: string };

let cached: { token: string; until: number } | null = null;

function b64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): Uint8Array {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
}

async function accessToken(): Promise<string> {
  if (cached && Date.now() < cached.until) return cached.token;
  const sa: ServiceAccount = JSON.parse(Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON") ?? "{}");
  if (!sa.client_email || !sa.private_key) throw new Error("GOOGLE_SERVICE_ACCOUNT_JSON is not set");
  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const now = Math.floor(Date.now() / 1000);
  const unsigned = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." +
    b64url(JSON.stringify({ iss: sa.client_email, scope: SCOPES, aud: tokenUri, iat: now, exp: now + 3600 }));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: unsigned + "." + b64url(sig),
    }),
  });
  if (!res.ok) throw new Error(`google token: ${res.status}`);
  const j = await res.json();
  cached = { token: j.access_token, until: Date.now() + (j.expires_in - 120) * 1000 };
  return cached.token;
}

async function google(url: string, init: RequestInit = {}): Promise<Response> {
  const token = await accessToken();
  return fetch(url, {
    ...init,
    headers: { ...(init.headers ?? {}), Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  });
}

/** حكم Play Integrity بعد فك تشفيره (tokenPayloadExternal). */
export type IntegrityVerdict = {
  requestDetails?: { requestPackageName?: string; requestHash?: string; timestampMillis?: string };
  appIntegrity?: { appRecognitionVerdict?: string; packageName?: string; certificateSha256Digest?: string[]; versionCode?: string };
  deviceIntegrity?: { deviceRecognitionVerdict?: string[] };
  accountDetails?: { appLicensingVerdict?: string };
};

export async function decodeIntegrity(pkg: string, integrityToken: string): Promise<IntegrityVerdict> {
  const res = await google(`https://playintegrity.googleapis.com/v1/${pkg}:decodeIntegrityToken`, {
    method: "POST",
    body: JSON.stringify({ integrity_token: integrityToken }),
  });
  if (!res.ok) throw new Error(`integrity decode: ${res.status}`);
  return (await res.json()).tokenPayloadExternal ?? {};
}

/** اشتراك من Google Play (purchases.subscriptionsv2). */
export type PlaySubscription = {
  subscriptionState?: string;
  acknowledgementState?: string;
  linkedPurchaseToken?: string;
  testPurchase?: unknown;
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
  lineItems?: {
    productId?: string;
    expiryTime?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean };
    offerDetails?: { basePlanId?: string; offerId?: string };
    offerPhase?: { freeTrial?: unknown };
  }[];
};

export async function getSubscription(pkg: string, purchaseToken: string): Promise<PlaySubscription | null> {
  const res = await google(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`,
  );
  if (res.status === 404 || res.status === 410) return null;
  if (!res.ok) throw new Error(`subscriptionsv2: ${res.status}`);
  return await res.json();
}

/** تأكيد الشراء خلال 3 أيام، وإلا تردّ Google المبلغ وتلغي الاشتراك. */
export async function acknowledge(pkg: string, productId: string, purchaseToken: string): Promise<void> {
  const res = await google(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases/subscriptions/${productId}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`,
    { method: "POST", body: "{}" },
  );
  if (!res.ok && res.status !== 400) throw new Error(`acknowledge: ${res.status}`);
}
