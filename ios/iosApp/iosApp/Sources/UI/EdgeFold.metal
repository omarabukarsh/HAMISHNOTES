#include <metal_stdlib>
#include <SwiftUI/SwiftUI.h>
using namespace metal;

// نوافذ شريط الأدوات (EdgeMenu في أندرويد): تخرج من فتحة في حافة الزر وتعود إليها بالانثناء.
// يُحسب لكل بكسل في النافذة المعروضة موضعُه في النافذة الأصلية، على معالج الرسوميات:
// لا شرائح ولا لقطات، والحواف منحنيات ناعمة بلا درجات.

static float smooth5(float d) { return d * d * d * (d * (d * 6.0 - 15.0) + 10.0); }
static float ease3(float t) { return t < 0.5 ? 4.0 * t * t * t : 1.0 - pow(-2.0 * t + 2.0, 3.0) / 2.0; }

/// position: البكسل المعروض. size: حجم النافذة. slitL/slitR: الفتحة على حافة النافذة العليا.
/// open: تقدّم الفتح (0..1، بعد التسارع). close: تقدّم الانثناء (0..1، بزمن خطي). radius: زوايا النافذة.
[[ stitchable ]] float2 edgeFold(float2 position, float2 size, float slitL, float slitR, float open, float close, float radius) {
    float w = size.x;
    float h = size.y;
    float2 none = float2(-100000.0, -100000.0);
    // لا شيء فوق خط الفتحة (حافة النافذة العليا عند أسفل الزر): ما دخل الفتحة يختفي عندها كما في أندرويد.
    // SwiftUI يحسب الشيدر أيضًا لبكسلات خارج حدود النافذة (بقدر maxSampleOffset)، فكان المحتوى المسحوب
    // يُرسم فوق الحافة شريطًا رفيعًا يصعد فوق الزر والشريط.
    if (position.y < 0.0) return none;
    if (close > 0.0) {
        // مرحلتان متداخلتان: الانحناء نحو الفتحة، والانسحاب داخلها (نفس منحنيات أندرويد)
        float bend = ease3(clamp(close / 0.5, 0.0, 1.0));
        float x = clamp((close - 0.2) / 0.8, 0.0, 1.0);
        float pull = x * x * (2.2 - 1.2 * x);
        float sy = position.y + pull * h;
        if (sy > h) return none;
        float d = smooth5(clamp(position.y / h, 0.0, 1.0));
        float l = mix(0.0, mix(slitL, 0.0, d), bend);
        float r = mix(w, mix(slitR, w, d), bend);
        if (r - l < 0.5 || position.x < l || position.x > r) return none;
        return float2((position.x - l) / (r - l) * w, sy);
    }
    // الفتح: مستطيل بزوايا مستديرة يكبر من خط بعرض الفتحة إلى النافذة كاملة
    float l = mix(slitL, 0.0, open);
    float r = mix(slitR, w, open);
    float b = max(mix(0.0, h, open), 1.0);
    float rad = min(mix(6.0, radius, open), min((r - l) / 2.0, b / 2.0));
    if (position.x < l || position.x > r || position.y < 0.0 || position.y > b) return none;
    float2 c = float2(clamp(position.x, l + rad, r - rad), clamp(position.y, rad, b - rad));
    if (distance(position, c) > rad) return none;
    return position;
}
