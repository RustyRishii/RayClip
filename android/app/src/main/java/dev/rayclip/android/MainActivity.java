package dev.rayclip.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText apiUrlInput;
    private EditText tokenInput;
    private EditText deviceIdInput;
    private EditText deviceNameInput;
    private TextView statusText;
    private RayClipApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermissionIfNeeded();
        api = new RayClipApi(this);
        setContentView(buildContentView());
        loadSettings();
        autoStartSyncServiceIfConfigured();
    }

    private LinearLayout buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 48, 36, 36);

        TextView title = new TextView(this);
        title.setText("RayClip");
        title.setTextSize(28);
        title.setTextColor(0xFF0F172A);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        TextView summary = new TextView(this);
        summary.setText("Configure the sync server below, then enable the Accessibility Service to start syncing. You can keep using Gboard as your keyboard.");
        summary.setTextColor(0xFF475569);
        summary.setTextSize(15);
        summary.setPadding(0, 0, 0, 24);
        root.addView(summary);

        apiUrlInput = addInput(root, "API URL", "https://rayclip.example.com");
        tokenInput = addInput(root, "Token", "same token as server");
        deviceIdInput = addInput(root, "Device ID", "android-phone");
        deviceNameInput = addInput(root, "Device Name", "Android Phone");

        Button saveButton = addButton(root, "Save Settings");
        saveButton.setOnClickListener(view -> saveSettings());

        Button checkServerButton = addButton(root, "Test Server Connection");
        checkServerButton.setOnClickListener(view -> {
            saveSettings();
            checkServerConnection();
        });

        // --- Recommended: Accessibility Service ---
        addSectionHeader(root, "\u2705  Recommended: Accessibility Service");

        TextView a11yDesc = new TextView(this);
        a11yDesc.setText("Enable the RayClip accessibility service to sync clipboard in the background. This lets you keep Gboard as your default keyboard.");
        a11yDesc.setTextColor(0xFF475569);
        a11yDesc.setTextSize(13);
        a11yDesc.setPadding(0, 0, 0, 8);
        root.addView(a11yDesc);

        Button a11yButton = addButton(root, "Open Accessibility Settings");
        a11yButton.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        // --- Alternative: Keyboard / IME method ---
        addSectionHeader(root, "\u2699\uFE0F  Alternative: Keyboard Method");

        TextView imeDesc = new TextView(this);
        imeDesc.setText("If the accessibility method doesn\u2019t work on your device, you can use RayClip as your keyboard instead. This gives guaranteed clipboard access.");
        imeDesc.setTextColor(0xFF475569);
        imeDesc.setTextSize(13);
        imeDesc.setPadding(0, 0, 0, 8);
        root.addView(imeDesc);

        Button startButton = addButton(root, "Start Sync Service");
        startButton.setOnClickListener(view -> {
            saveSettings();
            startSyncService();
        });

        Button stopButton = addButton(root, "Stop Sync Service");
        stopButton.setOnClickListener(view -> stopSyncService());

        Button inputSettingsButton = addButton(root, "Open Keyboard Settings");
        inputSettingsButton.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));

        Button pickerButton = addButton(root, "Choose Current Keyboard");
        pickerButton.setOnClickListener(view -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.showInputMethodPicker();
        });

        // --- Other ---
        Button appSettingsButton = addButton(root, "Open App Settings");
        appSettingsButton.setOnClickListener(view -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        statusText = new TextView(this);
        statusText.setText("Status: idle");
        statusText.setTextColor(0xFF334155);
        statusText.setTextSize(13);
        statusText.setPadding(0, 20, 0, 0);
        root.addView(statusText);

        return root;
    }

    private void addSectionHeader(LinearLayout root, String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextSize(16);
        header.setTextColor(0xFF1E293B);
        header.setPadding(0, 28, 0, 8);
        root.addView(header);
    }

    private EditText addInput(LinearLayout root, String label, String hint) {
        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextColor(0xFF334155);
        textView.setTextSize(13);
        textView.setPadding(0, 12, 0, 4);
        root.addView(textView);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        root.addView(input);
        return input;
    }

    private Button addButton(LinearLayout root, String label) {
        Button button = new Button(this);
        button.setText(label);
        root.addView(button);
        return button;
    }

    private void loadSettings() {
        apiUrlInput.setText(RayClipSettings.getApiUrl(this));
        tokenInput.setText(RayClipSettings.getToken(this));
        deviceIdInput.setText(RayClipSettings.getDeviceId(this));
        deviceNameInput.setText(RayClipSettings.getDeviceName(this));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = RayClipSettings.prefs(this).edit();
        editor.putString(RayClipSettings.KEY_API_URL, apiUrlInput.getText().toString().trim());
        editor.putString(RayClipSettings.KEY_TOKEN, tokenInput.getText().toString().trim());
        editor.putString(RayClipSettings.KEY_DEVICE_ID, deviceIdInput.getText().toString().trim());
        editor.putString(RayClipSettings.KEY_DEVICE_NAME, deviceNameInput.getText().toString().trim());
        editor.apply();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        autoStartSyncServiceIfConfigured();
    }

    private void startSyncService() {
        Intent intent = new Intent(this, RayClipSyncService.class).setAction(RayClipSyncService.ACTION_START);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Toast.makeText(this, "Sync service started", Toast.LENGTH_SHORT).show();
    }

    private void stopSyncService() {
        Intent intent = new Intent(this, RayClipSyncService.class).setAction(RayClipSyncService.ACTION_STOP);
        startService(intent);
        Toast.makeText(this, "Sync service stopped", Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void autoStartSyncServiceIfConfigured() {
        if (RayClipSettings.isConfigured(this)) {
            startSyncService();
        }
    }

    private void checkServerConnection() {
        statusText.setText("Status: checking server...");
        api.checkHealth(new RayClipApi.Callback<String>() {
            @Override
            public void onSuccess(String value) {
                runOnUiThread(() -> {
                    statusText.setText("Status: " + value);
                    Toast.makeText(MainActivity.this, "Server reachable", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    statusText.setText("Status: " + error.getMessage());
                    Toast.makeText(MainActivity.this, "Cannot reach server", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
