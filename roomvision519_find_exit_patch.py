from pathlib import Path

p=Path('app/src/main/java/com/roomvision/demo/GothicArView.java')
s=p.read_text(encoding='utf-8').replace('RoomVisionMatrixFlow518','RoomVisionFindExit519')
s=s.replace('import android.view.Surface;','import android.view.Surface;\nimport android.view.MotionEvent;')

a=s.index('    private static final String BG_FS =')
b=s.index('\n\n    // WORLD coordinates',a)
fs='''    private static final String BG_FS =
"#extension GL_OES_EGL_image_external : require\\n"+
"precision mediump float;\\n"+
"uniform samplerExternalOES uCamera; uniform vec2 uViewport; uniform float uTime; uniform vec4 uTargetRect; uniform float uTargetActive; varying vec2 vTexCoord;\\n"+
"float L(vec3 c){return dot(c,vec3(.299,.587,.114));}\\n"+
"float H(float n){return fract(sin(n*127.1)*43758.5453123);}\\n"+
"float one(vec2 p){float s=1.0-smoothstep(.040,.090,abs(p.x-.08));s*=step(-.40,p.y)*step(p.y,.40);float f=1.0-smoothstep(.040,.090,abs(p.y+.38));f*=step(-.20,p.x)*step(p.x,.22);return max(s,f);}\\n"+
"float zero(vec2 p){vec2 q=p/vec2(.37,.47);float r=length(q);return 1.0-smoothstep(.095,.185,abs(r-.76));}\\n"+
"void main(){\\n"+
" vec3 c=texture2D(uCamera,vTexCoord).rgb; float y=L(c); vec2 t=1.0/max(uViewport,vec2(1.0));\\n"+
" float gx=L(texture2D(uCamera,vTexCoord+vec2(t.x,0)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(t.x,0)).rgb);\\n"+
" float gy=L(texture2D(uCamera,vTexCoord+vec2(0,t.y)).rgb)-L(texture2D(uCamera,vTexCoord-vec2(0,t.y)).rgb);\\n"+
" float e=clamp(length(vec2(gx,gy))*7.8,0.0,1.0); float edge=smoothstep(.11,.40,e); float surfaceMask=1.0-edge*.92;\\n"+
" float cw=19.0; float ch=26.0; float colId=floor(gl_FragCoord.x/cw); float speed=.70+H(colId)*1.55; float offset=H(colId+17.0)*41.0;\\n"+
" float flowY=(gl_FragCoord.y-uTime*62.0*speed)/ch+offset; vec2 flowCell=vec2(gl_FragCoord.x/cw,flowY); vec2 fq=fract(flowCell)-.5; vec2 fid=floor(flowCell);\\n"+
" float rnd=H(fid.x*19.0+fid.y*7.0); float choose=step(.48,fract(rnd+y*.83)); float z=zero(fq); float o=one(fq); float flowGlyph=mix(z,o,choose);\\n"+
" float pulse=.62+.38*sin(uTime*(2.0+H(colId)*2.2)+fid.y*.73); pulse=.55+.45*max(pulse,0.0); float density=clamp(.30+pow(y,.68)*.72,0.0,1.0); float flow=flowGlyph*surfaceMask*density*pulse;\\n"+
" vec2 eq=fract(gl_FragCoord.xy/vec2(23.0,31.0))-.5; float edgeOne=one(eq)*edge; vec3 bg=vec3(.003,.014,.020)+vec3(.010,.055,.060)*y;\\n"+
" vec3 zeroCol=mix(vec3(.02,.30,.12),vec3(.12,1.00,.42),y); vec3 oneCol=mix(vec3(.03,.48,.72),vec3(.30,.98,1.00),y); vec3 glyphCol=mix(zeroCol,oneCol,choose); vec3 col=bg+glyphCol*flow*1.28;\\n"+
" vec3 edgeCol=mix(vec3(.25,.90,1.00),vec3(1.00,1.00,1.00),y); col+=edgeCol*edgeOne*1.55; float edgeGlow=edge*smoothstep(.20,.50,1.0-length(eq)*1.5); col+=vec3(.03,.22,.30)*edgeGlow*.55;\\n"+
" vec2 uv=gl_FragCoord.xy/max(uViewport,vec2(1.0)); float inside=step(uTargetRect.x,uv.x)*step(uv.x,uTargetRect.z)*step(uTargetRect.y,uv.y)*step(uv.y,uTargetRect.w)*uTargetActive;\\n"+
" float rcw=28.0; float rch=38.0; float rcol=floor(gl_FragCoord.x/rcw); float rflow=(gl_FragCoord.y-uTime*(88.0+H(rcol)*58.0))/rch+H(rcol+31.0)*29.0; vec2 rq=fract(vec2(gl_FragCoord.x/rcw,rflow))-.5; float rz=zero(rq);\\n"+
" float rp=.68+.32*sin(uTime*3.6+rcol*.73); vec3 redBg=vec3(.035,.001,.003)+vec3(.09,.002,.002)*y; vec3 redZero=mix(vec3(.55,.01,.015),vec3(1.00,.12,.08),y); vec3 redScene=redBg+redZero*rz*(.88+.42*rp); col=mix(col,redScene,inside);\\n"+
" gl_FragColor=vec4(clamp(col,0.0,1.0),1.0);\\n"+
"}\\n";'''
s=s[:a]+fs+s[b:]
s=s.replace('MATRIX FLOW • EDGES HOLD • 0/1 FALL • LIVE','FIND EXIT • MATRIX ROOM • 3 ATTEMPTS')

