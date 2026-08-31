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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class NeuralStyleEngine implements AutoCloseable {
    private static final String TAG = "RoomVisionNeural";
    private static final int STYLE_SIZE = 256;

    private final Context context;
    private Interpreter stylePredictor;
    private Interpreter styleTransformer;
    private GpuDelegate gpuDelegate;
    private ByteBuffer styleBottleneck;
    private int contentInputIndex = 0;
    private int styleInputIndex = 1;
    private int longSide = 384;
    private int configuredWidth = -1;
    private int configuredHeight = -1;
    private boolean gpuEnabled;

    NeuralStyleEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    void initialize() throws IOException {
        Interpreter.Options predictorOptions = new Interpreter.Options();
        predictorOptions.setNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        stylePredictor = new Interpreter(loadModelFile("style_predict.tflite"), predictorOptions);

        initializeTransformer(true);
        buildGothicStyle();
        Log.i(TAG, "STYLE_ENGINE_READY gpu=" + gpuEnabled + " longSide=" + longSide);
    }

    private void initializeTransformer(boolean preferGpu) throws IOException {
        if (styleTransformer != null) {
            try { styleTransformer.close(); } catch (Throwable ignored) { }
            styleTransformer = null;
        }
        if (gpuDelegate != null) {
            try { gpuDelegate.close(); } catch (Throwable ignored) { }
            gpuDelegate = null;
        }

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));
        if (preferGpu) {
            GpuDelegate candidate = null;
            try {
                candidate = new GpuDelegate();
                options.addDelegate(candidate);
                styleTransformer = new Interpreter(loadModelFile("style_transform.tflite"), options);
                gpuDelegate = candidate;
                gpuEnabled = true;
            } catch (Throwable gpuError) {
                Log.w(TAG, "GPU transformer unavailable; CPU fallback", gpuError);
                if (candidate != null) {
                    try { candidate.close(); } catch (Throwable ignored) { }
                }
                gpuDelegate = null;
                styleTransformer = null;
            }
        }
        if (styleTransformer == null) {
            Interpreter.Options cpu = new Interpreter.Options();
            cpu.setNumThreads(Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors())));
            styleTransformer = new Interpreter(loadModelFile("style_transform.tflite"), cpu);
            gpuEnabled = false;
        }
        configuredWidth = -1;
        configuredHeight = -1;
        resolveTransformInputs();
    }

    private void resolveTransformInputs() {
        int count = styleTransformer.getInputTensorCount();
        for (int i = 0; i < count; i++) {
            String name = styleTransformer.getInputTensor(i).name().toLowerCase(Locale.US);
            if (name.contains("content")) contentInputIndex = i;
            if (name.contains("style")) styleInputIndex = i;
        }
        Log.i(TAG, "TFLITE_INPUTS content=" + contentInputIndex + " style=" + styleInputIndex);
    }

    private void buildGothicStyle() throws IOException {
        Bitmap hero = null;
        Bitmap stone = null;
        try {
            hero = decodeAsset("worlds/gothic_castle/gothic_hero.jpg");
            stone = decodeAsset("worlds/gothic_castle/stone_albedo.jpg");
            ByteBuffer heroStyle = predictStyle(hero);
            ByteBuffer stoneStyle = predictStyle(stone);
            styleBottleneck = blendStyle(heroStyle, stoneStyle, 0.78f);
        } finally {
            if (hero != null && !hero.isRecycled()) hero.recycle();
            if (stone != null && !stone.isRecycled()) stone.recycle();
        }
    }

    private Bitmap decodeAsset(String path) throws IOException {
        try (java.io.InputStream in = context.getAssets().open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) throw new IOException("Cannot decode style asset: " + path);
            return bitmap;
        }
    }

    private ByteBuffer predictStyle(Bitmap bitmap) {
        ByteBuffer input = bitmapToFloatBuffer(bitmap, STYLE_SIZE, STYLE_SIZE);
        int outputBytes = stylePredictor.getOutputTensor(0).numBytes();
        ByteBuffer output = ByteBuffer.allocateDirect(outputBytes).order(ByteOrder.nativeOrder());
        stylePredictor.run(input, output);
        output.rewind();
        return output;
    }

    private ByteBuffer blendStyle(ByteBuffer a, ByteBuffer b, float aWeight) {
        a.rewind();
        b.rewind();
        int floats = Math.min(a.remaining(), b.remaining()) / 4;
        ByteBuffer out = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder());
        float bWeight = 1f - aWeight;
        for (int i = 0; i < floats; i++) {
            out.putFloat(a.getFloat() * aWeight + b.getFloat() * bWeight);
        }
        out.rewind();
        return out;
    }

    Bitmap processFrame(Bitmap source) {
        if (styleTransformer == null || styleBottleneck == null) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        try {
            return runTransform(source);
        } catch (Throwable firstFailure) {
            if (!gpuEnabled) {
                if (firstFailure instanceof RuntimeException) throw (RuntimeException) firstFailure;
                throw new IllegalStateException("CPU style inference failed", firstFailure);
            }
            Log.w(TAG, "GPU inference/resize failed; rebuilding transformer on CPU", firstFailure);
            try {
                initializeTransformer(false);
                return runTransform(source);
            } catch (IOException cpuInitError) {
                throw new IllegalStateException("Cannot initialize CPU style transformer", cpuInitError);
            }
        }
    }

    private Bitmap runTransform(Bitmap source) {
        int srcW = Math.max(1, source.getWidth());
        int srcH = Math.max(1, source.getHeight());
        int targetW;
        int targetH;
        if (srcW >= srcH) {
            targetW = longSide;
            targetH = align8(Math.max(128, Math.round(longSide * (srcH / (float) srcW))));
        } else {
            targetH = longSide;
            targetW = align8(Math.max(128, Math.round(longSide * (srcW / (float) srcH))));
        }
        configureTransformShape(targetW, targetH);

        ByteBuffer content = bitmapToFloatBuffer(source, targetW, targetH);
        ByteBuffer style = styleBottleneck.duplicate().order(ByteOrder.nativeOrder());
        style.rewind();
        ByteBuffer output = ByteBuffer.allocateDirect(styleTransformer.getOutputTensor(0).numBytes())
                .order(ByteOrder.nativeOrder());

        Object[] inputs = new Object[styleTransformer.getInputTensorCount()];
        inputs[contentInputIndex] = content;
        inputs[styleInputIndex] = style;
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, output);
        styleTransformer.runForMultipleInputsOutputs(inputs, outputs);
        output.rewind();
        return floatBufferToBitmap(output, targetW, targetH);
    }

    private void configureTransformShape(int width, int height) {
        if (width == configuredWidth && height == configuredHeight) return;
        styleTransformer.resizeInput(contentInputIndex, new int[]{1, height, width, 3});
        styleTransformer.allocateTensors();
        configuredWidth = width;
        configuredHeight = height;
        Log.i(TAG, "TFLITE_RESOLUTION " + width + "x" + height);
    }

    void setLongSide(int pixels) {
        int clamped = pixels <= 256 ? 256 : (pixels >= 512 ? 512 : 384);
        if (clamped == longSide) return;
        longSide = clamped;
        configuredWidth = -1;
        configuredHeight = -1;
    }

    int getLongSide() {
        return longSide;
    }

    boolean isGpuEnabled() {
        return gpuEnabled;
    }

    private int align8(int value) {
        return Math.max(128, ((value + 7) / 8) * 8);
    }

    private ByteBuffer bitmapToFloatBuffer(Bitmap bitmap, int width, int height) {
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        int[] pixels = new int[width * height];
        scaled.getPixels(pixels, 0, width, 0, 0, width, height);
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3 * 4).order(ByteOrder.nativeOrder());
        for (int c : pixels) {
            buffer.putFloat(((c >> 16) & 0xFF) / 255f);
            buffer.putFloat(((c >> 8) & 0xFF) / 255f);
            buffer.putFloat((c & 0xFF) / 255f);
        }
        buffer.rewind();
        if (scaled != bitmap && !scaled.isRecycled()) scaled.recycle();
        return buffer;
    }

    private Bitmap floatBufferToBitmap(ByteBuffer buffer, int width, int height) {
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int r = clamp255(Math.round(buffer.getFloat() * 255f));
            int g = clamp255(Math.round(buffer.getFloat() * 255f));
            int b = clamp255(Math.round(buffer.getFloat() * 255f));
            pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private MappedByteBuffer loadModelFile(String assetName) throws IOException {
        try (AssetFileDescriptor fd = context.getAssets().openFd(assetName);
             FileInputStream input = new FileInputStream(fd.getFileDescriptor());
             FileChannel channel = input.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    @Override
    public void close() {
        if (stylePredictor != null) {
            stylePredictor.close();
            stylePredictor = null;
        }
        if (styleTransformer != null) {
            styleTransformer.close();
            styleTransformer = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
    }
}
