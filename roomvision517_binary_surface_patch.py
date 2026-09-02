from pathlib import Path

p=Path('app/src/main/java/com/roomvision/demo/GothicArView.java')
s=p.read_text(encoding='utf-8').replace('RoomVisionBinary516','RoomVisionBinarySurface517')
a=s.index('    private static final String BG_FS =')
b=s.index('\n\n    // WORLD coordinates',a)
fs='''    private static final String BG_FS =
"#extension GL_OES_EGL_image_external : require\\n"+
"precision mediump float;\\n"+
"uniform samplerExternalOES uCamera; uniform vec2 uViewport; varying vec2 vTexCoord;\\n"+
"float L(vec3 c){return dot(c,vec3(.299,.587,.114));}\\n"+
"float one(vec2 p){float s=1.0-smoothstep(.045,.095,abs(p.x-.08));s*=step(-.39,p.y)*step(p.y,.40);float f=1.0-smoothstep(.045,.095,abs(p.y+.38));f*=step(-.19,p.x)*step(p.x,.21);return max(s,f);}\\n"+
"float zero(vec2 p){vec2 q=p/vec2(.36,.47);float r=length(q);return 1.0-smoothstep(.10,.19,abs(r-.76));}\\n"+
"void main(){vec3 c=texture2D(uCamera,vTexCoord).rgb;float y=L(c);vec2 t=1.0/max(uViewport,vec2(1.0));float gx=L(texture2D(uCamera,vTexCoord+vec2(t.x,0)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(t.x,0)).rgb);float gy=L(texture2D(uCamera,vTexCoord+vec2(0,t.y)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(0,t.y)).rgb);float e=clamp(length(vec2(gx,gy))*7.0,0.0,1.0);vec2 cell=gl_FragCoord.xy/vec2(11.0,15.0);vec2 q=fract(cell)-.5;float z=zero(q);float o=one(q);float edge=smoothstep(.12,.42,e);float surface=z*(1.0-edge*.88);float line=o*edge;float surfLight=.55+.45*pow(y,.62);vec3 bg=vec3(.012,.045,.060)+vec3(.020,.085,.095)*y;vec3 zeroCol=mix(vec3(.08,.55,.62),vec3(.40,1.00,.88),y);vec3 oneCol=mix(vec3(.58,.94,1.00),vec3(1.00,1.00,1.00),y);vec3 col=bg+zeroCol*surface*surfLight;col+=oneCol*line*(.90+.55*edge);float glow=smoothstep(.20,.72,e)*(1.0-smoothstep(.22,.48,length(q)));col+=vec3(.05,.35,.42)*glow*.38;gl_FragColor=vec4(clamp(col,0.0,1.0),1.0);}\\n";'''
s=s[:a]+fs+s[b:]
s=s.replace('BINARY VISION • 0/1 • FULL FRAME • LIVE','BINARY SURFACE • 1=EDGES • 0=SURFACES • LIVE')
p.write_text(s,encoding='utf-8')

g=Path('app/build.gradle.kts')
x=g.read_text(encoding='utf-8').replace('versionCode = 66','versionCode = 67').replace('versionName = "5.16.0"','versionName = "5.17.0"')
g.write_text(x,encoding='utf-8')
Path('app/src/main/res/values/strings.xml').write_text('<resources>\n<string name="app_name">Room Vision 5.17 BINARY SURFACE</string>\n</resources>\n',encoding='utf-8')

s=p.read_text(encoding='utf-8')
fa=s.index('    @Override public void onDrawFrame(GL10 gl) {')
fb=s.index('\n\n    /** Reconstructs',fa)
frame=s[fa:fb]
assert 'drawBackground();' in frame
assert 'acquireDepthImage16Bits' not in frame
assert 'drawDepthMaterial' not in frame
assert 'float one(vec2 p)' in s and 'float zero(vec2 p)' in s
assert 'float surface=z*(1.0-edge*.88);' in s
assert 'float line=o*edge;' in s
assert 'BINARY SURFACE • 1=EDGES • 0=SURFACES • LIVE' in s
assert 'versionCode = 67' in g.read_text(encoding='utf-8')
assert 'versionName = "5.17.0"' in g.read_text(encoding='utf-8')
print('BINARY_SURFACE_517_SOURCE_GATE=PASS')