field='''    private long lastStatusMs;'''
insert='''    private long lastStatusMs;\n    private final DoorTargetDetector doorDetector = new DoorTargetDetector();\n    private volatile float targetLeft, targetTop, targetRight, targetBottom;\n    private volatile boolean targetFound;\n    private volatile boolean exitUnlocked;\n    private volatile int attemptsLeft = 3;\n    private long nextDoorScanMs;\n    private volatile long gameResetAtMs;'''
if field not in s: raise SystemExit('field anchor missing')
s=s.replace(field,insert,1)

anchor='''    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {'''
touch='''    @Override public boolean onTouchEvent(MotionEvent event) {\n        if (event == null || event.getAction() != MotionEvent.ACTION_UP) return true;\n        if (exitUnlocked) return true;\n        long now = System.currentTimeMillis();\n        if (attemptsLeft <= 0 || gameResetAtMs > now) return true;\n        if (!targetFound) {\n            lastStatusMs = now;\n            status("НАЙДИ ВЫХОД • ДВЕРЬ ЕЩЁ СКАНИРУЕТСЯ");\n            return true;\n        }\n        float x = event.getX() / Math.max(1f, getWidth());\n        float y = event.getY() / Math.max(1f, getHeight());\n        boolean hit = x >= targetLeft && x <= targetRight && y >= targetTop && y <= targetBottom;\n        lastStatusMs = now;\n        if (hit) {\n            exitUnlocked = true;\n            status("ВЫХОД НАЙДЕН • ДВЕРЬ РАЗБЛОКИРОВАНА");\n        } else {\n            attemptsLeft--;\n            if (attemptsLeft > 0) {\n                status("НЕВЕРНО • ОСТАЛОСЬ ПОПЫТОК: " + attemptsLeft);\n            } else {\n                status("ВЫХОД НЕ НАЙДЕН • 3 ПОПЫТКИ ИСЧЕРПАНЫ");\n                targetFound = false;\n                doorDetector.reset();\n                gameResetAtMs = now + 1800L;\n            }\n        }\n        return true;\n    }\n\n'''+anchor
if anchor not in s: raise SystemExit('surface anchor missing')
s=s.replace(anchor,touch,1)

