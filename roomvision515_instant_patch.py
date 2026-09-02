from pathlib import Path

p=Path('app/src/main/java/com/roomvision/demo/GothicArView.java')
s=p.read_text(encoding='utf-8')
s=s.replace('private static final String TAG = "RoomVisionLab514";', 'private static final String TAG = "RoomVisionInstantLab515";')

start=s.index('    private static final String BG_FS =')
end=s.index('\n\n    // WORLD coordinates', start)
new='''    private static final String BG_FS =
            "#extension GL_OES_EGL_image_external : require\\n" +
            "precision mediump float;\\n" +
            "uniform samplerExternalOES uCamera;\\n" +
            "uniform vec2 uTexel;\\n" +
            "varying vec2 vTexCoord;\\n" +
            "float lum(vec3 c){return dot(c,vec3(0.299,0.587,0.114));}\\n" +
            "void main(){\\n" +
            "  vec3 c=texture2D(uCamera,vTexCoord).rgb;\\n" +
            "  vec3 l=texture2D(uCamera,vTexCoord-vec2(uTexel.x,0.0)).rgb;\\n" +
            "  vec3 r=texture2D(uCamera,vTexCoord+vec2(uTexel.x,0.0)).rgb;\\n" +
            "  vec3 u=texture2D(uCamera,vTexCoord+vec2(0.0,uTexel.y)).rgb;\\n" +
            "  vec3 d=texture2D(uCamera,vTexCoord-vec2(0.0,uTexel.y)).rgb;\\n" +
            "  vec3 sm=(c*4.0+l+r+u+d)/8.0;\\n" +
            "  float y=clamp(lum(sm),0.0,1.0);\\n" +
            "  float gx=lum(r)-lum(l);\\n" +
            "  float gy=lum(u)-lum(d);\\n" +
            "  float g=length(vec2(gx,gy));\\n" +
            "  float edge=smoothstep(0.055,0.185,g);\\n" +
            "  float fine=smoothstep(0.018,0.070,g)*(1.0-edge);\\n" +
            "  float q=floor(y*8.0+0.5)/8.0;\\n" +
            "  q=mix(y,q,0.36);\\n" +
            "  vec3 silver=mix(vec3(0.105,0.145,0.185),vec3(0.86,0.91,0.94),smoothstep(0.03,0.97,q));\\n" +
            "  float chroma=max(max(sm.r,sm.g),sm.b)-min(min(sm.r,sm.g),sm.b);\\n" +
            "  vec3 retained=mix(vec3(y),sm,0.22+0.16*chroma);\\n" +
            "  vec3 outc=mix(retained,silver,0.68);\\n" +
            "  float coolShadow=1.0-smoothstep(0.20,0.62,y);\\n" +
            "  outc+=vec3(0.00,0.030,0.050)*coolShadow;\\n" +
            "  outc=mix(outc,vec3(0.055,0.090,0.115),edge*0.72);\\n" +
            "  outc+=vec3(0.015,0.105,0.145)*fine*0.52;\\n" +
            "  float spec=smoothstep(0.78,0.99,y)*(1.0-edge);\\n" +
            "  outc+=vec3(0.045,0.075,0.090)*spec;\\n" +
            "  outc=(outc-0.5)*1.10+0.5;\\n" +
            "  gl_FragColor=vec4(clamp(outc,0.0,1.0),1.0);\\n" +
            "}\\n";'''
s=s[:start]+new+s[end:]

old='''                // No tracked plane polygons: the room geometry comes from dense depth only.
                config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
                config.setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY);
                depthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
                if (!depthSupported) {
                    session.close();
                    session = null;
                    unavailable("Телефон не поддерживает ARCore Depth API");
                    return;
                }
                config.setDepthMode(Config.DepthMode.AUTOMATIC);
                session.configure(config);'''
if old not in s:
    raise SystemExit('5.14 depth config block not found')
s=s.replace(old,'''                // Instant 5.15: no depth, no plane discovery, no delayed surface reconstruction.
                config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
                config.setDepthMode(Config.DepthMode.DISABLED);
                depthSupported = false;
                session.configure(config);''')
s=s.replace('status("SECRET LAB • ЗАМЕНЯЮ ПОВЕРХНОСТИ…");','status("INSTANT LAB • КАМЕРА СРАЗУ ИЗМЕНЕНА");')

old='''        bgProgram = createProgram(BG_VS, BG_FS);
        meshProgram = createProgram(MESH_VS, MESH_FS);
        cameraTexture = createExternalTexture();
        stoneTexture = loadTexture("castle_wall_slates_diff_2k.jpg");
        cracksTexture = loadTexture("cracks.png");
        grungeTexture = loadTexture("grunge.jpg");
        cameraTextureBound = false;'''
