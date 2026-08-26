package ru.nika.v3test;

public interface ProgressCallback {
    void onProgress(int percent, String stage, String message);
}
