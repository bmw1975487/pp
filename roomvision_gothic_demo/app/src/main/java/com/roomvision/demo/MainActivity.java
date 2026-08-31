package com.roomvision.demo;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

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
    private static final int CAMERA_REQUEST = 2001;
    private GothicCameraView cameraView;
    private boolean cameraMode;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8,9,11));
        getWindow().setNavigationBarColor(Color.rgb(8,9,11));
        buildHome();
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(String text,float sp,int color,int gravity){TextView v=new TextView(this);v.setText(text);v.setTextSize(sp);v.setTextColor(color);v.setGravity(gravity);return v;}
    private void buildHome(){
        cameraMode=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);getWindow().setStatusBarColor(Color.rgb(8,9,11));getWindow().setNavigationBarColor(Color.rgb(8,9,11));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(dp(24),dp(34),dp(24),dp(28));GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(8,9,11),Color.rgb(17,20,24),Color.rgb(5,6,8)});root.setBackground(bg);
        TextView brand=label("ДРУГАЯ РЕАЛЬНОСТЬ",13,Color.rgb(216,181,106),Gravity.CENTER);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(brand,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView title=label("Выберите мир",31,Color.WHITE,Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));
        TextView intro=label("Посмотрите на обычную комнату через камеру — и увидьте другую реальность.",16,Color.rgb(190,194,200),Gravity.CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(90));ip.setMargins(dp(6),0,dp(6),dp(12));root.addView(intro,ip);root.addView(new Space(this),new LinearLayout.LayoutParams(1,0,.45f));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(20),dp(26),dp(20),dp(24));GradientDrawable cbg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(39,43,48),Color.rgb(19,21,24),Color.rgb(10,11,13)});cbg.setCornerRadius(dp(22));cbg.setStroke(dp(1),Color.rgb(92,79,58));card.setBackground(cbg);
        TextView icon=label("♜",72,Color.rgb(205,209,214),Gravity.CENTER);card.addView(icon,new LinearLayout.LayoutParams(-1,dp(110)));TextView castle=label("ГОТИЧЕСКИЙ\nЗАМОК",28,Color.WHITE,Gravity.CENTER);castle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(castle,new LinearLayout.LayoutParams(-1,dp(92)));TextView desc=label("Камень • холодный свет • туман • древние стены",14,Color.rgb(172,176,182),Gravity.CENTER);card.addView(desc,new LinearLayout.LayoutParams(-1,dp(60)));
        Button start=new Button(this);start.setText("СМОТРЕТЬ ЧЕРЕЗ КАМЕРУ");start.setTextSize(15);start.setTextColor(Color.rgb(18,15,10));start.setTypeface(Typeface.DEFAULT,Typeface.BOLD);start.setAllCaps(false);GradientDrawable bbg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(201,168,99),Color.rgb(236,211,151)});bbg.setCornerRadius(dp(16));start.setBackground(bbg);start.setOnClickListener(v->requestOrOpenCamera());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(58));bp.setMargins(dp(4),dp(16),dp(4),0);card.addView(start,bp);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(400));cp.setMargins(0,0,0,dp(10));root.addView(card,cp);root.addView(new Space(this),new LinearLayout.LayoutParams(1,0,.55f));TextView footer=label("DEMO 0.1 • OFFLINE • БЕЗ AR-СКАНИРОВАНИЯ",11,Color.rgb(112,116,122),Gravity.CENTER);root.addView(footer,new LinearLayout.LayoutParams(-1,dp(38)));setContentView(root);
    }
    private void requestOrOpenCamera(){if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)showCamera();else requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==CAMERA_REQUEST){if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)showCamera();else Toast.makeText(this,"Для режима нужен доступ к камере",Toast.LENGTH_LONG).show();}}
    private void showCamera(){
        cameraMode=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.BLACK);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);cameraView=new GothicCameraView(this);root.addView(cameraView,new FrameLayout.LayoutParams(-1,-1));TextView back=label("‹",44,Color.WHITE,Gravity.CENTER);back.setOnClickListener(v->leaveCamera());FrameLayout.LayoutParams backp=new FrameLayout.LayoutParams(dp(64),dp(64),Gravity.TOP|Gravity.START);backp.setMargins(dp(8),dp(20),0,0);root.addView(back,backp);
        TextView mode=label("ГОТИЧЕСКИЙ ЗАМОК",13,Color.WHITE,Gravity.CENTER);mode.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable badge=new GradientDrawable();badge.setColor(Color.argb(135,5,6,8));badge.setCornerRadius(dp(18));badge.setStroke(dp(1),Color.argb(100,216,181,106));mode.setBackground(badge);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(190),dp(42),Gravity.TOP|Gravity.CENTER_HORIZONTAL);mp.setMargins(0,dp(30),0,0);root.addView(mode,mp);
        TextView hint=label("Двигайте телефон и смотрите на комнату",12,Color.rgb(224,226,230),Gravity.CENTER);GradientDrawable hb=new GradientDrawable();hb.setColor(Color.argb(105,0,0,0));hb.setCornerRadius(dp(16));hint.setBackground(hb);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(280),dp(38),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.setMargins(0,0,0,dp(104));root.addView(hint,hp);
        TextView shutter=label("●",56,Color.WHITE,Gravity.CENTER);GradientDrawable sb=new GradientDrawable();sb.setShape(GradientDrawable.OVAL);sb.setColor(Color.argb(80,255,255,255));sb.setStroke(dp(3),Color.WHITE);shutter.setBackground(sb);shutter.setOnClickListener(v->cameraView.capturePhoto());FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(76),dp(76),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);sp.setMargins(0,0,0,dp(20));root.addView(shutter,sp);setContentView(root);cameraView.onHostResume();
    }
    private void leaveCamera(){if(cameraView!=null){cameraView.onHostPause();cameraView.release();cameraView=null;}buildHome();}
    @Override public void onBackPressed(){if(cameraMode)leaveCamera();else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();if(cameraMode&&cameraView!=null)cameraView.onHostResume();}
    @Override protected void onPause(){if(cameraMode&&cameraView!=null)cameraView.onHostPause();super.onPause();}
    @Override protected void onDestroy(){if(cameraView!=null)cameraView.release();super.onDestroy();}

    private static final class GothicCameraView extends GLSurfaceView implements GLSurfaceView.Renderer,SurfaceTexture.OnFrameAvailableListener,SensorEventListener{
        private static final String TAG="RoomVision";private static final float[] QUAD={-1f,-1f,0f,0f,1f,-1f,1f,0f,-1f,1f,0f,1f,1f,1f,1f,1f};
        private static final String VERTEX_SHADER="attribute vec2 aPosition;\nattribute vec2 aTexCoord;\nuniform mat4 uTexMatrix;\nuniform float uRotation;\nvarying vec2 vTexCoord;\nvec2 rot(vec2 uv,float r){if(r<0.5)return uv;if(r<1.5)return vec2(1.0-uv.y,uv.x);if(r<2.5)return vec2(1.0-uv.x,1.0-uv.y);return vec2(uv.y,1.0-uv.x);}\nvoid main(){gl_Position=vec4(aPosition,0.0,1.0);vec2 uv=rot(aTexCoord,uRotation);vTexCoord=(uTexMatrix*vec4(uv,0.0,1.0)).xy;}\n";
        private static final String FRAGMENT_SHADER="#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nuniform samplerExternalOES uCamera;uniform vec2 uTexel;uniform vec2 uMotion;uniform float uTime;varying vec2 vTexCoord;\nfloat lum(vec3 c){return dot(c,vec3(0.299,0.587,0.114));}\nfloat hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}\nfloat noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);float a=hash(i),b=hash(i+vec2(1.,0.)),c=hash(i+vec2(0.,1.)),d=hash(i+vec2(1.,1.));return mix(mix(a,b,f.x),mix(c,d,f.x),f.y);}\nfloat brick(vec2 uv){vec2 p=uv*vec2(7.,12.);float row=floor(p.y);p.x+=mod(row,2.)*.5;vec2 q=fract(p);float e=min(min(q.x,1.-q.x),min(q.y,1.-q.y));return smoothstep(.035,.085,e);}\nvoid main(){vec2 uv=clamp(vTexCoord,.001,.999);vec3 src=texture2D(uCamera,uv).rgb;float l=lum(src);float lx=lum(texture2D(uCamera,clamp(uv+vec2(uTexel.x*2.,0.),.001,.999)).rgb);float ly=lum(texture2D(uCamera,clamp(uv+vec2(0.,uTexel.y*2.),.001,.999)).rgb);float edge=clamp((abs(l-lx)+abs(l-ly))*5.5,0.,1.);float flat=1.-smoothstep(.10,.55,edge);vec3 gray=vec3(l);vec3 grade=mix(src,gray,.54);grade=(grade-.5)*1.22+.5;grade*=vec3(.80,.87,.94);grade-=.055;vec2 wuv=uv+uMotion;float b=brick(wuv);float n=noise(wuv*vec2(30.,42.));float n2=noise(wuv*vec2(8.,11.)+3.7);float mortar=1.-b;float sv=.64+.25*n+.11*n2-.48*mortar;vec3 stone=vec3(.34,.36,.37)*sv*(.48+.90*l);stone*=vec3(.85,.91,.96);vec3 col=mix(grade,stone,.20+.48*flat);float crack=noise(wuv*vec2(53.,21.)+vec2(9.,2.));crack=smoothstep(.82,.90,crack)*(.15+.35*flat);col*=1.-crack*.36;float fog=noise(uv*vec2(3.5,5.)+vec2(uTime*.018,uTime*.010));fog*=smoothstep(.72,1.,uv.y)*.15;col=mix(col,vec3(.42,.47,.51),fog);float flick=.018*sin(uTime*4.2)+.012*sin(uTime*7.7);float warm=max(0.,1.-length(uv-vec2(.16,.56))*1.55);col+=vec3(.22,.10,.025)*warm*(.13+flick);vec2 p=uv-.5;float vign=1.-smoothstep(.34,.76,dot(p,p)*1.38);col*=mix(.64,1.,vign);col=pow(max(col,0.),vec3(.95));gl_FragColor=vec4(clamp(col,0.,1.),1.);}\n";
        private final Context context;private final FloatBuffer quadBuffer;private final float[] textureMatrix=new float[16];private final AtomicBoolean frameAvailable=new AtomicBoolean(false);private int program,cameraTextureId,viewWidth,viewHeight;private SurfaceTexture cameraTexture;private Size previewSize=new Size(1280,720);private float rotationCode=1f;private long startNanos;private CameraDevice cameraDevice;private CameraCaptureSession captureSession;private HandlerThread cameraThread;private Handler cameraHandler;private final SensorManager sensorManager;private final Sensor rotationSensor;private volatile float motionX,motionY;private boolean baseOrientationSet;private float baseYaw,basePitch;
        GothicCameraView(Context context){super(context);this.context=context;setEGLContextClientVersion(2);setRenderer(this);setRenderMode(RENDERMODE_WHEN_DIRTY);setPreserveEGLContextOnPause(true);quadBuffer=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();quadBuffer.put(QUAD).position(0);sensorManager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);rotationSensor=sensorManager!=null?sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR):null;}
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){program=createProgram(VERTEX_SHADER,FRAGMENT_SHADER);cameraTextureId=createExternalTexture();cameraTexture=new SurfaceTexture(cameraTextureId);cameraTexture.setOnFrameAvailableListener(this);startNanos=System.nanoTime();startCameraThread();openBackCamera();}
        @Override public void onSurfaceChanged(GL10 gl,int width,int height){viewWidth=width;viewHeight=height;GLES20.glViewport(0,0,width,height);}@Override public void onFrameAvailable(SurfaceTexture surfaceTexture){frameAvailable.set(true);requestRender();}
        @Override public void onDrawFrame(GL10 gl){if(cameraTexture==null)return;if(frameAvailable.compareAndSet(true,false)){cameraTexture.updateTexImage();cameraTexture.getTransformMatrix(textureMatrix);}GLES20.glClearColor(.02f,.02f,.025f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glUseProgram(program);int pos=GLES20.glGetAttribLocation(program,"aPosition"),tex=GLES20.glGetAttribLocation(program,"aTexCoord");quadBuffer.position(0);GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,16,quadBuffer);quadBuffer.position(2);GLES20.glEnableVertexAttribArray(tex);GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,16,quadBuffer);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"uTexMatrix"),1,false,textureMatrix,0);GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uRotation"),rotationCode);GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uTexel"),1f/Math.max(1,previewSize.getWidth()),1f/Math.max(1,previewSize.getHeight()));GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uMotion"),motionX,motionY);GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uTime"),(System.nanoTime()-startNanos)/1_000_000_000f);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,cameraTextureId);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCamera"),0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(tex);}
        private int createExternalTexture(){int[] id=new int[1];GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,id[0]);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);return id[0];}
        private int createProgram(String v,String f){int vs=compile(GLES20.GL_VERTEX_SHADER,v),fs=compile(GLES20.GL_FRAGMENT_SHADER,f),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException(GLES20.glGetProgramInfoLog(p));GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);return p;}private int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new RuntimeException(GLES20.glGetShaderInfoLog(s));return s;}
        private void startCameraThread(){if(cameraThread!=null)return;cameraThread=new HandlerThread("RoomVisionCamera");cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());}
        private void openBackCamera(){if(cameraTexture==null)return;CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);try{String selected=null;CameraCharacteristics charsFound=null;for(String id:manager.getCameraIdList()){CameraCharacteristics c=manager.getCameraCharacteristics(id);Integer f=c.get(CameraCharacteristics.LENS_FACING);if(f!=null&&f==CameraCharacteristics.LENS_FACING_BACK){selected=id;charsFound=c;break;}}if(selected==null||charsFound==null)throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);StreamConfigurationMap map=charsFound.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map!=null)previewSize=choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));cameraTexture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());Integer so=charsFound.get(CameraCharacteristics.SENSOR_ORIENTATION);rotationCode=rotationFor(so==null?90:so);manager.openCamera(selected,new CameraDevice.StateCallback(){@Override public void onOpened(CameraDevice c){cameraDevice=c;createPreviewSession();}@Override public void onDisconnected(CameraDevice c){c.close();cameraDevice=null;}@Override public void onError(CameraDevice c,int e){c.close();cameraDevice=null;Log.e(TAG,"Camera error="+e);}},cameraHandler);}catch(SecurityException|CameraAccessException e){Log.e(TAG,"CAMERA_INIT failed",e);}}
        private Size choosePreviewSize(Size[] sizes){if(sizes==null||sizes.length==0)return new Size(1280,720);Size best=sizes[0];long target=1280L*720,bestDelta=Long.MAX_VALUE;for(Size s:sizes){long area=(long)s.getWidth()*s.getHeight();if(area>1920L*1080L)continue;long d=Math.abs(area-target);if(d<bestDelta){best=s;bestDelta=d;}}return best;}
        private float rotationFor(int sensorOrientation){WindowManager wm=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);int r=wm.getDefaultDisplay().getRotation(),d=0;if(r==Surface.ROTATION_90)d=90;else if(r==Surface.ROTATION_180)d=180;else if(r==Surface.ROTATION_270)d=270;int rel=(sensorOrientation-d+360)%360;return rel==90?1f:rel==180?2f:rel==270?3f:0f;}
        private void createPreviewSession(){if(cameraDevice==null||cameraTexture==null)return;try{Surface surface=new Surface(cameraTexture);CaptureRequest.Builder b=cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);b.addTarget(surface);b.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);cameraDevice.createCaptureSession(Arrays.asList(surface),new CameraCaptureSession.StateCallback(){@Override public void onConfigured(CameraCaptureSession s){if(cameraDevice==null)return;captureSession=s;try{s.setRepeatingRequest(b.build(),null,cameraHandler);}catch(CameraAccessException e){Log.e(TAG,"repeat",e);}}@Override public void onConfigureFailed(CameraCaptureSession s){Log.e(TAG,"Session failed");}},cameraHandler);}catch(CameraAccessException e){Log.e(TAG,"preview",e);}}
        void capturePhoto(){if(viewWidth<=0||viewHeight<=0)return;queueEvent(()->{int w=viewWidth,h=viewHeight;ByteBuffer buf=ByteBuffer.allocateDirect(w*h*4).order(ByteOrder.nativeOrder());GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buf);buf.rewind();Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);b.copyPixelsFromBuffer(buf);Matrix flip=new Matrix();flip.preScale(1f,-1f);Bitmap fixed=Bitmap.createBitmap(b,0,0,w,h,flip,false);b.recycle();new Thread(()->saveBitmap(fixed),"RoomVisionSave").start();});}
        private void saveBitmap(Bitmap bitmap){String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date()),name="RoomVision_Gothic_"+stamp+".jpg";ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/RoomVision");v.put(MediaStore.Images.Media.IS_PENDING,1);Uri uri=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);boolean ok=false;if(uri!=null){try(OutputStream out=context.getContentResolver().openOutputStream(uri)){ok=out!=null&&bitmap.compress(Bitmap.CompressFormat.JPEG,94,out);v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);context.getContentResolver().update(uri,v,null,null);}catch(Exception e){Log.e(TAG,"PHOTO_CAPTURE",e);}}bitmap.recycle();boolean result=ok;post(()->Toast.makeText(context,result?"Фото сохранено":"Не удалось сохранить фото",Toast.LENGTH_SHORT).show());}
        void onHostResume(){super.onResume();baseOrientationSet=false;if(sensorManager!=null&&rotationSensor!=null)sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);if(cameraDevice==null&&cameraTexture!=null){startCameraThread();openBackCamera();}}void onHostPause(){if(sensorManager!=null)sensorManager.unregisterListener(this);closeCamera();super.onPause();}void release(){closeCamera();if(cameraTexture!=null){cameraTexture.release();cameraTexture=null;}}
        private void closeCamera(){try{if(captureSession!=null){captureSession.close();captureSession=null;}if(cameraDevice!=null){cameraDevice.close();cameraDevice=null;}}catch(Exception ignored){}if(cameraThread!=null){cameraThread.quitSafely();try{cameraThread.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}cameraThread=null;cameraHandler=null;}}
        @Override public void onSensorChanged(SensorEvent e){if(e.sensor.getType()!=Sensor.TYPE_GAME_ROTATION_VECTOR)return;float[] m=new float[9],o=new float[3];SensorManager.getRotationMatrixFromVector(m,e.values);SensorManager.getOrientation(m,o);float yaw=o[0],pitch=o[1];if(!baseOrientationSet){baseYaw=yaw;basePitch=pitch;baseOrientationSet=true;}motionX=clamp((yaw-baseYaw)*-.030f,-.085f,.085f);motionY=clamp((pitch-basePitch)*.030f,-.085f,.085f);}private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}@Override public void onAccuracyChanged(Sensor s,int accuracy){}
    }
}
