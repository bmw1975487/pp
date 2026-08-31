package com.roomvision.demo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Lightweight four-mode live-art engine. No OpenCV/TFLite/native runtime. */
final class OpenCvFilterEngine {
    private static final int MAX_SIDE = 640;

    Bitmap process(Bitmap source, FilterType type) {
        Bitmap input = scaleForRealtime(source);
        switch (type) {
            case CRAYON: return crayon(input);
            case ASCII: return ascii(input);
            case BLUEPRINT: return blueprint(input);
            default: return input;
        }
    }

    private Bitmap scaleForRealtime(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int max = Math.max(w, h);
        if (max <= MAX_SIDE) return source.copy(Bitmap.Config.ARGB_8888, false);
        float s = MAX_SIDE / (float) max;
        return Bitmap.createScaledBitmap(source,
                Math.max(2, Math.round(w * s)), Math.max(2, Math.round(h * s)), true);
    }

    /** Bright wax-crayon look: broad colour blocks, hard dark contours and paper grain. */
    private Bitmap crayon(Bitmap in) {
        int w = in.getWidth(), h = in.getHeight();
        int[] p = pixels(in);
        int[] out = new int[p.length];
        int[] lum = new int[p.length];
        for (int i = 0; i < p.length; i++) lum[i] = luma(p[i]);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int c = p[i];
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;

                // Large, saturated wax colour blocks.
                r = quantBoost(r, 52); g = quantBoost(g, 52); b = quantBoost(b, 52);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                if (max - min > 18) {
                    r = clamp((int)((r - 128) * 1.22 + 136));
                    g = clamp((int)((g - 128) * 1.22 + 136));
                    b = clamp((int)((b - 128) * 1.22 + 136));
                }

                // Deterministic wax/paper grain, not flickering frame noise.
                int grain = hashNoise(x, y) - 128;
                r = clamp(r + grain / 10 + 5);
                g = clamp(g + grain / 12 + 4);
                b = clamp(b + grain / 11 + 3);

                if (x > 0 && x < w - 1 && y > 0 && y < h - 1) {
                    int gx = Math.abs(lum[i + 1] - lum[i - 1]);
                    int gy = Math.abs(lum[i + w] - lum[i - w]);
                    int edge = gx + gy;
                    if (edge > 68) {
                        int ink = Math.max(16, 74 - edge / 5);
                        r = (r * ink) / 100;
                        g = (g * ink) / 100;
                        b = (b * ink) / 100;
                    }
                }
                out[i] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        return fromPixelsAndRecycle(in, out);
    }

    /** Full-frame terminal/ASCII world. */
    private Bitmap ascii(Bitmap in) {
        int w = in.getWidth(), h = in.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.rgb(3, 8, 5));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(Math.max(9, w / 58f));
        paint.setColor(Color.rgb(104, 255, 154));
        String chars = "@%#*+=-:. ";
        int stepX = Math.max(8, w / 64);
        int stepY = Math.max(10, (int)(stepX * 1.35f));
        int[] p = pixels(in);
        for (int y = stepY; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int v = luma(p[Math.min(p.length - 1, y * w + x)]);
                int idx = Math.min(chars.length() - 1, (255 - v) * (chars.length() - 1) / 255);
                int a = 110 + v / 2;
                paint.setAlpha(Math.min(255, a));
                canvas.drawText(String.valueOf(chars.charAt(idx)), x, y, paint);
            }
        }
        in.recycle();
        return out;
    }

    /** Real blueprint: deep blueprint paper, white/cyan technical contours, grid and drafting marks. */
    private Bitmap blueprint(Bitmap in) {
        int w = in.getWidth(), h = in.getHeight();
        int[] src = pixels(in);
        int[] lum = new int[src.length];
        for (int i = 0; i < src.length; i++) lum[i] = luma(src[i]);
        int[] dst = new int[src.length];

        final int paperR = 10, paperG = 48, paperB = 112;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int edge = 0;
                if (x > 0 && x < w - 1 && y > 0 && y < h - 1) {
                    int gx = Math.abs(lum[i + 1] - lum[i - 1]);
                    int gy = Math.abs(lum[i + w] - lum[i - w]);
                    edge = Math.min(255, (gx + gy) * 2);
                }
                int scene = lum[i];
                int r = clamp(paperR + scene / 28 + edge * 3 / 4);
                int g = clamp(paperG + scene / 12 + edge * 4 / 5);
                int b = clamp(paperB + scene / 8 + edge);
                if (edge > 80) { r = 210; g = 242; b = 255; }
                dst[i] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(dst, 0, w, 0, 0, w, h);
        in.recycle();

        Canvas c = new Canvas(out);
        Paint grid = new Paint();
        grid.setStrokeWidth(1f);
        grid.setColor(Color.argb(38, 190, 225, 255));
        int minor = Math.max(18, w / 24);
        for (int x = 0; x < w; x += minor) c.drawLine(x, 0, x, h, grid);
        for (int y = 0; y < h; y += minor) c.drawLine(0, y, w, y, grid);
        grid.setColor(Color.argb(65, 220, 242, 255));
        int major = minor * 5;
        for (int x = 0; x < w; x += major) c.drawLine(x, 0, x, h, grid);
        for (int y = 0; y < h; y += major) c.drawLine(0, y, w, y, grid);

        Paint marks = new Paint(Paint.ANTI_ALIAS_FLAG);
        marks.setColor(Color.argb(150, 225, 248, 255));
        marks.setStrokeWidth(Math.max(1.2f, w / 420f));
        int m = Math.max(12, w / 30), len = Math.max(28, w / 8);
        c.drawLine(m, m, m + len, m, marks); c.drawLine(m, m, m, m + len, marks);
        c.drawLine(w - m - len, h - m, w - m, h - m, marks); c.drawLine(w - m, h - m - len, w - m, h - m, marks);
        return out;
    }

    private int[] pixels(Bitmap b) {
        int[] p = new int[b.getWidth() * b.getHeight()];
        b.getPixels(p, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return p;
    }

    private Bitmap fromPixelsAndRecycle(Bitmap in, int[] p) {
        Bitmap out = Bitmap.createBitmap(in.getWidth(), in.getHeight(), Bitmap.Config.ARGB_8888);
        out.setPixels(p, 0, in.getWidth(), 0, 0, in.getWidth(), in.getHeight());
        in.recycle();
        return out;
    }

    private int luma(int c) { return (((c >> 16) & 255) * 54 + ((c >> 8) & 255) * 183 + (c & 255) * 19) >> 8; }
    private int quantBoost(int v, int step) { int q = Math.min(255, (v / step) * step + step / 2); return clamp((int)((q - 128) * 1.12 + 132)); }
    private int hashNoise(int x, int y) { int n = x * 374761393 + y * 668265263; n = (n ^ (n >>> 13)) * 1274126177; return (n ^ (n >>> 16)) & 255; }
    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
