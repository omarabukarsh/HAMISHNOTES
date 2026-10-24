"""
تجربة الذكاء في MyNotes Pro على نموذج Gemma 4 E2B نفسه (ملف .litertlm الذي في التطبيق، CPU):
الملخص المنظَّم، والخريطة الذهنية (المهم فقط)، وبطاقات المراجعة — قبل التحديث (legacy) وبعده (new) في نفس التشغيل.

المستندات:
  - antibiotics.pdf (صيدلة، إنجليزي وعربي): تظليلات ثابتة (كالتجارب السابقة) + تظليلات عشوائية.
  - tools/ai-eval/docs/*.txt: تاريخ، أحياء (عربي)، حاسوب، اقتصاد (عربي + مصطلحات إنجليزية)، قانون (عربي)، فيزياء،
    تُرسم كصفحات (layout.py) وتُظلَّل عشوائيًا (بذرة ثابتة SEED لتكون المقارنة عادلة).
المقاييس مستقلة عن فحوص التطبيق: كلمات مخترعة، تغطية، أرقام، نفي، أسطر في غير قسمها، نقاط مقصوصة، بطاقات مكررة...
"""
import collections
import glob
import os
import random
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import checks as C  # noqa: E402
import layout  # noqa: E402
import pipeline as PL  # noqa: E402

MODEL = sys.argv[1] if len(sys.argv) > 1 else "model.litertlm"
STUB = MODEL == "--stub"
SEED = int(os.environ.get("EVAL_SEED", "2026"))
TSV = "app/src/sharedTest/assets/antibiotics_words.tsv"
PDF = "app/src/sharedTest/assets/antibiotics.pdf"

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
        # نموذج وهمي للتجربة المحلية: يعيد الملاحظات نفسها نقاطًا، وبطاقة واحدة
        if "flashcards" in system:
            return "Q: What is stated first?\nA: " + " ".join(prompt.split()[2:6]), 0.0
        if "numbered LINES" in system:
            return "\n".join(f"- {k + 1}: " + " ".join(l.split()[1:5]) for k, l in enumerate(prompt.split("LINES:\n")[-1].splitlines()[:4])), 0.0
        if "DRAFT" in prompt:
            return "OK", 0.0
        return "\n".join("- " + l for l in prompt.split("NOTES:\n")[-1].splitlines()), 0.0
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


# ---------------------------------------------------------------- antibiotics.pdf

def arabic_fix(w):
    """مثل ArabicFix.word في التطبيق: «ال» المستقلة ← «لا»، و«األ» ← «الأ»."""
    core = w.strip("()[]«».,،:؛;!؟?\"'")
    fixed = {"ال": "لا", "وال": "ولا", "فال": "فلا"}.get(core)
    if fixed:
        w = w.replace(core, fixed)
    if re.search(r"[؀-ۿ]", w):
        for a, b in (("األ", "الأ"), ("اإل", "الإ"), ("اآل", "الآ")):
            w = w.replace(a, b)
    return w


def antibiotics_pages():
    rows = [l.rstrip("\n").split("\t") for l in open(TSV, encoding="utf-8") if not l.startswith("#")]
    bold_of = {}
    try:
        import pdfplumber

        with pdfplumber.open(PDF) as pdf:
            for pno in sorted({int(r[0]) for r in rows}):
                pg = pdf.pages[pno]
                W = float(pg.width)
                for w in pg.extract_words(extra_attrs=["fontname"]):
                    bold_of.setdefault(pno, []).append(
                        (w["x0"] / W, w["top"] / W, bool(re.search(r"(?i)bold|black|heavy|semibold|demi", w["fontname"]))))
    except Exception as e:  # noqa
        print("pdfplumber unavailable, no bold info:", e)

    def is_bold(page, l, t):
        best = None
        for x, y, b in bold_of.get(page, []):
            d = abs(x - l) + abs(y - t)
            if d < 0.03 and (best is None or d < best[0]):
                best = (d, b)
        return bool(best and best[1])
    out = []
    for p, ln, l, t, r, b, txt in rows:
        p, l, t, r, b = int(p), float(l), float(t), float(r), float(b)
        out.append((p, int(ln), l, t, r, b, arabic_fix(txt), is_bold(p, l, t)))
    return PL.Pages(out)


