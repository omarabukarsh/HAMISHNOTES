"""
نقل حرفي لـ TextLayout.kt (visualRows، Row.cells، readTable، buildText، wordsUnder) إلى Python،
لاختبار قراءة نص الشرائح خارج أندرويد. fixed=True يطبّق الإصلاحات المقترحة (انظر FIXES).
الكلمة: (text, l, t, r, b, line, bold) بإحداثيات مقسومة على عرض الصفحة (مثل PdfTextLayer).
"""
import re

TERMINAL = ".!?؟…"
# رموز النقاط في أول السطر: ليست خلية جدول
BULLETS = set("•●◦▪■□➢➤►▸‣⁃–—-*·✓✔")

FIXES = """
F1 رمز النقطة في أول السطر لا يكون خلية مستقلة (يُلصق بما بعده).
F3 سطر قصير عريض أكبر قليلًا من نص الصفحة = عنوان (عنوان الشريحة من كلمة واحدة).
F2 صف واحد بخلايا (2 أو 3+) يبدأ جدولًا فقط إن كان في الصفحة صف مجاور قريب بخليتين فأكثر
   وأعمدة متطابقة؛ وإلا فالفراغ داخل جملة (مكان سهم أو صورة) والصف نص عادي.
"""


def h(w):
    return w[4] - w[2]