if old not in s:
    raise SystemExit('5.14 surface init block not found')
s=s.replace(old,'''        bgProgram = createProgram(BG_VS, BG_FS);
        meshProgram = 0;
        cameraTexture = createExternalTexture();
        stoneTexture = 0;
        cracksTexture = 0;
        grungeTexture = 0;
        cameraTextureBound = false;''')

a=s.index('    @Override public void onDrawFrame(GL10 gl) {')
b=s.index('\n\n    /** Reconstructs', a)
draw='''    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Session s = session;
        if (!resumed || s == null || cameraTexture == 0) return;
        try {
            if (!cameraTextureBound) {
                s.setCameraTextureName(cameraTexture);
                cameraTextureBound = true;
            }
            if (geometryDirty && viewportWidth > 0 && viewportHeight > 0) {
                s.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight);
                geometryDirty = false;
            }
            Frame frame = s.update();
            updateBackgroundGeometry(frame);
            // Instant full-frame stylization on EVERY live camera frame.
            // No tracking wait, no depth image, no mesh accumulation, no pieces appearing later.
            drawBackground();
            statusThrottled("INSTANT SECRET LAB • FULL FRAME • LIVE");
            if (captureRequested.compareAndSet(true, false)) captureGlFrame();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "AR camera unavailable", e);
            unavailable("ARCore потерял камеру");
        } catch (Throwable t) {
            Log.e(TAG, "Instant Lab frame failed", t);
            statusThrottled("INSTANT LAB • FRAME ERROR " + t.getClass().getSimpleName());
        }
    }'''
s=s[:a]+draw+s[b:]

old='''        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(bgProgram, "uCamera"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);'''
if old not in s:
    raise SystemExit('drawBackground block not found')
s=s.replace(old,'''        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(bgProgram, "uCamera"), 0);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(bgProgram, "uTexel"),
                1.0f / Math.max(1, viewportWidth), 1.0f / Math.max(1, viewportHeight));
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);''')
s=s.replace('"RoomVision_Depth_Gothic_" + stamp + ".jpg"','"RoomVision_Instant_Lab_" + stamp + ".jpg"')
p.write_text(s,encoding='utf-8')

g=Path('app/build.gradle.kts')
gs=g.read_text(encoding='utf-8').replace('versionCode = 64','versionCode = 65').replace('versionName = "5.14.0"','versionName = "5.15.0"')
g.write_text(gs,encoding='utf-8')
Path('app/src/main/res/values/strings.xml').write_text('<resources>\n    <string name="app_name">Room Vision LAB 5.15 INSTANT</string>\n</resources>\n',encoding='utf-8')

m=Path('app/src/main/java/com/roomvision/demo/MainActivity.java')
ms=m.read_text(encoding='utf-8')
for x,y in {
    'ROOM VISION LAB 5.14':'ROOM VISION LAB 5.15 INSTANT',
    'СЕКРЕТНАЯ ЛАБОРАТОРИЯ\\nDEPTH MATERIAL WORLD':'СЕКРЕТНАЯ ЛАБОРАТОРИЯ\\nINSTANT CAMERA WORLD',
    'ОТКРЫТЬ LAB AR LIVE':'ОТКРЫТЬ INSTANT LAB',
    'DEPTH MATERIAL НЕДОСТУПЕН • ':'INSTANT LAB НЕДОСТУПЕН • ',
    'SECRET LAB / DEPTH':'SECRET LAB / INSTANT',
    'LAB DEPTH • ЗАПУСК…':'INSTANT LAB • ЗАПУСК…'
}.items():
    ms=ms.replace(x,y)
m.write_text(ms,encoding='utf-8')

# Hard source gate: the live frame must be immediate full-screen, not delayed depth mesh.
s=p.read_text(encoding='utf-8')
a=s.index('    @Override public void onDrawFrame(GL10 gl) {')
b=s.index('\n\n    /** Reconstructs',a)
frame=s[a:b]
assert 'drawBackground();' in frame
assert 'acquireDepthImage16Bits' not in frame
assert 'drawDepthMaterial' not in frame
assert 'TrackingState.TRACKING' not in frame
assert 'INSTANT SECRET LAB • FULL FRAME • LIVE' in frame
assert 'Config.DepthMode.DISABLED' in s
assert 'meshProgram = 0;' in s
assert 'uniform vec2 uTexel;' in s
assert 'vec3 sm=(c*4.0+l+r+u+d)/8.0;' in s
assert 'versionCode = 65' in g.read_text(encoding='utf-8')
assert 'versionName = "5.15.0"' in g.read_text(encoding='utf-8')
print('INSTANT_515_SOURCE_GATE=PASS')
