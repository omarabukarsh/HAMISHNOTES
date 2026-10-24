"""
تجربة الترتيب المنظَّم للملخص بنموذج Gemma 4 E2B (نفس ملف .litertlm الذي في التطبيق)
على تظليلات حقيقية من antibiotics.pdf (إنجليزي وعربي). يطابق منطق AiStructure.kt:
عناوين بالحجم (مستوى 1 و2) وبالخط العريض (مستوى 2)، جداول بالكود برأسها من الصفحة،
أجزاء صغيرة بالكود، فحص ضد الاختلاق، إبراز المهم، ودمج التكرار. يكتب results.md.
"""
import collections
import re
import sys
import time

TSV = "app/src/sharedTest/assets/antibiotics_words.tsv"
PDF = "app/src/sharedTest/assets/antibiotics.pdf"
MODEL = sys.argv[1] if len(sys.argv) > 1 else "model.litertlm"
STUB = MODEL == "--stub"

# ---------------------------------------------------------------- الكلمات + الخط العريض

rows = [l.rstrip("\n").split("\t") for l in open(TSV, encoding="utf-8") if not l.startswith("#")]
bold_of = {}
try:
    import pdfplumber

    with pdfplumber.open(PDF) as pdf:
        for pno in sorted({int(r[0]) for r in rows}):
            pg = pdf.pages[pno]
            ws = pg.extract_words(extra_attrs=["fontname"])
            W = float(pg.width)
            for w in ws:
                bold_of.setdefault(pno, []).append(
                    (w["x0"] / W, w["top"] / W, w["text"], bool(re.search(r"(?i)bold|black|heavy|semibold|demi", w["fontname"]))))
except Exception as e:  # noqa
    print("pdfplumber unavailable, no bold info:", e)


def is_bold(page, l, t, text):
    best = None
    for x, y, tx, b in bold_of.get(page, []):
        d = abs(x - l) + abs(y - t)
        if d < 0.03 and (best is None or d < best[0]):
            best = (d, b)
    return bool(best and best[1])


def arabic_fix(w):
    """مثل ArabicFix.word في التطبيق: «ال» المستقلة ← «لا»، و«األ» ← «الأ»."""
    core = w.strip("()[]«».,،:؛;!؟?\"'")
    fixed = {"ال": "لا", "وال": "ولا", "فال": "فلا"}.get(core)
    if fixed:
        w = w.replace(core, fixed)
    if re.search(r"[\u0600-\u06FF]", w):
        for a, b in (("األ", "الأ"), ("اإل", "الإ"), ("اآل", "الآ")):
            w = w.replace(a, b)
    return w


pages = collections.defaultdict(lambda: collections.OrderedDict())
for p, ln, l, t, r, b, txt in rows:
    p, l, t, r, b = int(p), float(l), float(t), float(r), float(b)
    pages[p].setdefault(int(ln), []).append((l, t, r, b, arabic_fix(txt), is_bold(p, l, t, txt)))


