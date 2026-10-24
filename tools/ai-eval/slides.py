"""
يصنع tools/ai-eval/docs/slides_nsaids.pdf: شرائح تحاكي ما أرسله المستخدم (محاضرة NSAIDs):
عنوان كبير، نص شريحة كبير يلتف، نقاط «•» بفراغ بعدها، أسهم ↑ ↓ → كنص، وأسهم مرسومة كخطوط
(مثل قلم المستخدم، لا تُقرأ كنص)، ورسم بتسميات صغيرة تحته. يُشغَّل مرة واحدة ويُحفظ الملف في المستودع.
"""
import os

from reportlab.lib.colors import HexColor, black
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
pdfmetrics.registerFont(TTFont("Sans", FONT))
pdfmetrics.registerFont(TTFont("SansB", BOLD))
W, H = 960, 540
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "docs", "slides_nsaids.pdf")


def text(c, x, y, s, size, bold=False, color=black):
    c.setFillColor(color)
    c.setFont("SansB" if bold else "Sans", size)
    c.drawString(x, H - y, s)
    return c.stringWidth(s, "SansB" if bold else "Sans", size)


def bullet(c, x, y, s, size):
    """«•» ثم فراغ واسع ثم النص (كما في PowerPoint)."""
    text(c, x, y, "•", size)
    return text(c, x + size * 1.6, y, s, size)


def arrow(c, x, y, size, up=True):
    """سهم مرسوم بخطوط (ليس نصًا)."""
    c.setStrokeColor(HexColor("#1F2A6B"))
    c.setLineWidth(2)
    y0, y1 = (H - y + 2, H - y + size * 0.9) if up else (H - y + size * 0.9, H - y + 2)
    c.line(x, y0, x, y1)
    tip = y1
    d = -6 if up else 6
    c.line(x - 5, tip + d, x, tip)
    c.line(x + 5, tip + d, x, tip)


def harrow(c, x0, x1, y, size):
    c.setStrokeColor(black)
    c.setLineWidth(3)
    yy = H - y + size * 0.35
    c.line(x0, yy, x1, yy)
    c.line(x1 - 10, yy + 7, x1, yy)
    c.line(x1 - 10, yy - 7, x1, yy)


def build():
    c = canvas.Canvas(OUT, pagesize=(W, H))
    # 1) Anti-inflammatory drugs
    text(c, 70, 90, "Anti-inflammatory drugs", 40, bold=True)
    text(c, 55, 150, "Drugs that reduces the sign of inflammation", 34)
    text(c, 55, 200, "(redness, swelling, pain and fever) in the", 34)
    text(c, 55, 250, "body. HOW?", 34)
    c.setStrokeColor(HexColor("#3355AA"))
    c.rect(150, H - 520, 300, 220)
    text(c, 600, 300, "Examples of INFLAMMATION", 18, color=HexColor("#C0392B"))
    c.showPage()
    # 2) What are the anti-inflammatory drugs?
    text(c, 80, 120, "What are the anti-inflammatory drugs?", 34, bold=True)
    text(c, 80, 220, "They are drugs or substances that reduce", 30)
    text(c, 80, 265, "inflammation.", 30)
    c.showPage()
    # 3) Inflammation + 5 cardinal signs figure
    text(c, 65, 70, "Inflammation", 40, bold=True)
    text(c, 45, 135, "A normal response to tissue damage or infection and", 30)
    text(c, 45, 175, "associated with:", 30)
    for k, s in enumerate(["Pain", "Fever", "Swelling", "Redness", "Loss of function"]):
        bullet(c, 50, 230 + 45 * k, s, 30)
    c.setFillColor(HexColor("#D8E3A5"))
    c.rect(330, H - 520, 590, 330, fill=1, stroke=0)
    text(c, 420, 220, "5 Cardinal Signs of Inflammation", 20, bold=True, color=HexColor("#3A4A2A"))
    for k, s in enumerate(["Pain", "Heat", "Redness", "Swelling", "Loss of"]):
        text(c, 365 + 112 * k, 470, s, 11, bold=True, color=HexColor("#3A4A2A"))
    text(c, 365 + 112 * 4, 484, "Function", 11, bold=True, color=HexColor("#3A4A2A"))
    c.showPage()
    # 4) Effect on Acid/base balance (arrows: some text glyphs, some drawn)
    text(c, 120, 60, "8. Effect on Acid/base balance", 30, bold=True)
    text(c, 40, 110, "1. At therapeutic to high dose (Low toxic dose)", 28, bold=True)
    bullet(c, 40, 160, "Stimulate respiratory center causing hyperventilation", 26)
    bullet(c, 40, 205, "Decreses CO2 (wash out)", 26)
    x = 40 + 26 * 1.6
    w = text(c, x, 250, "Increases HCO3", 26)
    text(c, 40, 250, "•", 26)
    harrow(c, x + w + 15, x + w + 70, 250, 26)
    text(c, x + w + 85, 250, "PH causes (respiratory alkalosis)", 26)
    arrow(c, x + w + 92, 228, 22, up=True)
    bullet(c, 40, 295, "The kidney excrete bicarbonate HCO3 to balance the pH).", 26)
    text(c, 40, 360, "2. Toxic doses:", 30, bold=True)
    bullet(c, 40, 405, "Excretion of HCO3 (Metaboic acidosis)", 26)
    bullet(c, 40, 450, " Inhibit respiratory center causing hypoventilation", 26)
    x = 40 + 26 * 1.6
    w1 = text(c, x, 492, "causes retension of", 26)
    arrow(c, x + w1 + 12, 492, 24, up=True)
    w2 = text(c, x + w1 + 28, 492, "CO2  and", 26)
    arrow(c, x + w1 + 28 + w2 + 14, 492, 24, up=False)
    w3 = text(c, x + w1 + 28 + w2 + 30, 492, "HCO3", 26)
    harrow(c, x + w1 + 28 + w2 + 30 + w3 + 40, x + w1 + 28 + w2 + 30 + w3 + 140, 492, 26)
    text(c, 60, 530, "respiratory acidosis", 26)
    c.showPage()
    # 5) same content with arrows as text glyphs (to test that text arrows are kept)
    text(c, 40, 80, "2. Toxic doses:", 30, bold=True)
    bullet(c, 40, 130, "Inhibit respiratory center causing hypoventilation", 26)
    text(c, 40 + 26 * 1.6, 172, "causes retension of ↑CO2 and ↓HCO3 → respiratory acidosis", 26)
    c.showPage()
    c.save()
    print("wrote", OUT)


if __name__ == "__main__":
    build()
