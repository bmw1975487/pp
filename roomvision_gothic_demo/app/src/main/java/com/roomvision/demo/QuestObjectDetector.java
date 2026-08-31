package com.roomvision.demo;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;

/**
 * Lightweight quest vision trigger. The bundled COCO detector fires for
 * plant-like targets (notably "potted plant"). A custom flower detector can
 * later replace the model without changing the trigger logic.
 */
final class QuestObjectDetector implements AutoCloseable {
    private static final String TAG = "RoomVisionQuestVision";
    private static final long TRIGGER_COOLDOWN_MS = 3500L;

    private ObjectDetector detector;
    private ToneGenerator tone;
    private int frameCounter;
    private long lastTriggerMs;
    private volatile String lastHit;

    void initialize(Context context) {
        try {
            BaseOptions base = BaseOptions.builder()
                    .setModelAssetPath("object_detector.tflite")
                    .build();
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.IMAGE)
                    .setMaxResults(8)
                    .setScoreThreshold(0.42f)
                    .build();
            detector = ObjectDetector.createFromOptions(context.getApplicationContext(), options);
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95);
            Log.i(TAG, "Object detector ready: target=flower/plant");
        } catch (Throwable t) {
            Log.w(TAG, "Object detector unavailable", t);
            close();
        }
    }

    boolean isReady() { return detector != null; }

    /** Runs only every eighth camera frame to keep live rendering responsive. */
    String inspect(android.graphics.Bitmap bitmap) {
        if (detector == null || bitmap == null || bitmap.isRecycled()) return null;
        frameCounter++;
        if ((frameCounter & 7) != 0) return null;
        MPImage image = null;
        try {
            image = new BitmapImageBuilder(bitmap).build();
            var result = detector.detect(image);
            for (var detection : result.detections()) {
                for (var category : detection.categories()) {
                    String name = category.categoryName();
                    if (name == null) continue;
                    String n = name.trim().toLowerCase(java.util.Locale.US);
                    if (n.contains("flower") || n.contains("plant") || n.contains("potted")) {
                        lastHit = name + " " + Math.round(category.score() * 100f) + "%";
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastTriggerMs >= TRIGGER_COOLDOWN_MS) {
                            lastTriggerMs = now;
                            try { if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 260); }
                            catch (Throwable ignored) { }
                        }
                        return lastHit;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Detection frame failed", t);
        } finally {
            if (image != null) try { image.close(); } catch (Throwable ignored) { }
        }
        return null;
    }

    String lastHit() { return lastHit; }

    @Override public void close() {
        ObjectDetector d = detector;
        detector = null;
        if (d != null) try { d.close(); } catch (Throwable ignored) { }
        ToneGenerator tg = tone;
        tone = null;
        if (tg != null) try { tg.release(); } catch (Throwable ignored) { }
    }
}
