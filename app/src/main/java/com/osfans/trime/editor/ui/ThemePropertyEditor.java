/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ThemePropertyEditor extends LinearLayout {
    public interface Listener { default void onPropertyChangeStarted() {} void onPropertyChanged(); }
    private ThemeEditorModel.Key key;
    private Listener listener;
    private final TextView title;
    private final EditText label, x, y, width, height, fill, text;

    public ThemePropertyEditor(Context context) {
        super(context); setOrientation(VERTICAL); setPadding(20, 16, 20, 16); setBackgroundColor(0xfffafafa);
        title = text("Selected key", 17); addView(title, new LayoutParams(-1, -2));
        label = field("Label", false); x = field("X", true); y = field("Y", true); width = field("Width", true); height = field("Height", true); fill = field("Fill color", false); text = field("Text color", false);
    }
    private TextView text(String value, float size) { TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(0xff263238); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private EditText field(String hint, boolean number) {
        EditText view = new EditText(getContext()); view.setHint(hint); view.setSingleLine(true); view.setContentDescription(hint + " property");
        if (number) view.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LayoutParams params = new LayoutParams(-1, 48); params.topMargin = 4; addView(view, params);
        view.setOnFocusChangeListener((v, focused) -> { if (!focused) commit(); }); return view;
    }
    public void setListener(Listener listener) { this.listener = listener; }
    public void bind(ThemeEditorModel.Key key) { this.key = key; boolean enabled = key != null; title.setText(enabled ? "Key: " + key.id : "Select a key"); for (int i = 1; i < getChildCount(); i++) getChildAt(i).setEnabled(enabled); if (enabled) { label.setText(key.label); x.setText(String.valueOf(key.x)); y.setText(String.valueOf(key.y)); width.setText(String.valueOf(key.width)); height.setText(String.valueOf(key.height)); fill.setText(String.format("#%08X", key.fillColor)); text.setText(String.format("#%08X", key.textColor)); } }
    private float number(EditText v, float fallback) { try { return Float.parseFloat(v.getText().toString()); } catch (Exception e) { return fallback; } }
    private int color(EditText v, int fallback) { try { return Color.parseColor(v.getText().toString()); } catch (Exception e) { return fallback; } }
    public void commit() { if (key == null) return; if (listener != null) listener.onPropertyChangeStarted(); key.label = label.getText().toString(); key.x = number(x, key.x); key.y = number(y, key.y); key.width = Math.max(1, number(width, key.width)); key.height = Math.max(1, number(height, key.height)); key.fillColor = color(fill, key.fillColor); key.textColor = color(text, key.textColor); if (listener != null) listener.onPropertyChanged(); }
}
