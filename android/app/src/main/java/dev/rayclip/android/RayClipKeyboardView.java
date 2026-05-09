package dev.rayclip.android;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import android.widget.TextView;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;

/**
 * A Gboard-style QWERTY keyboard built entirely in code.
 * Supports letters, numbers, symbols, shift, caps lock,
 * backspace (with long-press repeat), contextual enter key,
 * and key preview popups. English (UK) layout.
 */
public class RayClipKeyboardView extends LinearLayout {

    public interface KeyListener {
        void onText(String text);
        void onBackspace();
        void onEnter();
    }

    // --- Gboard Light theme colors ---
    private static final int KEYBOARD_BG   = 0xFFE8EAED;
    private static final int KEY_BG        = 0xFFFFFFFF;
    private static final int KEY_PRESSED   = 0xFFCBCDD1;
    private static final int FUNC_BG       = 0xFFD3D6DA;
    private static final int FUNC_PRESSED  = 0xFFB8BBC0;
    private static final int ACCENT_BG     = 0xFF4285F4;
    private static final int ACCENT_PRESSED = 0xFF3367D6;
    private static final int TEXT_PRIMARY   = 0xFF37474F;
    private static final int TEXT_SECONDARY = 0xFF5F6368;
    private static final int TEXT_ON_ACCENT = 0xFFFFFFFF;
    private static final int TOOLBAR_BG    = 0xFFFFFFFF;
    private static final int TOOLBAR_TEXT   = 0xFF4285F4;
    private static final int PREVIEW_BG    = 0xFF37474F;
    private static final int PREVIEW_TEXT   = 0xFFFFFFFF;

    // --- Dimensions ---
    private static final int KEY_HEIGHT_DP  = 46;
    private static final int KEY_RADIUS_DP  = 6;
    private static final int KEY_MARGIN_DP  = 3;
    private static final int LETTER_SP      = 20;
    private static final int FUNC_LABEL_SP  = 14;
    private static final int PREVIEW_SP     = 26;

    // --- Layouts ---
    private static final String[][] LETTERS = {
        {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
        {"z", "x", "c", "v", "b", "n", "m"}
    };
    private static final String[] TOP_ROW_NUMBERS = {
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
    };
    private static final String[][] SYMBOLS1 = {
        {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
        {"@", "#", "£", "_", "&", "-", "+", "(", ")"},
        {"*", "\"", "'", ":", ";", "!", "?"}
    };
    private static final String[][] SYMBOLS2 = {
        {"~", "`", "|", "\u2022", "\u221A", "\u03C0", "\u00F7", "\u00D7", "\u00A7", "\u2206"},
        {"\u00A3", "\u20AC", "\u00A5", "^", "\u00B0", "=", "{", "}"},
        {"%", "\u00A9", "\u00AE", "\u2122", "\u2713", "[", "]"}
    };

    private KeyListener listener;
    private boolean shifted = false;
    private boolean capsLock = false;
    private int mode = 0; // 0=letters, 1=symbols1, 2=symbols2
    private int imeAction = EditorInfo.IME_ACTION_UNSPECIFIED;
    private LinearLayout keysContainer;
    private PopupWindow previewPopup;
    private TextView previewTextView;

    public RayClipKeyboardView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(KEYBOARD_BG);
        initPreviewPopup();
        buildLayout();
    }

    public void setKeyListener(KeyListener listener) {
        this.listener = listener;
    }

    public void setEditorInfo(EditorInfo info) {
        if (info != null) {
            this.imeAction = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        } else {
            this.imeAction = EditorInfo.IME_ACTION_UNSPECIFIED;
        }
        buildKeys();
    }

    // ========== PREVIEW POPUP ==========

    private void initPreviewPopup() {
        previewTextView = new TextView(getContext());
        previewTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, PREVIEW_SP);
        previewTextView.setTextColor(PREVIEW_TEXT);
        previewTextView.setGravity(Gravity.CENTER);
        previewTextView.setTypeface(Typeface.DEFAULT);
        previewTextView.setIncludeFontPadding(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PREVIEW_BG);
        bg.setCornerRadius(dp(8));
        previewTextView.setBackground(bg);
        previewTextView.setPadding(dp(16), dp(8), dp(16), dp(10));
        previewTextView.setMinWidth(dp(48));
        previewTextView.setMinHeight(dp(48));

        previewPopup = new PopupWindow(previewTextView,
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        previewPopup.setClippingEnabled(false);
        previewPopup.setAnimationStyle(0);
    }

    private void showPreview(View anchor, String label) {
        if (previewPopup == null || label.length() > 2) return;
        previewTextView.setText(label);
        previewTextView.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

        int pw = Math.max(previewTextView.getMeasuredWidth(), dp(48));
        int ph = previewTextView.getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);

        int x = loc[0] + (anchor.getWidth() - pw) / 2;
        int y = loc[1] - ph - dp(6);

        try {
            if (previewPopup.isShowing()) {
                previewPopup.update(x, y, pw, ph);
            } else {
                previewPopup.setWidth(pw);
                previewPopup.setHeight(ph);
                previewPopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
            }
        } catch (Exception ignored) { }
    }

