"""الحالات المرجعية (AntibioticsCases.kt) على نقل TextLayout إلى Python: نص كل تمييز مقابل المتوقع."""
import math
import re
import sys

import textlayout as T

KT = "app/src/sharedTest/java/com/mynotes/app/golden/AntibioticsCases.kt"
TSV = "app/src/sharedTest/assets/antibiotics_words.tsv"
WIDTH = 0.010 + 4 * 0.0035  # Sizes.width(HIGHLIGHTER, 4)


def kt_strings(block):
    """يجمع سلاسل Kotlin المتتالية المربوطة بـ +، ويعيد قائمة العناصر المفصولة بفواصل في المستوى الأعلى."""
    items, cur, i = [], "", 0
    while i < len(block):
        c = block[i]
        if c == '"':
            j, s = i + 1, ""
            while block[j] != '"':
                if block[j] == "\\":
                    s += {"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$"}.get(block[j + 1], block[j + 1])
                    j += 2
                else:
                    s += block[j]
                    j += 1
            cur += s
            i = j + 1
        elif c == ",":
            items.append(cur)
            cur = ""
            i += 1
        else:
            i += 1
    if cur:
        items.append(cur)
    return items


def cases():
    src = open(KT, encoding="utf-8").read()
    out = []
    for m in re.finditer(r'GoldenCase\(\s*id = "([^"]+)"(.*?)\n    \)', src, re.S):
        body = m.group(2)
        strokes = []
        for s in re.finditer(r'line\((\d+), ([\d.]+)f, ([\d.]+)f, ([\d.]+)f(, Pipeline\.GREEN)?\)', body):
            p, y, x0, x1, g = int(s.group(1)), float(s.group(2)), float(s.group(3)), float(s.group(4)), bool(s.group(5))
            strokes.append((p, y, x0 - 0.004, x1 + 0.004, "G" if g else "Y"))
        for s in re.finditer(r'Stroke\((\d+), stroke\(([\d.]+)f, ([\d.]+)f, ([\d.]+)f\)(, Pipeline\.GREEN)?\)', body):
            strokes.append((int(s.group(1)), float(s.group(2)), float(s.group(3)), float(s.group(4)), "G" if s.group(5) else "Y"))
        mt = re.search(r"markTexts = listOf\((.*?)\n        \),?\n", body, re.S)
        out.append((m.group(1), strokes, kt_strings(mt.group(1)) if mt else []))
    return out


def load_words():
    pages = {}
    for l in open(TSV, encoding="utf-8"):
        if l.startswith("#") or not l.strip():
            continue
        f = l.rstrip("\n").split("\t")
        pages.setdefault(int(f[0]), []).append((f[6], float(f[2]), float(f[3]), float(f[4]), float(f[5]), int(f[1]), False))
    return pages


def words_under(words, y, x0, x1, band=WIDTH):
    n = 24
    pts = [(x0 + (x1 - x0) * k / n, y) for k in range(n + 1)]
    samples = [pts[0]]
    for a, c in zip(pts, pts[1:]):
        d = math.hypot(c[0] - a[0], c[1] - a[1])
        m = max(0, min(200, int(d / 0.003)))
        for k in range(1, m + 1):
            f = k / (m + 1)
            samples.append((a[0] + (c[0] - a[0]) * f, a[1] + (c[1] - a[1]) * f))
        samples.append(c)
    half = band / 2
    hit = set()
    for i, w in enumerate(words):
        slack = min(half, (w[4] - w[2]) * 0.6)
        if any(w[1] <= px <= w[3] and w[2] - slack <= py <= w[4] + slack for px, py in samples):
            hit.add(i)
    out = set()
    for row in T.visual_rows(words, range(len(words))):
        hr = [i for i in row if i in hit]
        if not hr:
            continue
        left, right = min(words[i][1] for i in hr), max(words[i][3] for i in hr)
        out.update(i for i in row if words[i][1] >= left - 1e-4 and words[i][3] <= right + 1e-4)
    return out


def run(fixed):
    pages = load_words()
    res = []
    for cid, strokes, expected in cases():
        marks = []  # (page, color, set(idx))
        for p, y, x0, x1, col in strokes:
            ws = pages[p]
            idx = words_under(ws, y, x0, x1)
            if not idx:
                continue
            order = [i for r in T.visual_rows(ws, range(len(ws))) for i in r]
            pos = {i: k for k, i in enumerate(order)}
            lo, hi = min(pos[i] for i in idx), max(pos[i] for i in idx)
            merged = False
            for m in marks:
                if m[0] == p and m[1] == col:
                    mlo, mhi = min(pos[i] for i in m[2]), max(pos[i] for i in m[2])
                    if mhi + 1 >= lo and hi + 1 >= mlo:
                        m[2].update(idx)
                        merged = True
                        break
            if not merged:
                marks.append([p, col, set(idx)])
        texts = [T.build_text(pages[m[0]], m[2], fixed) for m in marks]
        res.append((cid, texts == expected, texts, expected))
    return res


if __name__ == "__main__":
    for fixed in (False, True):
        r = run(fixed)
        print(("FIXED" if fixed else "CURRENT"), sum(ok for _, ok, _, _ in r), "/", len(r), [c for c, ok, _, _ in r if ok])
    if len(sys.argv) > 1:
        for cid, ok, got, exp in run(sys.argv[1] == "fixed"):
            if not ok:
                print("=====", cid)
                print("--- got:"); print("\n~~\n".join(got))
                print("--- expected:"); print("\n~~\n".join(exp))
