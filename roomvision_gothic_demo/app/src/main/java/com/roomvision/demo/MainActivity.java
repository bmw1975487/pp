package com.roomvision.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import java.io.InputStream;

public class MainActivity extends ComponentActivity {
    private static final int CAMERA_REQUEST = 2001;
    private static final String WORLD = "worlds/gothic_castle/";

    private boolean cameraMode;
    private NeuralCameraController cameraController;

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
        } catch (Exception ignored) {
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

        TextView brand = label("ROOM VISION NEURAL", 12, Color.rgb(213, 178, 99), Gravity.CENTER);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setLetterSpacing(.16f);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView title = label("Другая реальность", 31, Color.WHITE, Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(56)));

        TextView subtitle = label("Живая камера преобразуется нейросетью прямо на телефоне. Без токенов и сервера.",
                15, Color.rgb(181, 185, 191), Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, dp(72));
        subLp.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(subtitle, subLp);
        root.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, .08f));

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
                new int[]{Color.argb(18, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.argb(246, 4, 5, 7)});
        scrim.setBackground(scrimBg);
        card.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(22));

        TextView single = label("НЕЙРОМИР • ON-DEVICE", 11, Color.rgb(213, 178, 99), Gravity.START);
        single.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        single.setLetterSpacing(.12f);
        content.addView(single, new LinearLayout.LayoutParams(-1, dp(32)));

        TextView castle = label("ГОТИЧЕСКИЙ\nЗАМОК", 30, Color.WHITE, Gravity.START);
        castle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(castle, new LinearLayout.LayoutParams(-1, dp(94)));

        TextView desc = label("Камера → нейростиль → ваша комната в другом визуальном мире.\nGPU/LiteRT • авто-качество 256/384/512 px",
                14, Color.rgb(205, 208, 212), Gravity.START);
        content.addView(desc, new LinearLayout.LayoutParams(-1, dp(78)));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.START);
        for (String text : new String[]{"OFFLINE", "LIVE", "NEURAL"}) {
            TextView chip = label(text, 10, Color.rgb(226, 228, 230), Gravity.CENTER);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(Color.argb(125, 20, 21, 24));
            chipBg.setCornerRadius(dp(14));
            chipBg.setStroke(dp(1), Color.argb(130, 150, 152, 156));
            chip.setBackground(chipBg);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(78), dp(30));
            clp.setMargins(0, 0, dp(8), 0);
            chips.addView(chip, clp);
        }
        content.addView(chips, new LinearLayout.LayoutParams(-1, dp(42)));

        Button start = new Button(this);
        start.setText("СМОТРЕТЬ ЧЕРЕЗ КАМЕРУ");
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

        TextView footer = label("ROOM VISION NEURAL 2.0 • SINGLE WORLD", 10,
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showCamera();
            else Toast.makeText(this, "Для live-режима нужен доступ к камере", Toast.LENGTH_LONG).show();
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
        root.setBackgroundColor(Color.BLACK);

        ImageView processed = new ImageView(this);
        processed.setBackgroundColor(Color.BLACK);
        processed.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ColorMatrix grade = new ColorMatrix();
        grade.setSaturation(0.82f);
        grade.postConcat(new ColorMatrix(new float[]{
                0.92f, 0, 0, 0, -6,
                0, 0.98f, 0, 0, -2,
                0, 0, 1.08f, 0, 4,
                0, 0, 0, 1, 0
        }));
        processed.setColorFilter(new ColorMatrixColorFilter(grade));
        root.addView(processed, new FrameLayout.LayoutParams(-1, -1));

        View atmosphere = new View(this);
        GradientDrawable atmosphereBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(58, 8, 11, 18), Color.TRANSPARENT, Color.argb(72, 2, 4, 8)});
        atmosphere.setBackground(atmosphereBg);
        root.addView(atmosphere, new FrameLayout.LayoutParams(-1, -1));

        TextView back = label("‹", 44, Color.WHITE, Gravity.CENTER);
        GradientDrawable roundDark = new GradientDrawable();
        roundDark.setShape(GradientDrawable.OVAL);
        roundDark.setColor(Color.argb(130, 0, 0, 0));
        back.setBackground(roundDark);
        back.setOnClickListener(v -> leaveCamera());
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP | Gravity.START);
        backLp.setMargins(dp(12), dp(22), 0, 0);
        root.addView(back, backLp);

        TextView mode = label("ГОТИЧЕСКИЙ ЗАМОК", 12, Color.WHITE, Gravity.CENTER);
        mode.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mode.setLetterSpacing(.08f);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(Color.argb(165, 4, 5, 7));
        badge.setCornerRadius(dp(18));
        badge.setStroke(dp(1), Color.argb(160, 213, 178, 99));
        mode.setBackground(badge);
        FrameLayout.LayoutParams modeLp = new FrameLayout.LayoutParams(dp(210), dp(40), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        modeLp.setMargins(0, dp(30), 0, 0);
        root.addView(mode, modeLp);

        TextView status = label("ЗАГРУЗКА НЕЙРОДВИЖКА…", 10, Color.rgb(229, 197, 123), Gravity.CENTER);
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(Color.argb(150, 0, 0, 0));
        statusBg.setCornerRadius(dp(14));
        status.setBackground(statusBg);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(dp(310), dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        statusLp.setMargins(0, 0, 0, dp(112));
        root.addView(status, statusLp);

        TextView shutter = label("", 1, Color.WHITE, Gravity.CENTER);
        GradientDrawable shutterBg = new GradientDrawable();
        shutterBg.setShape(GradientDrawable.OVAL);
        shutterBg.setColor(Color.WHITE);
        shutterBg.setStroke(dp(5), Color.argb(180, 22, 23, 25));
        shutter.setBackground(shutterBg);
        FrameLayout.LayoutParams shutterLp = new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        shutterLp.setMargins(0, 0, 0, dp(24));
        root.addView(shutter, shutterLp);

        setContentView(root);
        cameraController = new NeuralCameraController(this, processed, status);
        shutter.setOnClickListener(v -> {
            if (cameraController != null) cameraController.captureCurrentFrame();
        });
        cameraController.start();
    }

    private void leaveCamera() {
        if (cameraController != null) {
            cameraController.stop();
            cameraController = null;
        }
        buildHome();
    }

    @Override
    public void onBackPressed() {
        if (cameraMode) leaveCamera();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (cameraController != null) cameraController.stop();
        super.onDestroy();
    }
}
