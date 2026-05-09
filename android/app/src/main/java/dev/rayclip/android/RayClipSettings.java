package dev.rayclip.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

final class RayClipSettings {
    static final String PREFS = "rayclip";
    static final String KEY_API_URL = "api_url";
    static final String KEY_TOKEN = "token";
    static final String KEY_DEVICE_ID = "device_id";
    static final String KEY_DEVICE_NAME = "device_name";

    private RayClipSettings() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getApiUrl(Context context) {
        return prefs(context).getString(KEY_API_URL, BuildConfig.DEFAULT_API_URL);
    }

    static String getToken(Context context) {
        return prefs(context).getString(KEY_TOKEN, BuildConfig.DEFAULT_TOKEN);
    }

    static String getDeviceId(Context context) {
        SharedPreferences preferences = prefs(context);
        String existing = preferences.getString(KEY_DEVICE_ID, "");

        if (existing != null && !existing.isEmpty()) {
            return existing;
        }

        String generated = "android-" + UUID.randomUUID();
        preferences.edit().putString(KEY_DEVICE_ID, generated).apply();
        return generated;
    }

    static String getDeviceName(Context context) {
        return prefs(context).getString(KEY_DEVICE_NAME, "Android");
    }

    static boolean isConfigured(Context context) {
        String apiUrl = getApiUrl(context);
        String token = getToken(context);
        return apiUrl != null && apiUrl.startsWith("http") && token != null && !token.isEmpty();
    }
}
