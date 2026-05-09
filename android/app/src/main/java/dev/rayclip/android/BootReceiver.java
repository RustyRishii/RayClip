package dev.rayclip.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Starts the clipboard sync service automatically when the device boots.
 * This ensures the user never has to manually tap "Start Sync Service".
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "RayClipBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            if (!RayClipSettings.isConfigured(context)) {
                Log.d(TAG, "Skipping auto-start: not configured yet");
                return;
            }

            Log.d(TAG, "Auto-starting sync service after: " + action);
            Intent serviceIntent = new Intent(context, RayClipSyncService.class)
                    .setAction(RayClipSyncService.ACTION_START);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
