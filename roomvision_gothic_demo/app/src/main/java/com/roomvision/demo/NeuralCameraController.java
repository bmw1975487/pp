package com.roomvision.demo;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class NeuralCameraController {
    private static final String TAG = "RoomVisionModern";
    private final ComponentActivity activity;
    private final ImageView outputView;
    private final TextView statusView;
    private final TextView modeView;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private ProcessCameraProvider cameraProvider;
    private OpenCvFilterEngine artEngine;
    private MatrixEffectEngine matrixEngine;
    private ModernEngineStack modernStack;
    private AgslGothicRenderer gothicGpu;
    private QuestObjectDetector questVision;

    private volatile FilterType currentFilter = FilterType.MATRIX;
    private volatile Bitmap lastStyled;
    private volatile boolean stopped;
    private volatile String lastVisionHit;
    private volatile long lastVisionHitUntil;
    private int renderedFrames;
    private long fpsWindowStart = System.currentTimeMillis();
    private int shownFps;

    NeuralCameraController(ComponentActivity activity, ImageView outputView, TextView statusView, TextView modeView) {
        this.activity = activity;
        this.outputView = outputView;
        this.statusView = statusView;
        this.modeView = modeView;
    }

    void start() {
        stopped = false;
        setStatus("ЗАГРУЗКА MODERN ENGINE STACK…");
        analysisExecutor.execute(() -> {
            matrixEngine = new MatrixEffectEngine();
            artEngine = new OpenCvFilterEngine();

            modernStack = new ModernEngineStack();
            modernStack.initialize();
            if (Build.VERSION.SDK_INT >= 33 && modernStack.isAgslAvailable()) {
                try { gothicGpu = new AgslGothicRenderer(); }
                catch (Throwable t) { Log.w(TAG, "AGSL Gothic unavailable", t); gothicGpu = null; }
            }

            questVision = new QuestObjectDetector();
            questVision.initialize(activity);

            if (stopped) return;
            String stack = modernStack.describe();
            activity.runOnUiThread(() -> {
                statusView.setText(stack);
                bindCamera();
            });
        });
    }

    void setFilter(FilterType type) {
        currentFilter = type == null ? FilterType.MATRIX : type;
        activity.runOnUiThread(() -> modeView.setText(currentFilter.label.toUpperCase(Locale.ROOT)));
    }

    private void bindCamera() {
        if (stopped) return;
        setStatus("ЗАПУСК КАМЕРЫ…");
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(960, 540))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, analysis);
                setStatus("LIVE • " + currentFilter.label + " • VISION: FLOWER/PLANT");
            } catch (Throwable e) {
                Log.e(TAG, "Camera bind failed", e);
                setStatus("ОШИБКА КАМЕРЫ");
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    private void analyzeFrame(ImageProxy image) {
        if (stopped || !processing.compareAndSet(false, true)) { image.close(); return; }
        Bitmap raw = null;
        try {
            try { raw = imageProxyToBitmap(image); }
            catch (Throwable e) { Log.e(TAG, "Frame conversion failed", e); }
            finally { image.close(); }
            if (raw == null) return;

            // Quest vision runs sparsely on the same live frame and beeps when a plant/flower target appears.
            if (questVision != null && questVision.isReady()) {
                String hit = questVision.inspect(raw);
                if (hit != null) {
                    lastVisionHit = hit;
                    lastVisionHitUntil = SystemClock.elapsedRealtime() + 2600L;
                }
            }

            long started = System.nanoTime();
            FilterType requested = currentFilter;
            Bitmap styled;
            String engineName;
            try {
                if (requested == FilterType.MATRIX) {
                    styled = matrixEngine.process(raw);
                    engineName = "MATRIX WORLD";
                } else if (requested == FilterType.GOTHIC && gothicGpu != null && Build.VERSION.SDK_INT >= 33) {
                    styled = gothicGpu.render(raw, SystemClock.uptimeMillis());
                    engineName = "GOTHIC AGSL GPU";
                } else {
                    styled = artEngine.process(raw, requested);
                    engineName = requested == FilterType.GOTHIC ? "GOTHIC CPU" : "LIVE ART";
                }
            } catch (Throwable e) {
                Log.e(TAG, "Mode failed: " + requested, e);
                styled = raw.copy(Bitmap.Config.ARGB_8888, false);
                engineName = "SAFE CAMERA";
            }

            long ms = Math.max(1, (System.nanoTime() - started) / 1_000_000L);
            updateFps();
            Bitmap ready = styled;
            String finalEngineName = engineName;
            if (!stopped) activity.runOnUiThread(() -> {
                if (stopped) { if (ready != null && !ready.isRecycled()) ready.recycle(); return; }
                Bitmap prev = lastStyled;
                lastStyled = ready;
                outputView.setImageBitmap(ready);

                StringBuilder s = new StringBuilder();
                s.append(finalEngineName).append(" • ").append(requested.label)
                        .append(" • ").append(shownFps).append(" FPS • ").append(ms).append(" ms");
                if (modernStack != null && modernStack.isFilamentAvailable()) {
                    s.append(" • FILAMENT/").append(modernStack.filamentBackend());
                }
                if (questVision != null && questVision.isReady()) s.append(" • VISION");
                if (lastVisionHit != null && SystemClock.elapsedRealtime() < lastVisionHitUntil) {
                    s.append(" • TARGET: ").append(lastVisionHit);
                }
                statusView.setText(s.toString());
                if (prev != null && prev != ready && !prev.isRecycled()) prev.recycle();
            }); else if (ready != null && !ready.isRecycled()) ready.recycle();
        } finally {
            if (raw != null && !raw.isRecycled()) raw.recycle();
            processing.set(false);
        }
    }

    private void updateFps() {
        renderedFrames++;
        long now = System.currentTimeMillis(), d = now - fpsWindowStart;
        if (d >= 1000) {
            shownFps = Math.round(renderedFrames * 1000f / d);
            renderedFrames = 0;
            fpsWindowStart = now;
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        int w = image.getWidth(), h = image.getHeight(), rowStride = plane.getRowStride(), pixelStride = plane.getPixelStride();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int row = y * rowStride;
            for (int x = 0; x < w; x++) {
                int o = row + x * pixelStride;
                if (o + 3 >= buffer.limit()) break;
                int r = buffer.get(o) & 255, g = buffer.get(o + 1) & 255, b = buffer.get(o + 2) & 255, a = buffer.get(o + 3) & 255;
                pixels[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        int rot = image.getImageInfo().getRotationDegrees();
        if (rot == 0) return bitmap;
        Matrix m = new Matrix();
        m.postRotate(rot);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, w, h, m, true);
        bitmap.recycle();
        return rotated;
    }

    void captureCurrentFrame() {
        Bitmap f = lastStyled;
        if (f == null || f.isRecycled()) {
            Toast.makeText(activity, "Кадр ещё не готов", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap c = f.copy(Bitmap.Config.ARGB_8888, false);
        new Thread(() -> saveBitmap(c), "RoomVisionSave").start();
    }

    private void saveBitmap(Bitmap bitmap) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "RoomVision_" + currentFilter.name() + "_" + stamp + ".jpg");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RoomVision");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        boolean ok = false;
        if (uri != null) {
            try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                ok = out != null && bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                v.clear(); v.put(MediaStore.Images.Media.IS_PENDING, 0);
                activity.getContentResolver().update(uri, v, null, null);
            } catch (Throwable e) { Log.e(TAG, "Save failed", e); }
        }
        bitmap.recycle();
        boolean done = ok;
        activity.runOnUiThread(() -> Toast.makeText(activity, done ? "Фото сохранено" : "Не удалось сохранить фото", Toast.LENGTH_SHORT).show());
    }

    void stop() {
        stopped = true;
        if (cameraProvider != null) try { cameraProvider.unbindAll(); } catch (Throwable ignored) { }
        cameraProvider = null;
        Bitmap f = lastStyled; lastStyled = null;
        if (f != null && !f.isRecycled()) f.recycle();

        QuestObjectDetector q = questVision; questVision = null;
        if (q != null) try { q.close(); } catch (Throwable ignored) { }
        ModernEngineStack m = modernStack; modernStack = null;
        if (m != null) try { m.close(); } catch (Throwable ignored) { }
        gothicGpu = null; matrixEngine = null; artEngine = null;
        analysisExecutor.shutdown();
    }

    private void setStatus(String text) { activity.runOnUiThread(() -> statusView.setText(text)); }
}
