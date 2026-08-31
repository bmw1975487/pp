package com.roomvision.demo;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
    private static final String WORLD = "worlds/gothic_castle/";
    private static final int CAMERA_REQUEST = 2001;
    private GothicCameraView cameraView;
    private boolean cameraMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7, 8, 10));
        getWindow().setNavigationBarColor(Color.rgb(7, 8, 10));
        buildHome();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, float sp, int color, int gravity) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(gravity);
        return v;
    }

    private Bitmap assetBitmap(String path) {
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("RoomVision", "Asset load failed: " + path, e);
            return null;
        }
    }

    private void buildHome() {
        cameraMode = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        getWindow().setStatusBarColor(Color.rgb(7, 8, 10));
        getWindow().setNavigationBarColor(Color.rgb(7, 8, 10));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(7, 8, 10));

        TextView brand = label("ROOM VISION", 12, Color.rgb(213, 178, 99), Gravity.CENTER);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setLetterSpacing(.20f);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView title = label("Другая реальность", 31, Color.WHITE, Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView subtitle = label("Выберите мир и сразу смотрите на реальную комнату через его атмосферу.", 15,
                Color.rgb(181, 185, 191), Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, dp(72));
        subLp.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(subtitle, subLp);

        root.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, .10f));

        FrameLayout card = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.rgb(15, 17, 20));
        cardBg.setCornerRadius(dp(24));
        cardBg.setStroke(dp(1), Color.rgb(82, 70, 49));
        card.setBackground(cardBg);
        card.setClipToOutline(true);

        ImageView hero = new ImageView(this);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap heroBitmap = assetBitmap(WORLD + "gothic_hero.jpg");
        if (heroBitmap != null) hero.setImageBitmap(heroBitmap);
        card.addView(hero, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        GradientDrawable scrimBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(18, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.argb(244, 4, 5, 7)});
        scrim.setBackground(scrimBg);
        card.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(22));

        TextView single = label("ДОСТУПНЫЙ МИР", 11, Color.rgb(213, 178, 99), Gravity.START);
        single.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        single.setLetterSpacing(.16f);
        content.addView(single, new LinearLayout.LayoutParams(-1, dp(32)));

        TextView castle = label("ГОТИЧЕСКИЙ\nЗАМОК", 30, Color.WHITE, Gravity.START);
        castle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(castle, new LinearLayout.LayoutParams(-1, dp(94)));

        TextView desc = label("Древний камень  •  трещины  •  сырость\nтуман  •  холодный свет  •  факельные отблески", 14,
                Color.rgb(205, 208, 212), Gravity.START);
        content.addView(desc, new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.START);
        String[] chipTexts = {"OFFLINE", "REALTIME", "БЕЗ AR"};
        for (String chipText : chipTexts) {
            TextView chip = label(chipText, 10, Color.rgb(226, 228, 230), Gravity.CENTER);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(Color.argb(120, 20, 21, 24));
            chipBg.setCornerRadius(dp(14));
            chipBg.setStroke(dp(1), Color.argb(130, 150, 152, 156));
            chip.setBackground(chipBg);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(78), dp(30));
            clp.setMargins(0, 0, dp(8), 0);
            chips.addView(chip, clp);
        }
        content.addView(chips, new LinearLayout.LayoutParams(-1, dp(42)));

        Button start = new Button(this);
        start.setText("ВОЙТИ В ЗАМОК");
        start.setTextSize(15);
        start.setTextColor(Color.rgb(19, 15, 9));
        start.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        start.setAllCaps(false);
        GradientDrawable startBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(190, 151, 72), Color.rgb(236, 210, 147)});
        startBg.setCornerRadius(dp(17));
        start.setBackground(startBg);
        start.setOnClickListener(v -> requestOrOpenCamera());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(58));
        blp.setMargins(0, dp(12), 0, 0);
        content.addView(start, blp);

        card.addView(content, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        cardLp.setMargins(0, dp(4), 0, dp(10));
        root.addView(card, cardLp);

        TextView footer = label("ROOM VISION 1.0  •  SINGLE WORLD EDITION", 10,
                Color.rgb(103, 107, 114), Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(34)));

        setContentView(root);
    }

    private void requestOrOpenCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCamera();
            } else {
                Toast.makeText(this, "Для режима «Готический замок» нужен доступ к камере", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showCamera() {
        cameraMode = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        cameraView = new GothicCameraView(this);
        root.addView(cameraView, new FrameLayout.LayoutParams(-1, -1));

        TextView back = label("‹", 44, Color.WHITE, Gravity.CENTER);
        GradientDrawable roundDark = new GradientDrawable();
        roundDark.setShape(GradientDrawable.OVAL);
        roundDark.setColor(Color.argb(115, 0, 0, 0));
        back.setBackground(roundDark);
        back.setOnClickListener(v -> leaveCamera());
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP | Gravity.START);
        backLp.setMargins(dp(12), dp(22), 0, 0);
        root.addView(back, backLp);

        TextView mode = label("ГОТИЧЕСКИЙ ЗАМОК", 12, Color.WHITE, Gravity.CENTER);
        mode.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mode.setLetterSpacing(.08f);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(Color.argb(155, 4, 5, 7));
        badge.setCornerRadius(dp(18));
        badge.setStroke(dp(1), Color.argb(145, 213, 178, 99));
        mode.setBackground(badge);
        FrameLayout.LayoutParams modeLp = new FrameLayout.LayoutParams(dp(202), dp(40), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        modeLp.setMargins(0, dp(30), 0, 0);
        root.addView(mode, modeLp);

        TextView live = label("●  LIVE", 10, Color.rgb(229, 197, 123), Gravity.CENTER);
        GradientDrawable liveBg = new GradientDrawable();
        liveBg.setColor(Color.argb(120, 0, 0, 0));
        liveBg.setCornerRadius(dp(14));
        live.setBackground(liveBg);
        FrameLayout.LayoutParams liveLp = new FrameLayout.LayoutParams(dp(72), dp(30), Gravity.TOP | Gravity.END);
        liveLp.setMargins(0, dp(35), dp(14), 0);
        root.addView(live, liveLp);

        TextView hint = label("Реальная камера • мир применяется мгновенно", 11,
                Color.rgb(226, 228, 232), Gravity.CENTER);
        GradientDrawable hintBg = new GradientDrawable();
        hintBg.setColor(Color.argb(115, 0, 0, 0));
        hintBg.setCornerRadius(dp(16));
        hint.setBackground(hintBg);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(dp(300), dp(38), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintLp.setMargins(0, 0, 0, dp(112));
        root.addView(hint, hintLp);

        TextView shutter = label("", 1, Color.WHITE, Gravity.CENTER);
        GradientDrawable shutterBg = new GradientDrawable();
        shutterBg.setShape(GradientDrawable.OVAL);
        shutterBg.setColor(Color.WHITE);
        shutterBg.setStroke(dp(5), Color.argb(155, 22, 23, 25));
        shutter.setBackground(shutterBg);
        shutter.setOnClickListener(v -> cameraView.capturePhoto());
        FrameLayout.LayoutParams shutterLp = new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        shutterLp.setMargins(0, 0, 0, dp(24));
        root.addView(shutter, shutterLp);

        setContentView(root);
        cameraView.onHostResume();
    }

    private void leaveCamera() {
        if (cameraView != null) {
            cameraView.onHostPause();
            cameraView.release();
            cameraView = null;
        }
        buildHome();
    }

    @Override
    public void onBackPressed() {
        if (cameraMode) leaveCamera();
        else super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        if (cameraMode && cameraView != null) cameraView.onHostResume();
    }

    @Override protected void onPause() {
        if (cameraMode && cameraView != null) cameraView.onHostPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (cameraView != null) cameraView.release();
        super.onDestroy();
    }

    private static final class GothicCameraView extends GLSurfaceView implements GLSurfaceView.Renderer,
            SurfaceTexture.OnFrameAvailableListener, SensorEventListener {
        private static final String TAG = "RoomVision";
        private static final float[] QUAD = {
                -1f,-1f, 0f,0f,
                 1f,-1f, 1f,0f,
                -1f, 1f, 0f,1f,
                 1f, 1f, 1f,1f
        };

        private static final String VS =
                "attribute vec2 aPosition;\n" +
                "attribute vec2 aTexCoord;\n" +
                "uniform mat4 uTexMatrix;\n" +
                "uniform float uRotation;\n" +
                "varying vec2 vTexCoord;\n" +
                "vec2 rot(vec2 uv,float r){if(r<0.5)return uv;if(r<1.5)return vec2(1.0-uv.y,uv.x);if(r<2.5)return vec2(1.0-uv.x,1.0-uv.y);return vec2(uv.y,1.0-uv.x);}\n" +
                "void main(){gl_Position=vec4(aPosition,0.0,1.0);vec2 uv=rot(aTexCoord,uRotation);vTexCoord=(uTexMatrix*vec4(uv,0.0,1.0)).xy;}\n";

        private static final String FS =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "uniform samplerExternalOES uCamera;\n" +
                "uniform sampler2D uStone;uniform sampler2D uDetail;uniform sampler2D uCracks;uniform sampler2D uGrunge;uniform sampler2D uFog;\n" +
                "uniform vec2 uTexel;uniform vec2 uMotion;uniform float uTime;varying vec2 vTexCoord;\n" +
                "float lum(vec3 c){return dot(c,vec3(.299,.587,.114));}\n" +
                "void main(){\n" +
                "vec2 uv=clamp(vTexCoord,.002,.998);vec3 src=texture2D(uCamera,uv).rgb;float l=lum(src);\n" +
                "float lx=lum(texture2D(uCamera,clamp(uv+vec2(uTexel.x*2.0,0.0),.002,.998)).rgb);\n" +
                "float ly=lum(texture2D(uCamera,clamp(uv+vec2(0.0,uTexel.y*2.0),.002,.998)).rgb);\n" +
                "float edge=clamp((abs(l-lx)+abs(l-ly))*6.0,0.0,1.0);float flat=1.0-smoothstep(.08,.48,edge);\n" +
                "vec3 gray=vec3(l);vec3 grade=mix(src,gray,.48);grade=(grade-.5)*1.24+.5;grade*=vec3(.78,.86,.96);grade-=.045;\n" +
                "vec2 base=uv+uMotion;vec2 suv=fract(base*vec2(3.5,5.2));vec3 stone=texture2D(uStone,suv).rgb;\n" +
                "float detail=texture2D(uDetail,fract(base*vec2(13.0,19.0))).r;stone*=.83+detail*.30;stone*=.42+l*.90;stone*=vec3(.86,.92,.98);\n" +
                "float gr=texture2D(uGrunge,fract(base*vec2(2.2,3.1)+vec2(.17,.41))).r;stone*=.88+gr*.20;\n" +
                "float amount=.36+.39*flat;vec3 col=mix(grade,stone,amount);\n" +
                "float crack=texture2D(uCracks,fract(base*vec2(1.18,1.72)+vec2(.31,.12))).r;crack=smoothstep(.18,.72,crack);col*=1.0-crack*(.12+.28*flat);\n" +
                "float damp=smoothstep(.60,.90,gr)*flat;col*=1.0-damp*.16;\n" +
                "vec2 fogUv=fract(uv*vec2(1.25,1.65)+vec2(uTime*.008,uTime*.004));float fog=texture2D(uFog,fogUv).r;fog=smoothstep(.52,.86,fog)*.18;fog*=.35+.65*smoothstep(.28,1.0,uv.y);col=mix(col,vec3(.47,.52,.57),fog);\n" +
                "float flick=.018*sin(uTime*4.1)+.010*sin(uTime*7.3)+.006*sin(uTime*12.7);float warm=max(0.0,1.0-length(uv-vec2(.16,.56))*1.50);col+=vec3(.24,.105,.024)*warm*(.13+flick);\n" +
                "float warm2=max(0.0,1.0-length(uv-vec2(.84,.56))*1.50);col+=vec3(.18,.075,.018)*warm2*(.09+flick*.65);\n" +
                "vec2 p=uv-.5;float vig=smoothstep(.70,.22,dot(p,p));col*=mix(.57,1.0,vig);\n" +
                "float grain=fract(sin(dot(gl_FragCoord.xy+uTime,vec2(12.9898,78.233)))*43758.5453);col+=(grain-.5)*.018;\n" +
                "col=pow(max(col,0.0),vec3(.94));gl_FragColor=vec4(clamp(col,0.0,1.0),1.0);}\n";

        private final Context context;
        private final FloatBuffer quadBuffer;
        private final float[] textureMatrix = new float[16];
        private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
        private int program;
        private int cameraTextureId;
        private int stoneTex, detailTex, cracksTex, grungeTex, fogTex;
        private int viewWidth, viewHeight;
        private SurfaceTexture cameraTexture;
        private Size previewSize = new Size(1280,720);
        private float rotationCode = 1f;
        private long startNanos;
        private CameraDevice cameraDevice;
        private CameraCaptureSession captureSession;
        private HandlerThread cameraThread;
        private Handler cameraHandler;
        private final SensorManager sensorManager;
        private final Sensor rotationSensor;
        private volatile float motionX, motionY;
        private boolean baseOrientationSet;
        private float baseYaw, basePitch;

        GothicCameraView(Context context) {
            super(context);
            this.context = context;
            setEGLContextClientVersion(2);
            setRenderer(this);
            setRenderMode(RENDERMODE_WHEN_DIRTY);
            setPreserveEGLContextOnPause(true);
            quadBuffer = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            quadBuffer.put(QUAD).position(0);
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            rotationSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) : null;
        }

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            program = createProgram(VS, FS);
            cameraTextureId = createExternalTexture();
            stoneTex = loadTexture("worlds/gothic_castle/stone_albedo.jpg");
            detailTex = loadTexture("worlds/gothic_castle/stone_detail.jpg");
            cracksTex = loadTexture("worlds/gothic_castle/cracks.png");
            grungeTex = loadTexture("worlds/gothic_castle/grunge.jpg");
            fogTex = loadTexture("worlds/gothic_castle/fog.jpg");
            startCameraThread();
            cameraTexture = new SurfaceTexture(cameraTextureId);
            cameraTexture.setOnFrameAvailableListener(this, cameraHandler);
            startNanos = System.nanoTime();
            openBackCamera();
            Log.i(TAG, "EFFECT_READY textures=" + stoneTex + "," + detailTex + "," + cracksTex + "," + grungeTex + "," + fogTex);
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            viewWidth = width;
            viewHeight = height;
            GLES20.glViewport(0,0,width,height);
        }

        @Override public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            frameAvailable.set(true);
            requestRender();
        }

        @Override public void onDrawFrame(GL10 gl) {
            if (cameraTexture == null) return;
            if (frameAvailable.compareAndSet(true,false)) {
                cameraTexture.updateTexImage();
                cameraTexture.getTransformMatrix(textureMatrix);
            }
            GLES20.glClearColor(.01f,.012f,.016f,1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            int pos = GLES20.glGetAttribLocation(program,"aPosition");
            int tex = GLES20.glGetAttribLocation(program,"aTexCoord");
            quadBuffer.position(0);
            GLES20.glEnableVertexAttribArray(pos);
            GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,quadBuffer);
            quadBuffer.position(2);
            GLES20.glEnableVertexAttribArray(tex);
            GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,16,quadBuffer);

            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTexMatrix"),1,false,textureMatrix,0);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uRotation"),rotationCode);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uTexel"),
                    1f/Math.max(1,previewSize.getWidth()),1f/Math.max(1,previewSize.getHeight()));
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uMotion"),motionX,motionY);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uTime"),(System.nanoTime()-startNanos)/1_000_000_000f);

            bindExternal(0,"uCamera",cameraTextureId);
            bind2D(1,"uStone",stoneTex);
            bind2D(2,"uDetail",detailTex);
            bind2D(3,"uCracks",cracksTex);
            bind2D(4,"uGrunge",grungeTex);
            bind2D(5,"uFog",fogTex);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(pos);
            GLES20.glDisableVertexAttribArray(tex);
        }

        private void bindExternal(int unit, String uniform, int texture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,uniform), unit);
        }

        private void bind2D(int unit, String uniform, int texture) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,uniform), unit);
        }

        private int loadTexture(String assetPath) {
            Bitmap bitmap = null;
            try (InputStream in = context.getAssets().open(assetPath)) {
                bitmap = BitmapFactory.decodeStream(in);
                if (bitmap == null) throw new IllegalStateException("decode failed");
                int[] id = new int[1];
                GLES20.glGenTextures(1,id,0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_REPEAT);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
                Log.i(TAG,"TEXTURE_LOAD " + assetPath + " " + bitmap.getWidth() + "x" + bitmap.getHeight());
                return id[0];
            } catch (Exception e) {
                Log.e(TAG,"TEXTURE_LOAD failed " + assetPath,e);
                return fallbackTexture();
            } finally {
                if (bitmap != null) bitmap.recycle();
            }
        }

        private int fallbackTexture() {
            int[] id = new int[1];
            GLES20.glGenTextures(1,id,0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
            byte[] px = {(byte)128,(byte)128,(byte)128,(byte)255};
            ByteBuffer b = ByteBuffer.allocateDirect(4).put(px);
            b.position(0);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,1,1,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,b);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            return id[0];
        }

        private int createExternalTexture() {
            int[] id = new int[1];
            GLES20.glGenTextures(1,id,0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,id[0]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            return id[0];
        }

        private int createProgram(String v, String f) {
            int vs = compile(GLES20.GL_VERTEX_SHADER,v);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER,f);
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p,vs);
            GLES20.glAttachShader(p,fs);
            GLES20.glLinkProgram(p);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
            if (ok[0] == 0) throw new RuntimeException(GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            return p;
        }

        private int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s,src);
            GLES20.glCompileShader(s);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
            if (ok[0] == 0) throw new RuntimeException(GLES20.glGetShaderInfoLog(s));
            return s;
        }

        private void startCameraThread() {
            if (cameraThread != null) return;
            cameraThread = new HandlerThread("RoomVisionCamera");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
        }

        private void openBackCamera() {
            if (cameraTexture == null) return;
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            try {
                String selected = null;
                CameraCharacteristics selectedChars = null;
                for (String id : manager.getCameraIdList()) {
                    CameraCharacteristics c = manager.getCameraCharacteristics(id);
                    Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        selected = id;
                        selectedChars = c;
                        break;
                    }
                }
                if (selected == null || selectedChars == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
                StreamConfigurationMap map = selectedChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
                cameraTexture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());
                Integer sensorOrientation = selectedChars.get(CameraCharacteristics.SENSOR_ORIENTATION);
                rotationCode = rotationFor(sensorOrientation == null ? 90 : sensorOrientation);
                Log.i(TAG,"CAMERA_RESOLUTION " + previewSize.getWidth() + "x" + previewSize.getHeight());
                manager.openCamera(selected,new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice c) { cameraDevice = c; createPreviewSession(); }
                    @Override public void onDisconnected(CameraDevice c) { c.close(); cameraDevice = null; }
                    @Override public void onError(CameraDevice c, int error) { c.close(); cameraDevice = null; Log.e(TAG,"Camera error="+error); }
                },cameraHandler);
            } catch (SecurityException | CameraAccessException e) {
                Log.e(TAG,"CAMERA_INIT failed",e);
            }
        }

        private Size choosePreviewSize(Size[] sizes) {
            if (sizes == null || sizes.length == 0) return new Size(1280,720);
            Size best = sizes[0];
            long target = 1920L * 1080L;
            long bestDelta = Long.MAX_VALUE;
            for (Size s : sizes) {
                long area = (long)s.getWidth() * s.getHeight();
                if (area > 1920L * 1080L) continue;
                long delta = Math.abs(area-target);
                if (delta < bestDelta) { best=s; bestDelta=delta; }
            }
            return best;
        }

        private float rotationFor(int sensorOrientation) {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            int r = wm.getDefaultDisplay().getRotation();
            int display = 0;
            if (r == Surface.ROTATION_90) display=90;
            else if (r == Surface.ROTATION_180) display=180;
            else if (r == Surface.ROTATION_270) display=270;
            int rel = (sensorOrientation-display+360)%360;
            return rel==90 ? 1f : rel==180 ? 2f : rel==270 ? 3f : 0f;
        }

        private void createPreviewSession() {
            if (cameraDevice == null || cameraTexture == null) return;
            try {
                Surface surface = new Surface(cameraTexture);
                CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                builder.addTarget(surface);
                builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
                cameraDevice.createCaptureSession(Arrays.asList(surface),new CameraCaptureSession.StateCallback() {
                    @Override public void onConfigured(CameraCaptureSession session) {
                        if (cameraDevice == null) return;
                        captureSession = session;
                        try { session.setRepeatingRequest(builder.build(),null,cameraHandler); }
                        catch (CameraAccessException e) { Log.e(TAG,"repeat",e); }
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession session) { Log.e(TAG,"Session failed"); }
                },cameraHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG,"preview",e);
            }
        }

        void capturePhoto() {
            if (viewWidth <= 0 || viewHeight <= 0) return;
            queueEvent(() -> {
                int w=viewWidth,h=viewHeight;
                ByteBuffer buf=ByteBuffer.allocateDirect(w*h*4).order(ByteOrder.nativeOrder());
                GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buf);
                buf.rewind();
                Bitmap raw=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                raw.copyPixelsFromBuffer(buf);
                Matrix flip=new Matrix();
                flip.preScale(1f,-1f);
                Bitmap fixed=Bitmap.createBitmap(raw,0,0,w,h,flip,false);
                raw.recycle();
                new Thread(() -> saveBitmap(fixed),"RoomVisionSave").start();
            });
        }

        private void saveBitmap(Bitmap bitmap) {
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
            String name="RoomVision_Gothic_"+stamp+".jpg";
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/RoomVision");
            values.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            boolean ok=false;
            if (uri != null) {
                try (OutputStream out=context.getContentResolver().openOutputStream(uri)) {
                    ok=out != null && bitmap.compress(Bitmap.CompressFormat.JPEG,95,out);
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING,0);
                    context.getContentResolver().update(uri,values,null,null);
                } catch (Exception e) { Log.e(TAG,"PHOTO_CAPTURE",e); }
            }
            bitmap.recycle();
            boolean saved=ok;
            post(() -> Toast.makeText(context,saved ? "Фото сохранено в Pictures/RoomVision" : "Не удалось сохранить фото",Toast.LENGTH_SHORT).show());
        }

        void onHostResume() {
            super.onResume();
            baseOrientationSet=false;
            if (sensorManager != null && rotationSensor != null)
                sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);
            if (cameraDevice == null && cameraTexture != null) { startCameraThread(); openBackCamera(); }
        }

        void onHostPause() {
            if (sensorManager != null) sensorManager.unregisterListener(this);
            closeCamera();
            super.onPause();
        }

        void release() {
            closeCamera();
            if (cameraTexture != null) { cameraTexture.release(); cameraTexture=null; }
        }

        private void closeCamera() {
            try {
                if (captureSession != null) { captureSession.close(); captureSession=null; }
                if (cameraDevice != null) { cameraDevice.close(); cameraDevice=null; }
            } catch (Exception ignored) { }
            if (cameraThread != null) {
                cameraThread.quitSafely();
                try { cameraThread.join(500); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                cameraThread=null;
                cameraHandler=null;
            }
        }

        @Override public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_GAME_ROTATION_VECTOR) return;
            float[] matrix=new float[9], orientation=new float[3];
            SensorManager.getRotationMatrixFromVector(matrix,event.values);
            SensorManager.getOrientation(matrix,orientation);
            float yaw=orientation[0],pitch=orientation[1];
            if (!baseOrientationSet) { baseYaw=yaw; basePitch=pitch; baseOrientationSet=true; }
            motionX=clamp((yaw-baseYaw)*-.034f,-.10f,.10f);
            motionY=clamp((pitch-basePitch)*.034f,-.10f,.10f);
        }

        private float clamp(float value,float min,float max) { return Math.max(min,Math.min(max,value)); }
        @Override public void onAccuracyChanged(Sensor sensor,int accuracy) { }
    }
}
