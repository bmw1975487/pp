package com.roomvision.demo;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class RoomVisionApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
        CrashReporter.record("APPLICATION_CREATE");
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                CrashReporter.record("ACTIVITY_CREATED " + activity.getClass().getSimpleName());
            }
            @Override public void onActivityStarted(Activity activity) {
                CrashReporter.record("ACTIVITY_STARTED " + activity.getClass().getSimpleName());
            }
            @Override public void onActivityResumed(Activity activity) {
                CrashReporter.record("ACTIVITY_RESUMED " + activity.getClass().getSimpleName());
                if (CrashReporter.shouldAutoOpenMaxOnce()) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        if (CrashReporter.shouldAutoOpenMaxOnce() && CrashReporter.sharePendingToMax(activity)) {
                            CrashReporter.markAutoOpened();
                        }
                    }, 700);
                }
            }
            @Override public void onActivityPaused(Activity activity) {
                CrashReporter.record("ACTIVITY_PAUSED " + activity.getClass().getSimpleName());
            }
            @Override public void onActivityStopped(Activity activity) {
                CrashReporter.record("ACTIVITY_STOPPED " + activity.getClass().getSimpleName());
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) {
                CrashReporter.record("ACTIVITY_DESTROYED " + activity.getClass().getSimpleName());
            }
        });
    }
}
