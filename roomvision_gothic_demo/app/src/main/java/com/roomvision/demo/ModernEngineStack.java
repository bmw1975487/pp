package com.roomvision.demo;

import android.os.Build;
import android.util.Log;

import com.google.android.filament.Engine;
import com.google.android.filament.Filament;

/**
 * Bootstraps the modern Android rendering / AI stack used by Room Vision.
 *
 * Active today:
 *  - Filament with explicit Vulkan backend (fallback to DEFAULT if unavailable)
 *  - AGSL RuntimeShader capability detection (Android 13+)
 *
 * Packaged and ready for world-specific use:
 *  - MediaPipe Tasks Vision
 *  - LiteRT 2.x
 *
 * MediaPipe and LiteRT are intentionally capability-probed here rather than
 * forced to allocate a model on every app launch. World engines can load the
 * specific model they need later without paying startup RAM for unused worlds.
 */
final class ModernEngineStack implements AutoCloseable {
    private static final String TAG = "RoomVisionModern";

    private Engine filamentEngine;
    private String filamentBackend = "OFF";
    private boolean agsl;
    private boolean mediaPipe;
    private boolean liteRt;

    void initialize() {
        agsl = Build.VERSION.SDK_INT >= 33;
        mediaPipe = classExists("com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter");
        liteRt = classExists("com.google.ai.edge.litert.CompiledModel")
                || classExists("com.google.ai.edge.litert.Interpreter");

        try {
            Filament.init();
            try {
                filamentEngine = Engine.create(Engine.Backend.VULKAN);
                if (filamentEngine != null) filamentBackend = "VULKAN";
            } catch (Throwable vulkanError) {
                Log.w(TAG, "Filament Vulkan unavailable, trying default backend", vulkanError);
            }
            if (filamentEngine == null) {
                filamentEngine = Engine.create();
                filamentBackend = filamentEngine != null ? "DEFAULT" : "OFF";
            }
        } catch (Throwable filamentError) {
            filamentEngine = null;
            filamentBackend = "OFF";
            Log.w(TAG, "Filament unavailable; AGSL/CPU worlds remain active", filamentError);
        }

        Log.i(TAG, describe());
    }

    boolean isAgslAvailable() { return agsl; }
    boolean isFilamentAvailable() { return filamentEngine != null; }
    String filamentBackend() { return filamentBackend; }
    boolean isMediaPipeAvailable() { return mediaPipe; }
    boolean isLiteRtAvailable() { return liteRt; }

    String describe() {
        return "Filament=" + filamentBackend
                + " • AGSL=" + (agsl ? "ON" : "OFF")
                + " • MediaPipe=" + (mediaPipe ? "ON" : "OFF")
                + " • LiteRT=" + (liteRt ? "ON" : "OFF");
    }

    private boolean classExists(String name) {
        try {
            Class.forName(name, false, ModernEngineStack.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override public void close() {
        Engine e = filamentEngine;
        filamentEngine = null;
        if (e != null) {
            try { e.destroy(); } catch (Throwable t) { Log.w(TAG, "Filament destroy failed", t); }
        }
    }
}
