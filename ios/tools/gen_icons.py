#!/usr/bin/env python3
"""يحوّل أيقونات أندرويد (res/drawable/*.xml) إلى كود Kotlin (ImageVector) لنسخة iPad.

التشغيل من جذر المستودع:  python3 ios/tools/gen_icons.py
يكتب ios/shared/src/iosMain/kotlin/com/hamishnotes/app/R.kt و Drawables.kt
"""
import os
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app/src/main/res")
OUT = os.path.join(ROOT, "ios/shared/src/iosMain/kotlin/com/hamishnotes/app")
A = "{http://schemas.android.com/apk/res/android}"


def color(v):
    if v is None:
        return None
    v = v.strip().lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    if len(v) == 6:
        v = "FF" + v
    if len(v) == 4:
        v = "".join(c * 2 for c in v)
    return "0x" + v.upper()


def num(v, d="0f"):
    if v is None:
        return d
    return v.replace("dp", "") + "f"


def kstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def emit(node, out, ind):
    for ch in node:
        tag = ch.tag
        if tag == "path":
            fill = color(ch.get(A + "fillColor"))
            stroke = color(ch.get(A + "strokeColor"))
            args = ["pathData = addPathNodes(" + kstr(ch.get(A + "pathData").strip()) + ")"]
            if fill:
                args.append("fill = SolidColor(Color(%s))" % fill)
            if stroke:
                args.append("stroke = SolidColor(Color(%s))" % stroke)
                args.append("strokeLineWidth = " + num(ch.get(A + "strokeWidth"), "1f"))
            cap = ch.get(A + "strokeLineCap")
            if cap:
                args.append("strokeLineCap = StrokeCap." + {"round": "Round", "square": "Square", "butt": "Butt"}[cap])
            join = ch.get(A + "strokeLineJoin")
            if join:
                args.append("strokeLineJoin = StrokeJoin." + {"round": "Round", "bevel": "Bevel", "miter": "Miter"}[join])
            if ch.get(A + "fillType") == "evenOdd":
                args.append("pathFillType = PathFillType.EvenOdd")
            out.append(ind + "addPath(" + ", ".join(args) + ")")
        elif tag == "group":
            out.append(ind + "addGroup(rotate = %s, pivotX = %s, pivotY = %s, translationX = %s, translationY = %s)" % (
                num(ch.get(A + "rotation")), num(ch.get(A + "pivotX")), num(ch.get(A + "pivotY")),
                num(ch.get(A + "translateX")), num(ch.get(A + "translateY"))))
            emit(ch, out, ind)
            out.append(ind + "clearGroup()")


def main():
    names = []
    bodies = []
    for f in sorted(os.listdir(os.path.join(RES, "drawable"))):
        if not f.endswith(".xml"):
            continue
        root = ET.parse(os.path.join(RES, "drawable", f)).getroot()
        if root.tag != "vector":
            continue
        name = f[:-4]
        idx = len(names)
        names.append(name)
        lines = []
        emit(root, lines, "            ")
        bodies.append("        %d -> ImageVector.Builder(%s, %s.dp, %s.dp, %sf, %sf).apply {\n%s\n        }.build()" % (
            idx, kstr(name),
            root.get(A + "width").replace("dp", ""), root.get(A + "height").replace("dp", ""),
            root.get(A + "viewportWidth"), root.get(A + "viewportHeight"), "\n".join(lines)))
    fonts = sorted(f[:-4] for f in os.listdir(os.path.join(RES, "font")) if f.endswith(".ttf"))

    with open(os.path.join(OUT, "R.kt"), "w") as o:
        o.write("// مولَّد بواسطة ios/tools/gen_icons.py من res/ في تطبيق أندرويد. لا تعدّله يدويًا.\n")
        o.write("@file:Suppress(\"ClassName\", \"ObjectPropertyName\")\n\npackage com.hamishnotes.app\n\n")
        o.write("/** نفس أسماء موارد أندرويد، فيبقى الكود المشترك كما هو (R.drawable.ph_pen ...). */\nobject R {\n")
        o.write("    object drawable {\n")
        for i, n in enumerate(names):
            o.write("        const val %s = %d\n" % (n, i))
        o.write("        const val logo_app = %d\n" % len(names))
        o.write("    }\n    object font {\n")
        for i, n in enumerate(fonts):
            o.write("        const val %s = %d\n" % (n, i))
        o.write("    }\n}\n\n")
        o.write("/** أسماء ملفات الخطوط داخل حزمة التطبيق (font/). */\ninternal val fontFiles = listOf(%s)\n" %
                ", ".join(kstr(n) for n in fonts))

    with open(os.path.join(OUT, "Drawables.kt"), "w") as o:
        o.write("// مولَّد بواسطة ios/tools/gen_icons.py من res/drawable في تطبيق أندرويد. لا تعدّله يدويًا.\n")
        o.write("package com.hamishnotes.app\n\n")
        for imp in ["androidx.compose.ui.graphics.Color", "androidx.compose.ui.graphics.PathFillType",
                    "androidx.compose.ui.graphics.SolidColor", "androidx.compose.ui.graphics.StrokeCap",
                    "androidx.compose.ui.graphics.StrokeJoin", "androidx.compose.ui.graphics.vector.ImageVector",
                    "androidx.compose.ui.graphics.vector.addPathNodes", "androidx.compose.ui.unit.dp"]:
            o.write("import %s\n" % imp)
        o.write("\n/** عدد الأيقونات المتجهة (ما بعده صور PNG). */\ninternal const val VECTOR_COUNT = %d\n\n" % len(names))
        o.write("internal fun buildVector(id: Int): ImageVector? =\n    when (id) {\n")
        o.write("\n".join(bodies))
        o.write("\n        else -> null\n    }\n")


main()
