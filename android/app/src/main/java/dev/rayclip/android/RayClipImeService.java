package dev.rayclip.android;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class RayClipImeService extends InputMethodService {

    private RayClipKeyboardView keyboardView;

    @Override
    public void onCreate() {
        super.onCreate();
        startSyncService();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        startSyncService();
        if (keyboardView != null) {
            keyboardView.setEditorInfo(attribute);
        }
    }

    @Override
    public View onCreateInputView() {
        keyboardView = new RayClipKeyboardView(this);
        keyboardView.setKeyListener(new RayClipKeyboardView.KeyListener() {
            @Override
            public void onText(String text) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(text, 1);
                }
            }

            @Override
            public void onBackspace() {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.deleteSurroundingText(1, 0);
                }
            }

            @Override
            public void onEnter() {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    EditorInfo info = getCurrentInputEditorInfo();
                    if (info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
                        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
                        if (action != EditorInfo.IME_ACTION_NONE
                                && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                            ic.performEditorAction(action);
                            return;
                        }
                    }
                    ic.commitText("\n", 1);
                }
            }
        });

        return keyboardView;
    }

    private void startSyncService() {
        Intent intent = new Intent(this, RayClipSyncService.class)
                .setAction(RayClipSyncService.ACTION_START);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
