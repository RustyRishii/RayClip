package dev.rayclip.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class RayClipSyncService extends Service {
    static final String ACTION_START = "dev.rayclip.android.START";
    static final String ACTION_STOP = "dev.rayclip.android.STOP";

    private static final String TAG = "RayClipSyncService";
    private static final String CHANNEL_ID = "rayclip-sync";
    private static final int NOTIFICATION_ID = 4317;
    private static final long REMOTE_POLL_MS = 2500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ClipboardManager clipboardManager;
    private RayClipApi api;
    private String lastLocalHash = "";
    private String lastAppliedRemoteClipId = "";
    private String lastAppliedRemoteHash = "";
    private boolean uploading;

    private final ClipboardManager.OnPrimaryClipChangedListener clipboardListener = this::uploadLocalClipboard;

    private final Runnable pollRemote = new Runnable() {
        @Override
        public void run() {
            fetchLatestRemoteClip();
            handler.postDelayed(this, REMOTE_POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        api = new RayClipApi(this);
        clipboardManager.addPrimaryClipChangedListener(clipboardListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        handler.removeCallbacks(pollRemote);
        handler.post(pollRemote);
        uploadLocalClipboard();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRemote);

        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener);
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void uploadLocalClipboard() {
        if (!RayClipSettings.isConfigured(this) || uploading) {
            return;
        }

        CharSequence text = readClipboardText();

        if (text == null || text.length() == 0) {
            return;
        }

        String value = text.toString();
        String hash = RayClipHash.sha256(value);

        if (hash.equals(lastLocalHash) || hash.equals(lastAppliedRemoteHash)) {
            return;
        }

        uploading = true;
        lastLocalHash = hash;

        api.postClip(value, new RayClipApi.Callback<RayClipApi.Clip>() {
            @Override
            public void onSuccess(RayClipApi.Clip value) {
                uploading = false;
                Log.d(TAG, "Uploaded local clipboard");
            }

            @Override
            public void onError(Exception error) {
                uploading = false;
                Log.w(TAG, "Failed to upload clipboard", error);
            }
        });
    }

    private void fetchLatestRemoteClip() {
        if (!RayClipSettings.isConfigured(this)) {
            return;
        }

        api.fetchLatest(new RayClipApi.Callback<RayClipApi.Clip>() {
            @Override
            public void onSuccess(RayClipApi.Clip clip) {
                if (clip == null || clip.id.isEmpty() || clip.text.isEmpty()) {
                    return;
                }

                if (clip.id.equals(lastAppliedRemoteClipId)) {
                    return;
                }

                if (clip.sourceDeviceId.equals(RayClipSettings.getDeviceId(RayClipSyncService.this))) {
                    return;
                }

                handler.post(() -> applyRemoteClip(clip));
            }

            @Override
            public void onError(Exception error) {
                Log.w(TAG, "Failed to fetch latest clipboard", error);
            }
        });
    }

    private void applyRemoteClip(RayClipApi.Clip clip) {
        ClipData data = ClipData.newPlainText("RayClip", clip.text);
        clipboardManager.setPrimaryClip(data);
        lastAppliedRemoteClipId = clip.id;
        lastAppliedRemoteHash = !clip.sha256.isEmpty() ? clip.sha256 : RayClipHash.sha256(clip.text);
        lastLocalHash = lastAppliedRemoteHash;
        Log.d(TAG, "Applied remote clipboard from " + clip.sourceDeviceId);
    }

    private CharSequence readClipboardText() {
        try {
            if (!clipboardManager.hasPrimaryClip()) {
                return null;
            }

            ClipData data = clipboardManager.getPrimaryClip();

            if (data == null || data.getItemCount() == 0) {
                return null;
            }

            return data.getItemAt(0).coerceToText(this);
        } catch (SecurityException error) {
            Log.w(TAG, "Clipboard read blocked. Enable/select RayClip as an input method.", error);
            return null;
        }
    }

    private Notification buildNotification() {
        ensureNotificationChannel();

        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent activityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                activityIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, RayClipSyncService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(RayClipSettings.getDeviceName(this))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(activityPendingIntent)
                .setOngoing(true)
                .addAction(R.drawable.ic_notification, "Stop", stopPendingIntent)
                .build();
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        manager.createNotificationChannel(channel);
    }
}