old='''            Frame frame = s.update();\n            updateBackgroundGeometry(frame);\n            // Instant full-frame stylization on EVERY live camera frame.\n            // No tracking wait, no depth image, no mesh accumulation, no pieces appearing later.\n            drawBackground();\n            statusThrottled("FIND EXIT • MATRIX ROOM • 3 ATTEMPTS");'''
new='''            Frame frame = s.update();\n            updateBackgroundGeometry(frame);\n            long nowMs = System.currentTimeMillis();\n            if (gameResetAtMs > 0L && nowMs >= gameResetAtMs) {\n                attemptsLeft = 3; gameResetAtMs = 0L; nextDoorScanMs = 0L;\n            }\n            if (!exitUnlocked && attemptsLeft > 0 && nowMs >= nextDoorScanMs) {\n                nextDoorScanMs = nowMs + 650L;\n                updateDoorTarget(frame);\n            }\n            drawBackground();\n            if (exitUnlocked) statusThrottled("ВЫХОД НАЙДЕН • КРАСНЫЕ 0 = ДВЕРЬ");\n            else if (targetFound) statusThrottled("НАЙДИ ВЫХОД • ПОПЫТКИ: " + attemptsLeft);\n            else statusThrottled("СКАНИРУЮ КОМНАТУ • ИЩУ ДВЕРЬ");'''
# 5.18 source comment remains, but status was replaced above; find a safer block if exact not present.
if old not in s:
    old2='''            Frame frame = s.update();\n            updateBackgroundGeometry(frame);\n            // Instant full-frame stylization on EVERY live camera frame.\n            // No tracking wait, no depth image, no mesh accumulation, no pieces appearing later.\n            drawBackground();\n            statusThrottled("MATRIX FLOW • EDGES HOLD • 0/1 FALL • LIVE");'''
    if old2 not in s: raise SystemExit('onDrawFrame anchor missing')
    old=old2
s=s.replace(old,new,1)

method_anchor='''    /** Reconstructs a discontinuity-safe triangle mesh in ARCore world coordinates. */'''
method='''    private void updateDoorTarget(Frame frame) {\n        Image image = null;\n        try {\n            image = frame.acquireCameraImage();\n            DoorTargetDetector.Result r = doorDetector.detect(frame, image);\n            if (r != null) {\n                targetLeft = r.left; targetTop = r.top; targetRight = r.right; targetBottom = r.bottom;\n                targetFound = true;\n            }\n        } catch (NotYetAvailableException ignored) {\n        } catch (Throwable t) {\n            Log.w(TAG, "door scan failed", t);\n        } finally {\n            if (image != null) try { image.close(); } catch (Throwable ignored) { }\n        }\n    }\n\n'''+method_anchor
if method_anchor not in s: raise SystemExit('method anchor missing')
s=s.replace(method_anchor,method,1)

needle='''        GLES20.glUniform1f(GLES20.glGetUniformLocation(bgProgram, "uTime"), android.os.SystemClock.uptimeMillis() * 0.001f);'''
add=needle+'''\n        float glBottom = 1.0f - targetBottom;\n        float glTop = 1.0f - targetTop;\n        GLES20.glUniform4f(GLES20.glGetUniformLocation(bgProgram, "uTargetRect"), targetLeft, glBottom, targetRight, glTop);\n        GLES20.glUniform1f(GLES20.glGetUniformLocation(bgProgram, "uTargetActive"), exitUnlocked ? 1.0f : 0.0f);'''
if needle not in s: raise SystemExit('time uniform anchor missing')
s=s.replace(needle,add,1)
p.write_text(s,encoding='utf-8')

g=Path('app/build.gradle.kts')
x=g.read_text(encoding='utf-8').replace('versionCode = 68','versionCode = 69').replace('versionName = "5.18.0"','versionName = "5.19.0"')
g.write_text(x,encoding='utf-8')
Path('app/src/main/res/values/strings.xml').write_text('<resources>\n<string name="app_name">Room Vision 5.19 FIND EXIT</string>\n</resources>\n',encoding='utf-8')

s=p.read_text(encoding='utf-8')
assert 'DoorTargetDetector' in s
assert 'attemptsLeft = 3' in s
assert 'ВЫХОД НАЙДЕН' in s
assert 'uTargetRect' in s and 'uTargetActive' in s
assert 'vec3 redZero=' in s
assert 'frame.acquireCameraImage()' in s
assert 'versionCode = 69' in g.read_text(encoding='utf-8')
assert 'versionName = "5.19.0"' in g.read_text(encoding='utf-8')
print('FIND_EXIT_519_SOURCE_GATE=PASS')
