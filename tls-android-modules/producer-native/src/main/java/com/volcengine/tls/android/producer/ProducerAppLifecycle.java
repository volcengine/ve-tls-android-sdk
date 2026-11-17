package com.volcengine.tls.android.producer;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.os.Bundle;

/** Translates application UI visibility into Producer work scheduled off the main thread. */
final class ProducerAppLifecycle implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
    private final Application application;
    private final Runnable background;
    private final Runnable foreground;
    private boolean uiHidden;

    ProducerAppLifecycle(Application application, Runnable background, Runnable foreground) {
        this.application = application;
        this.background = background;
        this.foreground = foreground;
    }

    void register() {
        application.registerComponentCallbacks(this);
        application.registerActivityLifecycleCallbacks(this);
    }

    void unregister() {
        application.unregisterComponentCallbacks(this);
        application.unregisterActivityLifecycleCallbacks(this);
    }

    @Override
    public void onTrimMemory(int level) {
        // UI_HIDDEN is a process-wide signal and works even if open completed after Activity start.
        if (level == TRIM_MEMORY_UI_HIDDEN && !uiHidden) {
            uiHidden = true;
            background.run();
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (uiHidden) {
            uiHidden = false;
            foreground.run();
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
    @Override public void onConfigurationChanged(Configuration configuration) {}
    @Override public void onLowMemory() {}
}
