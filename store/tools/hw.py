import math, random, json, sys
from HersheyFonts import HersheyFonts

def strokes_for(text, font='scripts', size=1.0, slant=0.12, seed=1):
    """Return list of strokes, each list of (x,y) in units where cap height ~ size; y down, baseline 0."""
    rnd = random.Random(seed)
    f = HersheyFonts(); f.load_default_font(font); f.normalize_rendering(1.0)
    out = []
    # per-word jitter
    cur = []
    for (x1, y1), (x2, y2) in f.lines_for_text(text):
        p1 = (x1, -y1); p2 = (x2, -y2)
        if cur and abs(cur[-1][0]-p1[0]) < 1e-6 and abs(cur[-1][1]-p1[1]) < 1e-6:
            cur.append(p2)
        else:
            if cur: out.append(cur)
            cur = [p1, p2]
    if cur: out.append(cur)
    res = []
    for s in out:
        dy = rnd.uniform(-0.03, 0.03); dx = rnd.uniform(-0.02, 0.02)
        pts = [((x + dx) * size - (y) * slant * size, (y + dy) * size) for x, y in s]
        res.append(smooth(pts))
    return res

def smooth(pts, n=4):
    if len(pts) < 3: 
        # densify line
        (x1,y1),(x2,y2)=pts[0],pts[-1]
        return [(x1+(x2-x1)*t/6, y1+(y2-y1)*t/6) for t in range(7)]
    p = [pts[0]] + pts + [pts[-1]]
    out = []
    for i in range(1, len(p)-2):
        p0,p1,p2,p3 = p[i-1],p[i],p[i+1],p[i+2]
        for k in range(n):
            t = k/n; t2=t*t; t3=t2*t
            out.append(tuple(0.5*((2*p1[j]) + (-p0[j]+p2[j])*t + (2*p0[j]-5*p1[j]+4*p2[j]-p3[j])*t2 + (-p0[j]+3*p1[j]-3*p2[j]+p3[j])*t3) for j in range(2)))
    out.append(pts[-1])
    return out

if __name__ == '__main__':
    from PIL import Image, ImageDraw
    im = Image.new('RGB', (1400, 500), 'white'); d = ImageDraw.Draw(im)
    for row, font in enumerate(['scripts', 'futural', 'cursive']):
        for s in strokes_for('Penicillin G is acid-labile -> give IV', font, 46, seed=row):
            d.line([(40 + x, 120 + row * 150 + y) for x, y in s], fill=(20, 40, 120), width=4, joint='curve')
    im.save('sample.png')