    private void dismissPreview() {
        try {
            if (previewPopup != null && previewPopup.isShowing()) {
                previewPopup.dismiss();
            }
        } catch (Exception ignored) { }
    }

    // ========== LAYOUT BUILDERS ==========

    private void buildLayout() {
        removeAllViews();

        // Slim toolbar / sync indicator
        LinearLayout toolbar = new LinearLayout(getContext());
        toolbar.setOrientation(HORIZONTAL);
        toolbar.setBackgroundColor(TOOLBAR_BG);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(14), dp(6), dp(14), dp(6));

        TextView syncLabel = new TextView(getContext());
        syncLabel.setText("\u2713  Clipboard sync active");
        syncLabel.setTextColor(TOOLBAR_TEXT);
        syncLabel.setTextSize(12);
        syncLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        toolbar.addView(syncLabel);
        addView(toolbar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Thin separator
        View sep = new View(getContext());
        sep.setBackgroundColor(0x18000000);
        addView(sep, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        // Keys container
        keysContainer = new LinearLayout(getContext());
        keysContainer.setOrientation(VERTICAL);
        keysContainer.setPadding(dp(3), dp(4), dp(3), dp(8));
        addView(keysContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        buildKeys();
    }

    private void buildKeys() {
        keysContainer.removeAllViews();
        if (mode == 0) {
            buildLetterKeys();
        } else if (mode == 1) {
            buildSymbolKeys(SYMBOLS1, "=\\<");
        } else {
            buildSymbolKeys(SYMBOLS2, "123");
        }
        buildBottomRow();
    }

    // --- Letter layout ---

    private void buildLetterKeys() {
        // Row 0: q w e r t y u i o p
        keysContainer.addView(makeLetterRow(LETTERS[0], 0, 0));
        // Row 1: a s d f g h j k l (centered with padding)
        keysContainer.addView(makeLetterRow(LETTERS[1], dp(18), 1));
        // Row 2: SHIFT + z x c v b n m + BACKSPACE
        keysContainer.addView(makeShiftRow());
    }

    private LinearLayout makeLetterRow(String[] keys, int sidePad, int rowIndex) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setLayoutParams(rowParams());
        if (sidePad > 0) row.setPadding(sidePad, 0, sidePad, 0);

        for (int i = 0; i < keys.length; i++) {
            String base = keys[i];
            String display = (shifted || capsLock) ? base.toUpperCase() : base;
            // Long-press number hint for top row only
            String longPressChar = (rowIndex == 0 && i < TOP_ROW_NUMBERS.length)
                    ? TOP_ROW_NUMBERS[i] : null;
            row.addView(makeCharKey(display, 1f, KEY_BG, KEY_PRESSED, LETTER_SP,
                    true, longPressChar));
        }
        return row;
    }

    private LinearLayout makeShiftRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setLayoutParams(rowParams());

        // Shift key
        Button shift = makeFuncButton(capsLock ? "\u21E7" : "\u21E7", 1.5f);
        if (capsLock) {
            shift.setTextColor(ACCENT_BG);
        } else if (shifted) {
            shift.setTextColor(TEXT_PRIMARY);
        }
        shift.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (capsLock) { capsLock = false; shifted = false; }
            else if (shifted) { capsLock = true; }
            else { shifted = true; }
            buildKeys();
        });
        row.addView(shift);

        for (String key : LETTERS[2]) {
            String display = (shifted || capsLock) ? key.toUpperCase() : key;
            row.addView(makeCharKey(display, 1f, KEY_BG, KEY_PRESSED, LETTER_SP,
                    true, null));
        }

        // Backspace
        row.addView(makeBackspaceKey(1.5f));
        return row;
    }

    // --- Symbol layout ---

    private void buildSymbolKeys(String[][] symbols, String moreLabel) {
        keysContainer.addView(makeSymbolRow(symbols[0], 0));
        keysContainer.addView(makeSymbolRow(symbols[1], dp(18)));

        LinearLayout row2 = new LinearLayout(getContext());
        row2.setOrientation(HORIZONTAL);
        row2.setLayoutParams(rowParams());

        Button more = makeFuncButton(moreLabel, 1.5f);
        more.setTextSize(TypedValue.COMPLEX_UNIT_SP, FUNC_LABEL_SP);
        more.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            mode = (mode == 1) ? 2 : 1;
            buildKeys();
        });
        row2.addView(more);

        for (String key : symbols[2]) {
            row2.addView(makeCharKey(key, 1f, KEY_BG, KEY_PRESSED, 18, false, null));
        }

        row2.addView(makeBackspaceKey(1.5f));
        keysContainer.addView(row2);
    }

    private LinearLayout makeSymbolRow(String[] keys, int sidePad) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setLayoutParams(rowParams());
        if (sidePad > 0) row.setPadding(sidePad, 0, sidePad, 0);
        for (String k : keys) {
            row.addView(makeCharKey(k, 1f, KEY_BG, KEY_PRESSED, 18, false, null));
        }
        return row;
    }

    // --- Bottom row ---

    private void buildBottomRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setLayoutParams(rowParams());

        // Mode switch
        String modeLabel = (mode == 0) ? "?123" : "ABC";
        Button modeBtn = makeFuncButton(modeLabel, 1.5f);
        modeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, FUNC_LABEL_SP);
        modeBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            mode = (mode == 0) ? 1 : 0;
            shifted = false;
            capsLock = false;
            buildKeys();
        });
        row.addView(modeBtn);

        // Comma
        row.addView(makeCharKey(",", 1f, KEY_BG, KEY_PRESSED, 18, false, null));

        // Space bar
        Button space = makeBaseButton("English (UK)", 5f, KEY_BG, KEY_PRESSED);
        space.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        space.setTextColor(TEXT_SECONDARY);
        space.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) listener.onText(" ");
        });
        row.addView(space);

        // Period
        row.addView(makeCharKey(".", 1f, KEY_BG, KEY_PRESSED, 18, false, null));

        // Enter key (contextual)
        row.addView(makeEnterKey(1.5f));

        keysContainer.addView(row);
    }

    // ========== KEY FACTORIES ==========

    private Button makeCharKey(String label, float weight, int bg, int pressed,
                                int textSizeSp, boolean showPreview, String longPressChar) {
        Button key = makeBaseButton(label, weight, bg, pressed);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        key.setTextColor(TEXT_PRIMARY);
        key.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        key.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) listener.onText(label);
            if (mode == 0 && shifted && !capsLock) {
                shifted = false;
                buildKeys();
            }
        });

        if (showPreview) {
            key.setOnTouchListener((v, ev) -> {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    showPreview(v, label);
                } else if (ev.getAction() == MotionEvent.ACTION_UP
                        || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    dismissPreview();
                }
                return false;
            });
        }

        if (longPressChar != null) {
            key.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                dismissPreview();
                if (listener != null) listener.onText(longPressChar);
                return true;
            });
        }

        return key;
    }

    private Button makeBackspaceKey(float weight) {
        Button bksp = makeFuncButton("\u232B", weight);
        bksp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        bksp.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) listener.onBackspace();
        });
        bksp.setOnLongClickListener(v -> {
            final Runnable[] rep = new Runnable[1];
            rep[0] = () -> {
                if (bksp.isPressed()) {
                    if (listener != null) listener.onBackspace();
                    bksp.postDelayed(rep[0], 50);
                }
            };
            bksp.postDelayed(rep[0], 300);
            return true;
        });
        return bksp;
    }

    private Button makeEnterKey(float weight) {
        boolean hasAction = imeAction != EditorInfo.IME_ACTION_UNSPECIFIED
                && imeAction != EditorInfo.IME_ACTION_NONE;

        int bg = hasAction ? ACCENT_BG : FUNC_BG;
        int pr = hasAction ? ACCENT_PRESSED : FUNC_PRESSED;
        int textColor = hasAction ? TEXT_ON_ACCENT : TEXT_PRIMARY;

        String label;
        switch (imeAction) {
            case EditorInfo.IME_ACTION_SEARCH: label = "\uD83D\uDD0D"; break;
            case EditorInfo.IME_ACTION_SEND:   label = "\u27A4"; break;
            case EditorInfo.IME_ACTION_GO:     label = "\u2192"; break;
            case EditorInfo.IME_ACTION_DONE:   label = "\u2713"; break;
            case EditorInfo.IME_ACTION_NEXT:   label = "\u2192"; break;
            default:                           label = "\u21B5"; break;
        }

        Button enter = makeBaseButton(label, weight, bg, pr);
        enter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        enter.setTextColor(textColor);
        enter.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) listener.onEnter();
        });
        return enter;
    }

    private Button makeFuncButton(String label, float weight) {
        Button btn = makeBaseButton(label, weight, FUNC_BG, FUNC_PRESSED);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btn.setTextColor(TEXT_SECONDARY);
        return btn;
    }

    private Button makeBaseButton(String label, float weight, int bgColor, int pressedColor) {
        Button key = new Button(getContext());
        key.setText(label);
        key.setAllCaps(false);
        key.setGravity(Gravity.CENTER);
        key.setPadding(0, 0, 0, 0);
        key.setMinimumWidth(0);
        key.setMinWidth(0);
        key.setMinimumHeight(dp(KEY_HEIGHT_DP));
        key.setStateListAnimator(null);

        // Rounded background with press state
        key.setBackground(makeKeyStateList(bgColor, pressedColor));

        // Elevation + outline for subtle shadow (Gboard-like)
        float radius = dpF(KEY_RADIUS_DP);
        key.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        key.setClipToOutline(true);
        key.setElevation(dpF(0.8f));

        LayoutParams lp = new LayoutParams(0, dp(KEY_HEIGHT_DP), weight);
        lp.setMargins(dp(KEY_MARGIN_DP), dp(KEY_MARGIN_DP), dp(KEY_MARGIN_DP), dp(KEY_MARGIN_DP));
        key.setLayoutParams(lp);

        return key;
    }

    // ========== DRAWABLE HELPERS ==========

    private StateListDrawable makeKeyStateList(int normal, int pressed) {
        StateListDrawable sld = new StateListDrawable();

        GradientDrawable pressedBg = new GradientDrawable();
        pressedBg.setColor(pressed);
        pressedBg.setCornerRadius(dpF(KEY_RADIUS_DP));

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(normal);
        normalBg.setCornerRadius(dpF(KEY_RADIUS_DP));

        sld.addState(new int[]{android.R.attr.state_pressed}, pressedBg);
        sld.addState(new int[]{}, normalBg);
        return sld;
    }

    private LayoutParams rowParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getContext().getResources().getDisplayMetrics());
    }

    private float dpF(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getContext().getResources().getDisplayMetrics());
    }
}
