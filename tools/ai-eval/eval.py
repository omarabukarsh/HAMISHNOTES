"""
تجربة ترتيب الملخص بنموذج Gemma 4 E2B (نفس ملف .litertlm الذي في التطبيق) على تظليلات حقيقية من antibiotics.pdf.
يقارن الطريقة الحالية (النموذج يرتّب كل شيء دفعة واحدة) بالطريقة الجديدة (الكود يقسّم حسب عناوين المحاضرة،
والنموذج يصوغ كل مجموعة صغيرة، ثم فحص ضد الاختلاق ودمج التكرار).
يكتب النتائج في results.md.
"""
import collections
import re
import sys
import time

import litert_lm

TSV = "app/src/sharedTest/assets/antibiotics_words.tsv"
MODEL = sys.argv[1] if len(sys.argv) > 1 else "model.litertlm"

# ---------------------------------------------------------------- الصفحات والتظليلات

rows = [l.rstrip("\n").split("\t") for l in open(TSV, encoding="utf-8") if not l.startswith("#")]
pages = collections.defaultdict(lambda: collections.OrderedDict())
for p, ln, l, t, r, b, txt in rows:
    pages[int(p)].setdefault(int(ln), []).append((float(l), float(t), float(r), float(b), txt))


def med(v):
    v = sorted(v)
    return v[len(v) // 2] if v else 0


def line_h(ws):
    return med([w[3] - w[1] for w in ws])


def line_text(ws, cells=True):
    ws = sorted(ws, key=lambda w: w[0])
    h = line_h(ws)
    out = ws[0][4]
    for a, b in zip(ws, ws[1:]):
        out += (" | " if cells and b[0] - a[2] > h * 1.6 else " ") + b[4]
    return out


# تظليلات طالب: (صفحة، أرقام الأسطر). كل عنصر = تظليل واحد
HIGHLIGHTS = [
    (3, None, (0.685, 0.740)), (3, None, (0.750, 0.810)), (3, None, (0.850, 0.860)),
    (4, [2, 3], None), (4, [4, 5], None), (4, [6, 7], None), (4, [8], None),
    (4, [10], None), (4, [11], None), (4, [12], None),
    (4, [14, 15], None), (4, [17], None), (4, [18, 19], None), (4, [20], None),
    (4, [24], None), (4, [25, 26], None),
    (5, [4, 5], None), (5, [6], None), (5, [7, 8], None), (5, [10], None), (5, [13], None),
    (5, [18], None), (5, [19], None),
    (5, [21], None), (5, [22], None), (5, [23], None),
]

marks = []
for page, lines, trange in HIGHLIGHTS:
    pl = pages[page]
    if lines is None:
        lines = [ln for ln, ws in pl.items() if trange[0] <= ws[0][1] <= trange[1]]
    texts = [line_text(pl[ln]) for ln in lines]
    # سطر تكملة (بدون خلايا) يُلحق بآخر خلية
    text = texts[0]
    for t in texts[1:]:
        text += " " + t.replace(" | ", " ")
    top = min(pl[ln][0][1] for ln in lines)
    marks.append({"page": page, "top": top, "text": text})


def page_headings(page):
    pl = pages[page]
    hs = [line_h(ws) for ws in pl.values()]
    normal = med(hs)
    out = []
    for ws in pl.values():
        t = line_text(ws, cells=False)
        if line_h(ws) >= normal * 1.15 and 3 <= len(t) <= 90 and re.search(r"[A-Za-z؀-ۿ]", t) \
                and "Exam Summary" not in t:
            out.append((ws[0][1], t))
    return sorted(out)


def title_for(m):
    for p in range(m["page"], max(-1, m["page"] - 4), -1):
        hs = page_headings(p)
        cand = [h for h in hs if p != m["page"] or h[0] <= m["top"] + 0.002]
        if cand:
            return cand[-1][1]
    return None


# ---------------------------------------------------------------- النموذج

SAMPLER = None
try:
    SAMPLER = litert_lm.SamplerConfig(top_k=20, top_p=0.9, temperature=0.2)
except Exception as e:  # noqa
    print("sampler config unavailable:", e)

engine = litert_lm.Engine(MODEL, backend=litert_lm.Backend.CPU)


def gen(system, prompt):
    kw = {}
    if SAMPLER is not None:
        kw["sampler_config"] = SAMPLER
    try:
        conv = engine.create_conversation(system_message=system, extra_context={"enable_thinking": False}, **kw)
        full = prompt
    except TypeError:
        conv = engine.create_conversation()
        full = system + "\n\n" + prompt
    t0 = time.time()
    with conv:
        out = "".join(str(c) for c in conv.send_message_async(full))
    out = re.sub(r"(?s)<think>.*?</think>", "", out)
    return out.strip(), time.time() - t0


def clean(raw):
    out = []
    for l in raw.replace("```", "").splitlines():
        l = l.replace("**", "").replace("__", "").rstrip()
        t = l.strip()
        if t.startswith("* "):
            l = "- " + t[2:]
        if re.match(r"^\|?\s*:?-{2,}", t):  # فاصل جدول markdown
            continue
        if t.startswith("|") and t.endswith("|"):
            l = " | ".join(c.strip() for c in t.strip("|").split("|"))
        out.append(l)
    return re.sub(r"\n{3,}", "\n\n", "\n".join(out)).strip()


# ---------------------------------------------------------------- الطريقة الحالية

OLD_SYSTEM = """You organize study notes for a pharmacy student.
Reorganize the notes you receive into a clear, well-structured study summary.
Strict rules:
- Use ONLY information found in the notes. Never add facts, numbers, doses or explanations that are not in the notes.
- Do not drop any information from the notes.
- Keep every note in its original language (Arabic stays Arabic, English stays English). Keep drug names and medical terms exactly as written.
- Group related points under short headings. Merge repeated points.
- When several items share the same attributes (for example drug, class, mechanism, side effects), present them as a table.
Output format (plain text only, no other markdown, no introduction, no closing remarks):
# Heading
## Subheading
- bullet point
Tables: one row per line, cells separated by " | ", first row is the header."""


def chunks(text, maxc):
    out, cur = [], ""
    for p in [x for x in text.split("\n\n") if x.strip()]:
        if cur and len(cur) + len(p) > maxc:
            out.append(cur)
            cur = ""
        cur += p + "\n\n"
    if cur.strip():
        out.append(cur)
    return out


all_notes = "\n\n".join(m["text"] for m in marks)
old_parts = chunks(all_notes, 2400)
old_out, old_time = [], 0.0
for part in old_parts:
    o, dt = gen(OLD_SYSTEM, "Notes:\n\n" + part)
    old_out.append(clean(o))
    old_time += dt

# ---------------------------------------------------------------- الطريقة الجديدة

NEW_SYSTEM = """You rewrite highlighted notes from ONE section of a pharmacy lecture into a clean part of a study summary.
Rules:
- Use ONLY the notes. Never add facts, numbers, doses or drug names that are not in the notes.
- Keep every fact. Merge repeated points. Keep drug names and medical terms exactly as written. Arabic stays Arabic.
- Do NOT write the section title; it is added for you.
- If the notes are rows of a table (cells separated by " | "), or several items share the same attributes, output a table: one row per line, cells separated by " | ", first row is a short header.
- Otherwise output short bullets starting with "- ", one fact per bullet, in the same order as the notes.
- Output only the result. No introduction, no markdown symbols other than "- " and " | ".

Example
NOTES:
Clavulanic acid | Amoxicillin (co-amoxiclav) | Augmentin
Sulbactam | Ampicillin | Unasyn
OUTPUT:
Inhibitor | Combined with | Brand
Clavulanic acid | Amoxicillin (co-amoxiclav) | Augmentin
Sulbactam | Ampicillin | Unasyn

Example
NOTES:
Pen V is acid-stable so it is given orally. Used only for mild infections such as pharyngitis and otitis media.
OUTPUT:
- Pen V: acid-stable → given orally
- Used only for mild infections: pharyngitis, otitis media"""

COMMON = set("""drug drugs class route spectrum mechanism action uses use side effect effects adverse notes note example examples
generation dose doses indication indications contraindication feature features type group name comparison summary important
other infection infections inhibitor inhibitor combined brand detail details resistance treatment organism given used only
with from that this table header""".split())


def suspicious(out, allowed):
    nums = set(re.findall(r"\d+(?:[.,]\d+)?", out))
    ok_nums = set(re.findall(r"\d+(?:[.,]\d+)?", allowed))
    bad_nums = [n for n in nums if n not in ok_nums]
    words = [w.lower() for w in re.findall(r"[A-Za-z][A-Za-z-]{4,}", out)]
    ok_words = set(w.lower() for w in re.findall(r"[A-Za-z][A-Za-z-]{4,}", allowed)) | COMMON
    unknown = [w for w in words if w not in ok_words]
    bad = bool(bad_nums) or len(unknown) > max(3, len(words) // 4)
    return bad, bad_nums, unknown


def fallback(notes):
    return "\n".join(l if " | " in l else "- " + l for l in notes.splitlines() if l.strip())


def norm(s):
    return re.sub(r"[^\w]+", " ", s.lower()).strip()


groups = []  # [(title, [marks])]
for m in marks:
    t = title_for(m)
    if groups and groups[-1][0] == t:
        groups[-1][1].append(m)
    else:
        groups.append((t, [m]))

def cells(m):
    return m["text"].count(" | ") + 1 if " | " in m["text"] else 0


def table_header(m, n):
    """رأس الجدول من الصفحة نفسها: أول سطر في كتلة الأسطر المقسّمة إلى خلايا فوق أول صف، إن كانت خلاياه قصيرة."""
    pl = list(pages[m["page"]].values())
    above = [ws for ws in pl if ws[0][1] < m["top"] - 0.001]
    block = []
    for ws in reversed(above):
        lt = line_text(ws)
        if " | " in lt and lt.count(" | ") + 1 == n:
            block.insert(0, lt)
        elif block:
            break
    if not block:
        return None
    cs = block[0].split(" | ")
    if len(cs) == n and all(len(c) <= 22 for c in cs) and len(block[0]) <= 60:
        return cs
    return None


def runs(ms):
    """تقسيم المجموعة: صفوف جدول متتالية بنفس عدد الخلايا = جدول (بالكود)، والباقي نص (بالنموذج)."""
    out = []
    for m in ms:
        k = cells(m)
        if out and out[-1][0] == k:
            out[-1][1].append(m)
        else:
            out.append((k, [m]))
    return out


new_sections, new_time, log = collections.OrderedDict(), 0.0, []
for title, ms in groups:
    for k, rm in runs(ms):
        if k >= 2:
            head = table_header(rm[0], k)
            rows_ = ([" | ".join(head)] if head else []) + [m["text"].replace("®", "") for m in rm]
            new_sections.setdefault(title, []).append("\n".join(rows_))
            log.append(f"- {title}: table by code ({len(rm)} rows, header={'yes' if head else 'no'})")
            continue
        notes = "\n".join(m["text"] for m in rm)
        for part in chunks(notes.replace("\n", "\n\n"), 2400):
            part = part.replace("\n\n", "\n").strip()
            o, dt = gen(NEW_SYSTEM, "SECTION: " + (title or "") + "\nNOTES:\n" + part)
            new_time += dt
            body = clean(o)
            # العنوان الذي قد يكرره النموذج
            body = "\n".join(l for l in body.splitlines() if norm(l.lstrip("#- ")) != norm(title or "~"))
            bad, bn, unk = suspicious(body, part + " " + (title or ""))
            log.append(f"- {title}: {dt:.1f}s, fallback={bad}, new numbers={bn}, unknown words={unk[:8]}")
            if bad:
                body = fallback(part)
            new_sections.setdefault(title, []).append(body)

# دمج التكرار
seen, final = set(), []
for title, bodies in new_sections.items():
    if title:
        final.append("# " + title)
    for line in "\n\n".join(bodies).splitlines():
        k = norm(line)
        if line.strip() and k in seen and " | " not in line:
            continue
        if not line.strip() and final and not final[-1].strip():
            continue
        seen.add(k)
        final.append(line)
    final.append("")
new_out = "\n".join(final).strip()

with open("results.md", "w", encoding="utf-8") as f:
    f.write("# AI summary eval — Gemma 4 E2B (.litertlm, CPU)\n\n")
    f.write(f"Highlights: {len(marks)}\n\n## Raw highlights\n\n```\n{all_notes}\n```\n\n")
    f.write(f"## OLD method ({len(old_parts)} calls, {old_time:.0f}s)\n\n```\n" + "\n\n".join(old_out) + "\n```\n\n")
    f.write(f"## NEW method ({len(log)} calls, {new_time:.0f}s)\n\n```\n{new_out}\n```\n\n### Log\n\n" + "\n".join(log) + "\n")
print(open("results.md", encoding="utf-8").read())
