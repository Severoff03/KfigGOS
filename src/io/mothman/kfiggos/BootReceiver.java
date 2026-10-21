// Made by Mothman
package io.mothman.kfiggos;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            context.startForegroundService(new Intent(context, KeyboardService.class));
        } catch (IllegalStateException | SecurityException exception) {
            Log.w("KfigGOS", "Service start blocked; start it again through ADB.", exception);
        }
    }
}
