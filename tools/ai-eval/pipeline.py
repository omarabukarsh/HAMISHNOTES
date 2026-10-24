"""
خط المعالجة كما في التطبيق، بنسختين في نفس التشغيل:
  legacy=True  → التطبيق قبل التحديث (pro@8afc919): AiStructure VERSION 3، MindMapAi mm1، بطاقات من التظليل الخام.
  legacy=False → التطبيق بعد التحديث: AiStructure VERSION 4، MindMapAi mm2، بطاقات من الملخص (checks.py).
البرومبتات الجديدة تُقرأ من ملفات Kotlin نفسها، والقديمة من legacy_prompts.py.
"""
import collections
import re

import checks as C
import legacy_prompts as LP

KT = "app/src/main/java/com/mynotes/app/"


def _kt(file, name):
    src = open(KT + file, encoding="utf-8").read()
    return re.search(r'const val ' + name + r' = """(.*?)"""', src, re.S).group(1)


NEW = {"SYSTEM": _kt("AiStructure.kt", "SYSTEM"), "REVIEW_SYSTEM": _kt("AiStructure.kt", "REVIEW_SYSTEM"),
       "MM_SYSTEM": _kt("MindMap.kt", "SYSTEM"), "CARDS_SYSTEM": _kt("AiOrganize.kt", "CARDS_SYSTEM")}
OLD = {"SYSTEM": LP.SYSTEM, "REVIEW_SYSTEM": LP.REVIEW_SYSTEM, "MM_SYSTEM": LP.MM_SYSTEM, "CARDS_SYSTEM": LP.CARDS_SYSTEM}


# ---------------------------------------------------------------- الصفحات (OcrWord)

