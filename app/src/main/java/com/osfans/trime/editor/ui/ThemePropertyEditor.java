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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class ThemePropertyEditor extends LinearLayout {
    public interface Listener { default void onPropertyChangeStarted() {} void onPropertyChanged(); }
    private ThemeEditorModel.Key key;
    private List<ThemeEditorModel.Key> keys = Collections.emptyList();
    private final Set<EditText> edited = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean binding;
    private ThemeEditorModel.LayoutMode layoutMode = ThemeEditorModel.LayoutMode.NONE;
    private boolean readOnly;
    private Listener listener;
    private final TextView title;
    private final EditText label, click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown, keyStyle, popup, x, y, width, height, fill, text;

    public ThemePropertyEditor(Context context) {
        super(context); setOrientation(VERTICAL); setPadding(dp(16), dp(12), dp(16), dp(16)); setBackgroundColor(0xff121726);
        title = text("Selected key", 17); addView(title, new LayoutParams(-1, -2));
        label = field("Display label", false); click = field("Click action", false); longClick = field("Long-click action", false); swipeLeft = field("Swipe left", false); swipeRight = field("Swipe right", false); swipeUp = field("Swipe up", false); swipeDown = field("Swipe down", false); keyStyle = field("Key style name", false); popup = field("Popup text or comma-separated items", false);
        x = field("X", true); y = field("Y", true); width = field("Width", true); height = field("Height", true); fill = field("Preview fill (style-controlled)", false); text = field("Preview text (style-controlled)", false); fill.setEnabled(false); text.setEnabled(false);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size) { TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(0xfff4f6ff); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private EditText field(String hint, boolean number) {
        EditText view = new EditText(getContext()); view.setHint(hint); view.setSingleLine(true); view.setContentDescription(hint + " property");
        if (number) view.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LayoutParams params = new LayoutParams(-1, dp(44)); params.topMargin = dp(7); addView(view, params);
        view.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            public void onTextChanged(CharSequence value, int start, int before, int count) { if (!binding) edited.add(view); }
            public void afterTextChanged(android.text.Editable value) { }
        });
        view.setOnFocusChangeListener((v, focused) -> { if (!focused) commit(); }); return view;
    }
    public void setListener(Listener listener) { this.listener = listener; }
    public void setReadOnly(boolean value) { readOnly = value; bindSelection(keys, key); }
    public void setLayoutMode(ThemeEditorModel.LayoutMode mode) { layoutMode = mode == null ? ThemeEditorModel.LayoutMode.NONE : mode; updateApplicability(); }
    public void bind(ThemeEditorModel.Key key) { bindSelection(key == null ? Collections.emptyList() : Collections.singletonList(key), key); }

    public void bindSelection(List<ThemeEditorModel.Key> selection, ThemeEditorModel.Key primary) {
        binding = true;
        keys = selection == null ? Collections.emptyList() : new ArrayList<>(selection);
        key = primary != null && keys.contains(primary) ? primary : keys.isEmpty() ? null : keys.get(keys.size() - 1);
        boolean hasKeys = !keys.isEmpty(), enabled = hasKeys && !readOnly;
        title.setText(keys.isEmpty() ? "Select a key" : keys.size() == 1 ? "Key: " + key.id : keys.size() + " keys selected");
        boolean directlyEditable = enabled && keys.size() == 1;
        for (int i = 1; i < getChildCount(); i++) getChildAt(i).setEnabled(directlyEditable);
        if (hasKeys) {
            bindString(label, k -> k.label); bindString(click, k -> k.click); bindString(longClick, k -> k.longClick);
            bindString(swipeLeft, k -> k.swipeLeft); bindString(swipeRight, k -> k.swipeRight); bindString(swipeUp, k -> k.swipeUp); bindString(swipeDown, k -> k.swipeDown);
            bindString(keyStyle, k -> k.keyStyle); bindString(popup, k -> k.popup);
            bindNumber(x, k -> k.x); bindNumber(y, k -> k.y); bindNumber(width, k -> k.width); bindNumber(height, k -> k.height);
            fill.setText(keys.size() == 1 ? String.format("#%08X", key.fillColor) : "");
            text.setText(keys.size() == 1 ? String.format("#%08X", key.textColor) : "");
        } else clearFields();
        edited.clear(); binding = false; updateApplicability();
    }

    private interface StringValue { String get(ThemeEditorModel.Key key); }
    private interface NumberValue { float get(ThemeEditorModel.Key key); }
    private void bindString(EditText field, StringValue value) {
        String first = safe(value.get(keys.get(0))); boolean same = true;
        for (int i = 1; i < keys.size(); i++) if (!first.equals(safe(value.get(keys.get(i))))) { same = false; break; }
        field.setText(same ? first : ""); field.setHint(same ? first.isEmpty() ? "Unset — enter to apply" : field.getContentDescription() : "Mixed values — enter to apply");
    }
    private void bindNumber(EditText field, NumberValue value) {
        float first = value.get(keys.get(0)); boolean same = true;
        for (int i = 1; i < keys.size(); i++) if (Float.compare(first, value.get(keys.get(i))) != 0) { same = false; break; }
        field.setText(same ? String.valueOf(first) : ""); field.setHint(same ? field.getContentDescription() : "Mixed values — enter to apply");
    }
    private void clearFields() { for (int i = 1; i < getChildCount(); i++) if (getChildAt(i) instanceof EditText) ((EditText) getChildAt(i)).setText(""); }
    private void updateApplicability() {
        boolean enabled = !keys.isEmpty() && !readOnly && keys.size() == 1, coordinates = enabled && layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS;
        boolean literalEvents = enabled && key != null && !key.hasNonLiteralEventSource; for (EditText field : new EditText[]{click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown}) { field.setEnabled(literalEvents); if (!literalEvents && enabled) field.setHint("Inline/Raw event source — use Event..."); }
        x.setEnabled(coordinates); y.setEnabled(coordinates); if (!coordinates) { x.setHint(layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? "Use Batch... for multiple keys" : "Not applicable to this layout"); y.setHint(layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? "Use Batch... for multiple keys" : "Not applicable to this layout"); }
        fill.setEnabled(false); text.setEnabled(false); fill.setHint("Not directly applicable — edit the referenced style"); text.setHint("Not directly applicable — edit the referenced style");
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private float number(EditText v, float fallback) { try { return Float.parseFloat(v.getText().toString()); } catch (Exception e) { return fallback; } }

    public void commit() {
        if (keys.isEmpty() || readOnly) return;
        if (keys.size() == 1) { commitSingle(); return; }
        boolean applicableXy = layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS;
        boolean changed = false;
        for (ThemeEditorModel.Key item : keys) {
            if (edited.contains(label) && !label.getText().toString().equals(item.label)) changed = true;
            if (edited.contains(click) && !click.getText().toString().trim().equals(item.click)) changed = true;
            if (edited.contains(longClick) && !longClick.getText().toString().trim().equals(item.longClick)) changed = true;
            if (edited.contains(swipeLeft) && !swipeLeft.getText().toString().trim().equals(item.swipeLeft)) changed = true;
            if (edited.contains(swipeRight) && !swipeRight.getText().toString().trim().equals(item.swipeRight)) changed = true;
            if (edited.contains(swipeUp) && !swipeUp.getText().toString().trim().equals(item.swipeUp)) changed = true;
            if (edited.contains(swipeDown) && !swipeDown.getText().toString().trim().equals(item.swipeDown)) changed = true;
            if (edited.contains(keyStyle) && !keyStyle.getText().toString().trim().equals(item.keyStyle)) changed = true;
            if (edited.contains(popup) && !popup.getText().toString().trim().equals(item.popup)) changed = true;
            if (edited.contains(width) && number(width, item.width) != item.width) changed = true;
            if (edited.contains(height) && number(height, item.height) != item.height) changed = true;
            if (applicableXy && edited.contains(x) && number(x, item.x) != item.x) changed = true;
            if (applicableXy && edited.contains(y) && number(y, item.y) != item.y) changed = true;
        }
        if (!changed) { edited.clear(); return; }
        if (listener != null) listener.onPropertyChangeStarted();
        for (ThemeEditorModel.Key item : keys) {
            if (edited.contains(label)) item.label = label.getText().toString();
            if (edited.contains(click)) item.click = click.getText().toString().trim();
            if (edited.contains(longClick)) item.longClick = longClick.getText().toString().trim();
            if (edited.contains(swipeLeft)) item.swipeLeft = swipeLeft.getText().toString().trim();
            if (edited.contains(swipeRight)) item.swipeRight = swipeRight.getText().toString().trim();
            if (edited.contains(swipeUp)) item.swipeUp = swipeUp.getText().toString().trim();
            if (edited.contains(swipeDown)) item.swipeDown = swipeDown.getText().toString().trim();
            if (edited.contains(keyStyle)) item.keyStyle = keyStyle.getText().toString().trim();
            if (edited.contains(popup)) { item.popup = popup.getText().toString().trim(); item.popupArray = item.popup.contains(","); }
            if (edited.contains(width)) item.width = Math.max(1, number(width, item.width));
            if (edited.contains(height)) item.height = Math.max(1, number(height, item.height));
            if (applicableXy && edited.contains(x)) item.x = number(x, item.x);
            if (applicableXy && edited.contains(y)) item.y = number(y, item.y);
        }
        edited.clear(); if (listener != null) listener.onPropertyChanged();
    }

    private void commitSingle() {
        ThemeEditorModel.Key item = keys.get(0);
        String nextLabel = label.getText().toString(), nextClick = click.getText().toString().trim(), nextLongClick = longClick.getText().toString().trim(), nextSwipeLeft = swipeLeft.getText().toString().trim(), nextSwipeRight = swipeRight.getText().toString().trim(), nextSwipeUp = swipeUp.getText().toString().trim(), nextSwipeDown = swipeDown.getText().toString().trim(), nextStyle = keyStyle.getText().toString().trim(), nextPopup = popup.getText().toString().trim();
        float nextX = layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? number(x, item.x) : item.x, nextY = layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? number(y, item.y) : item.y, nextWidth = Math.max(1, number(width, item.width)), nextHeight = Math.max(1, number(height, item.height));
        if (nextLabel.equals(item.label) && nextClick.equals(item.click) && nextLongClick.equals(item.longClick) && nextSwipeLeft.equals(item.swipeLeft) && nextSwipeRight.equals(item.swipeRight) && nextSwipeUp.equals(item.swipeUp) && nextSwipeDown.equals(item.swipeDown) && nextStyle.equals(item.keyStyle) && nextPopup.equals(item.popup) && nextX == item.x && nextY == item.y && nextWidth == item.width && nextHeight == item.height) { edited.clear(); return; }
        if (listener != null) listener.onPropertyChangeStarted(); item.label = nextLabel; item.click = nextClick; item.longClick = nextLongClick; item.swipeLeft = nextSwipeLeft; item.swipeRight = nextSwipeRight; item.swipeUp = nextSwipeUp; item.swipeDown = nextSwipeDown; item.keyStyle = nextStyle; if (!nextPopup.equals(item.popup)) item.popupArray = nextPopup.contains(","); item.popup = nextPopup; item.x = nextX; item.y = nextY; item.width = nextWidth; item.height = nextHeight; edited.clear(); if (listener != null) listener.onPropertyChanged();
    }

}
