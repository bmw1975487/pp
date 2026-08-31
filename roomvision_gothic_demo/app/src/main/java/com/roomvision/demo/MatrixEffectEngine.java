package com.roomvision.demo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;

import java.util.Random;

/**
 * Dedicated animated Matrix world renderer.
 * This is intentionally not a color filter: it rebuilds the camera frame as a
 * digital luminance/edge map, adds motion trails, animated code rain,
 * scanlines and time-varying glitch slices.
 */
final class MatrixEffectEngine {
    private static final int MAX_SIDE = 560;
    private static final String GLYPHS = "01ABCDEFGHIJKLMNOPQRSTUVWXYZ#$%&*+-<>[]{}:/";

    private byte[] previousLuma;
    private int previousWidth = -1;
    private int previousHeight = -1;

    Bitmap process(Bitmap source) {
        Bitmap input = scaleForRealtime(source);
        int w = input.getWidth();
        int h = input.getHeight();
        int[] src = new int[w * h];
        input.getPixels(src, 0, w, 0, 0, w, h);

        byte[] luma = new byte[src.length];
        for (int i = 0; i < src.length; i++) {
            int c = src[i];
            int r = (c >> 16) & 255;
            int g = (c >> 8) & 255;
            int b = c & 255;
            luma[i] = (byte) ((r * 54 + g * 183 + b * 19) >> 8);
        }

        boolean havePrev = previousLuma != null && previousWidth == w && previousHeight == h;
        int[] dst = new int[src.length];
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x;
                int lum = luma[i] & 255;
                int gx = Math.abs((luma[i + 1] & 255) - (luma[i - 1] & 255));
                int gy = Math.abs((luma[i + w] & 255) - (luma[i - w] & 255));
                int edge = Math.min(255, gx + gy);
                int motion = havePrev ? Math.abs(lum - (previousLuma[i] & 255)) : 0;

                int base = Math.max(0, lum - 58);
                int green = clamp((int) (base * 0.72f + edge * 1.85f + motion * 1.30f));
                int red = clamp((int) (base * 0.035f + edge * 0.035f));
                int blue = clamp((int) (base * 0.07f + motion * 0.34f));

                // Make highlights look like emissive digital geometry instead of a green photo.
                if (edge > 48) {
                    green = clamp(green + 48);
                    blue = clamp(blue + 12);
                }
                dst[i] = 0xff000000 | (red << 16) | (green << 8) | blue;
            }
        }

        // Keep dimensions for motion trails on the next live frame.
        previousLuma = luma;
        previousWidth = w;
        previousHeight = h;

        Bitmap digital = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        digital.setPixels(dst, 0, w, 0, 0, w, h);
        if (!input.isRecycled()) input.recycle();

        Canvas canvas = new Canvas(digital);
        long now = SystemClock.uptimeMillis();
        drawScanlines(canvas, w, h);
        drawCodeRain(canvas, w, h, now, luma);
        drawGlitches(canvas, digital, w, h, now);
        drawHudPulse(canvas, w, h, now);
        return digital;
    }

    private Bitmap scaleForRealtime(Bitmap source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int max = Math.max(w, h);
        if (max <= MAX_SIDE) return source.copy(Bitmap.Config.ARGB_8888, false);
        float s = MAX_SIDE / (float) max;
        return Bitmap.createScaledBitmap(source, Math.max(2, Math.round(w * s)), Math.max(2, Math.round(h * s)), true);
    }

    private void drawScanlines(Canvas c, int w, int h) {
        Paint p = new Paint();
        p.setColor(Color.argb(42, 0, 0, 0));
        p.setStrokeWidth(1f);
        for (int y = 1; y < h; y += 4) c.drawLine(0, y, w, y, p);

        Paint glow = new Paint();
        glow.setColor(Color.argb(18, 0, 255, 85));
        glow.setStrokeWidth(1f);
        for (int y = 2; y < h; y += 13) c.drawLine(0, y, w, y, glow);
    }

    private void drawCodeRain(Canvas c, int w, int h, long now, byte[] luma) {
        final int cell = Math.max(12, w / 34);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(cell);
        p.setTextAlign(Paint.Align.CENTER);

        int columns = Math.max(1, w / cell);
        for (int col = 0; col <= columns; col++) {
            long seed = col * 1103515245L + 0x4d41545249584cL;
            float speed = 0.045f + ((seed >>> 8) & 31) * 0.0027f;
            float phase = (seed & 1023) / 1023f * h;
            float head = (phase + now * speed) % (h + cell * 12f) - cell * 6f;
            int x = col * cell + cell / 2;

            for (int trail = 0; trail < 7; trail++) {
                float y = head - trail * cell * 0.92f;
                if (y < -cell || y > h + cell) continue;
                int iy = Math.max(0, Math.min(h - 1, (int) y));
                int ix = Math.max(0, Math.min(w - 1, x));
                int sceneLum = luma[iy * w + ix] & 255;
                int alpha = Math.max(28, Math.min(205, 198 - trail * 25 + sceneLum / 5));
                if (trail == 0) p.setColor(Color.argb(238, 205, 255, 220));
                else if (trail == 1) p.setColor(Color.argb(alpha, 88, 255, 150));
                else p.setColor(Color.argb(alpha, 0, 225, 76));

                int glyphIndex = (int) Math.floorMod(seed + trail * 17L + now / 95L, GLYPHS.length());
                c.drawText(String.valueOf(GLYPHS.charAt(glyphIndex)), x, y, p);
            }
        }
    }

    private void drawGlitches(Canvas c, Bitmap bitmap, int w, int h, long now) {
        long frame = now / 140L;
        Random rnd = new Random(frame * 0x9E3779B97F4A7C15L);
        int bands = 1 + rnd.nextInt(3);
        Paint tint = new Paint();
        tint.setColor(Color.argb(35, 0, 255, 90));
        for (int i = 0; i < bands; i++) {
            if (rnd.nextFloat() > 0.48f) continue;
            int bh = 3 + rnd.nextInt(Math.max(4, h / 28));
            int y = rnd.nextInt(Math.max(1, h - bh));
            int shift = -w / 18 + rnd.nextInt(Math.max(2, w / 9));
            Rect src = new Rect(0, y, w, Math.min(h, y + bh));
            Rect dst = new Rect(shift, y, w + shift, Math.min(h, y + bh));
            c.drawBitmap(bitmap, src, dst, null);
            c.drawRect(0, y, w, y + bh, tint);
        }
    }

    private void drawHudPulse(Canvas c, int w, int h, long now) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 270.0));
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(Color.argb((int) (55 + pulse * 70), 0, 255, 105));
        int pad = Math.max(8, w / 42);
        int len = Math.max(18, w / 10);
        c.drawLine(pad, pad, pad + len, pad, p);
        c.drawLine(pad, pad, pad, pad + len, p);
        c.drawLine(w - pad - len, h - pad, w - pad, h - pad, p);
        c.drawLine(w - pad, h - pad - len, w - pad, h - pad, p);
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
