package com.example.thoikhoabieu;

import android.app.Application;

import com.example.thoikhoabieu.notify.NotificationHelper;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
    }
}
