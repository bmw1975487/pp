from pathlib import Path

p = Path('app/src/main/java/com/roomvision/demo/GothicArView.java')
s = p.read_text(encoding='utf-8').replace('RoomVisionSurfaceZeroNoGrid521', 'RoomVisionColorOnes522')

a = s.index('    private static final String BG_FS =')
b = s.index('\n\n    // WORLD coordinates', a)

fs = '''    private static final String BG_FS =
"#extension GL_OES_EGL_image_external : require\\n"+
"precision mediump float;\\n"+
"uniform samplerExternalOES uCamera; uniform vec2 uViewport; uniform float uTime; varying vec2 vTexCoord;\\n"+
"float L(vec3 c){return dot(c,vec3(.299,.587,.114));}\\n"+
"float H(float n){return fract(sin(n*127.1+17.7)*43758.5453123);}\\n"+
"float oneGlyph(vec2 p){\\n"+
" float stem=1.0-smoothstep(.055,.115,abs(p.x-.055)); stem*=step(-.42,p.y)*step(p.y,.43);\\n"+
" float cap=1.0-smoothstep(.050,.110,abs((p.y-.30)-(.58*(p.x+.12)))); cap*=step(-.34,p.x)*step(p.x,.09)*step(.10,p.y)*step(p.y,.44);\\n"+
" float foot=1.0-smoothstep(.050,.115,abs(p.y+.40)); foot*=step(-.23,p.x)*step(p.x,.28);\\n"+
" return max(stem,max(cap,foot));\\n"+
"}\\n"+
"void main(){\\n"+
" vec3 cam=texture2D(uCamera,vTexCoord).rgb; float y=clamp(L(cam),0.0,1.0);\\n"+
" vec2 texel=1.0/max(uViewport,vec2(1.0));\\n"+
" float yl=L(texture2D(uCamera,vTexCoord-vec2(texel.x,0.0)).rgb); float yr=L(texture2D(uCamera,vTexCoord+vec2(texel.x,0.0)).rgb);\\n"+
" float yd=L(texture2D(uCamera,vTexCoord-vec2(0.0,texel.y)).rgb); float yu=L(texture2D(uCamera,vTexCoord+vec2(0.0,texel.y)).rgb);\\n"+
" float gx=yr-yl; float gy=yu-yd; float e=clamp(length(vec2(gx,gy))*10.2,0.0,1.0);\\n"+
" float edge=smoothstep(.085,.31,e);\\n"+
" vec2 grad=vec2(gx,gy); float glen=max(length(grad),.0001); vec2 normal=grad/glen; vec2 tangent=vec2(-normal.y,normal.x);\\n"+
" vec2 px=vTexCoord*uViewport;\\n"+
" px += grad*vec2(34.0,30.0);\\n"+
" px.x += sin((vTexCoord.y*9.0+y*2.6)*6.2831)*4.0;\\n"+
" px.y += cos((vTexCoord.x*7.0+y*1.8)*6.2831)*3.0;\\n"+
" float cellW=34.0; float cellH=49.0;\\n"+
" float row=floor((px.y+(H(floor(px.x/67.0)*17.0)-.5)*15.0)/cellH);\\n"+
" float rowShift=(H(row*13.0)-.5)*23.0 + gy*44.0;\\n"+
" float col=floor((px.x+rowShift)/cellW);\\n"+
" float jx=(H(col*19.0+row*11.0)-.5)*13.0; float jy=(H(col*7.0+row*29.0)-.5)*11.0;\\n"+
" vec2 center=vec2((col+.5)*cellW-rowShift+jx,(row+.5)*cellH+jy);\\n"+
" vec2 q=(px-center)/vec2(cellW,cellH);\\n"+
" float ang=.30*atan(tangent.y,tangent.x); float ca=cos(ang), sa=sin(ang); q=mat2(ca,-sa,sa,ca)*q;\\n"+
" float glyph=oneGlyph(q*1.08);\\n"+
" float keep=step(.10,H(col*41.0+row*59.0)); float one=glyph*edge*keep;\\n"+
" float maxc=max(cam.r,max(cam.g,cam.b)); vec3 hue=cam/max(maxc,.12);\\n"+
" vec3 oneCol=mix(vec3(.96),clamp(hue,0.0,1.0),smoothstep(.07,.22,maxc)); oneCol=clamp(oneCol*(.92+.22*maxc),0.0,1.0);\\n"+
" vec3 base=floor(cam*7.0)/7.0; base=mix(base,vec3(y),.08); base=pow(clamp(base,0.0,1.0),vec3(.92))*.72;\\n"+
" float glow=smoothstep(.055,.26,e)*glyph*keep;\\n"+
" vec3 colOut=base; colOut+=oneCol*glow*.28; colOut=mix(colOut,oneCol,clamp(one*1.65,0.0,1.0));\\n"+
" gl_FragColor=vec4(clamp(colOut,0.0,1.0),1.0);\\n"+
"}\\n";'''

s = s[:a] + fs + s[b:]
s = s.replace('SURFACE ZERO • NO SCREEN GRID • LIVE', 'COLOR ONES • IMAGE EDGES • LIVE')
p.write_text(s, encoding='utf-8')

g = Path('app/build.gradle.kts')
x = g.read_text(encoding='utf-8')
x = x.replace('versionCode = 71', 'versionCode = 72')
x = x.replace('versionName = "5.21.0"', 'versionName = "5.22.0"')
g.write_text(x, encoding='utf-8')

Path('app/src/main/res/values/strings.xml').write_text(
    '<resources>\n<string name="app_name">Cartoon Ones 5.22</string>\n</resources>\n',
    encoding='utf-8'
)

s = p.read_text(encoding='utf-8')
fa = s.index('    @Override public void onDrawFrame(GL10 gl) {')
fb = s.index('\n\n    /** Reconstructs', fa)
frame = s[fa:fb]
assert 'drawBackground();' in frame
assert 'acquireDepthImage16Bits' not in frame
assert 'drawDepthMaterial' not in frame
assert 'uniform float uTime;' in s
assert 'oneGlyph' in s
assert 'vec3 hue=cam/max(maxc,.12);' in s
assert 'COLOR ONES • IMAGE EDGES • LIVE' in s
assert 'gl_FragCoord.xy/vec2' not in fs
assert 'zeroGlyph' not in fs
assert 'versionCode = 72' in g.read_text(encoding='utf-8')
assert 'versionName = "5.22.0"' in g.read_text(encoding='utf-8')
print('COLOR_ONES_522_SOURCE_GATE=PASS')
