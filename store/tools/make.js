const { chromium } = require('playwright');
const decks = [
  { file: 'Cardiology - Heart Failure', color: '#1f4e79', accent: '#e8f0f8', title: 'Heart Failure', sub: 'Cardiology · Lecture 4', sections: [
    ['Definition', ['Inability of the heart to pump enough blood to meet metabolic demands', 'HFrEF: ejection fraction ≤ 40%', 'HFpEF: ejection fraction ≥ 50%', 'Most common causes: ischemic heart disease, hypertension, valvular disease']],
    ['Clinical features', ['Left-sided: dyspnea, orthopnea, PND, basal crackles', 'Right-sided: raised JVP, hepatomegaly, ankle edema', 'Fatigue and reduced exercise tolerance', 'S3 gallop on auscultation']],
    ['Management', ['ACE inhibitor or ARNI + beta-blocker', 'Mineralocorticoid receptor antagonist', 'SGLT2 inhibitor', 'Loop diuretics for fluid overload', 'Device therapy: ICD / CRT when indicated']]]},
  { file: 'Organic Chemistry - Reactions', color: '#2e7d4f', accent: '#eaf6ee', title: 'Substitution & Elimination', sub: 'Organic Chemistry · Chapter 7', sections: [
    ['SN1 vs SN2', ['SN2: one step, backside attack, inversion of configuration', 'SN1: carbocation intermediate, racemization', 'Tertiary substrates favour SN1; methyl/primary favour SN2', 'Polar aprotic solvents speed up SN2']],
    ['E1 vs E2', ['E2: strong base, anti-periplanar geometry', 'E1: weak base, carbocation intermediate', 'Zaitsev product = more substituted alkene', 'Bulky base → Hofmann product']]]},
  { file: 'Anatomy - Upper Limb', color: '#8a3b2f', accent: '#f8ece9', title: 'The Upper Limb', sub: 'Human Anatomy · Unit 2', sections: [
    ['Brachial plexus', ['Roots C5–T1 → trunks → divisions → cords → branches', 'Musculocutaneous, axillary, radial, median, ulnar nerves', 'Erb palsy: C5–C6 — "waiter’s tip" position', 'Klumpke palsy: C8–T1 — claw hand']],
    ['Rotator cuff', ['Supraspinatus, Infraspinatus, Teres minor, Subscapularis', 'Supraspinatus initiates abduction (0–15°)', 'Most commonly torn: supraspinatus tendon']]]},
  { file: 'Calculus II - Series', color: '#5b3f8c', accent: '#f1ecf8', title: 'Infinite Series', sub: 'Calculus II · Week 9', sections: [
    ['Convergence tests', ['Divergence test: if lim aₙ ≠ 0 the series diverges', 'p-series Σ 1/nᵖ converges when p > 1', 'Ratio test: L < 1 converges, L > 1 diverges', 'Comparison and limit comparison tests']],
    ['Power series', ['Σ cₙ(x − a)ⁿ — find radius with the ratio test', 'Check both endpoints separately', 'Taylor series: f⁽ⁿ⁾(a)/n! · (x − a)ⁿ']]]},
  { file: 'Microbiology - Bacteria', color: '#b5651d', accent: '#fbf0e4', title: 'Bacterial Structure', sub: 'Microbiology · Lecture 2', sections: [
    ['Cell wall', ['Gram-positive: thick peptidoglycan, teichoic acids', 'Gram-negative: thin peptidoglycan + outer membrane (LPS)', 'Acid-fast: mycolic acids (Mycobacterium)']],
    ['Virulence factors', ['Capsule: anti-phagocytic', 'Pili / fimbriae: adherence', 'Exotoxins vs endotoxin (lipid A)', 'Spores: Bacillus and Clostridium']]]},
  { file: 'Physiology - Renal', color: '#0f6e78', accent: '#e6f4f5', title: 'Renal Physiology', sub: 'Physiology · Lecture 11', sections: [
    ['Glomerular filtration', ['GFR ≈ 125 mL/min', 'Starling forces across the glomerular capillary', 'Afferent constriction ↓ GFR; efferent constriction ↑ GFR']],
    ['Tubular transport', ['Proximal tubule reabsorbs ~65% of Na⁺ and water', 'Loop of Henle: countercurrent multiplier', 'ADH inserts aquaporin-2 in collecting duct']]]},
];
const css = (d) => `
@page { size: 1280px 720px; margin: 0 }
* { box-sizing: border-box; margin: 0 }
body { font-family: 'DejaVu Sans', Arial, sans-serif; color: #1d2433 }
.page { width: 1280px; height: 720px; page-break-after: always; position: relative; overflow: hidden }
.cover { background: ${d.color}; color: white; padding: 110px 100px }
.cover .tag { font-size: 22px; letter-spacing: 3px; text-transform: uppercase; opacity: .8 }
.cover h1 { font-size: 92px; margin: 26px 0 20px; line-height: 1.05 }
.cover .line { width: 140px; height: 6px; background: white; opacity: .7; margin: 34px 0 }
.cover .shape { position: absolute; right: -120px; bottom: -120px; width: 520px; height: 520px; border-radius: 50%; background: rgba(255,255,255,.08) }
.cover .shape2 { position: absolute; right: 120px; top: 80px; width: 200px; height: 200px; border-radius: 50%; border: 14px solid rgba(255,255,255,.12) }
.content { padding: 64px 90px }
.content .hd { border-bottom: 5px solid ${d.color}; padding-bottom: 14px; display: flex; justify-content: space-between; align-items: baseline }
.content h2 { font-size: 50px; color: ${d.color} }
.content .crumb { font-size: 18px; color: #7a8394 }
ul { margin-top: 38px; padding-left: 34px }
li { font-size: 31px; line-height: 1.45; margin: 12px 0 }
.box { position: absolute; right: 90px; bottom: 60px; background: ${d.accent}; border-left: 8px solid ${d.color}; padding: 18px 26px; font-size: 22px; max-width: 520px }
.foot { position: absolute; left: 90px; bottom: 26px; font-size: 15px; color: #9aa1ad }
`;
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  for (const d of decks) {
    let html = `<style>${css(d)}</style><div class="page cover"><div class="shape"></div><div class="shape2"></div><div class="tag">${d.sub}</div><h1>${d.title}</h1><div class="line"></div><div style="font-size:26px;opacity:.85">Lecture slides</div></div>`;
    d.sections.forEach(([h, items], i) => {
      html += `<div class="page content"><div class="hd"><h2>${h}</h2><span class="crumb">${d.title}</span></div><ul>${items.map(x => `<li>${x}</li>`).join('')}</ul>${i == 0 ? `<div class="box"><b>Key point:</b> learn the definitions first — they come up in every exam.</div>` : ''}<div class="foot">${d.sub} · page ${i + 2}</div></div>`;
    });
    await p.setContent(html);
    await p.pdf({ path: d.file + '.pdf', width: '1280px', height: '720px', printBackground: true });
  }
  await b.close();
})();
