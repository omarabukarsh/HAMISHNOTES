// قسيمة الاشتراك: موقّعة بمفتاح ES256 خاص (ENTITLEMENT_PRIVATE_KEY: PKCS8 بترميز base64)،
// والتطبيق يحمل المفتاح العام المقابل فقط (Lic.kt). الصيغة: "v1.<payload>.<sig r||s>" بترميز base64url.

export type GrantPayload = {
  sub: string;
  dev: string;
  pkg: string;
  iat: number;
  until: number;
  exp: number;
  trial: boolean;
  renew: boolean;
};

let key: CryptoKey | null = null;

async function signingKey(): Promise<CryptoKey> {
  if (key) return key;
  const b64 = Deno.env.get("ENTITLEMENT_PRIVATE_KEY");
  if (!b64) throw new Error("ENTITLEMENT_PRIVATE_KEY is not set");
  const der = Uint8Array.from(atob(b64.trim()), (c) => c.charCodeAt(0));
  key = await crypto.subtle.importKey("pkcs8", der, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  return key;
}

function b64url(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function signGrant(p: GrantPayload): Promise<string> {
  const head = "v1." + b64url(new TextEncoder().encode(JSON.stringify(p)));
  // WebCrypto يعطي التوقيع بصيغة r||s (64 بايت)؛ التطبيق يحوّلها إلى DER
  const sig = new Uint8Array(
    await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, await signingKey(), new TextEncoder().encode(head)),
  );
  return head + "." + b64url(sig);
}

export async function sha256Hex(s: string): Promise<string> {
  const d = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
  return Array.from(d, (b) => b.toString(16).padStart(2, "0")).join("");
}
