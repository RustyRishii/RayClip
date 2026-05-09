package dev.rayclip.android;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * An AccessibilityService that monitors clipboard changes and syncs them
 * with the RayClip server. This approach lets the user keep Gboard (or any
 * other keyboard) as their default while still getting clipboard sync.
 *
 * The user enables this once in Settings → Accessibility → RayClip.
 */
public class RayClipAccessibilityService extends AccessibilityService {

    private static final String TAG = "RayClipA11y";
    private static final long REMOTE_POLL_MS = 2500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ClipboardManager clipboardManager;
    private RayClipApi api;
    private String lastLocalHash = "";
    private String lastAppliedRemoteClipId = "";
    private String lastAppliedRemoteHash = "";
    private boolean uploading;

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener =
            this::onClipboardChanged;

    private final Runnable pollRemote = new Runnable() {
        @Override
        public void run() {
            fetchLatestRemoteClip();
            handler.postDelayed(this, REMOTE_POLL_MS);
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility service connected — starting clipboard sync");

        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        api = new RayClipApi(this);

        clipboardManager.addPrimaryClipChangedListener(clipListener);
        handler.post(pollRemote);

        // Upload current clipboard immediately
        onClipboardChanged();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't need to handle accessibility events —
        // we only use this service for clipboard privileges.
    }

    @Override
    public void onInterrupt() {
        // Required override, nothing to do.
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRemote);
        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        }
        super.onDestroy();
    }

    // ========== CLIPBOARD → SERVER ==========

    private void onClipboardChanged() {
        if (!RayClipSettings.isConfigured(this) || uploading) return;

        CharSequence text = readClipboard();
        if (text == null || text.length() == 0) return;

        String value = text.toString();
        String hash = RayClipHash.sha256(value);

        if (hash.equals(lastLocalHash) || hash.equals(lastAppliedRemoteHash)) return;

        uploading = true;
        lastLocalHash = hash;

        api.postClip(value, new RayClipApi.Callback<RayClipApi.Clip>() {
            @Override
            public void onSuccess(RayClipApi.Clip clip) {
                uploading = false;
                Log.d(TAG, "Uploaded clipboard (" + value.length() + " chars)");
            }

            @Override
            public void onError(Exception error) {
                uploading = false;
                Log.w(TAG, "Upload failed", error);
            }
        });
    }

    // ========== SERVER → CLIPBOARD ==========

    private void fetchLatestRemoteClip() {
        if (!RayClipSettings.isConfigured(this)) return;

        api.fetchLatest(new RayClipApi.Callback<RayClipApi.Clip>() {
            @Override
            public void onSuccess(RayClipApi.Clip clip) {
                if (clip == null || clip.id.isEmpty() || clip.text.isEmpty()) return;
                if (clip.id.equals(lastAppliedRemoteClipId)) return;
                if (clip.sourceDeviceId.equals(
                        RayClipSettings.getDeviceId(RayClipAccessibilityService.this))) return;

                handler.post(() -> applyRemoteClip(clip));
            }

            @Override
            public void onError(Exception error) {
                Log.w(TAG, "Poll failed", error);
            }
        });
    }

    private void applyRemoteClip(RayClipApi.Clip clip) {
        try {
            ClipData data = ClipData.newPlainText("RayClip", clip.text);
            clipboardManager.setPrimaryClip(data);
            lastAppliedRemoteClipId = clip.id;
            lastAppliedRemoteHash = !clip.sha256.isEmpty()
                    ? clip.sha256 : RayClipHash.sha256(clip.text);
            lastLocalHash = lastAppliedRemoteHash;
            Log.d(TAG, "Applied remote clip from " + clip.sourceDeviceId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to write clipboard", e);
        }
    }

    // ========== HELPERS ==========

    private CharSequence readClipboard() {
        try {
            if (!clipboardManager.hasPrimaryClip()) return null;
            ClipData data = clipboardManager.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) return null;
            return data.getItemAt(0).coerceToText(this);
        } catch (SecurityException e) {
            Log.w(TAG, "Clipboard read blocked — expected on some devices", e);
            return null;
        }
    }
}
