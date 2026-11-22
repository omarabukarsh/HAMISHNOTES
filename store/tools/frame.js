// Frames real app screenshots into Play Store listing images.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const DIR = __dirname, SHOTS = process.env.SHOTS || path.join(DIR, 'shots'), OUT = process.env.OUT || path.join(DIR, '..', 'listing');
fs.mkdirSync(OUT, { recursive: true });

const C = {
  reader:     { en: ['Write on your <m>lectures</m>', 'Pens with pressure, highlighters, shapes and sticky notes — right on the PDF.'],
                ar: ['اكتب على <m>محاضراتك</m>', 'أقلام بالضغط، وتظليل، وأشكال، وملاحظات لاصقة — مباشرة على ملف PDF.'] },
  summary:    { en: ['Highlight it. It’s in your <m>summary</m>', 'Everything you highlight is collected into a clean summary, automatically.'],
                ar: ['ظلّل… وتجده في <m>الملخص</m>', 'كل ما تظلّله يُجمع في ملخص مرتّب تلقائيًا.'] },
  library:    { en: ['Every lecture, <m>by subject</m>', 'Exam countdowns, reading time, and pick up right where you left off.'],
                ar: ['كل محاضراتك <m>حسب المادة</m>', 'عدّاد للامتحان، ووقت القراءة، وتابع من حيث توقفت.'] },
  mindmap:    { en: ['Turn highlights into a <m>mind map</m>', 'See the whole lecture at a glance.'],
                ar: ['حوّل تظليلك إلى <m>خريطة ذهنية</m>', 'المحاضرة كلها في نظرة واحدة.'] },
  flashcards: { en: ['Review with <m>flashcards</m>', 'Turn key facts into cards and test yourself before the exam.'],
                ar: ['راجع ببطاقات <m>المراجعة</m>', 'حوّل المعلومات المهمة إلى بطاقات واختبر نفسك قبل الامتحان.'] },
  split:      { en: ['Two documents <m>side by side</m>', 'Lecture and slides together — write on both.'],
                ar: ['مستندان <m>جنبًا إلى جنب</m>', 'المحاضرة والشرائح معًا — واكتب على الاثنين.'] },
  stats:      { en: ['Stay on <m>track</m>', 'Daily goal, study streak and time spent on every subject.'],
                ar: ['التزم <m>بخطتك</m>', 'هدف يومي، وأيام دراسة متتالية، ووقت كل مادة.'] },
  'reader-dark': { en: ['Dark mode for <m>late nights</m>', 'Easy on the eyes. Works offline, and your files stay on your device.'],
                ar: ['وضع داكن <m>للسهر</m>', 'مريح للعين، يعمل دون إنترنت، وملفاتك تبقى على جهازك.'] },
};
// order on the store; dark backgrounds alternate for rhythm
const ORDER = ['reader', 'summary', 'library', 'mindmap', 'flashcards', 'split', 'stats', 'reader-dark'];
const DEVICES = {
  tabletp:{ W: 1600, H: 2560, src: 'tabletp', portrait: true },
  tablet: { W: 2560, H: 1600, src: 'tablet', portrait: false },
};

