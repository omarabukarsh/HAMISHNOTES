"""ينشر ملف نتائج كتنبيهات (annotations) في GitHub لقراءتها عبر الـ API (حتى 10 تنبيهات لكل خطوة)."""
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "results.md"
title = sys.argv[2] if len(sys.argv) > 2 else "eval"
text = open(path, encoding="utf-8").read()
size = 3500
parts = [text[i:i + size] for i in range(0, len(text), size)][:10]
for i, p in enumerate(parts, 1):
    esc = p.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::notice title={title} {i}/{len(parts)}::{esc}")