def med(v):
    v = sorted(v)
    return v[len(v) // 2] if v else 0


def pct(v, q):
    v = sorted(v)
    return v[min(len(v) - 1, int(len(v) * q))] if v else 0


class Pages:
    def __init__(self, rows):
        """rows: (page, line, l, t, r, b, text, bold)"""
        self.p = collections.defaultdict(collections.OrderedDict)
        for page, ln, l, t, r, b, txt, bold in rows:
            self.p[page].setdefault(ln, []).append((l, t, r, b, txt, bold))
        self._heads = {}

    def lines(self, page):
        return sorted(self.p.get(page, {}).items(), key=lambda kv: min(w[1] for w in kv[1]))


def line_h(ws):
    return max(med([w[3] - w[1] for w in ws]), 1e-4)


def line_text(ws, cells=True):
    ar = C.is_arabic(" ".join(w[4] for w in ws))
    ws = sorted(ws, key=lambda w: -w[2] if ar else w[0])
    h = line_h(ws)
    out = ws[0][4]
    for a, b in zip(ws, ws[1:]):
        gap = (a[0] - b[2]) if ar else (b[0] - a[2])
        out += (" | " if cells and gap > h * 1.6 else " ") + b[4]
    return out


def ncells(text):
    return text.count(" | ") + 1 if " | " in text else 0


def page_headings(P, page):
    if page in P._heads:
        return P._heads[page]
    pl = P.p.get(page, {})
    if not pl:
        P._heads[page] = []
        return []
    normal = med([line_h(ws) for ws in pl.values()])
    left = pct([min(w[0] for w in ws) for ws in pl.values()], 0.1)
    right = pct([max(w[2] for w in ws) for ws in pl.values()], 0.9)
    out = []
    for ws in pl.values():
        t = line_text(ws, cells=False).strip()
        if not (3 <= len(t) <= 90) or not re.search(r"[^\W\d_]", t) or re.search(r"(?i)page \d+|^\d+$", t):
            continue
        at_margin = (max(w[2] for w in ws) >= right - 0.06) if C.is_arabic(t) else (min(w[0] for w in ws) <= left + 0.03)
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
    P._heads[page] = sorted(out, key=lambda h: h[1])
    return P._heads[page]


def title_for(P, m, window):
    """window=3: القديم. وإلا: 3 صفحات دائمًا، وأبعد (حتى window) فقط عبر صفحات مقروءة (مثل AiStructure.titleFor)."""
    lo = m["page"]
    if window == 3:
        lo = max(0, m["page"] - 3)
    else:
        all_read = True
        while lo > 0 and m["page"] - lo < window:
            read = (lo - 1) in P.p
            if m["page"] - (lo - 1) > 3 and not (all_read and read):
                break
            all_read = all_read and read
            lo -= 1
    before = []
    for p in range(lo, m["page"] + 1):
        before += [h for h in page_headings(P, p) if p < m["page"] or h[1] <= m["top"] + 0.002]
    main = next((h for h in reversed(before) if h[3] == 1), None)
    sub = next((h for h in reversed(before) if h[3] == 2 and (main is None or (h[0], h[1]) > (main[0], main[1]))), None)
    if sub and m["page"] - sub[0] > 1:
        sub = None
    return (main[2] if main else None, sub[2] if sub else None)


def table_header(P, m, n):
    def block_of(page, before_top):
        ls = [ws for _, ws in P.lines(page)]
        if before_top is not None:
            ls = [ws for ws in ls if min(w[1] for w in ws) < before_top - 0.001]
        block = []
        for ws in reversed(ls):
            lt = line_text(ws)
            if ncells(lt) == n:
                block.insert(0, lt)
            elif block:
                break
        return block
    block = block_of(m["page"], m["top"]) or (block_of(m["page"] - 1, None) if m["page"] > 0 else [])
    if not block or m["text"].strip().startswith(block[0].strip()):
        return None
    cs = [c.strip() for c in block[0].split(" | ")]
    return cs if len(cs) == n and all(len(c) <= 22 for c in cs) and len(block[0]) <= 60 else None


# ---------------------------------------------------------------- نص التظليل (مثل TextLayout.buildText)

def mark_text(P, page, line_ids):
    """أسطر متتالية من صفحة → نص التظليل: فقرات بأسطرها (\\n)، فقرات منفصلة (\\n\\n)، صفوف جدول بـ " | "، عناوين «# »."""
    pl = P.p[page]
    body = med([line_h(ws) for ws in pl.values()])
    rows = [pl[ln] for ln in line_ids]
    blocks, prev = [], None
    for ws in rows:
        lt = line_text(ws)
        h = line_h(ws)
        top, bot = min(w[1] for w in ws), max(w[3] for w in ws)
        kind = "table" if ncells(lt) >= 2 else ("head" if h >= 1.25 * body else "para")
        # سطر بخلية واحدة بعد صف جدول قريب منه = تكملة خلية (مثل TextLayout.readTable)
        if kind == "para" and prev is not None and prev[0] == "table" and top - prev[1] <= 1.8 * max(h, prev[2]):
            row = blocks[-1][-1].split(" | ")
            edge = max(w[2] for w in ws) if C.is_arabic(lt) else min(w[0] for w in ws)
            k = min(range(len(prev[3])), key=lambda i: abs(prev[3][i] - edge))
            if k < len(row) and abs(prev[3][k] - edge) <= 2.2 * prev[2]:
                row[k] = (row[k] + " " + line_text(ws, cells=False)).strip()
                blocks[-1][-1] = " | ".join(row)
                prev = (prev[0], bot, prev[2], prev[3])
                continue
        anchors = cell_edges(ws) if kind == "table" else None
        new_par = True
        if prev is not None and kind == prev[0] and kind != "head":
            gap = top - prev[1]
            new_par = gap > 0.9 * max(h, prev[2]) or abs(h - prev[2]) > 0.25 * max(h, prev[2])
        text = lt if kind == "table" else line_text(ws, cells=False)
        if kind == "head":
            blocks.append(["# " + text])
        elif new_par:
            blocks.append([text])
        else:
            blocks[-1].append(text)
        prev = (kind, bot, h, anchors)
    return "\n\n".join("\n".join(b) for b in blocks)


def cell_edges(ws):
    """بداية كل خلية (اليسار، أو اليمين في العربية) لمطابقة أسطر التكملة."""
    ar = C.is_arabic(" ".join(w[4] for w in ws))
    ws = sorted(ws, key=lambda w: -w[2] if ar else w[0])
    h = line_h(ws)
    edges = [ws[0][2] if ar else ws[0][0]]
    for a, b in zip(ws, ws[1:]):
        gap = (a[0] - b[2]) if ar else (b[0] - a[2])
        if gap > h * 1.6:
            edges.append(b[2] if ar else b[0])
    return edges


def make_mark(P, page, line_ids, emph=()):
    top = min(min(w[1] for w in P.p[page][ln]) for ln in line_ids)
    return {"page": page, "top": top, "text": mark_text(P, page, line_ids), "emph": list(emph)}


# ---------------------------------------------------------------- مقارنات قديمة (VERSION 3)

COMMON = set("""drug drugs class route spectrum mechanism action uses use side effect effects adverse notes note example examples
generation dose doses indication indications contraindication feature features type group name comparison summary important
other infection infections inhibitor combined brand detail details resistance treatment organism given used only
with from that this table header""".split())
OLD_NEG = re.compile(r"(?i)\b(not|no|never|without|except|avoid|contraindicated)\b|(?<![^\W\d_])[وف]?(لا|ليس|ليست|غير|لن|لم|بدون|دون|يُمنع|ممنوع)(?![^\W\d_])")


def old_suspicious(out, allowed):
    num = re.compile(r"\d+(?:[.,]\d+)?")
    ok = set(num.findall(allowed))
    if any(n not in ok for n in num.findall(out)):
        return True
    w = re.compile(r"[A-Za-z][A-Za-z-]{4,}")
    words = [x.lower() for x in w.findall(out)]
    if not words:
        return False
    okw = {x.lower() for x in w.findall(allowed)} | COMMON
    return sum(1 for x in words if x not in okw) > max(3, len(words) // 4)


def old_dropped(out, notes):
    num = re.compile(r"\d+(?:[.,]\d+)?")
    on = set(num.findall(out))
    if any(n not in on for n in num.findall(notes)):
        return True
    w = re.compile(r"[A-Za-z][A-Za-z-]{4,}")
    ow = {x.lower() for x in w.findall(out)}
    terms = {x.lower() for x in w.findall(notes)} - COMMON
    if len(terms) >= 4 and sum(1 for x in terms if x not in ow) * 4 > len(terms):
        return True
    return len(OLD_NEG.findall(out)) < len(OLD_NEG.findall(notes))


# ---------------------------------------------------------------- تنظيف الناتج (AiOrganizer.clean)

LATEX = {"\\beta": "β", "\\alpha": "α", "\\gamma": "γ", "\\delta": "δ", "\\mu": "µ", "\\rightarrow": "→", "\\to": "→",
         "\\leftarrow": "←", "\\uparrow": "↑", "\\downarrow": "↓", "\\approx": "≈", "\\ge": "≥", "\\geq": "≥", "\\le": "≤",
         "\\leq": "≤", "\\times": "×", "\\pm": "±", "\\%": "%", "\\frac{1}{2}": "½"}


def latex(s):
    if "$" not in s and "\\" not in s:
        return s
    keys = sorted(LATEX, key=len, reverse=True)

    def rep(m):
        x = m.group(1)
        for k in keys:
            x = x.replace(k, LATEX[k])
        return re.sub(r"_\{([^}]*)\}", r"\1", x).replace("{", "").replace("}", "").strip()
    s = re.sub(r"\$([^$\n]{1,40})\$", rep, s)
    for k in keys:
        s = s.replace(k, LATEX[k])
    return s


def clean(raw):
    out = []
    for l0 in latex(raw).replace("```", "").splitlines():
        l = l0.rstrip().replace("**", "").replace("__", "")
        t = l.strip()
        if t and all(c in "|-: " for c in t):
            continue
        if t.startswith("|") and t.endswith("|") and len(t) > 2:
            l = " | ".join(c.strip() for c in t[1:-1].split("|"))
        if t.startswith("* ") or t.startswith("• "):
            l = "- " + t[2:]
        out.append(l)
    return re.sub(r"\n{3,}", "\n\n", "\n".join(out)).strip()


def norm(s):
    return re.sub(r"[^\w]+|_", " ", s.lower()).strip()


def emphasize(body, emph):
    for e in sorted(emph, key=len, reverse=True):
        i = body.lower().find(e.lower())
        if i < 0 or body[:i].count("*") % 4:
            continue
        body = body[:i] + "**" + body[i:i + len(e)] + "**" + body[i + len(e):]
    return body


# ---------------------------------------------------------------- الأجزاء (AiStructure.parts)

def old_mark_lines(text):
    return [l.strip()[2:].strip() if l.strip().startswith("# ") else l.strip() for l in text.replace("\r", "").split("\n") if l.strip() and l.strip() != "#"]


def parts(P, marks, legacy, budget):
    window = 3 if legacy else 8
    ml = old_mark_lines if legacy else C.mark_lines
    groups = []
    for m in marks:
        key = title_for(P, m, window)
        if groups and groups[-1][0] == key:
            groups[-1][1].append(m)
        else:
            groups.append((key, [m]))
    out = []

    def part(t, s, table, notes, ms, headed=True, how=""):
        out.append({"title": t, "sub": s, "table": table, "notes": notes, "marks": ms, "headed": headed, "how": how,
                    "emph": [e for m in ms for e in m["emph"]]})

    for (title, sub), ms in groups:
        runs = []
        for m in ms:
            ls = ml(m["text"])
            if not ls:
                continue
            k = ncells(ls[0]) if all(" | " in l for l in ls) else 0
            if runs and runs[-1][0] == k and (k >= 2 or k == 0):
                runs[-1][1].append(m)
            else:
                runs.append((k, [m]))
        for k, rm in runs:
            if k >= 2:
                head = table_header(P, rm[0], k)
                rows = ([" | ".join(head)] if head else []) + [l.replace("®", "") for m in rm for l in ml(m["text"])]
                part(title, sub, "\n".join(rows), "\n".join(rows), rm, head is not None, "page table")
                continue
            # القديم: كل تظليل قطعة واحدة بأسطره كما هي؛ الجديد: سطر سطر (الجمل موصولة)
            lines = [("\n".join(ml(m["text"])), m) for m in rm] if legacy else [(l, m) for m in rm for l in ml(m["text"])]
            kr = [] if legacy else C.keyed_runs([l for l, _ in lines])
            cur, cm = [], []

            def flush():
                if cur:
                    part(title, sub, None, "\n".join(cur), list(cm))
                cur.clear()
                cm.clear()
            i = 0
            while i < len(lines):
                r = next((r for r in kr if r[0] == i), None)
                if r:
                    flush()
                    tb = C.keyed_table([lines[j][0] for j in range(r[0], r[1])])
                    ms_ = []
                    for j in range(r[0], r[1]):
                        if lines[j][1] not in ms_:
                            ms_.append(lines[j][1])
                    part(title, sub, tb, tb, ms_, True, "keyed table")
                    i = r[1]
                    continue
                t, m = lines[i]
                if cur and sum(len(x) + 1 for x in cur) + len(t) > budget:
                    flush()
                cur.append(t)
                if m not in cm:
                    cm.append(m)
                i += 1
            flush()
    return out


def trivial(p):
    return p["table"] is None and len(p["notes"]) <= 200 and len([l for l in p["notes"].splitlines() if l.strip()]) <= 2


def strip_title(p, cleaned):
    heads = [norm(x) for x in (p["title"], p["sub"]) if x]
    ls = [l for l in cleaned.splitlines() if norm(l.lstrip("#- ")) not in heads]
    return "\n".join(("- " + l.lstrip("# ")) if l.lstrip().startswith("#") else l for l in ls).strip()


def old_fallback(notes):
    return "\n".join(l if " | " in l else "- " + l.strip() for l in notes.splitlines() if l.strip())


def new_fallback(notes):
    return C.tidy(old_fallback(notes))


def checked(p, raw, legacy, tidy_it=True):
    body = strip_title(p, clean(raw).replace("**", ""))
    if not legacy and tidy_it:
        body = C.tidy(body)
    if not body.strip():
        return None, "empty"
    allowed = p["notes"] + " " + (p["title"] or "") + " " + (p["sub"] or "")
    if legacy:
        if old_suspicious(body, allowed):
            return None, "suspicious"
        if old_dropped(body, p["notes"]):
            return None, "dropped"
        return body, ""
    bl = C.bad_lines(body, p["notes"])
    salvaged = ""
    if bl:
        # مثل AiStructure.checked: يُحذف سطر النقطة بلا أصل وحده ويُعاد الفحص كله
        if any(" | " in x for x in bl):
            return None, "ungrounded row: " + bl[0][:70]
        drop = {x.strip() for x in bl}
        body = re.sub(r"\n{3,}", "\n\n", "\n".join(l for l in body.splitlines() if l.strip() not in drop)).strip()
        if not body or C.bad_lines(body, p["notes"]):
            return None, "ungrounded: " + bl[0][:70]
        salvaged = " (removed: " + " / ".join(x[:60] for x in bl) + ")"
    if C.suspicious(body, allowed):
        return None, "suspicious"
    why = C.dropped(body, p["notes"])
    if why:
        return None, "dropped " + why + salvaged
    return body, salvaged


def prompt(p):
    return "SECTION: " + " › ".join(x for x in (p["title"], p["sub"]) if x) + "\nNOTES:\n" + p["notes"]


def process(p, gen, legacy, log, stats):
    PR = OLD if legacy else NEW
    fb = old_fallback if legacy else new_fallback
    tag = " › ".join(str(x) for x in (p["title"], p["sub"]))
    if p["table"] is not None:
        log.append(f"- [{tag}] {p['how']} by code")
        stats["tables"] += 1
        return emphasize(p["table"], p["emph"])
    if trivial(p):
        log.append(f"- [{tag}] small part by code")
        return emphasize(fb(p["notes"]), p["emph"])
    o, dt = gen(PR["SYSTEM"], prompt(p))
    stats["t_summary"] += dt
    stats["ai_parts"] += 1
    draft, why = checked(p, o, legacy)
    if draft is None:
        stats["fallbacks"] += 1
        log.append(f"- [{tag}] model {dt:.1f}s → fallback ({why})")
        return emphasize(fb(p["notes"]), p["emph"])
    log.append(f"- [{tag}] model {dt:.1f}s → accepted{why}")
    if why:
        stats["salvaged"] += 1
    if draft == fb(p["notes"]) or (not legacy and not C.needs_review(draft)):
        return emphasize(draft, p["emph"])
    o2, dt2 = gen(PR["REVIEW_SYSTEM"], prompt(p) + "\n\nDRAFT:\n" + draft)
    stats["t_summary"] += dt2
    stats["reviews"] += 1
    r = clean(o2).strip()
    if not r or re.match(r"(?i)^ok\b", r) or r == "حسنًا":
        log.append(f"  review {dt2:.1f}s → OK")
        return emphasize(draft, p["emph"])
    c, w2 = checked(p, r, legacy, tidy_it=False)
    if c is not None and C.well_formed(c):
        log.append(f"  review {dt2:.1f}s → CORRECTED")
        return emphasize(c, p["emph"])
    log.append(f"  review {dt2:.1f}s → correction rejected ({w2 or 'bad format'})")
    return emphasize(draft, p["emph"])


def assemble(done, legacy):
    sections = collections.OrderedDict()
    for t, s, b in done:
        sections.setdefault((t, s), []).append(b)
    seen_all, out, last = set(), [], "\0"
    for (title, sub), bodies in sections.items():
        seen = seen_all if legacy else set()
        sub_line = ("## " + sub) if sub else None
        if title != last:
            if out and out[-1].strip():
                out.append("")
            if title:
                out.append("# " + title)
            elif not legacy and sub and last not in ("\0", None):
                sub_line = "# " + sub
            last = title
        if sub_line:
            out.append(sub_line)
        for line in "\n\n".join(bodies).splitlines():
            k = norm(line.replace("**", ""))
            if line.strip() and " | " not in line and k and (legacy or len(k.split()) >= 4):
                if k in seen:
                    continue
                seen.add(k)
            if not line.strip() and (not out or not out[-1].strip()):
                continue
            out.append(line)
        out.append("")
    return "\n".join(out).strip()


def budget_of(marks):
    notes_all = "\n".join(m["text"] for m in marks)
    ar = sum(1 for c in notes_all if "\u0600" <= c <= "\u06FF") > len(notes_all) / 4
    return 1300 if ar else 2400


def summarize(P, marks, gen, legacy):
    budget = budget_of(marks)
    stats = collections.Counter()
    log = []
    ps = parts(P, marks, legacy, budget)
    done = [(p["title"], p["sub"], process(p, gen, legacy, log, stats)) for p in ps]
    return ps, done, assemble(done, legacy), log, stats


# ---------------------------------------------------------------- الخريطة الذهنية (MindMapAi)

EXPLAIN = re.compile(r"(?i)^(شرح|توضيح|ملاحظ|explanation|explain|lecture notes|notes?\b|figure|شكل)")
OLD_SCORE = re.compile(r"(?i)\b(DOC|drug of choice|first[- ]line|contraindicat|toxic|side effect|adverse|mechanism|MOA|not|never|avoid|only|most)\b|لا |ممنوع|سام|أعراض|آلية|الأفضل|فقط")
NEW_SCORE = re.compile(r"(?i)\b(defined? as|refers? to|is called|means|cause[sd]?|result|lead[s]? to|rule|except|must|always|never|not|avoid|only|most|main|key|first|types?|steps?|DOC|drug of choice|first[- ]line|contraindicat|toxic|side effect|adverse|mechanism)\b|لا |ليس|ممنوع|يجب|دائمًا|فقط|إلا|أهم|أول|أنواع|أسباب|نتيجة|يؤدي|تعريف|يُعرّف|آلية|الأفضل")
CUTTERS = [" because ", " since ", " which ", " meaning ", " i.e.", " e.g.", " — ", " – ", " لأن", " لان", " حيث ", " أي ", " مثل ", "; ", "؛ "]


def score(line, legacy):
    s = 0
    if "**" in line:
        s += 6
    if re.search(r"\d", line):
        s += 2
    if (OLD_SCORE if legacy else NEW_SCORE).search(line):
        s += 2
    if len(line) < 70:
        s += 1
    return s


def cut(t, mx):
    if len(t) <= mx:
        return t
    head = t[:mx - 1]
    sp = head.rfind(" ")
    ws = (head[:sp] if sp >= mx // 2 else head).split(" ")
    while len(ws) > 2 and ws[-1].lower() in TRAIL:
        ws = ws[:-1]
    return " ".join(ws).rstrip(" ,،:;—-(") + "…"


TRAIL = {"a", "an", "the", "of", "to", "and", "or", "is", "are", "was", "be", "in", "on", "at", "for", "with", "by",
         "from", "as", "that", "which", "it", "its", "they", "their", "this", "these", "those", "but", "so", "when", "if", "than",
         "also", "very", "then", "such", "في", "من", "على", "إلى", "الى", "عن", "و", "أو", "ثم", "أن", "التي", "الذي", "هو", "هي", "مع",
         "لكن", "بل", "له", "لها", "فإن"}
CONNECT = {"or", "and", "but", "so", "because", "which", "أو", "و", "ثم", "لكن", "بل", "لأن", "حيث"}


def fragment(p):
    ws = p.split()
    return bool(ws) and ws[0].lower().strip(",،") in CONNECT


def brief(t0, max_words=7):
    ws = t0.split()

    def real(w):
        return any(c.isalnum() for c in w)
    if sum(1 for w in ws if real(w)) <= max_words:
        return t0.strip()
    k = n = 0
    while k < len(ws) and n < max_words:
        if real(ws[k]):
            n += 1
        k += 1
    while k > 2 and (not real(ws[k - 1]) or ws[k - 1].lower().strip(",،:;—-(") in TRAIL):
        k -= 1
    return " ".join(ws[:k]).rstrip(",،:;—-(→= ") + "…"


def key_phrase(s, mx, legacy):
    t = re.sub(r"^[-•]\s*", "", s.replace("**", "")).strip()
    t = re.sub(r"\s*\([^)]{0,80}\)", "", t)
    t = re.sub(r"\s*\[[^\]]{0,80}\]", "", t)
    for c in CUTTERS:
        i = t.lower().find(c.lower())
        if 12 <= i < len(t):
            t = t[:i]
    d = re.search(r"[.!؟?](\s|$)", t)
    if d and d.start() >= 12:
        t = t[:d.start()]
    t = t.strip().rstrip(",،:.")
    if len(t) <= mx:
        return t
    return (t[:mx - 1].rstrip() + "…") if legacy else cut(t, mx)


def mm_sections(text, legacy):
    out, h1, h2, buf = [], None, None, []

    def flush():
        if any(x.strip() for x in buf) and not any(EXPLAIN.search(h or "") for h in (h1, h2)):
            out.append((h1, h2, "\n".join(buf).strip()))
        buf.clear()
    for raw in text.splitlines():
        l = raw.strip()
        if l.startswith("## "):
            flush()
            h2 = l[3:].strip()
        elif l.startswith("# "):
            flush()
            h1, h2 = l[2:].strip(), None
        elif l:
            buf.append(l)
        elif buf and not legacy:
            buf.append("")
    flush()
    return out


def old_code_points(body):
    ls = [l for l in body.splitlines() if l.strip()]
    ls = [": ".join(c.strip() for c in l.split(" | ")[:2]) if " | " in l else l for l in ls]
    top = sorted(sorted(enumerate(ls), key=lambda x: -score(x[1], True))[:4])
    return [q for q in (key_phrase(l, 50, True) for _, l in top) if len(q) >= 3]


def table_points(rows):
    out, i = [], 0
    while i < len(rows):
        if " | " not in rows[i]:
            i += 1
            continue
        n = len(rows[i].split(" | "))
        j = i
        while j < len(rows) and " | " in rows[j] and len(rows[j].split(" | ")) == n:
            j += 1
        for r in range(i + 1 if j - i >= 2 else i, j):
            c = [x.strip() for x in rows[r].replace("**", "").split(" | ")]
            first = next((x for x in c if x and x != "—"), None)
            if first is None:
                continue
            v = next((x for x in c[1:] if x and x not in ("—", "-")), None)
            head = brief(cut(first, 28), 4)
            label = head if v is None else brief(head + ": " + key_phrase(v, 80, False), 7)
            out.append((label, score(rows[r], False)))
        i = max(j, i + 1)
    top = sorted(sorted(enumerate(out), key=lambda x: -x[1][1])[:5])
    return [x[1][0] for x in top]


NEG_MM = re.compile(r"(?i)\b(not|no|never|without|except|cannot)\b|(?<![^\W\d_])[وف]?(لا|ليس|ليست|غير|لن|لم|بدون|دون)(?![^\W\d_])")


def phrase_ok(ph, line):
    n = len(ph.split())
    if not (2 <= n <= 7) or len(ph) > 60:
        return False
    nums = C.numbers(line)
    if any(x not in nums for x in C.NUM.findall(ph)):
        return False
    src = set(C.content(line))
    cw = C.content(ph)
    if not cw or sum(1 for w in cw if w not in src) > (1 if len(cw) >= 5 else 0):
        return False
    seq = C.content(line)
    pos = 0
    for w in cw:
        if w not in src:
            continue
        j = next((i for i in range(pos, len(seq)) if seq[i] == w), None)
        if j is None:
            return False
        pos = j + 1
    return not (NEG_MM.search(line) and not NEG_MM.search(ph))


def line_point(l):
    t = l[2:] if l.startswith("- ") else (l[2:] if l.startswith("• ") else l)
    return brief(key_phrase(t, 60, False), 7)


def new_code_points(lines, n=4):
    top = sorted(sorted(enumerate(lines), key=lambda x: -score(x[1], False))[:n])
    return [q for q in (line_point(l) for _, l in top) if len(q) >= 3]


def parse_points(raw, lines):
    rx = re.compile(r"^[-•*]?\s*\(?(\d{1,2})\)?\s*[:.)\-–]\s*(.+)$")
    used, out, fixed = set(), [], 0
    for l0 in clean(raw).splitlines():
        m = rx.match(l0.strip())
        if not m:
            continue
        k = int(m.group(1)) - 1
        if not (0 <= k < len(lines)) or k in used:
            continue
        used.add(k)
        ph = m.group(2).strip().rstrip(".،,")
        if phrase_ok(ph, lines[k]):
            out.append(ph)
        else:
            out.append(line_point(lines[k]))
            fixed += 1
        if len(out) == 5:
            break
    res, seen = [], set()
    for q in out:
        if len(q) >= 3 and q.lower() not in seen:
            seen.add(q.lower())
            res.append(q)
    return res, fixed


def mind_map(summary, gen, legacy, stats):
    tree = []
    for h1, h2, body in mm_sections(summary, legacy):
        if legacy:
            if len([l for l in body.splitlines() if l.strip()]) <= 2:
                pts, how = old_code_points(body), "code"
            else:
                o, dt = gen(OLD["MM_SYSTEM"], "SECTION: " + " › ".join(x for x in (h1, h2) if x) + "\n" + body)
                stats["t_mm"] += dt
                got = [key_phrase(l.strip()[2:], 60, True) for l in clean(o).splitlines() if l.strip().startswith(("- ", "• "))]
                allowed = body + " " + (h1 or "") + " " + (h2 or "")
                got = [q for q in got if 3 <= len(q) <= 60 and not old_suspicious(q, allowed)]
                pts = list(dict.fromkeys(got))[:5] or old_code_points(body)
                how = f"ai {dt:.0f}s"
        else:
            all_ = [l.strip() for l in body.splitlines()]
            text = [l for l in all_ if l and " | " not in l]
            tp = table_points(all_)
            if not text:
                tx, how = [], "code"
            elif len(text) <= 3:
                tx, how = [q for q in (line_point(l) for l in text) if len(q) >= 3], "code"
            else:
                numbered = "\n".join(f"{k + 1}. " + (l[2:] if l.startswith("- ") else l) for k, l in enumerate(text))
                o, dt = gen(NEW["MM_SYSTEM"], "SECTION: " + " › ".join(x for x in (h1, h2) if x) + "\nLINES:\n" + numbered)
                stats["t_mm"] += dt
                got, fixed = parse_points(o, text)
                stats["mm_fixed"] += fixed
                tx = got if len(got) >= 2 else new_code_points(text)
                how = f"ai {dt:.0f}s"
            seen, pts = set(), []
            for q in [x for x in tx if not fragment(x)][:5] + tp:
                if q.lower() not in seen:
                    seen.add(q.lower())
                    pts.append(q)
        tree.append((h1, h2, body, pts, how))
    return tree


# ---------------------------------------------------------------- البطاقات

def parse_cards(raw, legacy=False):
    out, q, a = [], None, []
    qre = re.compile(r"(?i)^(?:\d+[.)]\s*)?(?:Q|Question|س|سؤال)\s*[:：]\s*(.*)$")
    are = re.compile(r"(?i)^(?:A|Answer|ج|جواب|الإجابة)\s*[:：]\s*(.*)$")

    def flush():
        nonlocal q
        if q and q.strip() and "".join(a).strip():
            out.append((q.strip(), " ".join(a).strip()))
        q = None
        a.clear()
    for l in (raw if legacy else latex(raw)).replace("**", "").splitlines():
        l = l.strip()
        qm, am = qre.match(l), are.match(l)
        if qm:
            flush()
            q = qm.group(1)
        elif am and q is not None:
            a.append(am.group(1))
        elif l and q is not None and a:
            a.append(l)
    flush()
    return out


def keyed_cards(lines, topic):
    """مثل AiStructure.keyedCards."""
    src = "\n".join(lines) + " " + (topic or "")
    out = []
    for l0 in lines:
        l = l0.replace("**", "")
        if not l.strip().startswith("- "):
            continue
        kv = C.keyed(l)
        if not kv:
            continue
        q = ((topic + ": ") if topic else "") + kv[0].rstrip(": ") + ("؟" if C.is_arabic(l) else "?")
        a = kv[1].strip().rstrip(".")
        if C.card_ok(q, a, src):
            out.append((q, a))
    return out


def cards(ps, done, gen, legacy, stats, budget):
    out, buf, tables = [], [], []

    def flush(force):
        text = "\n\n".join(buf) if not legacy else "\n".join(buf)
        if not legacy and force and text.strip() and sum(1 for l in text.splitlines() if l.startswith("- ")) < 2:
            buf.clear()
            text = ""
        if text.strip() and (force or len(text) >= budget // 2):
            if legacy:
                o, dt = gen(OLD["CARDS_SYSTEM"], "Notes:\n\n" + text)
                made = parse_cards(o, legacy=True)
            else:
                o, dt = gen(NEW["CARDS_SYSTEM"], C.cards_prompt(text))
                raw = [(C.arabic_question(q, a), a) for q, a in parse_cards(o)]
                ok = [c for c in raw if C.card_ok(c[0], c[1], text)]
                stats["cards_rejected"] += len(raw) - len(ok)
                for q, a in raw:
                    why = C.card_why(q, a, text)
                    if why:
                        stats.setdefault("rejected_log", []).append(f"  card rejected ({why}): Q: {q[:80]} | A: {a[:80]}")
                if not raw:
                    stats.setdefault("rejected_log", []).append("  cards: model output not parsed: " + o[:200].replace("\n", " / "))
                made = C.dedupe_cards(ok)
            stats["t_cards"] += dt
            out.extend(made)
            buf.clear()
        if not legacy and (force or not buf):
            out.extend(tables)
            tables.clear()
    for i, (p, (_, _, body)) in enumerate(zip(ps, done)):
        if i > 0 and ps[i - 1]["title"] != p["title"]:
            flush(True)
        if legacy:
            buf.append(p["notes"])
        else:
            topic = p["sub"] or p["title"]
            if p["table"] is not None:
                tables.extend(C.table_cards(p["table"].splitlines(), p["headed"], topic))
            else:
                pts = []
                for l in [x for x in body.splitlines() if x.strip()]:
                    c = keyed_cards([l], topic)
                    if c:
                        tables.extend(c)
                    else:
                        pts.append(l)
                if pts:
                    buf.append((("## " + topic + "\n") if topic else "") + "\n".join(pts))
        flush(False)
    flush(True)
    if legacy:
        seen, res = set(), []
        for q, a in out:
            if q.strip().lower() not in seen:
                seen.add(q.strip().lower())
                res.append((q, a))
        return res
    return C.dedupe_cards(out)
