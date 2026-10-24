"""
فحوص الملخص والخريطة والبطاقات، مطابقة لـ AiStructure.kt (VERSION 4) سطرًا بسطر.
عامة لأي مادة ولأي لغة (عربي/إنجليزي): لا قوائم كلمات خاصة بمادة.
"""
import re

AR = re.compile(r"[؀-ۿ]")
TASHKEEL = re.compile(r"[ً-ْٰـ]")
TOKEN = re.compile(r"[^\W_]+")
NUM = re.compile(r"\d+(?:[.,]\d+)?")
NEGATION = re.compile(r"(?i)\b(not|no|never|without|except|avoid|cannot|contraindicated)\b|(?<![^\W\d_])[وف]?(لا|ليس|ليست|غير|لن|لم|بدون|دون|يُمنع|ممنوع)(?![^\W\d_])")

AR_PREFIX = ["وبال", "وال", "بال", "فال", "كال", "لل", "ال", "و", "ف", "ب", "ل"]

EN_STOP = """the and for with from that this these those its they them their which who whom whose what when where why how
but also than then there here into onto over under between about after before during all any each both either
more most some such very can may might must should will would does did has have had are was were been being
use used using uses given give include includes including e.g i.e etc example examples note notes table header
item items detail details section part point points summary like via per whereas while because since are is
define name list state describe mention explain compare type types main kind kinds called known""".split()
AR_STOP = """في من على الى إلى عن مع هو هي هم هن هذا هذه ذلك تلك التي الذي الذين اللذان ثم او أو ام أم كما كان كانت يكون تكون
عند بين بعد قبل حتى اذا إذا ان أن إن لكن بل قد كل بعض اي أي ايضا أيضًا أيضا مثل حيث لذلك عندما ما ماذا لماذا كيف متى اين أين
هل هناك هنا ذات ذو وهو وهي له لها لهم به بها فيه فيها منه منها عليه عليها اذكر عرف عرّف عدد اشرح قارن الفرق""".split()


def norm_word(w):
    w = TASHKEEL.sub("", w.lower())
    for a, b in (("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ٱ", "ا"), ("ة", "ه"), ("ى", "ي")):
        w = w.replace(a, b)
    if AR.search(w):
        for _ in range(3):
            p = next((p for p in AR_PREFIX if w.startswith(p) and len(w) - len(p) >= 3), None)
            if p is None:
                break
            w = w[len(p):]
    if re.fullmatch(r"[a-z]+", w):
        if len(w) > 5 and w.endswith("ies"):
            w = w[:-3] + "y"
        elif len(w) > 6 and w.endswith("ing"):
            w = w[:-3]
        elif len(w) > 5 and w.endswith("ed"):
            w = w[:-2]
        elif len(w) > 5 and w.endswith("ly"):
            w = w[:-2]
        elif len(w) > 3 and w.endswith("s") and not w.endswith("ss"):
            w = w[:-1]
    return w


STOP = {norm_word(w) for w in EN_STOP + AR_STOP}


def words(s):
    """الكلمات بعد التطبيع (3 أحرف فأكثر، دون الأرقام الخالصة)."""
    s = TASHKEEL.sub("", s)
    return [norm_word(t) for t in TOKEN.findall(s) if len(t) >= 3 and not t.isdigit()]


def content(s):
    return [w for w in words(s) if w not in STOP]


def numbers(s):
    return set(NUM.findall(s))


def neg_count(s):
    return len(NEGATION.findall(s))


