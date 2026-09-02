from pathlib import Path

p=Path('app/src/main/java/com/roomvision/demo/GothicArView.java')
s=p.read_text(encoding='utf-8').replace('RoomVisionMatrixFlow518','RoomVisionSurfaceZeroNoGrid521')
a=s.index('    private static final String BG_FS =')
b=s.index('\n\n    // WORLD coordinates',a)
fs='''    private static final String BG_FS =
"#extension GL_OES_EGL_image_external : require\\n"+
"precision mediump float;\\n"+
"uniform samplerExternalOES uCamera; uniform vec2 uViewport; uniform float uTime; varying vec2 vTexCoord;\\n"+
"float L(vec3 c){return dot(c,vec3(.299,.587,.114));}\\n"+
"float H(float n){return fract(sin(n*127.1+17.7)*43758.5453123);}\\n"+
"float one(vec2 p){float stem=1.0-smoothstep(.045,.105,abs(p.x-.07));stem*=step(-.43,p.y)*step(p.y,.43);float foot=1.0-smoothstep(.045,.105,abs(p.y+.40));foot*=step(-.22,p.x)*step(p.x,.25);return max(stem,foot);}\\n"+
"float zeroGlyph(vec2 p){vec2 q=p/vec2(.41,.49);float r=length(q);return 1.0-smoothstep(.075,.155,abs(r-.76));}\\n"+
"float zeroGlow(vec2 p){vec2 q=p/vec2(.41,.49);float r=length(q);return 1.0-smoothstep(.16,.31,abs(r-.76));}\\n"+
"void main(){\\n"+
" vec3 cam=texture2D(uCamera,vTexCoord).rgb; float y=clamp(L(cam),0.0,1.0);\\n"+
" vec2 texel=1.0/max(uViewport,vec2(1.0));\\n"+
" float yl=L(texture2D(uCamera,vTexCoord-vec2(texel.x,0.0)).rgb); float yr=L(texture2D(uCamera,vTexCoord+vec2(texel.x,0.0)).rgb);\\n"+
" float yd=L(texture2D(uCamera,vTexCoord-vec2(0.0,texel.y)).rgb); float yu=L(texture2D(uCamera,vTexCoord+vec2(0.0,texel.y)).rgb);\\n"+
" float gx=yr-yl; float gy=yu-yd; float e=clamp(length(vec2(gx,gy))*8.6,0.0,1.0); float edge=smoothstep(.10,.34,e);\\n"+
" float flat=1.0-edge;\\n"+
" vec2 px=vTexCoord*uViewport;\\n"+
" px.x += gx*38.0 + sin((vTexCoord.y*10.0+y*2.3)*6.2831)*5.0;\\n"+
" px.y += gy*30.0 + (y-.5)*16.0;\\n"+
" float cellW=42.0; float cellH=58.0; float baseRow=floor((px.y+uTime*118.0)/cellH);\\n"+
" float rowShift=(H(baseRow*13.0)-.5)*30.0; float baseCol=floor((px.x+rowShift)/cellW);\\n"+
" float speed=.84+H(baseCol*31.0)*.58; float fy=px.y+uTime*(92.0+54.0*speed);\\n"+
" float row=floor(fy/cellH); float rowJ=(H(row*17.0+baseCol*7.0)-.5)*18.0;\\n"+
" float col=floor((px.x+rowJ)/cellW);\\n"+
" float jx=(H(col*19.0+row*11.0)-.5)*20.0; float jy=(H(col*7.0+row*29.0)-.5)*13.0;\\n"+
" float sx=.88+H(col*23.0+row*5.0)*.28; float sy=.90+H(col*3.0+row*37.0)*.24;\\n"+
" vec2 center=vec2((col+.5)*cellW-rowJ+jx,(row+.5)*cellH+jy);\\n"+
" vec2 q=(vec2(px.x,fy)-center)/vec2(cellW*sx,cellH*sy);\\n"+
" float keep=step(.18,H(col*43.0+row*61.0)); float zg=zeroGlow(q)*keep; float z=zeroGlyph(q)*keep;\\n"+
" float surface=flat*(.76+.24*pow(y,.55)); float pulse=.88+.12*sin(uTime*3.1+row*.77+col*.41);\\n"+
" vec2 tangent=normalize(vec2(-gy,gx)+vec2(.0001,.0001)); float along=dot(px,tangent); float edgePhase=fract(along/37.0)-.5;\\n"+
" vec2 edgeQ=vec2(edgePhase,(fract((px.y+px.x*.17)/52.0)-.5)); float edgeOne=one(edgeQ)*edge;\\n"+
" vec3 room=mix(vec3(.065,.18,.16),vec3(.27,.55,.40),pow(y,.70)); room+=vec3(.035,.075,.060)*(1.0-edge);\\n"+
" vec3 zeroCol=mix(vec3(.10,.92,.28),vec3(.66,1.00,.72),pow(y,.50)); vec3 glowCol=vec3(.05,.58,.20);\\n"+
" vec3 edgeCol=mix(vec3(.30,.90,1.00),vec3(1.00,1.00,1.00),y);\\n"+
" vec3 col=room; col+=glowCol*zg*surface*.75; col+=zeroCol*z*surface*pulse*1.85; col+=edgeCol*edgeOne*1.70;\\n"+
" col+=vec3(.04,.22,.24)*edge*.42; col=(col-.5)*1.06+.58;\\n"+
" gl_FragColor=vec4(clamp(col,0.0,1.0),1.0);\\n"+
"}\\n";'''
s=s[:a]+fs+s[b:]
s=s.replace('MATRIX FLOW • EDGES HOLD • 0/1 FALL • LIVE','SURFACE ZERO • NO SCREEN GRID • LIVE')
p.write_text(s,encoding='utf-8')

g=Path('app/build.gradle.kts')
x=g.read_text(encoding='utf-8').replace('versionCode = 68','versionCode = 71').replace('versionName = "5.18.0"','versionName = "5.21.0"')
g.write_text(x,encoding='utf-8')
Path('app/src/main/res/values/strings.xml').write_text('<resources>\n<string name="app_name">Room Vision 5.21 SURFACE ZERO</string>\n</resources>\n',encoding='utf-8')

s=p.read_text(encoding='utf-8')
fa=s.index('    @Override public void onDrawFrame(GL10 gl) {')
fb=s.index('\n\n    /** Reconstructs',fa)
frame=s[fa:fb]
assert 'drawBackground();' in frame
assert 'acquireDepthImage16Bits' not in frame
assert 'drawDepthMaterial' not in frame
assert 'uniform float uTime;' in s
assert 'vec2 px=vTexCoord*uViewport;' in s
assert 'float jx=' in s and 'float jy=' in s
assert 'zeroGlyph(q)*keep' in s
assert 'SURFACE ZERO • NO SCREEN GRID • LIVE' in s
assert 'gl_FragCoord.xy/vec2' not in fs
assert 'versionCode = 71' in g.read_text(encoding='utf-8')
assert 'versionName = "5.21.0"' in g.read_text(encoding='utf-8')
print('SURFACE_ZERO_NOGRID_521_SOURCE_GATE=PASS')
