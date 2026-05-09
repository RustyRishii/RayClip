package dev.rayclip.android;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RayClipApi {
    interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception error);
    }

    static final class Clip {
        final String id;
        final String text;
        final String sourceDeviceId;
        final String sourceDeviceName;
        final String sha256;

        Clip(String id, String text, String sourceDeviceId, String sourceDeviceName, String sha256) {
            this.id = id;
            this.text = text;
            this.sourceDeviceId = sourceDeviceId;
            this.sourceDeviceName = sourceDeviceName;
            this.sha256 = sha256;
        }
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    RayClipApi(Context context) {
        this.context = context.getApplicationContext();
    }

    void postClip(String text, Callback<Clip> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("sourceDeviceId", RayClipSettings.getDeviceId(context));
                body.put("sourceDeviceName", RayClipSettings.getDeviceName(context));

                JSONObject response = request("POST", "/v1/clips", body);
                callback.onSuccess(parseClip(response.optJSONObject("clip")));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    void fetchLatest(Callback<Clip> callback) {
        executor.execute(() -> {
            try {
                JSONObject response = request("GET", "/v1/clips/latest", null);
                callback.onSuccess(parseClip(response.optJSONObject("clip")));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    void checkHealth(Callback<String> callback) {
        executor.execute(() -> {
            try {
                String baseUrl = RayClipSettings.getApiUrl(context);
                if (baseUrl == null || baseUrl.isEmpty()) {
                    throw new IllegalStateException("Missing API URL");
                }

                URL url = new URL(stripTrailingSlash(baseUrl) + "/health");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Accept", "application/json");

                int status = connection.getResponseCode();
                String responseBody = readResponse(connection, status);

                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("Health check failed HTTP " + status + ": " + responseBody);
                }

                callback.onSuccess("Connected: " + stripTrailingSlash(baseUrl));
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        String baseUrl = RayClipSettings.getApiUrl(context);

        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("Missing API URL");
        }

        URL url = new URL(stripTrailingSlash(baseUrl) + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Authorization", "Bearer " + RayClipSettings.getToken(context));
        connection.setRequestProperty("Accept", "application/json");

        if (body != null) {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length));

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload);
            }
        }

        int status = connection.getResponseCode();
        String responseBody = readResponse(connection, status);

        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + ": " + responseBody);
        }

        return new JSONObject(responseBody);
    }

    private static String readResponse(HttpURLConnection connection, int status) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            StringBuilder builder = new StringBuilder();
            String row;

            while ((row = reader.readLine()) != null) {
                builder.append(row);
            }

            return builder.toString();
        }
    }

    private static Clip parseClip(JSONObject object) {
        if (object == null) {
            return null;
        }

        return new Clip(
                object.optString("id", ""),
                object.optString("text", ""),
                object.optString("sourceDeviceId", ""),
                object.optString("sourceDeviceName", ""),
                object.optString("sha256", "")
        );
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
