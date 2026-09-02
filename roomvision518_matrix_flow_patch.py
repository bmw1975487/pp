from pathlib import Path

p=Path('app/src/main/java/com/roomvision/demo/GothicArView.java')
s=p.read_text(encoding='utf-8').replace('RoomVisionBinarySurface517','RoomVisionMatrixFlow518')
a=s.index('    private static final String BG_FS =')
b=s.index('\n\n    // WORLD coordinates',a)
fs='''    private static final String BG_FS =
"#extension GL_OES_EGL_image_external : require\\n"+
"precision mediump float;\\n"+
"uniform samplerExternalOES uCamera; uniform vec2 uViewport; uniform float uTime; varying vec2 vTexCoord;\\n"+
"float L(vec3 c){return dot(c,vec3(.299,.587,.114));}\\n"+
"float H(float n){return fract(sin(n*127.1)*43758.5453123);}\\n"+
"float one(vec2 p){float s=1.0-smoothstep(.040,.090,abs(p.x-.08));s*=step(-.40,p.y)*step(p.y,.40);float f=1.0-smoothstep(.040,.090,abs(p.y+.38));f*=step(-.20,p.x)*step(p.x,.22);return max(s,f);}\\n"+
"float zero(vec2 p){vec2 q=p/vec2(.37,.47);float r=length(q);return 1.0-smoothstep(.095,.185,abs(r-.76));}\\n"+
"void main(){\\n"+
" vec3 c=texture2D(uCamera,vTexCoord).rgb; float y=L(c);\\n"+
" vec2 t=1.0/max(uViewport,vec2(1.0));\\n"+
" float gx=L(texture2D(uCamera,vTexCoord+vec2(t.x,0)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(t.x,0)).rgb);\\n"+
" float gy=L(texture2D(uCamera,vTexCoord+vec2(0,t.y)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(0,t.y)).rgb);\\n"+
" float e=clamp(length(vec2(gx,gy))*7.8,0.0,1.0); float edge=smoothstep(.11,.40,e);\\n"+
" float surfaceMask=1.0-edge*.92;\\n"+
" float cw=19.0; float ch=26.0; float colId=floor(gl_FragCoord.x/cw);\\n"+
" float speed=.70+H(colId)*1.55; float offset=H(colId+17.0)*41.0;\\n"+
" float flowY=(gl_FragCoord.y-uTime*62.0*speed)/ch+offset;\\n"+
" vec2 flowCell=vec2(gl_FragCoord.x/cw,flowY); vec2 fq=fract(flowCell)-.5; vec2 fid=floor(flowCell);\\n"+
" float rnd=H(fid.x*19.0+fid.y*7.0); float choose=step(.48,fract(rnd+y*.83));\\n"+
" float z=zero(fq); float o=one(fq); float flowGlyph=mix(z,o,choose);\\n"+
" float pulse=.62+.38*sin(uTime*(2.0+H(colId)*2.2)+fid.y*.73); pulse=.55+.45*max(pulse,0.0);\\n"+
" float density=clamp(.30+pow(y,.68)*.72,0.0,1.0); float flow=flowGlyph*surfaceMask*density*pulse;\\n"+
" vec2 eq=fract(gl_FragCoord.xy/vec2(23.0,31.0))-.5; float edgeOne=one(eq)*edge;\\n"+
" vec3 bg=vec3(.003,.014,.020)+vec3(.010,.055,.060)*y;\\n"+
" vec3 zeroCol=mix(vec3(.02,.30,.12),vec3(.12,1.00,.42),y);\\n"+
" vec3 oneCol=mix(vec3(.03,.48,.72),vec3(.30,.98,1.00),y);\\n"+
" vec3 glyphCol=mix(zeroCol,oneCol,choose); vec3 col=bg+glyphCol*flow*1.28;\\n"+
" vec3 edgeCol=mix(vec3(.25,.90,1.00),vec3(1.00,1.00,1.00),y); col+=edgeCol*edgeOne*1.55;\\n"+
" float edgeGlow=edge*smoothstep(.20,.50,1.0-length(eq)*1.5); col+=vec3(.03,.22,.30)*edgeGlow*.55;\\n"+
" gl_FragColor=vec4(clamp(col,0.0,1.0),1.0);\\n"+
"}\\n";'''
s=s[:a]+fs+s[b:]
s=s.replace('BINARY SURFACE • 1=EDGES • 0=SURFACES • LIVE','MATRIX FLOW • EDGES HOLD • 0/1 FALL • LIVE')
needle='''        GLES20.glUniform2f(GLES20.glGetUniformLocation(bgProgram, "uViewport"), Math.max(1, viewportWidth), Math.max(1, viewportHeight));'''
repl=needle+'''\n        GLES20.glUniform1f(GLES20.glGetUniformLocation(bgProgram, "uTime"), android.os.SystemClock.uptimeMillis() * 0.001f);'''
if needle not in s:
    raise SystemExit('uViewport drawBackground line not found')
s=s.replace(needle,repl)
p.write_text(s,encoding='utf-8')

g=Path('app/build.gradle.kts')
x=g.read_text(encoding='utf-8').replace('versionCode = 67','versionCode = 68').replace('versionName = "5.17.0"','versionName = "5.18.0"')
g.write_text(x,encoding='utf-8')
Path('app/src/main/res/values/strings.xml').write_text('<resources>\n<string name="app_name">Room Vision 5.18 MATRIX FLOW</string>\n</resources>\n',encoding='utf-8')

s=p.read_text(encoding='utf-8')
fa=s.index('    @Override public void onDrawFrame(GL10 gl) {')
fb=s.index('\n\n    /** Reconstructs',fa)
frame=s[fa:fb]
assert 'drawBackground();' in frame
assert 'acquireDepthImage16Bits' not in frame
assert 'drawDepthMaterial' not in frame
assert 'uniform float uTime;' in s
assert 'float flowY=(gl_FragCoord.y-uTime*62.0*speed)' in s
assert 'float edgeOne=one(eq)*edge;' in s
assert 'vec3 zeroCol=' in s and 'vec3 oneCol=' in s
assert 'MATRIX FLOW • EDGES HOLD • 0/1 FALL • LIVE' in s
assert 'GLES20.glUniform1f(GLES20.glGetUniformLocation(bgProgram, "uTime")' in s
assert 'versionCode = 68' in g.read_text(encoding='utf-8')
assert 'versionName = "5.18.0"' in g.read_text(encoding='utf-8')
print('MATRIX_FLOW_518_SOURCE_GATE=PASS')
