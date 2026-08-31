package com.roomvision.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.wysaid.nativePort.CGENativeLibrary;

import java.io.InputStream;

/**
 * Gothic world built on the ready-made MIT licensed android-gpuimage-plus engine.
 * The castle material is a CC0 Poly Haven texture downloaded by CI into app assets.
 */
final class CgeGothicEngine implements AutoCloseable {
    private static final String TAG = "RoomVisionCGEGothic";
    private static final String CASTLE_TEXTURE = "castle_wall_slates_diff_2k.jpg";

    // GPUImage Plus rule chain: preserve geometry, cool/darken scene, mix real castle stone,
    // add ready-made haze/edge/vignette effects. No handwritten per-pixel Gothic filter here.
    private static final String GOTHIC_RULE =
            "@beautify bilateral 3.0 7.0 1 " +
            "@adjust saturation 0.62 " +
            "@adjust contrast 1.32 " +
            "@adjust exposure -0.18 " +
            "@adjust colorbalance -0.10 0.00 0.08 " +
            "@blend softlight " + CASTLE_TEXTURE + " 52 " +
            "@style haze 0.10 -0.08 0.16 0.20 0.19 " +
            "@style edge 0.16 1.35 " +
            "@vignette 0.18 0.88";

    private final Context appContext;
    private volatile boolean ready;
    private volatile boolean disabled;

    CgeGothicEngine(Context context) {
        appContext = context.getApplicationContext();
    }

    void initialize() {
        if (ready || disabled) return;
        try {
            CGENativeLibrary.setLoadImageCallback(new CGENativeLibrary.LoadImageCallback() {
                @Override public Bitmap loadImage(String name, Object arg) {
                    try (InputStream in = appContext.getAssets().open(name)) {
                        return BitmapFactory.decodeStream(in);
                    } catch (Throwable t) {
                        Log.w(TAG, "Texture load failed: " + name, t);
                        return null;
                    }
                }

                @Override public void loadImageOK(Bitmap bmp, Object arg) {
                    if (bmp != null && !bmp.isRecycled()) bmp.recycle();
                }
            }, null);

            // Touch the library only here, lazily, so other modes never depend on native CGE startup.
            Class.forName("org.wysaid.nativePort.CGENativeLibrary", true, getClass().getClassLoader());
            ready = true;
            Log.i(TAG, "GPUImage Plus Gothic ready");
        } catch (Throwable t) {
            disabled = true;
            ready = false;
            Log.e(TAG, "GPUImage Plus Gothic unavailable; fallback will be used", t);
        }
    }

    boolean isReady() { return ready && !disabled; }

    Bitmap render(Bitmap source) {
        if (!isReady() || source == null || source.isRecycled()) return null;
        Bitmap input = null;
        try {
            int w = source.getWidth(), h = source.getHeight();
            int max = Math.max(w, h);
            if (max > 720) {
                float s = 720f / max;
                input = Bitmap.createScaledBitmap(source,
                        Math.max(2, Math.round(w * s)), Math.max(2, Math.round(h * s)), true);
            } else {
                input = source.copy(Bitmap.Config.ARGB_8888, false);
            }

            Bitmap result = CGENativeLibrary.filterImage_MultipleEffects(input, GOTHIC_RULE, 1.0f);
            if (result == null) throw new IllegalStateException("CGE returned null");
            if (result == input) input = null; // ownership is now result
            return result;
        } catch (Throwable t) {
            Log.e(TAG, "GPUImage Gothic frame failed; disabling native Gothic", t);
            disabled = true;
            ready = false;
            return null;
        } finally {
            if (input != null && !input.isRecycled()) input.recycle();
        }
    }

    @Override public void close() {
        ready = false;
        try { CGENativeLibrary.setLoadImageCallback(null, null); } catch (Throwable ignored) { }
    }
}
