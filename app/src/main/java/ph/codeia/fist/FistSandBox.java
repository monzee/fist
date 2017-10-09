package ph.codeia.fist;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;

/*
 * This file is a part of the fist project.
 */

public class FistSandBox extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);
    }
}