def grounded(line, allowed, nums):
    """كل رقم في السطر من المصدر، والكلمات المهمة الجديدة قليلة جدًا (لا شيء للسطر القصير)."""
    if any(n not in nums for n in NUM.findall(line)):
        return False
    cw = content(line)
    unk = sum(1 for w in cw if w not in allowed)
    limit = 0 if len(cw) <= 2 else max(1, len(cw) // 4)
    return unk <= limit


def pairs_of(seq, window=3):
    out = set()
    for i, a in enumerate(seq):
        for j in range(i + 1, min(len(seq), i + 1 + window)):
            out.add((a, seq[j]))
    return out


def faithful(line, notes_pairs, known):
    """الصياغة أمينة: نصف أزواج الكلمات المتتالية على الأقل موجودة بنفس الترتيب في الملاحظات."""
    cw = [w for w in content(line) if w in known]
    if len(cw) < 4:
        return True
    ps = list(zip(cw, cw[1:]))
    ok = sum(1 for p in ps if p in notes_pairs)
    return ok * 2 >= len(ps)


def is_row(l):
    return " | " in l


def blocks(lines):
    """[(is_table, [lines])]"""
    out = []
    for l in lines:
        t = is_row(l)
        if out and out[-1][0] == t and t:
            out[-1][1].append(l)
        else:
            out.append((t, [l]))
    return out


def sentences_of(notes):
    return [c for c in (content(x) for x in re.split(r"(?<=[.!?؟;؛])\s+|\n", notes)) if c]


def moved_label(line, sents):
    """«وسم: نص» صنع النموذج وسمه من جملة أخرى (مثل «Pen G: Food delays…»)."""
    kv = keyed(line)
    if not kv:
        return False
    k, r = content(kv[0]), content(kv[1])
    if not k or len(r) < 2:
        return False
    st = next((x for x in sents if any(a == r[0] and b == r[1] for a, b in zip(x, x[1:]))), None)
    if st is None:
        return False
    return any(w not in st for w in k)


def bad_lines(out, notes, extra=""):
    """أسطر الناتج التي لا أصل لها في الملاحظات (رأس الجدول مستثنى: كلمات قصيرة)."""
    sents = sentences_of(notes)
    allowed = set(content(notes))
    nums = numbers(notes)
    np_ = pairs_of(content(notes))
    bad = []
    for is_t, ls in blocks([l.strip() for l in out.splitlines() if l.strip()]):
        for k, l in enumerate(ls):
            if is_t and k == 0 and len(ls) >= 2:
                if any(len(c.split()) > 4 for c in l.split(" | ")) or any(n not in nums for n in NUM.findall(l)):
                    bad.append(l)
                continue
            if not grounded(l.replace("**", ""), allowed, nums):
                bad.append(l)
            elif not is_t and (not faithful(l.replace("**", ""), np_, allowed) or moved_label(l.replace("**", ""), sents)):
                bad.append(l)
    return bad


def missing_ratio(out, notes):
    nw = set(content(notes))
    if not nw:
        return 0.0
    ow = set(content(out))
    return sum(1 for w in nw if w not in ow) / len(nw)


def dropped(out, notes):
    """رقم مفقود، أو أكثر من خُمس الكلمات المهمة، أو نفي مفقود."""
    on = numbers(out)
    if any(n not in on for n in NUM.findall(notes)):
        return "number"
    nw = set(content(notes))
    if len(nw) >= 6 and missing_ratio(out, notes) > 0.2:
        return "words"
    if neg_count(out) < neg_count(notes):
        return "negation"
    return ""


def suspicious(out, allowed_text):
    """كلمات أو أرقام جديدة في الناتج كله (أي لغة)."""
    ok_n = numbers(allowed_text)
    if any(n not in ok_n for n in NUM.findall(out)):
        return True
    cw = content(out)
    if not cw:
        return False
    ok = set(content(allowed_text))
    return sum(1 for w in cw if w not in ok) > max(3, len(cw) // 4)


# ---------------------------------------------------------------- تنظيم الناتج بالكود

def split_long(line, limit=140):
    """نقطة طويلة فيها أكثر من جملة تُقسم عند نهاية كل جملة (كل جزء 20 حرفًا فأكثر)."""
    t = line.strip()
    if not t.startswith("- ") or len(t) <= limit:
        return [line]
    pieces, cur = [], ""
    for tok in re.split(r"(?<=[.!?؟])\s+", t[2:]):
        cur = (cur + " " + tok) if cur else tok
        if cur[-1:] in ".!?؟" and ended(cur):
            pieces.append(cur)
            cur = ""
    if cur:
        pieces.append(cur)
    if len(pieces) < 2 or any(len(p) < 20 for p in pieces):
        return [line]
    return ["- " + p.strip() for p in pieces]


def tidy(body):
    """صف جدول منفرد ← نقطة؛ «|» داخل نقطة ← «؛»؛ سطر بلا «- » ← نقطة؛ النقاط الطويلة تُقسم."""
    ls = [l.rstrip() for l in body.splitlines()]
    out = []
    i = 0
    while i < len(ls):
        l = ls[i].strip()
        if is_row(l) and not l.startswith("- "):
            j = i
            while j < len(ls) and is_row(ls[j].strip()) and not ls[j].strip().startswith("- "):
                j += 1
            run = [x.strip().strip("|").strip() for x in ls[i:j]]
            if len(run) == 1:
                out.append("- " + "; ".join(c.strip() for c in run[0].split("|") if c.strip()))
            else:
                out.extend(run)
            i = j
            continue
        if not l:
            out.append("")
        elif l.startswith("- "):
            x = l if "|" not in l else "- " + "; ".join(c.strip() for c in l[2:].split("|") if c.strip())
            out.extend(split_long(x))
        elif l.startswith("**"):
            out.append(l)
        else:
            out.extend(split_long("- " + l.lstrip("•*·▪ ").strip()))
        i += 1
    return re.sub(r"\n{3,}", "\n\n", "\n".join(out)).strip()


def well_formed(s):
    run, n = 0, -1
    for l in [x.strip() for x in s.splitlines()] + [""]:
        if not l:
            if run == 1:
                return False
            run, n = 0, -1
            continue
        if "|" in l:
            if l.startswith(("- ", "|")) or l.endswith("|"):
                return False
            c = [x.strip() for x in l.split("|")]
            if len(c) < 2 or any(not x for x in c) or (run and len(c) != n):
                return False
            n, run = len(c), run + 1
        else:
            if run == 1:
                return False
            run, n = 0, -1
            if not l.startswith(("- ", "**")):
                return False
    return True


def needs_review(draft):
    """المراجعة الذاتية فقط إن بقي في المسودة ما يحتاجها: تنسيق غير سليم أو نقطة طويلة جدًا."""
    if not well_formed(draft):
        return True
    return any(not is_row(l) and len(l.strip()) > 160 for l in draft.splitlines())


# ---------------------------------------------------------------- جدول «اسم: تفصيل» بالكود

KEYED = re.compile(r"^(.{2,40}?)(?:\s*(?<!\d)[:：](?!\d)\s*| [—–=] )(.+)$")
LABELS = {norm_word(w) for w in "note notes example examples e.g ملاحظة ملاحظات مثال أمثلة tip".split()}


def keyed(l):
    t = l.strip()
    for p in ("- ", "• "):
        if t.startswith(p):
            t = t[len(p):]
    if " | " in t:
        return None
    m = KEYED.match(t)
    if not m:
        return None
    k, v = m.group(1).strip().strip("*").strip(), m.group(2).strip()
    if len(k.split()) > 5 or len(k) > 40 or len(v) < 2 or len(v) > 200:
        return None
    if not re.search(r"[^\W\d_]", k) or k.endswith(".") or " ".join(norm_word(x) for x in TOKEN.findall(k)) in LABELS:
        return None
    return k, v


def keyed_runs(lines):
    """مدى الأسطر المتتالية (3 فأكثر) بشكل «اسم: تفصيل» بأسماء مختلفة: [(from, to)]."""
    out, i = [], 0
    while i < len(lines):
        j, keys = i, set()
        while j < len(lines):
            kv = keyed(lines[j])
            if not kv:
                break
            nk = " ".join(words(kv[0])) or kv[0].lower()
            if nk in keys:
                break
            keys.add(nk)
            j += 1
        if j - i >= 3:
            out.append((i, j))
            i = j
        else:
            i += 1
    return out


def is_arabic(s):
    return sum(1 for c in s if "؀" <= c <= "ۿ") > sum(1 for c in s if c.isalpha()) / 3


def keyed_table(lines):
    ar = is_arabic(" ".join(lines))
    head = "البند | التفصيل" if ar else "Item | Details"
    return "\n".join([head] + [" | ".join(keyed(l)) for l in lines])


# ---------------------------------------------------------------- أسطر التظليل (مثل AiStructure.markLines)

BULLET = re.compile(r"^([-•▪◦·*–]|\d{1,2}[.)]|[٠-٩]{1,2}[.)]|[a-h]\))\s")
TERMINAL = ".!?؟:;؛"


def mark_lines(text):
    """أسطر الجملة الواحدة تُوصل حتى نهايتها (حتى عبر فراغ بين الأسطر)؛ العناوين وصفوف الجداول والنقاط تبقى أسطرًا مستقلة."""
    out = []
    prev_open = False
    for block in re.split(r"\n\s*\n", text.replace("\r", "")):
        for raw in block.split("\n"):
            l = raw.strip()
            if l.startswith("# "):
                l = l[2:].strip()
                out.append(l)
                prev_open = False
                continue
            if not l:
                continue
            if " | " in l:
                out.append(l)
                prev_open = False
                continue
            if prev_open and out and not BULLET.match(l):
                out[-1] = (out[-1][:-1] + l) if out[-1].endswith("-") and l[:1].islower() else out[-1] + " " + l
            else:
                out.append(l)
            prev_open = not ended(out[-1])
    return out


ABBR = re.compile(r"(?i)(\be\.g\.|\bi\.e\.|\betc\.|\bvs\.|\bno\.|\bdr\.|\bfig\.)$")


def ended(l):
    """نهاية جملة: علامة نهاية، دون اختصار مثل e.g. ودون قوس مفتوح."""
    return l[-1:] in TERMINAL and not ABBR.search(l) and l.count("(") <= l.count(")")


# ---------------------------------------------------------------- البطاقات

YESNO = re.compile(r"(?i)^(is|are|does|do|can|was|were|has|have|will|هل)\b")
EN_QUESTION = re.compile(r"(?i)^(what|which|why|how|when|where|who|whom|whose|name|list|define|describe|state|explain)\b")


def card_ok(q, a, source):
    q, a = q.strip(), a.strip()
    if not (6 <= len(q) <= 220) or not a or len(a) > 180 or len(a.split()) > 25:
        return False
    src = set(content(source))
    nums = numbers(source)
    if not grounded(a, src, nums):
        return False
    qw = content(q)
    if sum(1 for w in qw if w not in src) > max(1, len(qw) // 3) or any(n not in nums for n in NUM.findall(q)):
        return False
    aw = set(content(a))
    if aw and aw <= set(qw):
        return False
    if neg_count(a) > 0 and neg_count(source) == 0:
        return False
    if is_arabic(a) and EN_QUESTION.match(q):
        return False
    return not YESNO.match(q)


def jaccard(a, b):
    a, b = set(content(a)), set(content(b))
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def same_card(q1, a1, q2, a2):
    return q1.strip().lower() == q2.strip().lower() or (jaccard(q1, q2) >= 0.6 and jaccard(a1, a2) >= 0.3) or \
        (jaccard(a1, a2) >= 0.8 and jaccard(q1, q2) >= 0.3)


def dedupe_cards(cards):
    out = []
    for q, a in cards:
        if any(same_card(q, a, oq, oa) for oq, oa in out):
            continue
        out.append((q, a))
    return out


GENERIC_HEAD = {" ".join(words(w)) for w in "details detail notes note description comments remarks التفصيل التفاصيل ملاحظات الوصف".split()}


def table_cards(rows, headed, title):
    """بطاقة لكل صف جدول: السؤال = البند (+ أعمدة الرأس)، والجواب = بقية الخلايا."""
    cells = [[c.strip().replace("**", "") for c in r.split(" | ")] for r in rows if " | " in r]
    if not cells:
        return []
    head = cells[0] if headed and len(cells) >= 2 else None
    data = cells[1:] if head else cells
    ar = is_arabic(" ".join(rows))
    generic = head is not None and all(" ".join(words(h)) in GENERIC_HEAD for h in head[1:])
    out = []
    for r in data[:12]:
        if len(r) < 2 or not r[0] or all(c in ("", "—", "-") for c in r[1:]):
            continue
        if head and not generic:
            parts = [(head[k] if k < len(head) else "", c) for k, c in enumerate(r) if k > 0 and c not in ("", "—", "-")]
            q = r[0] + " — " + " / ".join(h for h, _ in parts if h) + ("؟" if ar else "?")
            a = " · ".join((h + ": " + c) if (h and len(parts) > 1) else c for h, c in parts)
        else:
            q = ((title + ": ") if title else "") + r[0] + ("؟" if ar else "?")
            a = " · ".join(c for c in r[1:] if c not in ("", "—", "-"))
        if len(a) <= 200:
            out.append((q, a))
    return out
