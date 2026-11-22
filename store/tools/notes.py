import math, random, sys
from hw import strokes_for
random.seed(7)
NAVY='FF1B3A8C'; RED='FFC62828'; GREEN='FF2E7D32'
lines=[]
def emit(tool, color, width, pts, page=4):
    n=len(pts)
    s=' '.join(f'{x:.5f},{y:.5f},{0.55+0.25*math.sin(i/n*math.pi):.3f}' for i,(x,y) in enumerate(pts))
    lines.append(f'{page};{tool};{color};{width};{s}')
def text(t, x, y, size=0.034, color=NAVY, width=0.0026, seed=1, slant=0.1):
    for st in strokes_for(t, 'scripts', size, slant=slant, seed=seed):
        emit('FOUNTAIN', color, width, [(x+px, y+py) for px,py in st])
def wobble_line(x0,y0,x1,y1,color,width,tool='FOUNTAIN',n=24,amp=0.0012):
    pts=[(x0+(x1-x0)*i/n + random.uniform(-amp,amp), y0+(y1-y0)*i/n + random.uniform(-amp,amp)) for i in range(n+1)]
    emit(tool,color,width,pts)
def ellipse(cx,cy,rx,ry,color,width,turn=1.08):
    n=60; a0=random.uniform(0,6.28)
    pts=[(cx+rx*(1+0.03*math.sin(5*i/n))*math.cos(a0+turn*2*math.pi*i/n), cy+ry*math.sin(a0+turn*2*math.pi*i/n)) for i in range(n+1)]
    emit('FOUNTAIN',color,width,pts)
def arrow(x0,y0,x1,y1,color,width,bend=0.02):
    n=30; pts=[]
    for i in range(n+1):
        t=i/n; mx=(x0+x1)/2; my=(y0+y1)/2-bend
        x=(1-t)**2*x0+2*(1-t)*t*mx+t*t*x1; y=(1-t)**2*y0+2*(1-t)*t*my+t*t*y1
        pts.append((x,y))
    emit('FOUNTAIN',color,width,pts)
    ang=math.atan2(y1-pts[-3][1], x1-pts[-3][0]); L=0.016
    for s in (+0.5,-0.5):
        emit('FOUNTAIN',color,width,[(x1-L*math.cos(ang+s)*k/6, y1-L*math.sin(ang+s)*k/6) for k in range(7)][::-1])

# circle around t1/2 = 30 min (page coords, width units)
ellipse(0.135,0.823,0.06,0.016,RED,0.0024)
# arrow from gastric acid line to margin note
arrow(0.535,0.806,0.585,0.912,NAVY,0.0024,bend=-0.03)
text('oral form:', 0.60, 0.935, size=0.03, seed=3)
text('Pen V (acid-stable)', 0.60, 0.98, size=0.03, seed=8)
# handwritten list under "Clinical uses of Pen G"
text('Strep throat, syphilis,', 0.075, 0.935, seed=4)
text('meningitis, gas gangrene', 0.075, 0.985, seed=5)
text('DOC for syphilis!', 0.075, 1.050, color=RED, seed=6)
wobble_line(0.072,1.062,0.37,1.058,RED,0.0022)
wobble_line(0.074,1.068,0.36,1.066,RED,0.0018)
# tick marks next to bullets of 1.4
for y in (0.531,0.599,0.6711):
    emit('FOUNTAIN',GREEN,0.0026,[(0.025+0.006*k/4, y-0.004+0.008*k/4) for k in range(5)]+[(0.031+0.014*k/6, y+0.004-0.02*k/6) for k in range(1,7)])
# cardiology slide (page 1, 0.5625 tall)
lines_p5=lines; lines=[]
ellipse(0.437,0.238,0.05,0.021,RED,0.0022)
wobble_line(0.096,0.257,0.174,0.257,GREEN,0.0024)
arrow(0.55,0.232,0.495,0.236,NAVY,0.0022,bend=0.004)
text('EF = SV / EDV', 0.565, 0.247, size=0.03, seed=11)
text('normal 55-70%', 0.565, 0.292, size=0.03, seed=12)
open(sys.argv[2],'w').write('\n'.join(l.replace('4;','1;',1) for l in lines)+'\n')
lines=lines_p5
open(sys.argv[1],'w').write('\n'.join(lines)+'\n')
print(len(lines),'strokes')
