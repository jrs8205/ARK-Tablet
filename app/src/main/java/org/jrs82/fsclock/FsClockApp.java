package org.jrs82.fsclock;

import android.app.Application;

public class FsClockApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsManager.get().init(this);
    }
}
