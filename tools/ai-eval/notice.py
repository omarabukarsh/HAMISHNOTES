"""ينشر results.md كتنبيهات (annotations) في GitHub لقراءتها عبر الـ API."""
text = open("results.md", encoding="utf-8").read()
size = 3500
parts = [text[i:i + size] for i in range(0, len(text), size)][:10]
for i, p in enumerate(parts, 1):
    esc = p.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::notice title=eval part {i}/{len(parts)}::{esc}")