def median(v):
    v = sorted(v)
    return v[len(v) // 2]


def is_ar_char(c):
    return "؀" <= c <= "ۿ" or "ݐ" <= c <= "ݿ" or "ﭐ" <= c <= "﻿"


def body_height(words):
    if not words:
        return 0.012
    return median([h(w) for w in words])


def visual_rows(words, idx):
    valid = [i for i in idx if 0 <= i < len(words)]
    if not valid:
        return []
    groups = {}
    for i in valid:
        groups.setdefault(words[i][5], []).append(i)
    lines = []
    for g in groups.values():
        ids = sorted(g, key=lambda i: words[i][1])
        lines.append((ids, median([words[i][2] for i in ids]), median([words[i][4] for i in ids]), min(words[i][1] for i in ids)))
    lines.sort(key=lambda ln: (ln[1] + ln[2]) / 2)
    rows = []
    for ln in lines:
        if rows:
            first = rows[-1][0]
            ov = min(ln[2], first[2]) - max(ln[1], first[1])
            min_h = max(min(ln[2] - ln[1], first[2] - first[1]), 1e-6)
            if ov >= 0.5 * min_h:
                rows[-1].append(ln)
                continue
        rows.append([ln])
    return [[i for ln in sorted(r, key=lambda x: x[3]) for i in ln[0]] for r in rows]


class Cell:
    def __init__(self, text, l, r, rtl):
        self.text, self.l, self.r = text, l, r
        self.edge = r if rtl else l


class Row:
    def __init__(self, inp, fixed=False):
        ar = sum(1 for w in inp for ch in w[0] if is_ar_char(ch))
        la = sum(1 for w in inp for ch in w[0] if ch.isalpha() and not is_ar_char(ch))
        self.neutral = ar == 0 and la == 0
        self.rtl = ar > la
        self.words = sorted(inp, key=lambda w: -w[3]) if self.rtl else sorted(inp, key=lambda w: w[1])
        self.l = min(w[1] for w in self.words)
        self.r = max(w[3] for w in self.words)
        self.t = median([w[2] for w in self.words])
        self.b = median([w[4] for w in self.words])
        self.height = median([w[4] - w[2] for w in self.words])
        out, group = [], []

        def flush():
            if group:
                out.append(Cell(" ".join(w[0] for w in group), min(w[1] for w in group), max(w[3] for w in group), self.rtl))
                group.clear()
        for k, w in enumerate(self.words):
            if k > 0:
                prev = self.words[k - 1]
                gap = (prev[1] - w[3]) if self.rtl else (w[1] - prev[3])
                line_changed = w[5] != prev[5]
                bullet_lead = fixed and k == 1 and prev[0].strip() in BULLETS
                if not bullet_lead and (gap > 1.4 * self.height or (line_changed and gap > 1.0 * self.height)):
                    flush()
            group.append(w)
        flush()
        if fixed and out and out[0].text.split(" ", 1)[0] in BULLETS:
            # F1: «•  نص» خلية واحدة؛ ويُحذف الرمز من النص (التطبيق يعرض نقاطه بنفسه)
            c0 = out[0]
            out[0] = Cell(c0.text.split(" ", 1)[1] if " " in c0.text else c0.text, c0.l, c0.r, self.rtl)
        self.cells = out

    @property
    def text(self):
        return " ".join(c.text for c in self.cells)

    @property
    def numbered_heading(self):
        t = self.text
        return bool(re.search(r"^\d+([.٫]\d+)*[.٫]?\s+\S", t)) and len(t.split(" ")) <= 10 and t.rstrip()[-1:] != "."


def read_table(rows, start, out):
    first = rows[start]
    anchors = [c.edge for c in first.cells]
    tol = 2.2 * first.height
    out.append([c.text for c in first.cells])
    gaps = []
    j = start + 1

    def col_of(c):
        best, best_d = -1, float("inf")
        for k, a in enumerate(anchors):
            d = abs(c.edge - a)
            if d < best_d:
                best_d, best = d, k
        return best if best_d <= tol else -1
    while j < len(rows):
        row, prev = rows[j], rows[j - 1]
        lh = max(row.height, prev.height)
        gap = row.t - prev.b
        if gap > 1.8 * lh:
            break
        if len(gaps) >= 2 and gap > 1.4 * median(gaps) and gap > 1.0 * lh and len(row.cells) >= 2:
            break
        if not row.neutral and not first.neutral and row.rtl != first.rtl and len(row.cells) >= 2:
            break
        cols = [col_of(c) for c in row.cells]
        if len(row.cells) >= 2:
            if any(c < 0 for c in cols):
                break
            cells = [""] * len(anchors)
            for k, c in enumerate(row.cells):
                col = cols[k]
                cells[col] = c.text if not cells[col] else cells[col] + " " + c.text
            out.append(cells)
        else:
            col = cols[0]
            if col < 0 or gap > 0.9 * lh:
                break
            if col == 0 and len(anchors) > 1 and gap > 0.5 * lh:
                break
            last = out[-1]
            while len(last) < len(anchors):
                last.append("")
            last[col] = row.cells[0].text if not last[col] else last[col] + " " + row.cells[0].text
        gaps.append(gap)
        j += 1
    return j - start


def starts_new_paragraph(prev, nxt, block_l, block_r):
    lh = max(prev.height, nxt.height)
    if nxt.t - prev.b > 0.9 * lh:
        return True
    if abs(nxt.height - prev.height) > 0.25 * lh:
        return True
    pe = prev.text.rstrip()[-1:]
    ended = pe != "" and pe in TERMINAL
    if prev.rtl:
        if ended and prev.l > block_l + 2.5 * lh:
            return True
        if ended and nxt.r < block_r - 1.5 * lh:
            return True
    else:
        if ended and prev.r < block_r - 2.5 * lh:
            return True
        if ended and nxt.l > block_l + 1.5 * lh:
            return True
    return False


def page_table_rows(words, fixed):
    """صفوف الصفحة كلها (لـ F2: هل للصف جيران بخلايا في الصفحة؟)."""
    return [Row([words[i] for i in r], fixed) for r in visual_rows(words, range(len(words)))]


def aligned_neighbor(page_rows, row):
    """F2: صف مجاور في الصفحة (قريب رأسيًا) بخليتين فأكثر وحافة عمود مشتركة غير الأولى."""
    k = next((i for i, pr in enumerate(page_rows) if abs(pr.t - row.t) < 0.3 * row.height), None)
    if k is None:
        return False
    for j in (k - 1, k + 1):
        if 0 <= j < len(page_rows):
            nb = page_rows[j]
            if abs(nb.t - row.t) > 3.2 * max(nb.height, row.height) or len(nb.cells) < 2:
                continue
            tol = 2.2 * row.height
            if any(abs(a.edge - b.edge) <= tol for a in row.cells[1:] for b in nb.cells[1:]):
                return True
    return False


def build_text(page_words, idx, fixed=False):
    rows_idx = visual_rows(page_words, idx)
    if not rows_idx:
        return ""
    rows = [Row([page_words[i] for i in r], fixed) for r in rows_idx]
    body = body_height(page_words)
    prow = page_table_rows(page_words, fixed) if fixed else None
    n = len(rows)
    starts_table = []
    for i in range(n):
        multi = len(rows[i].cells) >= 2
        next_multi = i < n - 1 and len(rows[i + 1].cells) >= 2
        next_cont = i < n - 1 and len(rows[i + 1].cells) == 1 and rows[i + 1].t - rows[i].b < 0.9 * rows[i].height
        st = multi and (next_multi or next_cont or len(rows[i].cells) >= 3)
        if fixed and st and not next_multi and not aligned_neighbor(prow, rows[i]):
            st = False
        starts_table.append(st)
    is_heading = []
    for i in range(n):
        ratio = 1.25 if len(rows[i].words) >= 2 else 1.6
        hd = rows[i].height >= ratio * body or (rows[i].numbered_heading and rows[i].height >= 1.08 * body)
        if fixed and not hd:
            # F3: سطر قصير عريض (عنوان شريحة) أكبر قليلًا من نص الصفحة، بلا علامة نهاية جملة
            ws = rows[i].words
            bold = sum(1 for w in ws if w[6]) * 5 >= len(ws) * 4
            hd = bold and len(ws) <= 8 and rows[i].height >= 1.2 * body and rows[i].text.rstrip()[-1:] not in TERMINAL
        is_heading.append(not starts_table[i] and hd)
    block_l = min(r.l for r in rows)
    block_r = max(r.r for r in rows)
    out = []
    i = 0
    while i < n:
        if out:
            out.append("\n\n")
        if starts_table[i]:
            table = []
            used = read_table(rows, i, table)
            cols = max(len(r) for r in table)
            out.append("\n".join(" | ".join((r[k] if k < len(r) else "").strip() for k in range(cols)) for r in table))
            i += used
        elif is_heading[i]:
            j, parts = i, []
            while j < n and is_heading[j] and (j == i or abs(rows[j].height - rows[i].height) < 0.2 * rows[i].height):
                parts.append(rows[j].text)
                j += 1
            out.append("# " + " ".join(parts))
            i = j
        else:
            j, lines = i, []
            while j < n and not starts_table[j] and not is_heading[j]:
                if j > i and starts_new_paragraph(rows[j - 1], rows[j], block_l, block_r):
                    break
                lines.append(rows[j].text)
                j += 1
            out.append("\n".join(lines))
            i = j
    return "".join(out).strip()


def words_from_pdf(path):
    """كلمات الصفحات مثل PdfTextLayer: إحداثيات ÷ عرض الصفحة، ورقم سطر لكل سطر نص، وخط عريض."""
    import pdfplumber
    pages = []
    with pdfplumber.open(path) as pdf:
        for pg in pdf.pages:
            W = float(pg.width)
            ws = pg.extract_words(extra_attrs=["fontname", "size"], keep_blank_chars=False, x_tolerance=1.5)
            ws.sort(key=lambda w: (round(w["top"] / 3), w["x0"]))
            out, line, last_top = [], -1, None
            for w in ws:
                if last_top is None or abs(w["top"] - last_top) > 0.5 * (w["bottom"] - w["top"]):
                    line += 1
                    last_top = w["top"]
                out.append((w["text"], w["x0"] / W, w["top"] / W, w["x1"] / W, w["bottom"] / W, line,
                            bool(re.search(r"(?i)bold", w["fontname"]))))
            pages.append(out)
    return pages
