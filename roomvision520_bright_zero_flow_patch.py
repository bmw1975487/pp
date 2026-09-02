from pathlib import Path

p=Path('app/src/main/java/com/roomvision/demo/GothicArView.java')
s=p.read_text(encoding='utf-8').replace('RoomVisionMatrixFlow518','RoomVisionBrightZeroFlow520')
a=s.index('    private static final String BG_FS =')
b=s.index('\n\n    // WORLD coordinates',a)
fs='''    private static final String BG_FS =
"#extension GL_OES_EGL_image_external : require\\n"+
"precision mediump float;\\n"+
"uniform samplerExternalOES uCamera; uniform vec2 uViewport; uniform float uTime; varying vec2 vTexCoord;\\n"+
"float L(vec3 c){return dot(c,vec3(.299,.587,.114));}\\n"+
"float H(float n){return fract(sin(n*127.1)*43758.5453123);}\\n"+
"float one(vec2 p){float stem=1.0-smoothstep(.050,.115,abs(p.x-.06));stem*=step(-.42,p.y)*step(p.y,.42);float foot=1.0-smoothstep(.050,.115,abs(p.y+.40));foot*=step(-.20,p.x)*step(p.x,.24);return max(stem,foot);}\\n"+
"float zeroCore(vec2 p){vec2 q=p/vec2(.40,.48);float r=length(q);return 1.0-smoothstep(.085,.170,abs(r-.76));}\\n"+
"float zeroGlow(vec2 p){vec2 q=p/vec2(.40,.48);float r=length(q);return 1.0-smoothstep(.18,.34,abs(r-.76));}\\n"+
"void main(){\\n"+
" vec3 cam=texture2D(uCamera,vTexCoord).rgb; float y=clamp(L(cam),0.0,1.0);\\n"+
" vec2 t=1.0/max(uViewport,vec2(1.0));\\n"+
" float gx=L(texture2D(uCamera,vTexCoord+vec2(t.x,0)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(t.x,0)).rgb);\\n"+
" float gy=L(texture2D(uCamera,vTexCoord+vec2(0,t.y)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(0,t.y)).rgb);\\n"+
" float e=clamp(length(vec2(gx,gy))*8.8,0.0,1.0); float edge=smoothstep(.10,.34,e);\\n"+
" float surfaceMask=1.0-edge*.88;\\n"+
" float cw=27.0; float ch=39.0; float colId=floor(gl_FragCoord.x/cw);\\n"+
" float speed=.82+H(colId)*1.45; float offset=H(colId+23.0)*53.0;\\n"+
" float flowY=(gl_FragCoord.y-uTime*96.0*speed)/ch+offset;\\n"+
" vec2 fc=vec2(gl_FragCoord.x/cw,flowY); vec2 fq=fract(fc)-.5; vec2 fid=floor(fc);\\n"+
" float z=zeroCore(fq); float zg=zeroGlow(fq);\\n"+
" float stream=.72+.28*pow(1.0-fract(flowY*.115+H(colId)*.83),2.0);\\n"+
" float flicker=.86+.14*sin(uTime*(2.8+H(colId)*1.7)+fid.y*.77);\\n"+
" float zeroStrength=surfaceMask*(.82+.18*pow(y,.55))*stream*flicker;\\n"+
" vec2 eq=fract(gl_FragCoord.xy/vec2(34.0,47.0))-.5; float edgeOne=one(eq)*edge;\\n"+
" vec3 room=mix(vec3(.035,.115,.105),vec3(.18,.42,.30),pow(y,.68)); room+=vec3(y)*.10;\\n"+
" vec3 zeroCol=mix(vec3(.08,.88,.24),vec3(.58,1.00,.70),pow(y,.55));\\n"+
" vec3 zeroGlowCol=vec3(.04,.50,.18);\\n"+
" vec3 edgeCol=mix(vec3(.22,.86,1.00),vec3(.92,1.00,1.00),y);\\n"+
" vec3 col=room; col+=zeroGlowCol*zg*zeroStrength*.72; col+=zeroCol*z*zeroStrength*1.70;\\n"+
" col+=edgeCol*edgeOne*1.85; col+=vec3(.04,.28,.34)*edge*.38;\\n"+
" col=(col-.5)*1.12+.56;\\n"+
" gl_FragColor=vec4(clamp(col,0.0,1.0),1.0);\\n"+
"}\\n";'''
s=s[:a]+fs+s[b:]
s=s.replace('MATRIX FLOW • EDGES HOLD • 0/1 FALL • LIVE','BRIGHT ZERO FLOW • BIG 0 FALL • 1 EDGES • LIVE')
p.write_text(s,encoding='utf-8')

g=Path('app/build.gradle.kts')
x=g.read_text(encoding='utf-8').replace('versionCode = 68','versionCode = 70').replace('versionName = "5.18.0"','versionName = "5.20.0"')
g.write_text(x,encoding='utf-8')
Path('app/src/main/res/values/strings.xml').write_text('<resources>\n<string name="app_name">Room Vision 5.20 BRIGHT ZERO FLOW</string>\n</resources>\n',encoding='utf-8')

s=p.read_text(encoding='utf-8')
fa=s.index('    @Override public void onDrawFrame(GL10 gl) {')
fb=s.index('\n\n    /** Reconstructs',fa)
frame=s[fa:fb]
assert 'drawBackground();' in frame
assert 'acquireDepthImage16Bits' not in frame
assert 'drawDepthMaterial' not in frame
assert 'uniform float uTime;' in s
assert 'float flowY=(gl_FragCoord.y-uTime*96.0*speed)' in s
assert 'float z=zeroCore(fq);' in s
assert 'vec3 zeroCol=' in s
assert 'BRIGHT ZERO FLOW • BIG 0 FALL • 1 EDGES • LIVE' in s
assert 'versionCode = 70' in g.read_text(encoding='utf-8')
assert 'versionName = "5.20.0"' in g.read_text(encoding='utf-8')
print('BRIGHT_ZERO_FLOW_520_SOURCE_GATE=PASS')