function html(dev, lang, key, idx, file) {
  const [title, sub] = C[key][lang];
  const dark = idx % 2 === 1;
  const rtl = lang === 'ar';
  const { W, H, portrait } = dev;
  const u = Math.min(W, H) / 100; // 1u = 1% of the short side
  const bezel = 1.5 * u, radius = (portrait ? 6 : 3.2) * u;
  const shotW = portrait ? W * 0.80 : W * 0.78;
  const top = portrait ? H * 0.205 : H * 0.235;
  return `<!doctype html><html dir="${rtl ? 'rtl' : 'ltr'}"><head><meta charset="utf-8"><style>
@font-face { font-family: Almarai; src: url(fonts/almarai_extrabold.ttf); font-weight: 800 }
@font-face { font-family: Almarai; src: url(fonts/almarai_bold.ttf); font-weight: 700 }
@font-face { font-family: Almarai; src: url(fonts/almarai_regular.ttf); font-weight: 400 }
* { margin: 0; box-sizing: border-box }
html, body { width: ${W}px; height: ${H}px; overflow: hidden }
body { font-family: Almarai, sans-serif; position: relative;
  background: ${dark ? 'radial-gradient(120% 80% at 50% 0%, #2B3D63 0%, #1E2A44 55%, #141C2E 100%)'
                     : 'radial-gradient(120% 80% at 50% 0%, #FFFDF7 0%, #FAF4E6 60%, #F3E8CF 100%)'} }
.dots { position: absolute; inset: 0; opacity: ${dark ? .07 : .35};
  background-image: radial-gradient(${dark ? '#fff' : '#E4D6B5'} ${0.18 * u}px, transparent ${0.2 * u}px); background-size: ${2.6 * u}px ${2.6 * u}px }
.cap { position: absolute; top: ${portrait ? 5.5 * u : 4.6 * u}px; left: ${7 * u}px; right: ${7 * u}px; text-align: center; color: ${dark ? '#FFFFFF' : '#1E2A44'} }
h1 { font-weight: 800; font-size: ${(portrait ? 7.4 : 6.0) * u * (rtl ? 1.05 : 1)}px; line-height: 1.22; letter-spacing: ${rtl ? 0 : -0.02}em }
m { position: relative; white-space: nowrap; z-index: 0 }
m::before { content: ''; position: absolute; z-index: -1; left: -0.12em; right: -0.12em; bottom: 0.06em; height: 0.42em;
  background: #F4C95D; border-radius: 0.12em; transform: rotate(-1deg); opacity: ${dark ? .95 : 1} }
${dark ? 'm { color: #1E2A44 } m::before { bottom: -0.04em; height: 1.08em; top: auto }' : ''}
p { margin-top: ${1.6 * u}px; font-size: ${(portrait ? 3.15 : 2.45) * u}px; line-height: 1.45; color: ${dark ? '#C9D3E6' : '#5B6577'}; font-weight: 400 }
.dev { position: absolute; left: 50%; top: ${top}px; width: ${shotW + 2 * bezel}px; transform: translateX(-50%);
  background: #0E1117; border-radius: ${radius + bezel}px; padding: ${bezel}px;
  box-shadow: 0 ${3 * u}px ${7 * u}px rgba(20, 28, 46, ${dark ? .55 : .28}), 0 0 0 ${0.25 * u}px ${dark ? 'rgba(255,255,255,.12)' : 'rgba(30,42,68,.08)'} }
.dev img { display: block; width: 100%; border-radius: ${radius}px }
</style></head><body><div class="dots"></div>
<div class="cap"><h1>${title.replace(/<m>/g, '<m>').replace(/<\/m>/g, '</m>')}</h1><p>${sub}</p></div>
<div class="dev"><img src="${file}"></div></body></html>`;
}

(async () => {
  const which = process.argv.slice(2);
  const b = await chromium.launch();
  const page = await b.newPage();
  for (const [dname, dev] of Object.entries(DEVICES)) for (const lang of ['en', 'ar']) {
    if (which.length && !which.includes(`${dname}-${lang}`)) continue;
    let n = 0;
    for (const key of ORDER) {
      const shot = path.join(SHOTS, `${dev.src}-${lang}-${key}.png`);
      if (!fs.existsSync(shot)) continue;
      if (dev.portrait && key === 'split') continue; // half-width panes overflow the toolbar; see notes
      n++;
      await page.setViewportSize({ width: dev.W, height: dev.H });
      const tmp = path.join(DIR, 'tmp.html');
      fs.writeFileSync(tmp, html(dev, lang, key, n - 1, 'file://' + shot));
      await page.goto('file://' + tmp);
      await page.evaluate(() => document.fonts.ready);
      // the device sits just under the caption, whatever its line count
      await page.evaluate((gap) => {
        const cap = document.querySelector('.cap').getBoundingClientRect();
        document.querySelector('.dev').style.top = (cap.bottom + gap) + 'px';
      }, Math.min(dev.W, dev.H) * (dev.portrait ? 0.055 : 0.04));
      await page.waitForTimeout(150);
      const out = path.join(OUT, `${dname}-${lang}`, `${String(n).padStart(2, '0')}-${key}.png`);
      fs.mkdirSync(path.dirname(out), { recursive: true });
      await page.screenshot({ path: out });
      console.log(out);
    }
  }
  await b.close();
})();