# تظليلات طالب ثابتة (صفحة، أسطر أو None، مدى رأسي أو None، أجزاء مظللة مرتين) — نفس تجارب v1–v3
FIXED = [
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


def fixed_marks(P):
    ms = []
    for page, lines, trange, emph in FIXED:
        if lines is None:
            lines = [ln for ln, ws in P.p[page].items() if trange[0] <= min(w[1] for w in ws) <= trange[1]]
        ms.append(PL.make_mark(P, page, lines, emph))
    return ms


def random_emph(rng, text):
    # مثل التطبيق: نص المظلل مرتين بلا فواصل الخلايا
    ws = [w for w in text.replace("\n", " ").replace("# ", " ").split() if w != "|"]
    if len(ws) < 6 or rng.random() > 0.15:
        return []
    k = rng.randint(2, 4)
    i = rng.randrange(0, len(ws) - k)
    return [" ".join(ws[i:i + k])]


def random_marks_pages(P, pages, rng):
    """antibiotics: مقاطع عشوائية من 1–3 أسطر متتالية (قد تبدأ أو تنتهي وسط جملة)، دون العناوين وتذييل الصفحة."""
    ms = []
    for page in pages:
        heads = {round(h[1], 4) for h in PL.page_headings(P, page) if h[3] == 1}
        ls = [ln for ln, ws in P.lines(page) if round(min(w[1] for w in ws), 4) not in heads
              and not re.search(r"(?i)exam summary — page", PL.line_text(ws, False))]
        i = 0
        while i < len(ls):
            if rng.random() < 0.3:
                n = rng.randint(1, 3)
                span = ls[i:i + n]
                m = PL.make_mark(P, page, span)
                m["emph"] = random_emph(rng, m["text"])
                ms.append(m)
                i += n + rng.randint(1, 3)
            else:
                i += 1
    return ms


def random_marks_doc(doc, P, rng):
    """مستند تجربة: داخل كل كتلة (قائمة/فقرات) تظليلات من 1–3 أسطر متتالية (قد تبدأ أو تنتهي وسط جملة)،
    أو الكتلة كلها أحيانًا؛ صفوف متتالية من جدول؛ العناوين لا تُظلَّل. التظليل في صفحة واحدة (مثل التطبيق)."""
    ms = []

    def add(span):
        page = span[0][0]
        lines = [ln for p, ln in span if p == page]
        m = PL.make_mark(P, page, lines)
        m["emph"] = random_emph(rng, m["text"])
        ms.append(m)
    for kind, ids in doc.blocks:
        if kind == "heading":
            continue
        if kind == "table":
            data = ids[1:] or ids
            if rng.random() < 0.8:
                a = rng.randrange(len(data))
                add(data[a:a + rng.randint(1, len(data) - a)])
            continue
        if rng.random() < 0.2:
            add(list(ids))
            continue
        i = 0
        while i < len(ids):
            if rng.random() < 0.6:
                n = rng.randint(1, 3)
                add(ids[i:i + n])
                i += n + rng.randint(0, 1)
            else:
                i += 1
    return ms


# ---------------------------------------------------------------- المقاييس (مستقلة عن فحوص التطبيق)

def body_lines(summary):
    return [l.strip() for l in summary.splitlines() if l.strip() and not l.strip().startswith("#")]


def placed(summary):
    """[(h1, h2, line)] لكل سطر محتوى في الملخص."""
    out, h1, h2 = [], None, None
    for raw in summary.splitlines():
        l = raw.strip()
        if l.startswith("## "):
            h2 = l[3:]
        elif l.startswith("# "):
            h1, h2 = l[2:], None
        elif l:
            out.append((h1, h2, l))
    return out


def summary_metrics(P, marks, summary, all_heads):
    notes = "\n".join(m["text"] for m in marks)
    # المسموح: التظليل، وعناوين الصفحات، ورؤوس الجداول المأخوذة من الصفحة
    heads_tab = " ".join(ls[0] for t, ls in C.blocks(body_lines(summary)) if t and len(ls) >= 2)
    src = set(C.content(notes + " " + all_heads + " " + heads_tab))
    body = "\n".join(body_lines(summary)).replace("**", "")
    ow = C.content(body)
    invented = sorted({w for w in ow if w not in src})
    nw = set(C.content(notes))
    cover = sum(1 for w in nw if w in set(ow)) / max(1, len(nw))
    nn = C.NUM.findall(notes)
    on = set(C.NUM.findall(body))
    nums = sum(1 for n in nn if n in on) / max(1, len(nn))
    neg = (C.neg_count(body), C.neg_count(notes))
    frags = sum(1 for l in body_lines(summary) if re.match(r"^- [a-z]", l) and not re.match(r"^- [a-z][\w-]*\s*[:=]", l))
    tables = sum(1 for t, ls in C.blocks(body_lines(summary)) if t and len(ls) >= 2)
    # سطر في غير قسمه: أقرب تظليل إليه (بالكلمات) عنوانه الحقيقي مختلف عن مكانه في الملخص
    truth = [(PL.title_for(P, m, 60), set(C.content(m["text"]))) for m in marks]
    mis = 0
    for h1, h2, l in placed(summary):
        cw = set(C.content(l))
        if len(cw) < 2:
            continue
        best = max(truth, key=lambda t: len(cw & t[1]))
        if len(cw & best[1]) < 2:
            continue
        if {x for x in best[0] if x} != {x for x in (h1, h2) if x}:
            mis += 1
    return {"lines": len(body_lines(summary)), "invented": len(invented), "invented_words": invented[:12],
            "coverage": cover, "numbers": nums, "neg": neg, "fragments": frags, "tables": tables, "misplaced": mis}


def mm_metrics(tree):
    pts = [(q, body) for _, _, body, ps, _ in tree for q in ps]
    if not pts:
        return {"points": 0}
    words = [len(q.split()) for q, _ in pts]
    long_ = sum(1 for w in words if w > 7)
    trunc = 0
    ungrounded = 0
    for q, body in pts:
        toks = set(re.findall(r"[^\W_]+", body.lower()))
        if q.endswith("…"):
            last = re.findall(r"[^\W_]+", q.lower())
            if last and last[-1] not in toks:
                trunc += 1
        cw = set(C.content(q))
        if cw and not any(cw <= set(C.content(l)) for l in body.splitlines() if l.strip()):
            ungrounded += 1
    dup = len(pts) - len({q.lower() for q, _ in pts})
    return {"points": len(pts), "avg_words": sum(words) / len(words), "long": long_, "cut_mid_word": trunc,
            "not_from_one_line": ungrounded, "dup": dup}


def card_metrics(cards, notes):
    src = set(C.content(notes))
    nums = C.numbers(notes)
    if not cards:
        return {"cards": 0}
    ung = sum(1 for _, a in cards if not C.grounded(a, src, nums))
    qn = sum(1 for q, _ in cards if any(n not in nums for n in C.NUM.findall(q)))
    yn = sum(1 for q, _ in cards if C.YESNO.match(q.strip()))
    inq = sum(1 for q, a in cards if set(C.content(a)) and set(C.content(a)) <= set(C.content(q)))
    dups = sum(1 for i in range(len(cards)) for j in range(i) if C.same_card(*cards[i], *cards[j]))
    aw = [len(a.split()) for _, a in cards]
    return {"cards": len(cards), "answer_not_in_notes": ung, "new_number_in_q": qn, "yes_no": yn, "answer_in_q": inq,
            "near_dup_pairs": dups, "avg_answer_words": sum(aw) / len(aw), "long_answers": sum(1 for x in aw if x > 25)}


# ---------------------------------------------------------------- التشغيل

def docs():
    P = antibiotics_pages()
    yield "antibiotics (fixed highlights)", P, fixed_marks(P)
    rng = random.Random(SEED)
    P2 = antibiotics_pages()
    yield "antibiotics (random highlights)", P2, random_marks_pages(P2, [3, 4, 5, 9, 10, 29], rng)
    # كل مستند بمجموعتين مختلفتين من التظليلات العشوائية (A وB)
    for path in sorted(glob.glob(os.path.join(os.path.dirname(os.path.abspath(__file__)), "docs", "*.txt"))):
        for k, tag in enumerate("AB"):
            d = layout.Doc(path)
            P3 = PL.Pages(d.rows)
            rng = random.Random(SEED + 7919 * k + sum(map(ord, os.path.basename(path))))
            yield os.path.basename(path)[:-4] + f" (random {tag})", P3, random_marks_doc(d, P3, rng)


def run():
    report, metrics = [], []
    for name, P, marks in docs():
        marks = sorted(marks, key=lambda m: (m["page"], m["top"]))
        heads = " ".join(h[2] for p in P.p for h in PL.page_headings(P, p))
        notes = "\n".join(m["text"] for m in marks)
        row = {"doc": name, "marks": len(marks)}
        for legacy in (True, False):
            tag = "before" if legacy else "after"
            ps, done, summary, log, stats = PL.summarize(P, marks, gen, legacy)
            tree = PL.mind_map(summary, gen, legacy, stats)
            cs = PL.cards(ps, done, gen, legacy, stats, PL.budget_of(marks))
            # مصدر البطاقات المسموح: التظليل + عناوين الصفحات + رؤوس الجداول من الصفحة
            heads_tab = " ".join(ls[0] for t, ls in C.blocks(body_lines(summary)) if t and len(ls) >= 2)
            row[tag] = {"summary": summary_metrics(P, marks, summary, heads), "mm": mm_metrics(tree),
                        "cards": card_metrics(cs, notes + " " + heads + " " + heads_tab),
                        "t_summary": stats["t_summary"], "t_mm": stats["t_mm"], "t_cards": stats["t_cards"],
                        "ai_parts": stats["ai_parts"], "fallbacks": stats["fallbacks"], "reviews": stats["reviews"],
                        "code_tables": stats["tables"], "mm_fixed": stats["mm_fixed"], "cards_rejected": stats["cards_rejected"]}
            report.append((name, tag, marks, summary, log, tree, cs))
            print(f"[{name}] {tag}: summary {stats['t_summary']:.0f}s, mind map {stats['t_mm']:.0f}s, cards {stats['t_cards']:.0f}s", flush=True)
        metrics.append(row)
    write(report, metrics)


# ---------------------------------------------------------------- الكتابة

def fmt_metrics(metrics):
    out = [f"# AI eval v4 — Gemma 4 E2B (CPU), before = pro@8afc919 (VERSION 3), after = VERSION 4, seed {SEED}", ""]
    tot = collections.defaultdict(float)
    keys = [
        ("summary time s", lambda r: r["t_summary"]), ("mind map time s", lambda r: r["t_mm"]), ("cards time s", lambda r: r["t_cards"]),
        ("AI parts", lambda r: r["ai_parts"]), ("fallbacks", lambda r: r["fallbacks"]), ("self-reviews run", lambda r: r["reviews"]),
        ("tables in summary", lambda r: r["summary"]["tables"]),
        ("invented words", lambda r: r["summary"]["invented"]), ("coverage %", lambda r: 100 * r["summary"]["coverage"]),
        ("numbers kept %", lambda r: 100 * r["summary"]["numbers"]),
        ("negations lost", lambda r: max(0, r["summary"]["neg"][1] - r["summary"]["neg"][0])),
        ("broken-sentence bullets", lambda r: r["summary"]["fragments"]), ("lines in wrong section", lambda r: r["summary"]["misplaced"]),
        ("mm points", lambda r: r["mm"].get("points", 0)), ("mm avg words", lambda r: r["mm"].get("avg_words", 0)),
        ("mm >7 words", lambda r: r["mm"].get("long", 0)), ("mm cut mid-word", lambda r: r["mm"].get("cut_mid_word", 0)),
        ("mm not from one line", lambda r: r["mm"].get("not_from_one_line", 0)),
        ("cards", lambda r: r["cards"].get("cards", 0)), ("cards answer not in notes", lambda r: r["cards"].get("answer_not_in_notes", 0)),
        ("cards yes/no", lambda r: r["cards"].get("yes_no", 0)), ("cards answer in question", lambda r: r["cards"].get("answer_in_q", 0)),
        ("cards near-duplicate pairs", lambda r: r["cards"].get("near_dup_pairs", 0)),
        ("cards avg answer words", lambda r: r["cards"].get("avg_answer_words", 0)),
    ]
    for row in metrics:
        out.append(f"## {row['doc']} — {row['marks']} highlights\n")
        out.append("metric | before | after")
        for k, f in keys:
            b, a = f(row["before"]), f(row["after"])
            if not k.startswith(("coverage", "numbers", "mm avg", "cards avg")):
                tot[k + "|b"] += b
                tot[k + "|a"] += a
            out.append(f"{k} | {b:.0f} | {a:.0f}" if isinstance(b, int) or float(b).is_integer() and float(a).is_integer() else f"{k} | {b:.1f} | {a:.1f}")
        iw = row["before"]["summary"]["invented_words"], row["after"]["summary"]["invented_words"]
        out.append(f"\ninvented words — before: {', '.join(iw[0]) or '—'} · after: {', '.join(iw[1]) or '—'}\n")
    out.append("## TOTAL (all documents)\n")
    out.append("metric | before | after")
    for k, _ in keys:
        if k + "|b" in tot:
            out.append(f"{k} | {tot[k + '|b']:.0f} | {tot[k + '|a']:.0f}")
    return "\n".join(out) + "\n"


def fmt_doc(name, tag, summary, log, tree, cs, with_log=True):
    out = [f"## {name} — {tag}", "", "```", summary, "```"]
    if with_log:
        out += ["", "Log:", *log]
    out += ["", "Mind map:", "```"]
    last = object()
    for h1, h2, _, pts, how in tree:
        if h1 != last:
            out.append("● " + (h1 or ""))
            last = h1
        if h2:
            out.append("  ├─ " + h2)
        out += [f"      · {q}   [{how}]" for q in pts]
    out += ["```", "", "Cards:"]
    out += [f"- Q: {q}\n  A: {a}" for q, a in cs]
    return "\n".join(out) + "\n"


def write(report, metrics):
    m = fmt_metrics(metrics)
    full = [m, "\n# Outputs\n"] + [fmt_doc(n, t, s, l, tr, cs) for n, t, _, s, l, tr, cs in report]
    open("results.md", "w", encoding="utf-8").write("\n".join(full))
    open("notice_metrics.md", "w", encoding="utf-8").write(m)
    after = [r for r in report if r[1] == "after"]
    open("notice_summary.md", "w", encoding="utf-8").write(
        "\n".join(f"## {n}\n```\n{s}\n```\n" + "\n".join(l for l in lg if "fallback" in l or "review" in l) + "\n" for n, _, _, s, lg, _, _ in after))
    open("notice_mm_cards.md", "w", encoding="utf-8").write(
        "\n".join(fmt_doc(n, "after", "", [], tr, cs, with_log=False).split("```\n\n```\n", 1)[-1] for n, _, _, _, _, tr, cs in after))
    print(open("results.md", encoding="utf-8").read())


if __name__ == "__main__":
    run()
