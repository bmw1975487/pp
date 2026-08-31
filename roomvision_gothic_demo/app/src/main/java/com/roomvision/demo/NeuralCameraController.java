package com.roomvision.demo;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
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
    private static final String TAG = "RoomVisionNeural";

    private final ComponentActivity activity;
    private final ImageView outputView;
    private final TextView statusView;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private ProcessCameraProvider cameraProvider;
    private NeuralStyleEngine engine;
    private volatile Bitmap lastStyled;
    private volatile boolean stopped;
    private int consecutiveSlow;
    private int consecutiveFast;
    private int renderedFrames;
    private long fpsWindowStart = System.currentTimeMillis();
    private int shownFps;

    NeuralCameraController(ComponentActivity activity, ImageView outputView, TextView statusView) {
        this.activity = activity;
        this.outputView = outputView;
        this.statusView = statusView;
    }

    void start() {
        stopped = false;
        setStatus("ЗАГРУЗКА НЕЙРОДВИЖКА…");
        analysisExecutor.execute(() -> {
            try {
                engine = new NeuralStyleEngine(activity);
                engine.initialize();
                if (stopped) {
                    try { engine.close(); } catch (Throwable ignored) { }
                    engine = null;
                    return;
                }
                activity.runOnUiThread(this::bindCamera);
            } catch (Throwable error) {
                Log.e(TAG, "Neural engine init failed", error);
                setStatus("ОШИБКА НЕЙРОДВИЖКА");
            }
        });
    }

    private void bindCamera() {
        if (stopped) return;
        setStatus("ЗАПУСК КАМЕРЫ…");
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, analysis);
                setStatus("NEURAL LIVE");
            } catch (Throwable error) {
                Log.e(TAG, "Camera bind failed", error);
                setStatus("ОШИБКА КАМЕРЫ");
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    private void analyzeFrame(ImageProxy image) {
        if (stopped || engine == null || !processing.compareAndSet(false, true)) {
            image.close();
            return;
        }
        Bitmap raw = null;
        try {
            raw = imageProxyToBitmap(image);
        } catch (Throwable conversionError) {
            Log.e(TAG, "Frame conversion failed", conversionError);
        } finally {
            image.close();
        }

        if (raw == null) {
            processing.set(false);
            return;
        }

        long started = System.nanoTime();
        try {
            Bitmap styled = engine.processFrame(raw);
            long elapsedMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            adaptPerformance(elapsedMs);
            updateFps();
            if (stopped) {
                styled.recycle();
                return;
            }
            Bitmap ready = styled;
            activity.runOnUiThread(() -> {
                if (stopped || engine == null) {
                    if (!ready.isRecycled()) ready.recycle();
                    return;
                }
                Bitmap previous = lastStyled;
                lastStyled = ready;
                outputView.setImageBitmap(ready);
                statusView.setText(String.format(Locale.US,
                        "NEURAL LIVE  •  %s  •  %d px  •  %d FPS  •  %d ms",
                        engine.isGpuEnabled() ? "GPU" : "CPU",
                        engine.getLongSide(), shownFps, elapsedMs));
                if (previous != null && previous != ready && !previous.isRecycled()) previous.recycle();
            });
        } catch (Throwable inferenceError) {
            Log.e(TAG, "Inference failed", inferenceError);
            setStatus("ОШИБКА ОБРАБОТКИ");
        } finally {
            if (!raw.isRecycled()) raw.recycle();
            processing.set(false);
        }
    }

    private void adaptPerformance(long elapsedMs) {
        if (engine == null) return;
        if (elapsedMs > 80) {
            consecutiveSlow++;
            consecutiveFast = 0;
            if (consecutiveSlow >= 4) {
                if (engine.getLongSide() >= 512) engine.setLongSide(384);
                else if (engine.getLongSide() >= 384) engine.setLongSide(256);
                consecutiveSlow = 0;
            }
        } else if (elapsedMs < 38) {
            consecutiveFast++;
            consecutiveSlow = 0;
            if (consecutiveFast >= 30) {
                if (engine.getLongSide() <= 256) engine.setLongSide(384);
                else if (engine.getLongSide() <= 384) engine.setLongSide(512);
                consecutiveFast = 0;
            }
        } else {
            consecutiveSlow = Math.max(0, consecutiveSlow - 1);
            consecutiveFast = Math.max(0, consecutiveFast - 1);
        }
    }

    private void updateFps() {
        renderedFrames++;
        long now = System.currentTimeMillis();
        long delta = now - fpsWindowStart;
        if (delta >= 1000) {
            shownFps = Math.round(renderedFrames * 1000f / delta);
            renderedFrames = 0;
            fpsWindowStart = now;
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.rewind();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            int row = y * rowStride;
            for (int x = 0; x < width; x++) {
                int offset = row + x * pixelStride;
                if (offset + 3 >= buffer.limit()) break;
                int r = buffer.get(offset) & 0xFF;
                int g = buffer.get(offset + 1) & 0xFF;
                int b = buffer.get(offset + 2) & 0xFF;
                int a = buffer.get(offset + 3) & 0xFF;
                pixels[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        int rotation = image.getImageInfo().getRotationDegrees();
        if (rotation == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        bitmap.recycle();
        return rotated;
    }

    void captureCurrentFrame() {
        Bitmap frame = lastStyled;
        if (frame == null || frame.isRecycled()) {
            Toast.makeText(activity, "Кадр ещё не готов", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap copy = frame.copy(Bitmap.Config.ARGB_8888, false);
        new Thread(() -> saveBitmap(copy), "RoomVisionNeuralSave").start();
    }

    private void saveBitmap(Bitmap bitmap) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "RoomVision_Neural_Gothic_" + stamp + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RoomVisionNeural");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        boolean ok = false;
        if (uri != null) {
            try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                ok = out != null && bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                activity.getContentResolver().update(uri, values, null, null);
            } catch (Throwable error) {
                Log.e(TAG, "Photo save failed", error);
            }
        }
        bitmap.recycle();
        boolean saved = ok;
        activity.runOnUiThread(() -> Toast.makeText(activity,
                saved ? "Фото сохранено" : "Не удалось сохранить фото", Toast.LENGTH_SHORT).show());
    }

    void stop() {
        stopped = true;
        if (cameraProvider != null) {
            try { cameraProvider.unbindAll(); } catch (Throwable ignored) { }
            cameraProvider = null;
        }
        Bitmap frame = lastStyled;
        lastStyled = null;
        if (frame != null && !frame.isRecycled()) frame.recycle();
        try {
            analysisExecutor.execute(() -> {
                NeuralStyleEngine current = engine;
                engine = null;
                if (current != null) {
                    try { current.close(); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
        analysisExecutor.shutdown();
    }

    private void setStatus(String text) {
        activity.runOnUiThread(() -> statusView.setText(text));
    }
}
