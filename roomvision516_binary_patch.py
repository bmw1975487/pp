from pathlib import Path
p=Path('app/src/main/java/com/roomvision/demo/GothicArView.java')
s=p.read_text(encoding='utf-8').replace('RoomVisionInstantLab515','RoomVisionBinary516')
a=s.index('    private static final String BG_FS =')
b=s.index('\n\n    // WORLD coordinates',a)
fs='''    private static final String BG_FS =
"#extension GL_OES_EGL_image_external : require\\n"+
"precision mediump float;\\n"+
"uniform samplerExternalOES uCamera; uniform vec2 uViewport; varying vec2 vTexCoord;\\n"+
"float L(vec3 c){return dot(c,vec3(.299,.587,.114));}\\n"+
"float one(vec2 p){float s=1.0-smoothstep(.05,.10,abs(p.x-.08));s*=step(-.38,p.y)*step(p.y,.40);float f=1.0-smoothstep(.05,.10,abs(p.y+.38));f*=step(-.18,p.x)*step(p.x,.20);return max(s,f);}\\n"+
"float zero(vec2 p){vec2 q=p/vec2(.34,.46);float r=length(q);return 1.0-smoothstep(.12,.22,abs(r-.78));}\\n"+
"void main(){vec3 c=texture2D(uCamera,vTexCoord).rgb;float y=L(c);vec2 t=1.0/max(uViewport,vec2(1.0));float gx=L(texture2D(uCamera,vTexCoord+vec2(t.x,0)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(t.x,0)).rgb);float gy=L(texture2D(uCamera,vTexCoord+vec2(0,t.y)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(0,t.y)).rgb);float e=clamp(length(vec2(gx,gy))*4.5,0.0,1.0);vec2 cell=gl_FragCoord.xy/vec2(10.0,14.0);vec2 q=fract(cell)-.5;vec2 id=floor(cell);float h=fract(sin(dot(id,vec2(12.9898,78.233)))*43758.5453);float pick=step(.5,fract(y*1.7+h*.83+e*.7));float g=mix(zero(q),one(q),pick);float k=clamp(.16+pow(y,.72)*.52+e*.95,0.0,1.0);vec3 bg=vec3(.004,.015,.022)+vec3(0,.018,.024)*y;vec3 dc=mix(vec3(.01,.12,.14),vec3(.20,.96,.84),clamp(e*.8+y*.5,0.0,1.0));vec3 o=mix(bg,dc,g*k);gl_FragColor=vec4(clamp(o,0.0,1.0),1.0);}\\n";'''
s=s[:a]+fs+s[b:]
s=s.replace('uniform vec2 uTexel;','uniform vec2 uViewport;')
s=s.replace('GLES20.glUniform2f(GLES20.glGetUniformLocation(bgProgram, "uTexel"),\n                1.0f / Math.max(1, viewportWidth), 1.0f / Math.max(1, viewportHeight));','GLES20.glUniform2f(GLES20.glGetUniformLocation(bgProgram, "uViewport"), Math.max(1, viewportWidth), Math.max(1, viewportHeight));')
s=s.replace('INSTANT SECRET LAB • FULL FRAME • LIVE','BINARY VISION • 0/1 • FULL FRAME • LIVE')
p.write_text(s,encoding='utf-8')
g=Path('app/build.gradle.kts');x=g.read_text(encoding='utf-8').replace('versionCode = 65','versionCode = 66').replace('versionName = "5.15.0"','versionName = "5.16.0"');g.write_text(x,encoding='utf-8')
Path('app/src/main/res/values/strings.xml').write_text('<resources>\n<string name="app_name">Room Vision 5.16 BINARY</string>\n</resources>\n',encoding='utf-8')
s=p.read_text(encoding='utf-8');fa=s.index('    @Override public void onDrawFrame(GL10 gl) {');fb=s.index('\n\n    /** Reconstructs',fa);frame=s[fa:fb]
assert 'drawBackground();' in frame and 'acquireDepthImage16Bits' not in frame and 'drawDepthMaterial' not in frame
assert 'float one(vec2 p)' in s and 'float zero(vec2 p)' in s and 'BINARY VISION • 0/1' in s
print('BINARY_516_SOURCE_GATE=PASS')
