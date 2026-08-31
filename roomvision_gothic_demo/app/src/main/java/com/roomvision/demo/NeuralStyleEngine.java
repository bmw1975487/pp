package com.roomvision.demo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class NeuralStyleEngine implements AutoCloseable {
    private static final String TAG = "RoomVisionFilters";
    private static final int STYLE_SIZE = 256;
    private final Context context;
    private final EnumMap<FilterType, ByteBuffer> styles = new EnumMap<>(FilterType.class);
    private Interpreter predictor;
    private Interpreter transformer;
    private GpuDelegate gpuDelegate;
    private int contentInputIndex = 0;
    private int styleInputIndex = 1;
    private int longSide = 256;
    private int configuredWidth = -1, configuredHeight = -1;
    private boolean gpuEnabled;

    NeuralStyleEngine(Context context) { this.context = context.getApplicationContext(); }

    void initialize() throws IOException {
        Interpreter.Options p = new Interpreter.Options();
        p.setNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        predictor = new Interpreter(loadModelFile("style_predict.tflite"), p);
        initializeTransformer(true);
        styles.put(FilterType.VAN_GOGH, predictStyle(decodeAsset("styles/van_gogh.jpg")));
        styles.put(FilterType.KANDINSKY, predictStyle(decodeAsset("styles/kandinsky.jpg")));
        Log.i(TAG, "NEURAL_READY gpu=" + gpuEnabled + " styles=" + styles.size());
    }

    private void initializeTransformer(boolean preferGpu) throws IOException {
        closeTransformer();
        if (preferGpu) {
            GpuDelegate candidate = null;
            try {
                candidate = new GpuDelegate();
                Interpreter.Options o = new Interpreter.Options();
                o.addDelegate(candidate);
                transformer = new Interpreter(loadModelFile("style_transform.tflite"), o);
                gpuDelegate = candidate;
                gpuEnabled = true;
            } catch (Throwable e) {
                Log.w(TAG, "GPU unavailable, using CPU", e);
                if (candidate != null) try { candidate.close(); } catch (Throwable ignored) { }
                transformer = null;
                gpuDelegate = null;
            }
        }
        if (transformer == null) {
            Interpreter.Options o = new Interpreter.Options();
            o.setNumThreads(Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors())));
            transformer = new Interpreter(loadModelFile("style_transform.tflite"), o);
            gpuEnabled = false;
        }
        resolveInputs();
        configuredWidth = configuredHeight = -1;
    }

    private void resolveInputs() {
        contentInputIndex = 0;
        styleInputIndex = Math.min(1, transformer.getInputTensorCount() - 1);
        for (int i = 0; i < transformer.getInputTensorCount(); i++) {
            String n = transformer.getInputTensor(i).name().toLowerCase(Locale.US);
            if (n.contains("content")) contentInputIndex = i;
            if (n.contains("style")) styleInputIndex = i;
        }
    }

    private Bitmap decodeAsset(String path) throws IOException {
        try (java.io.InputStream in = context.getAssets().open(path)) {
            Bitmap b = BitmapFactory.decodeStream(in);
            if (b == null) throw new IOException("Cannot decode " + path);
            return b;
        }
    }

    private ByteBuffer predictStyle(Bitmap bitmap) {
        try {
            ByteBuffer input = bitmapToFloatBuffer(bitmap, STYLE_SIZE, STYLE_SIZE);
            ByteBuffer output = ByteBuffer.allocateDirect(predictor.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder());
            predictor.run(input, output);
            output.rewind();
            return output;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    Bitmap processFrame(Bitmap source, FilterType type) {
        ByteBuffer style = styles.get(type);
        if (style == null) throw new IllegalArgumentException("No neural style for " + type);
        try {
            return runTransform(source, style);
        } catch (Throwable first) {
            if (!gpuEnabled) throw new IllegalStateException("Neural CPU inference failed", first);
            Log.w(TAG, "GPU inference failed; retrying CPU", first);
            try {
                initializeTransformer(false);
                return runTransform(source, style);
            } catch (Throwable second) {
                throw new IllegalStateException("Neural inference failed", second);
            }
        }
    }

    private Bitmap runTransform(Bitmap source, ByteBuffer styleSource) {
        int sw = Math.max(1, source.getWidth()), sh = Math.max(1, source.getHeight());
        int tw, th;
        if (sw >= sh) {
            tw = longSide;
            th = align8(Math.max(128, Math.round(longSide * (sh / (float) sw))));
        } else {
            th = longSide;
            tw = align8(Math.max(128, Math.round(longSide * (sw / (float) sh))));
        }
        configure(tw, th);
        ByteBuffer content = bitmapToFloatBuffer(source, tw, th);
        ByteBuffer style = styleSource.duplicate().order(ByteOrder.nativeOrder());
        style.rewind();
        ByteBuffer output = ByteBuffer.allocateDirect(transformer.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder());
        Object[] inputs = new Object[transformer.getInputTensorCount()];
        inputs[contentInputIndex] = content;
        inputs[styleInputIndex] = style;
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, output);
        transformer.runForMultipleInputsOutputs(inputs, outputs);
        output.rewind();
        return floatBufferToBitmap(output, tw, th);
    }

    private void configure(int w, int h) {
        if (w == configuredWidth && h == configuredHeight) return;
        transformer.resizeInput(contentInputIndex, new int[]{1, h, w, 3});
        transformer.allocateTensors();
        configuredWidth = w;
        configuredHeight = h;
    }

    void setLongSide(int px) {
        longSide = px <= 256 ? 256 : (px >= 512 ? 512 : 384);
        configuredWidth = configuredHeight = -1;
    }

    int getLongSide() { return longSide; }
    boolean isGpuEnabled() { return gpuEnabled; }
    private int align8(int v) { return Math.max(128, ((v + 7) / 8) * 8); }

    private ByteBuffer bitmapToFloatBuffer(Bitmap bitmap, int w, int h) {
        Bitmap s = Bitmap.createScaledBitmap(bitmap, w, h, true);
        int[] p = new int[w * h];
        s.getPixels(p, 0, w, 0, 0, w, h);
        ByteBuffer b = ByteBuffer.allocateDirect(w * h * 12).order(ByteOrder.nativeOrder());
        for (int c : p) {
            b.putFloat(((c >> 16) & 255) / 255f);
            b.putFloat(((c >> 8) & 255) / 255f);
            b.putFloat((c & 255) / 255f);
        }
        b.rewind();
        if (s != bitmap && !s.isRecycled()) s.recycle();
        return b;
    }

    private Bitmap floatBufferToBitmap(ByteBuffer b, int w, int h) {
        int[] p = new int[w * h];
        for (int i = 0; i < p.length; i++) {
            int r = clamp(Math.round(b.getFloat() * 255));
            int g = clamp(Math.round(b.getFloat() * 255));
            int bl = clamp(Math.round(b.getFloat() * 255));
            p[i] = 0xff000000 | (r << 16) | (g << 8) | bl;
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(p, 0, w, 0, 0, w, h);
        return out;
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private MappedByteBuffer loadModelFile(String name) throws IOException {
        try (AssetFileDescriptor fd = context.getAssets().openFd(name);
             FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileChannel ch = in.getChannel()) {
            return ch.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    private void closeTransformer() {
        if (transformer != null) try { transformer.close(); } catch (Throwable ignored) { }
        transformer = null;
        if (gpuDelegate != null) try { gpuDelegate.close(); } catch (Throwable ignored) { }
        gpuDelegate = null;
    }

    @Override public void close() {
        if (predictor != null) try { predictor.close(); } catch (Throwable ignored) { }
        predictor = null;
        closeTransformer();
        styles.clear();
    }
}
