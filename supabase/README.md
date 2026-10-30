# خادم هامش: التحقق من الاشتراك ومكافحة القرصنة

## كيف يعمل

```
التطبيق ──(رمز الشراء + حكم Play Integrity + رمز دخول Supabase)──▶ license
                                                                    │
                    Google Play Developer API ◀── هل الاشتراك فعّال ومربوط بهذا الحساب؟
                    Play Integrity API        ◀── هل التطبيق أصلي ومن المتجر وعلى جهاز سليم؟
                                                                    │
التطبيق ◀──────────── قسيمة موقّعة ES256 (7 أيام دون إنترنت) ◀───────┘

Google Play ──(تجديد / إلغاء / استرداد)──▶ Pub/Sub ──▶ play-rtdn ──▶ جدول subscriptions
```

- **subscriptions**: جدول الاشتراكات. لا يكتب فيه إلا الخادم، والمستخدم يقرأ صفه فقط (RLS).
- **integrity_failures**: كل محاولة رُفضت (نسخة معدّلة، أو من خارج المتجر، أو جهاز مخترق، أو رمز شراء لحساب آخر). راجعه لترى حجم القرصنة.
- **has_premium(uid)**: دالة جاهزة لسياسات RLS لاحقًا (مثل مزامنة سحابية للمشتركين فقط).

## الأسرار (Supabase ← Edge Functions ← Secrets)

لا يُكتب أي منها في المستودع.

| الاسم | القيمة |
|---|---|
| `GOOGLE_SERVICE_ACCOUNT_JSON` | ملف JSON لحساب الخدمة كاملًا (الخطوة 2 أدناه) |
| `ENTITLEMENT_PRIVATE_KEY` | المفتاح الخاص لتوقيع القسائم (من ملف الأسرار المرسل إليك) |
| `ANDROID_PACKAGE` | اسم الحزمة النهائي، مثل `com.hamish.app` |
| `PREMIUM_PRODUCT` | `hamish_premium` |
| `RTDN_SECRET` | سلسلة عشوائية طويلة تضعها أيضًا في رابط Pub/Sub |
| `DEVICE_INTEGRITY` | اختياري: `device` (الافتراضي)، أو `basic` (أكثر تساهلًا)، أو `strong` |
| `GRANT_DAYS` | اختياري: أيام العمل دون إنترنت (الافتراضي 7) |

`SUPABASE_URL` و `SUPABASE_SERVICE_ROLE_KEY` يضيفهما Supabase تلقائيًا.

## خطوات الإعداد

### 1. قاعدة البيانات
شغّل `migrations/20261030120000_subscriptions.sql` في SQL Editor، أو:
```bash
supabase db push
```

### 2. Google Cloud (مشروع hamish-513214)
1. فعّل **Google Play Android Developer API** و **Play Integrity API**.
2. IAM ← Service Accounts ← أنشئ حسابًا (مثل `hamish-play`)، ثم Keys ← Add key ← JSON.

### 3. Play Console
1. **Users and permissions**: ادعُ بريد حساب الخدمة، وامنحه صلاحيتي *View financial data* و *Manage orders and subscriptions*.
2. **Monetize ← Subscriptions**: أنشئ المنتج `hamish_premium`، ثم:
   - خطة أساسية `yearly`: تجديد تلقائي سنوي، بسعر 4$.
   - عرض `free-trial-7d`: تجربة مجانية 7 أيام، للعملاء الجدد فقط.
3. **App integrity**:
   - اربط مشروع Google Cloud بـ Play Integrity.
   - فعّل **Automatic protection**: يحقن Google فحوص منع التعديل داخل التطبيق.
   - من صفحة **App signing** انسخ بصمة SHA-256 لمفتاح Google، وضعها في `PLAY_SIGNING_SHA256` عند البناء.
4. **Monetization setup ← Real-time developer notifications**: ضع اسم الموضوع (الخطوة 4).

### 4. Pub/Sub (في Google Cloud)
1. أنشئ موضوعًا، مثل `play-rtdn`.
2. امنح `google-play-developer-notifications@system.gserviceaccount.com` دور **Pub/Sub Publisher** على هذا الموضوع.
3. أنشئ اشتراك **Push** إلى:
   `https://nuwbcgpsyrdqaqzablis.supabase.co/functions/v1/play-rtdn?secret=<RTDN_SECRET>`

### 5. نشر الدوال
```bash
supabase functions deploy license --no-verify-jwt
```
```bash
supabase functions deploy play-rtdn --no-verify-jwt
```

### 6. بناء نسخة المتجر
متغيرات البيئة عند البناء:

| الاسم | من أين |
|---|---|
| `SUPABASE_ANON_KEY` | Supabase ← Project Settings ← API Keys (المفتاح العام anon أو publishable) |
| `HAMISH_KEYSTORE_B64` و `HAMISH_KEYSTORE_PASSWORD` و `HAMISH_KEY_ALIAS` و `HAMISH_KEY_PASSWORD` | ملف الأسرار |
| `PLAY_SIGNING_SHA256` | Play Console ← App signing |
| `GOOGLE_WEB_CLIENT_ID` | كما هو الآن |

إذا وُجد مفتاح المتجر، يُحذف وضع Premium التجريبي من التطبيق تلقائيًا.

```bash
GITHUB_RUN_NUMBER=<الرقم> gradle bundleRelease
```

### 7. تسجيل الدخول بـ Google في نسخة المتجر
أضف إلى عميل Android في Google Cloud بصمتَي SHA-1:
- مفتاح الرفع الجديد.
- مفتاح Google (من صفحة App signing).
