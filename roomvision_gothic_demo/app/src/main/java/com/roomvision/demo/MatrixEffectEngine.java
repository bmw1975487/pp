package com.roomvision.demo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;

import java.util.Random;

/** Dedicated animated Matrix world: scene edge map + dense falling code + scanlines + glitches. */
final class MatrixEffectEngine {
    private static final int MAX_SIDE = 560;
    private static final String GLYPHS = "01ABCDEFGHIJKLMNOPQRSTUVWXYZ#$%&*+-<>[]{}:/";
    private byte[] previousLuma;
    private int previousWidth = -1, previousHeight = -1;

    Bitmap process(Bitmap source) {
        Bitmap input = scaleForRealtime(source);
        int w = input.getWidth(), h = input.getHeight();
        int[] src = new int[w * h];
        input.getPixels(src, 0, w, 0, 0, w, h);
        byte[] luma = new byte[src.length];
        for (int i = 0; i < src.length; i++) {
            int c = src[i];
            luma[i] = (byte)((((c >> 16) & 255) * 54 + ((c >> 8) & 255) * 183 + (c & 255) * 19) >> 8);
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
                int base = Math.max(0, lum - 62);
                int green = clamp((int)(base * .55f + edge * 1.95f + motion * 1.45f));
                int red = clamp((int)(edge * .025f));
                int blue = clamp((int)(base * .05f + motion * .28f));
                if (edge > 45) green = clamp(green + 55);
                dst[i] = 0xff000000 | (red << 16) | (green << 8) | blue;
            }
        }
        previousLuma = luma; previousWidth = w; previousHeight = h;

        Bitmap digital = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        digital.setPixels(dst, 0, w, 0, 0, w, h);
        input.recycle();
        Canvas canvas = new Canvas(digital);
        long now = SystemClock.uptimeMillis();
        drawScanlines(canvas, w, h);
        drawCodeRain(canvas, w, h, now, luma);
        drawGlitches(canvas, digital, w, h, now);
        return digital;
    }

    private Bitmap scaleForRealtime(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight(), max = Math.max(w, h);
        if (max <= MAX_SIDE) return source.copy(Bitmap.Config.ARGB_8888, false);
        float s = MAX_SIDE / (float)max;
        return Bitmap.createScaledBitmap(source, Math.max(2, Math.round(w * s)), Math.max(2, Math.round(h * s)), true);
    }

    private void drawScanlines(Canvas c, int w, int h) {
        Paint dark = new Paint(); dark.setColor(Color.argb(48,0,0,0)); dark.setStrokeWidth(1f);
        for (int y = 1; y < h; y += 4) c.drawLine(0,y,w,y,dark);
        Paint glow = new Paint(); glow.setColor(Color.argb(24,0,255,90));
        for (int y = 2; y < h; y += 11) c.drawLine(0,y,w,y,glow);
    }

    /** Denser and faster than 4.2: ~46 columns, 10-symbol trails and quicker cycle. */
    private void drawCodeRain(Canvas c, int w, int h, long now, byte[] luma) {
        final int cell = Math.max(9, w / 46);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.MONOSPACE); p.setTextSize(cell); p.setTextAlign(Paint.Align.CENTER);
        int columns = Math.max(1, w / cell);
        for (int col = 0; col <= columns; col++) {
            long seed = col * 1103515245L + 0x4d41545249584cL;
            float speed = .075f + ((seed >>> 8) & 31) * .0038f;
            float phase = (seed & 2047) / 2047f * h;
            float head = (phase + now * speed) % (h + cell * 16f) - cell * 7f;
            int x = col * cell + cell / 2;
            for (int trail = 0; trail < 10; trail++) {
                float y = head - trail * cell * .88f;
                if (y < -cell || y > h + cell) continue;
                int iy = Math.max(0, Math.min(h - 1, (int)y));
                int ix = Math.max(0, Math.min(w - 1, x));
                int sceneLum = luma[iy * w + ix] & 255;
                int alpha = Math.max(24, Math.min(220, 215 - trail * 20 + sceneLum / 7));
                if (trail == 0) p.setColor(Color.argb(250,220,255,230));
                else if (trail < 3) p.setColor(Color.argb(alpha,82,255,145));
                else p.setColor(Color.argb(alpha,0,225,72));
                int gi = (int)Math.floorMod(seed + trail * 19L + now / 72L, GLYPHS.length());
                c.drawText(String.valueOf(GLYPHS.charAt(gi)), x, y, p);
            }
        }
    }

    private void drawGlitches(Canvas c, Bitmap bitmap, int w, int h, long now) {
        Random rnd = new Random((now / 120L) * 0x9E3779B97F4A7C15L);
        int bands = 2 + rnd.nextInt(3);
        Paint tint = new Paint(); tint.setColor(Color.argb(38,0,255,90));
        for (int i = 0; i < bands; i++) {
            if (rnd.nextFloat() > .62f) continue;
            int bh = 3 + rnd.nextInt(Math.max(4,h/30));
            int y = rnd.nextInt(Math.max(1,h-bh));
            int shift = -w/20 + rnd.nextInt(Math.max(2,w/10));
            Rect src = new Rect(0,y,w,Math.min(h,y+bh));
            Rect dst = new Rect(shift,y,w+shift,Math.min(h,y+bh));
            c.drawBitmap(bitmap,src,dst,null); c.drawRect(0,y,w,y+bh,tint);
        }
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
