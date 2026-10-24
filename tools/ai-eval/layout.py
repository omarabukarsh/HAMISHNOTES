"""
يحوّل ملف تجربة نصي إلى كلمات بمواضعها كما يقرؤها التطبيق من صفحة PDF (OcrWord):
عناوين بخط أكبر، أسطر قصيرة عريضة، فقرات تلتف على عدة أسطر، جداول بفراغات واسعة بين الخلايا،
والعربية من اليمين لليسار. المقاسات مأخوذة من antibiotics_words.tsv (إحداثيات 0..1).

صيغة الملف:  "# " عنوان 1، "## " عنوان 2، "!! " سطر قصير عريض، "| a | b" صف جدول (الأول رأسه)،
سطر فارغ = كتلة جديدة، وغير ذلك سطر نص (يلتف إن طال).
"""
import re

AR = re.compile(r"[؀-ۿ]")
LEFT, RIGHT, TOP, BOTTOM = 0.06, 0.94, 0.07, 0.93
STYLE = {"h1": (0.020, 0.0125), "h2": (0.016, 0.0105), "p": (0.0135, 0.0093)}  # (ارتفاع السطر، عرض الحرف)
SPACE = 0.35  # فراغ بين الكلمات (بالنسبة لعرض الحرف ×)


def is_ar(s):
    return sum(1 for c in s if "؀" <= c <= "ۿ") > sum(1 for c in s if c.isalpha()) / 3


class Doc:
    """rows: [(page, line, l, t, r, b, text, bold)]؛ blocks: [(kind, page, [line ids])] لاختيار تظليلات واقعية."""

    def __init__(self, path):
        self.rows, self.blocks = [], []
        self.page, self.line, self.y = 0, 0, TOP
        # كل سطر في الملف سطر (أو فقرة تلتف) في الصفحة؛ الأسطر المتتالية بلا فراغ = كتلة واحدة (قائمة أو فقرة)
        self._region = None
        for raw in open(path, encoding="utf-8").read().splitlines() + [""]:
            l = raw.rstrip()
            if not l.strip() or l.startswith(("# ", "## ", "!! ", "| ")):
                self.end_region()
            if not l.strip():
                self.y += 0.010
                continue
            if l.startswith("# "):
                self.heading(l[2:], "h1")
            elif l.startswith("## "):
                self.heading(l[3:], "h2")
            elif l.startswith("!! "):
                self.heading(l[3:], "p", bold=True)
            elif l.startswith("| "):
                self.table_row([c.strip() for c in l[2:].split("|")])
            else:
                self.paragraph(l.strip())
        self.end_region()
        self.flush_table()

    # ------------------------------------------------------------
    _table = None

    def need(self, h):
        if self.y + h > BOTTOM:
            self.page += 1
            self.line = 0
            self.y = TOP

    def place(self, words, h, cw, x0=None, x1=None, bold=False):
        """يضع كلمات سطر واحد ويعيد رقم السطر."""
        self.need(h)
        ar = is_ar(" ".join(words))
        x0 = LEFT if x0 is None else x0
        x1 = RIGHT if x1 is None else x1
        ln = self.line
        if ar:
            x = x1
            for w in words:
                wd = len(w) * cw
                self.rows.append((self.page, ln, x - wd, self.y, x, self.y + h, w, bold))
                x -= wd + SPACE * cw
        else:
            x = x0
            for w in words:
                wd = len(w) * cw
                self.rows.append((self.page, ln, x, self.y, x + wd, self.y + h, w, bold))
                x += wd + SPACE * cw
        self.line += 1
        self.y += h + 0.0075
        return (self.page, ln)

    def wrap(self, text, cw, width):
        out, cur = [], []
        for w in text.split():
            if cur and (len(" ".join(cur + [w])) * cw) > width:
                out.append(cur)
                cur = []
            cur.append(w)
        if cur:
            out.append(cur)
        return out

    def heading(self, text, kind, bold=False):
        self.end_region()
        self.flush_table()
        h, cw = STYLE[kind]
        self.y += 0.006
        self.need(h * 3)
        ids = [self.place(ws, h, cw, bold=bold or kind != "p") for ws in self.wrap(text, cw, RIGHT - LEFT)]
        self.blocks.append(("heading", ids))

    def paragraph(self, text):
        self.flush_table()
        h, cw = STYLE["p"]
        ids = [self.place(ws, h, cw) for ws in self.wrap(text, cw, RIGHT - LEFT)]
        if self._region is None:
            self._region = []
        self._region += ids

    def end_region(self):
        if self._region:
            self.blocks.append(("para", self._region))
        self._region = None

    def table_row(self, cells):
        if self._table is None:
            self._table = []
        self._table.append(cells)

    def flush_table(self):
        rows, self._table = self._table, None
        if not rows:
            return
        h, cw = STYLE["p"]
        n = max(len(r) for r in rows)
        widths = [max(len(r[k]) if k < len(r) else 0 for r in rows) for k in range(n)]
        total = sum(widths) or 1
        avail = RIGHT - LEFT - 0.03 * (n - 1)
        cols = [max(0.08, avail * w / total) for w in widths]
        ar = is_ar(" ".join(" ".join(r) for r in rows))
        ids = []
        for r in rows:
            self.need(h)
            ln = self.line
            x = RIGHT if ar else LEFT
            # كل خلية في عمودها؛ الخلية الأطول من عمودها تُقص إلى سطر واحد (جداول التجربة قصيرة الخلايا)
            for k, c in enumerate(r):
                ws = c.split()
                if ar:
                    xx = x
                    for w in ws:
                        wd = len(w) * cw
                        self.rows.append((self.page, ln, xx - wd, self.y, xx, self.y + h, w, False))
                        xx -= wd + SPACE * cw
                    x -= cols[k] + 0.03
                else:
                    xx = x
                    for w in ws:
                        wd = len(w) * cw
                        self.rows.append((self.page, ln, xx, self.y, xx + wd, self.y + h, w, False))
                        xx += wd + SPACE * cw
                    x += cols[k] + 0.03
            ids.append((self.page, ln))
            self.line += 1
            self.y += h + 0.012
        self.blocks.append(("table", ids))
