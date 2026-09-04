from PIL import Image, ImageDraw, ImageFilter, ImageEnhance, ImageOps
from pathlib import Path
import random, json

ROOT = Path(__file__).resolve().parent
ASSET = ROOT / "app/src/main/assets/worlds/gothic_castle"
DRAWABLE = ROOT / "app/src/main/res/drawable"
ASSET.mkdir(parents=True, exist_ok=True)
DRAWABLE.mkdir(parents=True, exist_ok=True)
random.seed(20260831)

# Power-of-two material maps for OpenGL ES 2 repeat sampling.
S = 2048
noise = Image.effect_noise((S, S), 66).convert("L")
low = Image.effect_noise((256, 256), 46).resize((S, S), Image.Resampling.BICUBIC)
rock = Image.blend(noise, low, 0.38)
stone = ImageOps.colorize(rock, black=(42, 44, 44), white=(153, 149, 137)).convert("RGB")
stone = ImageEnhance.Contrast(stone).enhance(1.16)
d = ImageDraw.Draw(stone, "RGBA")
row = 0
y = -40
while y < S + 220:
    h = random.randint(180, 260)
    x = -random.randint(120, 420) + (150 if row % 2 else 0)
    while x < S + 420:
        w = random.randint(300, 590)
        wobble = random.randint(-12, 12)
        x0, y0, x1, y1 = x, y + wobble, x + w, y + h + wobble
        d.rounded_rectangle((x0-13, y0-13, x1+13, y1+13), radius=16, outline=(17,18,18,245), width=21)
        d.rounded_rectangle((x0+5, y0+5, x1-5, y1-5), radius=12, outline=(195,187,166,34), width=5)
        if random.random() < .66:
            for _ in range(random.randint(1, 4)):
                cx = random.randint(int(x0+20), int(x1-20))
                cy = random.randint(int(y0+18), int(y1-18))
                r = random.randint(15, 62)
                tint = random.choice([(25,43,32,30),(55,40,26,24),(12,19,22,26),(110,91,53,16)])
                d.ellipse((cx-r, cy-r//2, cx+r, cy+r//2), fill=tint)
        x += w + random.randint(15, 34)
    y += h + random.randint(8, 22)
    row += 1
stone = stone.filter(ImageFilter.GaussianBlur(.28))
stone = ImageEnhance.Sharpness(stone).enhance(1.38)
stone.save(ASSET / "stone_albedo.jpg", "JPEG", quality=96, subsampling=0)

# Fine rock detail map.
detail = Image.effect_noise((1024,1024), 92).convert("L")
detail = ImageEnhance.Contrast(detail).enhance(1.48)
detail.save(ASSET / "stone_detail.jpg", "JPEG", quality=95)

# Grunge/damp map.
g = Image.effect_noise((1024,1024), 70).convert("L")
glow = Image.effect_noise((128,128), 45).resize((1024,1024), Image.Resampling.BICUBIC)
g = Image.blend(g, glow, .48)
g = ImageEnhance.Contrast(g).enhance(1.38)
g.save(ASSET / "grunge.jpg", "JPEG", quality=95)

# Crack mask.
C = 2048
cr = Image.new("L", (C,C), 0)
cd = ImageDraw.Draw(cr)
for _ in range(125):
    x = random.randint(-100, C+100)
    y = random.randint(-150, C//2)
    pts = [(x,y)]
    for _ in range(random.randint(5,14)):
        x += random.randint(-110,110)
        y += random.randint(30,155)
        pts.append((x,y))
    cd.line(pts, fill=random.randint(130,245), width=random.randint(1,4))
    if len(pts) > 3:
        bx,by = random.choice(pts[1:-1])
        branch = [(bx,by)]
        for _ in range(random.randint(2,5)):
            bx += random.randint(-75,75)
            by += random.randint(15,80)
            branch.append((bx,by))
        cd.line(branch, fill=random.randint(90,190), width=random.randint(1,3))
cr = cr.filter(ImageFilter.GaussianBlur(.65))
cr.save(ASSET / "cracks.png", "PNG", compress_level=6)

# Fog map animated in shader.
fog = Image.effect_noise((1024,1024), 54).convert("L")
fog2 = Image.effect_noise((128,128), 42).resize((1024,1024), Image.Resampling.BICUBIC)
fog = Image.blend(fog, fog2, .62).filter(ImageFilter.GaussianBlur(7))
fog = ImageEnhance.Contrast(fog).enhance(1.24)
fog.save(ASSET / "fog.jpg", "JPEG", quality=94)

# Original gothic hero art for the home screen.
W,H = 1080,1920
hero = Image.new("RGB", (W,H), (6,8,12))
hd = ImageDraw.Draw(hero, "RGBA")
for yy in range(H):
    t = yy/H
    c = (int(15-9*t), int(18-10*t), int(24-12*t))
    hd.line((0,yy,W,yy), fill=c+(255,))
vp = (W//2, 820)
for xx in range(-100,1180,100): hd.line((vp[0],vp[1],xx,H), fill=(52,54,57,90), width=3)
for yy in range(900,H,120): hd.line((0,yy,W,yy), fill=(50,52,54,70), width=3)
for cx in (130,950):
    hd.rectangle((cx-78,470,cx+78,H), fill=(38,40,42,255))
    hd.rectangle((cx-100,440,cx+100,520), fill=(56,57,59,255))
    hd.rectangle((cx-108,H-230,cx+108,H), fill=(29,30,32,255))
hd.pieslice((170,80,910,980),180,360,fill=(45,46,49,255))
hd.rectangle((170,520,910,1010),fill=(45,46,49,255))
hd.pieslice((265,210,815,930),180,360,fill=(5,8,13,255))
hd.rectangle((265,570,815,1080),fill=(5,8,13,255))
hd.polygon([(540,260),(400,520),(400,1000),(680,1000),(680,520)], fill=(11,22,31,255))
for xx in (445,540,635): hd.line((xx,535,xx,990), fill=(73,82,88,220), width=9)
hd.line((402,780,678,780), fill=(73,82,88,220), width=9)
light = Image.new("RGBA", (W,H), (0,0,0,0))
ld = ImageDraw.Draw(light)
for tx,ty in ((260,860),(820,860)):
    for r,a in ((250,10),(170,18),(105,34),(45,72)):
        ld.ellipse((tx-r,ty-r,tx+r,ty+r), fill=(255,130,42,a))
    ld.polygon([(tx,ty-48),(tx-15,ty+38),(tx+15,ty+38)], fill=(255,193,86,235))
light = light.filter(ImageFilter.GaussianBlur(30))
hero = Image.alpha_composite(hero.convert("RGBA"), light)
mist = Image.new("RGBA", (W,H), (0,0,0,0))
md = ImageDraw.Draw(mist)
for _ in range(44):
    cx=random.randint(-200,W+200); cy=random.randint(980,H); rx=random.randint(160,440); ry=random.randint(25,85)
    md.ellipse((cx-rx,cy-ry,cx+rx,cy+ry), fill=(135,150,160,random.randint(4,14)))
mist = mist.filter(ImageFilter.GaussianBlur(38))
hero = Image.alpha_composite(hero, mist).convert("RGB")
film = Image.effect_noise((W,H),24).convert("L")
film_rgb = ImageOps.colorize(film,(0,0,0),(35,35,35)).convert("RGB")
hero = Image.blend(hero, film_rgb, .09)
hero = ImageEnhance.Contrast(hero).enhance(1.10)
hero.save(ASSET / "gothic_hero.jpg", "JPEG", quality=96, subsampling=0)

# Launcher icon.
I = 512
icon = Image.new("RGBA", (I,I), (8,10,14,255))
it = ImageDraw.Draw(icon, "RGBA")
it.ellipse((28,28,484,484), outline=(195,160,87,110), width=7)
it.rectangle((105,225,407,420), fill=(53,56,62,255))
for x in (90,188,300,398):
    it.rectangle((x,155,x+72,420), fill=(64,67,72,255))
    it.polygon([(x-6,155),(x+36,90),(x+78,155)], fill=(78,80,85,255))
it.rectangle((220,305,292,420), fill=(8,10,14,255))
it.ellipse((223,197,289,263), fill=(213,178,94,190))
icon.save(DRAWABLE / "icon.png", "PNG", compress_level=5)

cfg = {
    "id":"gothic_castle",
    "name":"Готический замок",
    "version":"1.0",
    "renderer":{"stoneAmount":0.72,"stoneScale":3.5,"detail":0.24,"cracks":0.25,"grunge":0.27,"fog":0.17,"vignette":0.35,"motionAnchor":0.10}
}
(ASSET / "world.json").write_text(json.dumps(cfg,ensure_ascii=False,indent=2), encoding="utf-8")

for p in sorted(ASSET.iterdir()): print(p.name, p.stat().st_size)
print("icon.png", (DRAWABLE / "icon.png").stat().st_size)
