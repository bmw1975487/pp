package com.roomvision.demo;

import android.media.Image;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Lightweight door candidate detector. No ML model: works on a screen-oriented luminance grid. */
final class DoorTargetDetector {
    static final class Result {
        final float left, top, right, bottom, score;
        Result(float left, float top, float right, float bottom, float score) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom; this.score = score;
        }
        boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    private static final int GW = 48;
    private static final int GH = 72;
    private static final int N = GW * GH;
    private final FloatBuffer viewCoords = ByteBuffer.allocateDirect(N * 2 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final FloatBuffer imageCoords = ByteBuffer.allocateDirect(N * 2 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final float[] lum = new float[N];
    private final float[] vEdge = new float[N];
    private final float[] hEdge = new float[N];
    private Result stable;

    DoorTargetDetector() {
        for (int y = 0; y < GH; y++) {
            for (int x = 0; x < GW; x++) {
                viewCoords.put((x + 0.5f) / GW);
                viewCoords.put((y + 0.5f) / GH);
            }
        }
        viewCoords.position(0);
    }

    void reset() { stable = null; }
    Result current() { return stable; }

    Result detect(Frame frame, Image image) {
        if (frame == null || image == null || image.getPlanes().length == 0) return stable;
        try {
            viewCoords.position(0);
            imageCoords.position(0);
            frame.transformCoordinates2d(
                    Coordinates2d.VIEW_NORMALIZED,
                    viewCoords,
                    Coordinates2d.IMAGE_PIXELS,
                    imageCoords);
            imageCoords.position(0);
        } catch (Throwable ignored) {
            return stable;
        }

        Image.Plane p = image.getPlanes()[0];
        ByteBuffer yBuf = p.getBuffer().duplicate();
        int rowStride = p.getRowStride();
        int pixelStride = Math.max(1, p.getPixelStride());
        int iw = image.getWidth(), ih = image.getHeight();

        for (int i = 0; i < N; i++) {
            float fx = imageCoords.get();
            float fy = imageCoords.get();
            int px = clamp(Math.round(fx), 0, iw - 1);
            int py = clamp(Math.round(fy), 0, ih - 1);
            int off = py * rowStride + px * pixelStride;
            lum[i] = (off >= 0 && off < yBuf.limit()) ? ((yBuf.get(off) & 0xff) / 255f) : 0.5f;
        }

        for (int y = 1; y < GH - 1; y++) {
            for (int x = 1; x < GW - 1; x++) {
                int i = y * GW + x;
                vEdge[i] = Math.abs(lum[i + 1] - lum[i - 1]);
                hEdge[i] = Math.abs(lum[i + GW] - lum[i - GW]);
            }
        }

        float bestScore = -1f;
        int bx0 = 0, by0 = 0, bx1 = 0, by1 = 0;
        // Search tall rectangles in screen orientation. Door bottoms should live in the lower half.
        for (int h = 30; h <= 60; h += 3) {
            for (int w = 10; w <= 23; w += 2) {
                float aspect = h / (float) w;
                if (aspect < 1.65f || aspect > 4.2f) continue;
                for (int y0 = 3; y0 + h < GH - 2; y0 += 3) {
                    int y1 = y0 + h;
                    if (y1 < GH * 0.60f) continue;
                    for (int x0 = 2; x0 + w < GW - 2; x0 += 2) {
                        int x1 = x0 + w;
                        float left = avgVertical(x0, y0, y1);
                        float right = avgVertical(x1, y0, y1);
                        float top = avgHorizontal(y0, x0, x1);
                        float inside = avgInsideEdges(x0 + 2, y0 + 3, x1 - 2, y1 - 2);
                        float bottomBias = Math.min(1f, y1 / (GH * 0.92f));
                        float aspectBias = 1f - Math.min(1f, Math.abs(aspect - 2.25f) / 2.0f);
                        float sideBalance = 1f - Math.min(1f, Math.abs(left - right) * 3.0f);
                        float score = (left + right) * 2.4f + top * 0.75f
                                + bottomBias * 0.12f + aspectBias * 0.10f + sideBalance * 0.08f
                                - inside * 0.55f;
                        if (score > bestScore) {
                            bestScore = score; bx0 = x0; by0 = y0; bx1 = x1; by1 = y1;
                        }
                    }
                }
            }
        }

        if (bestScore < 0.16f) return stable;
        Result found = new Result(
                clamp01(bx0 / (float) GW - 0.015f),
                clamp01(by0 / (float) GH - 0.015f),
                clamp01(bx1 / (float) GW + 0.015f),
                clamp01(by1 / (float) GH + 0.015f),
                bestScore);

        if (stable == null) {
            stable = found;
        } else {
            float overlap = iou(stable, found);
            float a = overlap > 0.20f ? 0.28f : (found.score > stable.score * 1.18f ? 0.55f : 0.0f);
            if (a > 0f) {
                stable = new Result(
                        mix(stable.left, found.left, a), mix(stable.top, found.top, a),
                        mix(stable.right, found.right, a), mix(stable.bottom, found.bottom, a),
                        mix(stable.score, found.score, a));
            }
        }
        return stable;
    }

    private float avgVertical(int x, int y0, int y1) {
        x = clamp(x, 1, GW - 2); float s = 0f; int n = 0;
        for (int y = Math.max(1, y0); y <= Math.min(GH - 2, y1); y += 2) { s += vEdge[y * GW + x]; n++; }
        return n == 0 ? 0f : s / n;
    }

    private float avgHorizontal(int y, int x0, int x1) {
        y = clamp(y, 1, GH - 2); float s = 0f; int n = 0;
        for (int x = Math.max(1, x0); x <= Math.min(GW - 2, x1); x += 2) { s += hEdge[y * GW + x]; n++; }
        return n == 0 ? 0f : s / n;
    }

    private float avgInsideEdges(int x0, int y0, int x1, int y1) {
        if (x1 <= x0 || y1 <= y0) return 0f;
        float s = 0f; int n = 0;
        for (int y = Math.max(1, y0); y <= Math.min(GH - 2, y1); y += 4) {
            for (int x = Math.max(1, x0); x <= Math.min(GW - 2, x1); x += 4) {
                int i = y * GW + x; s += (vEdge[i] + hEdge[i]) * 0.5f; n++;
            }
        }
        return n == 0 ? 0f : s / n;
    }

    private static float iou(Result a, Result b) {
        float l = Math.max(a.left, b.left), t = Math.max(a.top, b.top);
        float r = Math.min(a.right, b.right), bt = Math.min(a.bottom, b.bottom);
        float inter = Math.max(0f, r - l) * Math.max(0f, bt - t);
        float aa = Math.max(0f, a.right - a.left) * Math.max(0f, a.bottom - a.top);
        float bb = Math.max(0f, b.right - b.left) * Math.max(0f, b.bottom - b.top);
        return inter / Math.max(0.0001f, aa + bb - inter);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
