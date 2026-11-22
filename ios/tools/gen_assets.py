#!/usr/bin/env python3
"""
يحوّل رسومات تطبيق أندرويد (Vector Drawables) وملصقات الأغلفة إلى ملفات SVG في Assets.xcassets،
فتبقى نسخة iPad (Swift) بنفس أيقونات Phosphor ورسوماتها تمامًا، وبجودة متجهة.

الاستخدام (من جذر المستودع):
    python3 ios/tools/gen_assets.py
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
STICKERS_KT = os.path.join(ROOT, "app", "src", "main", "java", "com", "hamishnotes", "app", "Stickers.kt")
OUT = os.path.join(ROOT, "ios", "iosApp", "iosApp", "Assets.xcassets")
A = "{http://schemas.android.com/apk/res/android}"


def color(v):
    """#AARRGGBB أو #RRGGBB → (rgb, alpha)."""
    if not v:
        return None, 1.0
    v = v.strip().lstrip("#")
    if len(v) == 8:
        a = int(v[0:2], 16) / 255.0
        return "#" + v[2:], a
    if len(v) == 3:
        v = "".join(ch * 2 for ch in v)
    return "#" + v, 1.0


def num(v, d=0.0):
    try:
        return float(re.sub(r"dp$", "", v))
    except Exception:
        return d


def convert_node(node, out):
    tag = node.tag
    if tag == "group":
        rot = num(node.get(A + "rotation"), 0)
        px = num(node.get(A + "pivotX"), 0)
        py = num(node.get(A + "pivotY"), 0)
        tx = num(node.get(A + "translateX"), 0)
        ty = num(node.get(A + "translateY"), 0)
        tr = []
        if tx or ty:
            tr.append(f"translate({tx},{ty})")
        if rot:
            tr.append(f"rotate({rot},{px},{py})")
        out.append(f'<g transform="{" ".join(tr)}">' if tr else "<g>")
        for ch in node:
            convert_node(ch, out)
        out.append("</g>")
    elif tag == "path":
        d = node.get(A + "pathData", "")
        fill, fa = color(node.get(A + "fillColor"))
        stroke, sa = color(node.get(A + "strokeColor"))
        attrs = [f'd="{d}"']
        attrs.append(f'fill="{fill}"' if fill else 'fill="none"')
        if fill and fa < 1:
            attrs.append(f'fill-opacity="{fa:.3f}"')
        fa2 = node.get(A + "fillAlpha")
        if fa2:
            attrs.append(f'fill-opacity="{fa2}"')
        if stroke:
            attrs.append(f'stroke="{stroke}"')
            if sa < 1:
                attrs.append(f'stroke-opacity="{sa:.3f}"')
            attrs.append(f'stroke-width="{num(node.get(A + "strokeWidth"), 1)}"')
            cap = node.get(A + "strokeLineCap")
            if cap:
                attrs.append(f'stroke-linecap="{cap}"')
            join = node.get(A + "strokeLineJoin")
            if join:
                attrs.append(f'stroke-linejoin="{join}"')
        ft = node.get(A + "fillType")
        if ft and ft.lower() == "evenodd":
            attrs.append('fill-rule="evenodd"')
        out.append("<path " + " ".join(attrs) + "/>")


def vector_to_svg(path):
    tree = ET.parse(path)
    r = tree.getroot()
    if r.tag != "vector":
        return None
    vw = num(r.get(A + "viewportWidth"), 24)
    vh = num(r.get(A + "viewportHeight"), 24)
    w = num(r.get(A + "width"), 24)
    h = num(r.get(A + "height"), 24)
    body = []
    for ch in r:
        convert_node(ch, body)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {vw} {vh}">'
            + "".join(body) + "</svg>\n")


def write_imageset(name, svg, template):
    d = os.path.join(OUT, name + ".imageset")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, name + ".svg"), "w") as f:
        f.write(svg)
    contents = {
        "images": [{"filename": name + ".svg", "idiom": "universal"}],
        "info": {"author": "xcode", "version": 1},
        "properties": {"preserves-vector-representation": True},
    }
    if template:
        contents["properties"]["template-rendering-intent"] = "template"
    with open(os.path.join(d, "Contents.json"), "w") as f:
        json.dump(contents, f, indent=2)


def stickers():
    src = open(STICKERS_KT, encoding="utf-8").read()
    start = src.index("private val StickerArt")
    body = src[start:]
    out = {}
    cur = None
    for line in body.splitlines():
        m = re.match(r'\s*"([a-z]+)" to listOf\(', line)
        if m:
            cur = m.group(1)
            out[cur] = []
            continue
        m = re.match(r'\s*StickerPiece\("([^"]+)",\s*(null|0x[0-9A-Fa-f]+),\s*(null|0x[0-9A-Fa-f]+),\s*([0-9.]+)f\)', line)
        if m and cur:
            out[cur].append((m.group(1), m.group(2), m.group(3), float(m.group(4))))
    for key, pieces in out.items():
        parts = []
        for d, fill, stroke, w in pieces:
            a = [f'd="{d}"']
            a.append(f'fill="#{fill[4:]}"' if fill != "null" else 'fill="none"')
            if stroke != "null":
                a.append(f'stroke="#{stroke[4:]}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"')
            parts.append("<path " + " ".join(a) + "/>")
        svg = '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">' + "".join(parts) + "</svg>\n"
        write_imageset("st_" + key, svg, False)
    return list(out.keys())


def main():
    n = 0
    for fn in sorted(os.listdir(RES)):
        if not fn.endswith(".xml"):
            continue
        name = fn[:-4]
        if name.startswith("widget_"):
            continue
        svg = vector_to_svg(os.path.join(RES, fn))
        if svg is None:
            continue
        # أيقونات Phosphor تُلوَّن من الكود، والرسومات الملونة (ic_) تبقى بألوانها
        write_imageset(name, svg, template=name.startswith("ph_"))
        n += 1
    keys = stickers()
    print(f"icons: {n}, stickers: {len(keys)}")


if __name__ == "__main__":
    sys.exit(main())