def med(v):
    v = sorted(v)
    return v[len(v) // 2] if v else 0


def is_ar(s):
    return sum(1 for c in s if "؀" <= c <= "ۿ") > sum(1 for c in s if c.isalpha()) / 3


def line_h(ws):
    return max(med([w[3] - w[1] for w in ws]), 1e-4)


def line_text(ws, cells=True):
    ar = is_ar(" ".join(w[4] for w in ws))
    ws = sorted(ws, key=lambda w: -w[2] if ar else w[0])
    h = line_h(ws)
    out = ws[0][4]
    for a, b in zip(ws, ws[1:]):
        gap = (a[0] - b[2]) if ar else (b[0] - a[2])
        out += (" | " if cells and gap > h * 1.6 else " ") + b[4]
    return out


# ---------------------------------------------------------------- تظليلات طالب

# (صفحة، أسطر أو None، مدى رأسي أو None، أجزاء مظللة مرتين)
HIGHLIGHTS = [
    (3, None, (0.685, 0.740), []), (3, None, (0.750, 0.810), []), (3, None, (0.850, 0.860), ["active against growing, not resting cells"]),
    (3, [40, 41, 42], None, []),
    (4, [2, 3], None, []), (4, [4, 5], None, []), (4, [6, 7], None, []), (4, [8], None, []),
    (4, [10], None, []), (4, [11], None, []), (4, [12], None, []),
    (4, [14, 15], None, []), (4, [17], None, ["NOT recommended"]), (4, [18, 19], None, []), (4, [20], None, []),
    (4, [24], None, []), (4, [25, 26], None, []),
    (5, [4, 5], None, []), (5, [6], None, []), (5, [7, 8], None, []), (5, [10], None, ["drug of choice"]), (5, [13], None, []),
    (5, [18], None, []), (5, [19], None, []),
    (5, [21], None, []), (5, [22], None, []), (5, [23], None, []),
    (9, [1, 2], None, []), (9, [3], None, []), (9, [4, 5], None, []),
    (10, [3], None, []), (10, [4], None, []), (10, [6], None, []),
    (10, [29], None, []), (10, [30], None, []), (10, [37], None, []), (10, [38], None, []),
]

marks = []
for page, lines, trange, emph in HIGHLIGHTS:
    pl = pages[page]
    if lines is None:
        lines = [ln for ln, ws in pl.items() if trange[0] <= ws[0][1] <= trange[1]]
    texts = [line_text(pl[ln]) for ln in lines]
    text = texts[0]
    for t in texts[1:]:
        text += " " + t.replace(" | ", " ")
    top = min(pl[ln][0][1] for ln in lines)
    marks.append({"page": page, "top": top, "text": text, "emph": emph})


# ---------------------------------------------------------------- العناوين (مثل AiStructure.pageHeadings)

def pct(v, q):
    v = sorted(v)
    return v[min(len(v) - 1, int(len(v) * q))] if v else 0


def page_headings(page):
    pl = pages[page]
    normal = med([line_h(ws) for ws in pl.values()])
    left = pct([min(w[0] for w in ws) for ws in pl.values()], 0.1)
    right = pct([max(w[2] for w in ws) for ws in pl.values()], 0.9)
    out = []
    for ws in pl.values():
        txt = " ".join(w[4] for w in ws)
        at_margin = (max(w[2] for w in ws) >= right - 0.06) if is_ar(txt) else (min(w[0] for w in ws) <= left + 0.03)
        t = line_text(ws, cells=False).strip()
        if not (3 <= len(t) <= 90) or not re.search(r"[^\W\d_]", t) or re.search(r"(?i)page \d+|^\d+$", t):
            continue
        h = line_h(ws)
        pos = min(w[1] for w in ws)
        if h >= normal * 1.25:
            out.append((page, pos, t, 1))
        elif not at_margin:
            continue
        elif h >= normal * 1.15:
            out.append((page, pos, t, 2))
        elif sum(1 for w in ws if w[5]) * 5 >= len(ws) * 4 and len(ws) <= 10 and " | " not in line_text(ws) and not t.endswith("."):
            out.append((page, pos, t.rstrip(":").strip(), 2))
    return sorted(out, key=lambda h: h[1])


DETECTED = {p: page_headings(p) for p in sorted({m["page"] for m in marks})}


def title_for(m):
    before = []
    for p in range(max(0, m["page"] - 3), m["page"] + 1):
        before += [h for h in (DETECTED.get(p) or page_headings(p)) if p < m["page"] or h[1] <= m["top"] + 0.002]
    main = next((h for h in reversed(before) if h[3] == 1), None)
    sub = next((h for h in reversed(before) if h[3] == 2 and (main is None or (h[0], h[1]) > (main[0], main[1]))), None)
    if sub and m["page"] - sub[0] > 1:
        sub = None
    return (main[2] if main else None, sub[2] if sub else None)


# ---------------------------------------------------------------- النموذج

if not STUB:
    import litert_lm

    try:
        SAMPLER = litert_lm.SamplerConfig(top_k=20, top_p=0.9, temperature=0.2)
    except Exception:  # noqa
        SAMPLER = None
    engine = litert_lm.Engine(MODEL, backend=litert_lm.Backend.CPU)


def gen(system, prompt):
    if STUB:
        return "- stub", 0.0
    kw = {"sampler_config": SAMPLER} if SAMPLER is not None else {}
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


LATEX = {"\\beta": "β", "\\alpha": "α", "\\rightarrow": "→", "\\to": "→", "\\uparrow": "↑", "\\downarrow": "↓",
         "\\approx": "≈", "\\geq": "≥", "\\ge": "≥", "\\leq": "≤", "\\le": "≤", "\\times": "×", "\\%": "%"}


def latex(s):
    def rep(m):
        x = m.group(1)
        for k in sorted(LATEX, key=len, reverse=True):
            x = x.replace(k, LATEX[k])
        return re.sub(r"_\{([^}]*)\}", r"\1", x).replace("{", "").replace("}", "").strip()
    s = re.sub(r"\$([^$\n]{1,40})\$", rep, s)
    for k in sorted(LATEX, key=len, reverse=True):
        s = s.replace(k, LATEX[k])
    return s


def clean(raw):
    out = []
    for l in latex(raw).replace("```", "").replace("**", "").splitlines():
        l = l.rstrip()
        t = l.strip()
        if t.startswith("* ") or t.startswith("• "):
            l = "- " + t[2:]
        if t and all(c in "|-: " for c in t):
            continue
        if t.startswith("|") and t.endswith("|") and len(t) > 2:
            l = " | ".join(c.strip() for c in t.strip("|").split("|"))
        out.append(l)
    return re.sub(r"\n{3,}", "\n\n", "\n".join(out)).strip()


SYSTEM = open("app/src/main/java/com/mynotes/app/AiStructure.kt", encoding="utf-8").read()
_ST = SYSTEM
SYSTEM = re.search(r'const val SYSTEM = """(.*?)"""', _ST, re.S).group(1)
REVIEW_SYSTEM = re.search(r'const val REVIEW_SYSTEM = """(.*?)"""', _ST, re.S).group(1)

COMMON = set("""drug drugs class route spectrum mechanism action uses use side effect effects adverse notes note example examples
generation dose doses indication indications contraindication feature features type group name comparison summary important
other infection infections inhibitor combined brand detail details resistance treatment organism given used only
with from that this table header""".split())


def norm(s):
    return re.sub(r"[^\w]+", " ", s.lower()).strip()


NEG = re.compile(r"(?i)\b(not|no|never|without|except|avoid|contraindicated)\b|(?<![^\W\d_])[وف]?(لا|ليس|ليست|غير|لن|لم|بدون|دون|يُمنع|ممنوع)(?![^\W\d_])")


def dropped(out, notes):
    nums = set(re.findall(r"\d+(?:[.,]\d+)?", out))
    if any(n not in nums for n in re.findall(r"\d+(?:[.,]\d+)?", notes)):
        return "number"
    ow = set(w.lower() for w in re.findall(r"[A-Za-z][A-Za-z-]{4,}", out))
    terms = set(w.lower() for w in re.findall(r"[A-Za-z][A-Za-z-]{4,}", notes)) - COMMON
    if len(terms) >= 4 and sum(1 for w in terms if w not in ow) * 4 > len(terms):
        return "terms"
    if len(NEG.findall(out)) < len(NEG.findall(notes)):
        return "negation"
    return ""


def suspicious(out, allowed):
    nums = set(re.findall(r"\d+(?:[.,]\d+)?", out))
    ok_nums = set(re.findall(r"\d+(?:[.,]\d+)?", allowed))
    bad_nums = [n for n in nums if n not in ok_nums]
    words = [w.lower() for w in re.findall(r"[A-Za-z][A-Za-z-]{4,}", out)]
    ok_words = set(w.lower() for w in re.findall(r"[A-Za-z][A-Za-z-]{4,}", allowed)) | COMMON
    unknown = [w for w in words if w not in ok_words]
    return bool(bad_nums) or len(unknown) > max(3, len(words) // 4), bad_nums, unknown


def fallback(notes):
    return "\n".join(l if " | " in l else "- " + l.strip() for l in notes.splitlines() if l.strip())


def emphasize(body, emph):
    for e in sorted(emph, key=len, reverse=True):
        i = body.lower().find(e.lower())
        if i < 0 or body[:i].count("*") % 4:
            continue
        body = body[:i] + "**" + body[i:i + len(e)] + "**" + body[i + len(e):]
    return body


def cells(text):
    return text.count(" | ") + 1 if " | " in text else 0


def table_header(m, n):
    def block_of(page, before_top):
        ls = sorted(pages[page].values(), key=lambda ws: ws[0][1])
        if before_top is not None:
            ls = [ws for ws in ls if ws[0][1] < before_top - 0.001]
        block = []
        for ws in reversed(ls):
            lt = line_text(ws)
            if cells(lt) == n:
                block.insert(0, lt)
            elif block:
                break
        return block
    block = block_of(m["page"], m["top"]) or (block_of(m["page"] - 1, None) if m["page"] > 0 else [])
    if not block or m["text"].startswith(block[0]):
        return None
    cs = [c.strip() for c in block[0].split(" | ")]
    return cs if len(cs) == n and all(len(c) <= 22 for c in cs) and len(block[0]) <= 60 else None


# ---------------------------------------------------------------- الأجزاء (مثل AiStructure.parts)

groups = []
for m in marks:
    key = title_for(m)
    if groups and groups[-1][0] == key:
        groups[-1][1].append(m)
    else:
        groups.append((key, [m]))

done, log, total = [], [], 0.0
for (title, sub), ms in groups:
    runs_ = []
    for m in ms:
        k = cells(m["text"])
        if runs_ and runs_[-1][0] == k and (k >= 2 or k == 0):
            runs_[-1][1].append(m)
        else:
            runs_.append((k, [m]))
    for k, rm in runs_:
        emph = [e for m in rm for e in m["emph"]]
        if k >= 2:
            head = table_header(rm[0], k)
            body = "\n".join(([" | ".join(head)] if head else []) + [m["text"].replace("®", "") for m in rm])
            log.append(f"- [{title} › {sub}] table by code ({len(rm)} rows, header={'yes' if head else 'no'})")
        else:
            notes = "\n".join(m["text"] for m in rm)
            if len(notes) <= 200 and len([l for l in notes.splitlines() if l.strip()]) <= 2:
                body = fallback(notes)
                log.append(f"- [{title} › {sub}] small part by code")
            else:
                o, dt = gen(SYSTEM, "SECTION: " + " › ".join(x for x in (title, sub) if x) + "\nNOTES:\n" + notes)
                total += dt
                body = clean(o)
                heads = [norm(x) for x in (title, sub) if x]
                body = "\n".join(("- " + l.lstrip("# ") if l.lstrip().startswith("#") else l)
                                 for l in body.splitlines() if norm(l.lstrip("#- ")) not in heads).strip()
                bad, bn, unk = suspicious(body, notes + " " + (title or "") + " " + (sub or ""))
                why = dropped(body, notes)
                if why:
                    log.append(f"  dropped check → fallback ({why})")
                    bad = True
                log.append(f"- [{title} › {sub}] model {dt:.1f}s, fallback={bad}, new numbers={bn}, unknown={unk[:6]}")
                if bad or not body:
                    body = fallback(notes)
                else:
                    # مراجعة ذاتية (مثل REVIEW_SYSTEM في AiStructure)
                    o2, dt2 = gen(REVIEW_SYSTEM, "SECTION: " + " › ".join(x for x in (title, sub) if x) + "\nNOTES:\n" + notes + "\n\nDRAFT:\n" + body)
                    total += dt2
                    r = clean(o2).strip()
                    if not r or re.match(r"(?i)^ok\b", r):
                        log.append(f"  review {dt2:.1f}s → OK")
                    else:
                        r = "\n".join(("- " + l.lstrip("# ") if l.lstrip().startswith("#") else l)
                                      for l in r.splitlines() if norm(l.lstrip("#- ")) not in heads).strip()
                        b2, _, u2 = suspicious(r, notes + " " + (title or "") + " " + (sub or ""))
                        w2 = dropped(r, notes)
                        if r and not b2 and not w2:
                            log.append(f"  review {dt2:.1f}s → CORRECTED")
                            body = r
                        else:
                            log.append(f"  review {dt2:.1f}s → correction rejected ({w2 or u2[:4]})")
        done.append((title, sub, emphasize(body, emph)))

# ---------------------------------------------------------------- التجميع (مثل AiStructure.assemble)

sections = collections.OrderedDict()
for t, s, b in done:
    sections.setdefault((t, s), []).append(b)
seen, out, last = set(), [], "\0"
for (title, sub), bodies in sections.items():
    if title != last:
        if out and out[-1].strip():
            out.append("")
        if title:
            out.append("# " + title)
        last = title
    if sub:
        out.append("## " + sub)
    for line in "\n\n".join(bodies).splitlines():
        k = norm(line.replace("**", ""))
        if line.strip() and " | " not in line and k and k in seen:
            continue
        seen.add(k)
        if not line.strip() and (not out or not out[-1].strip()):
            continue
        out.append(line)
    out.append("")
result = "\n".join(out).strip()

# ---------------------------------------------------------------- الخريطة الذهنية بالنقاط المهمة (مثل MindMapAi)

MMK = open("app/src/main/java/com/mynotes/app/MindMap.kt", encoding="utf-8").read()
MM_SYSTEM = re.search(r'const val SYSTEM = """(.*?)"""', MMK, re.S).group(1)
EXPLAIN = re.compile(r"(?i)(شرح|توضيح|explain|explanation)")
secs, h1, h2, buf = [], None, None, []
def mm_flush():
    if buf and not any(EXPLAIN.search(h or "") for h in (h1, h2)):
        secs.append((h1, h2, "\n".join(buf)))
    buf.clear()
for x in result.splitlines():
    x = x.strip()
    if x.startswith("## "):
        mm_flush(); h2 = x[3:]
    elif x.startswith("# "):
        mm_flush(); h1 = x[2:]; h2 = None
    elif x:
        buf.append(x)
mm_flush()
mm, last1, mmt = [], object(), 0
for a, b, body in secs:
    if a != last1:
        mm.append("  ● " + (a or "")); last1 = a
    if b:
        mm.append("    ├─ " + b)
    if len([l for l in body.splitlines() if l.strip()]) <= 2:
        pts, how = [l.lstrip("- ")[:50] for l in body.splitlines()], "code"
    else:
        o, dt = gen(MM_SYSTEM, "SECTION: " + " › ".join(x for x in (a, b) if x) + "\n" + body)
        mmt += dt
        pts = [l.strip()[2:].strip() for l in clean(o).splitlines() if l.strip().startswith(("- ", "• "))]
        pts = [q for q in pts if 3 <= len(q) <= 60 and not suspicious(q, body + " " + (a or "") + " " + (b or ""))[0]][:5]
        how = f"ai {dt:.0f}s"
    for q in pts:
        mm.append(f"        · {q}   [{how}]")
log.append(f"- mind map AI time {mmt:.0f}s")
table_log = []

with open("results.md", "w", encoding="utf-8") as f:
    f.write(f"# Structured summary eval v3 (tables in summary + self-review + AI mind map) — Gemma 4 E2B, {len(marks)} highlights, model time {total:.0f}s\n\n")
    f.write("## Detected headings\n\n")
    for p, hs in DETECTED.items():
        f.write(f"- p{p}: " + " ; ".join(f"L{h[3]} {h[2][:40]}" for h in hs) + "\n")
    f.write(f"\n## Result\n\n```\n{result}\n```\n\n## Log\n\n" + "\n".join(log) + "\n")
    f.write("\n## Mind map tree\n\n```\nAntibiotics\n" + "\n".join(mm) + "\n```\n")
print(open("results.md", encoding="utf-8").read())
