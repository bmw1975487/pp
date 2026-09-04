from PIL import Image, ImageDraw, ImageFilter, ImageEnhance
from pathlib import Path
import math, random

ROOT = Path(__file__).resolve().parent
ASSET = ROOT / "app/src/main/assets/styles"
DRAWABLE = ROOT / "app/src/main/res/drawable"
ASSET.mkdir(parents=True, exist_ok=True)
DRAWABLE.mkdir(parents=True, exist_ok=True)
random.seed(20260831)
S = 512

# Original style-reference art generated specifically for this project.
# These are not copied from the reference APKs or from copyrighted paintings.

def van_gogh():
    im = Image.new("RGB", (S,S), (12,24,55)); d=ImageDraw.Draw(im,"RGBA")
    for _ in range(900):
        cx=random.randrange(S); cy=random.randrange(S); r=random.randrange(8,70)
        a=random.random()*math.tau; length=random.randrange(15,70)
        col=random.choice([(35,91,160,75),(74,133,196,65),(247,193,64,92),(241,224,137,70),(15,55,115,82)])
        x2=cx+math.cos(a)*length; y2=cy+math.sin(a)*length
        d.arc((cx-r,cy-r,cx+r,cy+r),random.randrange(360),random.randrange(360)+180,fill=col,width=random.randrange(2,7))
        d.line((cx,cy,x2,y2),fill=col,width=random.randrange(1,5))
    im=im.filter(ImageFilter.GaussianBlur(.35)); return ImageEnhance.Contrast(im).enhance(1.15)

def kandinsky():
    im=Image.new("RGB",(S,S),(237,225,196)); d=ImageDraw.Draw(im,"RGBA")
    colors=[(210,52,47,210),(42,81,147,210),(238,184,45,210),(24,24,22,210),(61,146,112,190)]
    for _ in range(80):
        x=random.randrange(-40,S); y=random.randrange(-40,S); r=random.randrange(10,105); c=random.choice(colors)
        if random.random()<.55: d.ellipse((x-r,y-r,x+r,y+r),outline=c,width=random.randrange(3,14))
        else: d.polygon([(x,y-r),(x+r,y+r),(x-r,y+r)],fill=c)
    for _ in range(55):
        d.line((random.randrange(S),random.randrange(S),random.randrange(S),random.randrange(S)),fill=random.choice(colors),width=random.randrange(2,9))
    return im.filter(ImageFilter.GaussianBlur(.25))

def cyberpunk():
    im=Image.new("RGB",(S,S),(6,5,19)); d=ImageDraw.Draw(im,"RGBA")
    for y in range(S):
        d.line((0,y,S,y),fill=(8+int(10*y/S),5,23+int(25*y/S),255))
    for _ in range(130):
        x=random.randrange(S); y=random.randrange(S); w=random.randrange(2,20); h=random.randrange(20,170)
        col=random.choice([(0,245,255,160),(255,20,181,170),(122,53,255,160),(255,183,32,130)])
        d.rectangle((x,y,x+w,min(S,y+h)),fill=col)
    for _ in range(45):
        y=random.randrange(S); col=random.choice([(0,245,255,150),(255,20,181,150)])
        d.line((0,y,S,y+random.randrange(-15,16)),fill=col,width=random.randrange(1,5))
    return im.filter(ImageFilter.GaussianBlur(1.2))

styles={"van_gogh.jpg":van_gogh(),"kandinsky.jpg":kandinsky(),"cyberpunk.jpg":cyberpunk()}
for name,im in styles.items(): im.save(ASSET/name,"JPEG",quality=94,subsampling=0)

I=512
icon=Image.new("RGBA",(I,I),(8,9,14,255)); d=ImageDraw.Draw(icon,"RGBA")
for r,c in [(220,(0,230,255,80)),(175,(255,33,183,85)),(125,(245,194,67,100))]: d.ellipse((256-r,256-r,256+r,256+r),outline=c,width=8)
d.rounded_rectangle((92,150,420,362),42,fill=(18,20,29,255),outline=(230,232,238,100),width=5)
d.ellipse((173,173,339,339),fill=(6,8,14,255),outline=(0,235,255,200),width=8)
d.ellipse((222,222,290,290),fill=(255,40,181,220))
icon.save(DRAWABLE/"icon.png","PNG",compress_level=5)

for p in sorted(ASSET.iterdir()): print(p.name,p.stat().st_size)
print("icon.png",(DRAWABLE/"icon.png").stat().st_size)
