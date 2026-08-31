package com.roomvision.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

public class MainActivity extends ComponentActivity {
    private static final int CAMERA_REQUEST = 2001;
    private boolean cameraMode;
    private NeuralCameraController cameraController;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7,8,12));
        getWindow().setNavigationBarColor(Color.rgb(7,8,12));
        buildHome();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView label(String text,float sp,int color,int gravity) { TextView v=new TextView(this);v.setText(text);v.setTextSize(sp);v.setTextColor(color);v.setGravity(gravity);return v; }

    private void buildHome() {
        cameraMode=false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(dp(24),dp(32),dp(24),dp(32));root.setBackgroundColor(Color.rgb(7,8,12));
        TextView brand=label("ROOM VISION LIVE",14,Color.rgb(77,225,238),Gravity.CENTER);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);brand.setLetterSpacing(.15f);root.addView(brand,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView title=label("4 живых\nвизуальных мира",34,Color.WHITE,Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(110)));
        TextView desc=label("Только четыре сильных и визуально разных режима. Никаких дублей, нейросетей и тяжёлых библиотек.",16,Color.rgb(188,193,203),Gravity.CENTER);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,0,1);dl.setMargins(dp(10),dp(18),dp(10),dp(18));root.addView(desc,dl);
        Button start=new Button(this);start.setText("ОТКРЫТЬ LIVE-КАМЕРУ");start.setTextSize(16);start.setTypeface(Typeface.DEFAULT,Typeface.BOLD);start.setTextColor(Color.rgb(6,12,16));start.setAllCaps(false);GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(74,228,239),Color.rgb(207,112,255)});bg.setCornerRadius(dp(20));start.setBackground(bg);start.setOnClickListener(v->requestOrOpenCamera());root.addView(start,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView foot=label("Matrix • Crayon • ASCII • Blueprint",13,Color.rgb(135,239,246),Gravity.CENTER);root.addView(foot,new LinearLayout.LayoutParams(-1,dp(62)));
        setContentView(root);
    }

    private void requestOrOpenCamera() { if(checkSelfPermission(Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED)showCamera();else requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST); }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==CAMERA_REQUEST){if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)showCamera();else Toast.makeText(this,"Для live-режима нужен доступ к камере",Toast.LENGTH_LONG).show();}}

    private void showCamera() {
        cameraMode=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.BLACK);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        ImageView processed=new ImageView(this);processed.setBackgroundColor(Color.BLACK);processed.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(processed,new FrameLayout.LayoutParams(-1,-1));

        TextView back=label("‹",44,Color.WHITE,Gravity.CENTER);GradientDrawable round=new GradientDrawable();round.setShape(GradientDrawable.OVAL);round.setColor(Color.argb(145,0,0,0));back.setBackground(round);back.setOnClickListener(v->leaveCamera());FrameLayout.LayoutParams bl=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.TOP|Gravity.START);bl.setMargins(dp(12),dp(22),0,0);root.addView(back,bl);
        TextView mode=label("MATRIX",12,Color.WHITE,Gravity.CENTER);mode.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable mb=new GradientDrawable();mb.setColor(Color.argb(175,0,0,0));mb.setCornerRadius(dp(18));mb.setStroke(dp(1),Color.argb(210,0,255,110));mode.setBackground(mb);FrameLayout.LayoutParams ml=new FrameLayout.LayoutParams(dp(240),dp(40),Gravity.TOP|Gravity.CENTER_HORIZONTAL);ml.setMargins(0,dp(30),0,0);root.addView(mode,ml);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setGravity(Gravity.CENTER_HORIZONTAL);bottom.setPadding(dp(8),dp(6),dp(8),dp(12));GradientDrawable bottomBg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.argb(40,0,0,0),Color.argb(235,0,0,0)});bottom.setBackground(bottomBg);
        TextView status=label("ЗАГРУЗКА 4 РЕЖИМОВ…",10,Color.rgb(135,239,246),Gravity.CENTER);bottom.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chips.setPadding(dp(4),0,dp(4),0);
        for(FilterType type:FilterType.catalog()){Button b=new Button(this);b.setText(type.label);b.setTextSize(11);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setPadding(dp(14),0,dp(14),0);GradientDrawable cb=new GradientDrawable();boolean world=type==FilterType.MATRIX;cb.setColor(world?Color.argb(210,0,66,24):Color.argb(185,25,27,33));cb.setCornerRadius(dp(15));cb.setStroke(dp(1),world?Color.rgb(0,255,110):Color.rgb(100,110,126));b.setBackground(cb);b.setOnClickListener(v->{if(cameraController!=null)cameraController.setFilter(type);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.setMargins(dp(4),0,dp(4),0);chips.addView(b,lp);}scroll.addView(chips,new HorizontalScrollView.LayoutParams(-2,dp(46)));bottom.addView(scroll,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView shutter=label("●",40,Color.WHITE,Gravity.CENTER);GradientDrawable sb=new GradientDrawable();sb.setShape(GradientDrawable.OVAL);sb.setColor(Color.argb(110,255,255,255));sb.setStroke(dp(3),Color.WHITE);shutter.setBackground(sb);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(dp(62),dp(62));sl.setMargins(0,dp(4),0,0);bottom.addView(shutter,sl);
        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,dp(162),Gravity.BOTTOM);root.addView(bottom,bottomLp);

        setContentView(root);cameraController=new NeuralCameraController(this,processed,status,mode);cameraController.setFilter(FilterType.MATRIX);shutter.setOnClickListener(v->{if(cameraController!=null)cameraController.captureCurrentFrame();});cameraController.start();
    }

    private void leaveCamera(){if(cameraController!=null){cameraController.stop();cameraController=null;}buildHome();}
    @Override public void onBackPressed(){if(cameraMode)leaveCamera();else super.onBackPressed();}
    @Override protected void onDestroy(){if(cameraController!=null)cameraController.stop();super.onDestroy();}
}
