/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Compact, native property inspector for the selected keyboard key(s). */
public final class ThemePropertyEditor extends LinearLayout {
    public interface Listener { default void onPropertyChangeStarted() {} void onPropertyChanged(); }

    private static final int SURFACE = 0xff121726;
    private static final int PANEL = 0xff0d111d;
    private static final int LINE = 0xff343a50;
    private static final int TEXT = 0xfff4f6ff;
    private static final int MUTED = 0xff929bb3;

    private ThemeEditorModel.Key key;
    private List<ThemeEditorModel.Key> keys = Collections.emptyList();
    private final Set<EditText> edited = Collections.newSetFromMap(new IdentityHashMap<EditText, Boolean>());
    private final List<EditText> fields = new ArrayList<>();
    private boolean binding;
    private ThemeEditorModel.LayoutMode layoutMode = ThemeEditorModel.LayoutMode.NONE;
    private boolean readOnly;
    private Listener listener;

    private final TextView title;
    private final Button[] tabs = new Button[4];
    private final LinearLayout[] pages = new LinearLayout[4];
    private final TextView eventGuidance;
    private final TextView stateGuidance;
    private final TextView resourceReference;
    private final EditText label, click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown;
    private final EditText combo, composing, hasMenu, paging, ascii;
    private final EditText keyStyle, popup, x, y, width, height, fill, text;

    public ThemePropertyEditor(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(8), dp(12), dp(16));
        setBackgroundColor(SURFACE);

        title = makeText("Select a key", 16, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(4), dp(3), dp(4), dp(8));
        addView(title, new LayoutParams(-1, dp(40)));

        LinearLayout tabBar = new LinearLayout(context);
        tabBar.setOrientation(HORIZONTAL);
        tabBar.setPadding(0, dp(4), 0, dp(8));
        addView(tabBar, new LayoutParams(-1, dp(44)));
        String[] tabNames = {"Basic", "Event", "State", "Resource"};
        for (int i = 0; i < tabs.length; i++) {
            final int page = i;
            Button tab = new Button(context);
            tab.setText(tabNames[i]); tab.setAllCaps(false); tab.setTextSize(10);
            tab.setMinHeight(0); tab.setMinWidth(0); tab.setPadding(0, 0, 0, 0);
            tab.setContentDescription(tabNames[i] + " properties tab");
            tab.setOnClickListener(v -> showPage(page));
            tabs[i] = tab;
            LayoutParams tabParams = new LayoutParams(0, dp(30), 1); tabParams.setMargins(dp(1), 0, dp(1), 0);
            tabBar.addView(tab, tabParams);
        }

        for (int i = 0; i < pages.length; i++) {
            pages[i] = new LinearLayout(context); pages[i].setOrientation(VERTICAL);
            addView(pages[i], new LayoutParams(-1, -2));
        }

        section(pages[0], "BASIC INFO", "Key identity and displayed content");
        label = field(pages[0], "Display label", "label", false);
        popup = field(pages[0], "Popup text / comma-separated items", "popup", false);
        section(pages[0], "POSITION / SIZE", "Coordinates apply to absolute-key layouts");
        LinearLayout position = pair(pages[0]);
        x = field(position, "X", "x", true); y = field(position, "Y", "y", true);
        LinearLayout size = pair(pages[0]);
        width = field(size, "Width", "width", true); height = field(size, "Height", "height", true);
        section(pages[0], "APPEARANCE", "Style-backed values remain source managed");
        keyStyle = field(pages[0], "Key style reference", "key_style", false);
        fill = field(pages[0], "Resolved fill color", "background", false);
        section(pages[0], "TYPOGRAPHY", "Resolved preview text color; edit via the referenced style");
        text = field(pages[0], "Resolved text color", "text_color", false);
        fill.setEnabled(false); text.setEnabled(false);

        section(pages[1], "KEY EVENTS", "Literal event values can be edited safely here");
        eventGuidance = guidance(pages[1], "Click, long click and swipe bindings are literal values.");
        click = eventField(pages[1], "Click", "click");
        longClick = eventField(pages[1], "Long click", "long_click");
        swipeLeft = eventField(pages[1], "Swipe left", "swipe_left");
        swipeRight = eventField(pages[1], "Swipe right", "swipe_right");
        swipeUp = eventField(pages[1], "Swipe up", "swipe_up");
        swipeDown = eventField(pages[1], "Swipe down", "swipe_down");

        section(pages[2], "STATE OVERRIDES", "Per-state key behavior");
        stateGuidance = guidance(pages[2], "Literal state overrides are editable. Inherited or code-backed values stay source managed.");
        ascii = field(pages[2], "ASCII override", "ascii", false);
        composing = field(pages[2], "Composing override", "composing", false);
        paging = field(pages[2], "Paging override", "paging", false);
        hasMenu = field(pages[2], "Has-menu override", "has_menu", false);
        combo = field(pages[2], "Combo override", "combo", false);
        guidance(pages[2], "Other runtime states (pressed, send_bindings and inherited mappings) are not exposed by this model. Manage them in theme source; this inspector will not pretend to write them.");

        section(pages[3], "RESOURCE REFERENCE", "Current key style and resolved resources");
        resourceReference = guidance(pages[3], "No key selected.");
        guidance(pages[3], "Images, fonts and sounds are managed by the theme resource/source workflow. Use the referenced key style to locate advanced appearance resources; this inspector does not import or rewrite resource files.");

        showPage(0);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView makeText(String value, float size, int color) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private GradientDrawable background(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor); return drawable;
    }
    private void section(LinearLayout parent, String name, String detail) {
        View divider = new View(getContext()); divider.setBackgroundColor(0xff252b3d);
        LayoutParams dividerParams = new LayoutParams(-1, dp(1)); dividerParams.topMargin = dp(10); parent.addView(divider, dividerParams);
        TextView heading = makeText(name, 10, 0xffc7cedc); heading.setTypeface(Typeface.DEFAULT_BOLD); heading.setLetterSpacing(.08f);
        LayoutParams headingParams = new LayoutParams(-1, dp(28)); parent.addView(heading, headingParams);
        TextView sub = makeText(detail, 9, 0xff717b91); sub.setPadding(0, 0, 0, dp(3)); parent.addView(sub, new LayoutParams(-1, -2));
    }
    private TextView guidance(LinearLayout parent, String value) {
        TextView view = makeText(value, 10, MUTED); view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setBackground(background(0xff161c2b, 8, LINE)); parent.addView(view, spaced(-1, -2, 6)); return view;
    }
    private LinearLayout pair(LinearLayout parent) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(HORIZONTAL); parent.addView(row, new LayoutParams(-1, -2)); return row;
    }
    private EditText eventField(LinearLayout parent, String title, String sourceName) {
        TextView header = makeText(title + "                                      " + sourceName, 10, 0xffbac2d2);
        header.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)); parent.addView(header, new LayoutParams(-1, dp(24)));
        return field(parent, title, sourceName, false);
    }
    private EditText field(LinearLayout parent, String hint, String sourceName, boolean number) {
        EditText view = new EditText(getContext()); fields.add(view);
        view.setHint(hint); view.setSingleLine(true); view.setTextSize(11); view.setTextColor(TEXT); view.setHintTextColor(0xff727c94);
        view.setContentDescription(sourceName + " property"); view.setSelectAllOnFocus(false);
        view.setPadding(dp(10), 0, dp(10), 0); view.setBackground(background(PANEL, 8, LINE));
        if (number) view.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        LayoutParams params = new LayoutParams(parent == pages[0] || parent == pages[1] || parent == pages[2] ? -1 : 0, dp(38));
        if (parent.getOrientation() == HORIZONTAL) { params.width = 0; params.weight = 1; }
        params.setMargins(dp(2), dp(4), dp(2), dp(4)); parent.addView(view, params);
        view.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            public void onTextChanged(CharSequence value, int start, int before, int count) { if (!binding) edited.add(view); }
            public void afterTextChanged(android.text.Editable value) { }
        });
        view.setOnFocusChangeListener((v, focused) -> { if (!focused) commit(); });
        return view;
    }
    private LayoutParams spaced(int width, int height, int marginTop) { LayoutParams p = new LayoutParams(width, height); p.topMargin = dp(marginTop); return p; }
    private void showPage(int selected) {
        commit();
        for (int i = 0; i < pages.length; i++) {
            boolean active = i == selected; pages[i].setVisibility(active ? VISIBLE : GONE);
            tabs[i].setTextColor(active ? 0xffe3ddff : 0xff757e92);
            tabs[i].setBackground(active ? background(0xff292445, 7, Color.TRANSPARENT) : background(Color.TRANSPARENT, 7, Color.TRANSPARENT));
        }
    }

    public void setListener(Listener listener) { this.listener = listener; }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (tabs != null) for (Button tab : tabs) if (tab != null) tab.setEnabled(enabled);
        if (label != null) updateApplicability();
    }
    public void setReadOnly(boolean value) { readOnly = value; bindSelection(keys, key); }
    public void setLayoutMode(ThemeEditorModel.LayoutMode mode) { layoutMode = mode == null ? ThemeEditorModel.LayoutMode.NONE : mode; updateApplicability(); }
    public void bind(ThemeEditorModel.Key key) { bindSelection(key == null ? Collections.emptyList() : Collections.singletonList(key), key); }

    public void bindSelection(List<ThemeEditorModel.Key> selection, ThemeEditorModel.Key primary) {
        binding = true;
        keys = selection == null ? Collections.emptyList() : new ArrayList<>(selection);
        key = primary != null && keys.contains(primary) ? primary : keys.isEmpty() ? null : keys.get(keys.size() - 1);
        title.setText(keys.isEmpty() ? "Select a key" : keys.size() == 1 ? "Key: " + key.id : keys.size() + " keys selected");
        if (!keys.isEmpty()) {
            bindString(label, k -> k.label); bindString(click, k -> k.click); bindString(longClick, k -> k.longClick);
            bindString(swipeLeft, k -> k.swipeLeft); bindString(swipeRight, k -> k.swipeRight); bindString(swipeUp, k -> k.swipeUp); bindString(swipeDown, k -> k.swipeDown);
            bindString(combo, k -> k.combo); bindString(composing, k -> k.composing); bindString(hasMenu, k -> k.hasMenu); bindString(paging, k -> k.paging); bindString(ascii, k -> k.ascii);
            bindString(keyStyle, k -> k.keyStyle); bindString(popup, k -> k.popup);
            bindNumber(x, k -> k.x); bindNumber(y, k -> k.y); bindNumber(width, k -> k.width); bindNumber(height, k -> k.height);
            fill.setText(keys.size() == 1 ? String.format("#%08X", key.fillColor) : "");
            text.setText(keys.size() == 1 ? String.format("#%08X", key.textColor) : "");
            resourceReference.setText(keys.size() == 1
                    ? "Current key style: " + (safe(key.keyStyle).isEmpty() ? "(unset / inherited)" : key.keyStyle) + "\nResolved colors: " + fill.getText() + " / " + text.getText()
                    : "Multiple keys selected. Key style may be mixed; use Basic → Appearance to apply a shared reference.");
        } else {
            for (EditText field : fields) field.setText("");
            resourceReference.setText("No key selected. Select a key to inspect its style reference.");
        }
        edited.clear(); binding = false; updateApplicability();
    }

    private interface StringValue { String get(ThemeEditorModel.Key key); }
    private interface NumberValue { float get(ThemeEditorModel.Key key); }
    private void bindString(EditText field, StringValue value) {
        String first = safe(value.get(keys.get(0))); boolean same = true;
        for (int i = 1; i < keys.size(); i++) if (!first.equals(safe(value.get(keys.get(i))))) { same = false; break; }
        field.setText(same ? first : "");
        field.setHint(same ? first.isEmpty() ? "Unset — enter to apply" : field.getContentDescription() : "Mixed values — enter to apply");
    }
    private void bindNumber(EditText field, NumberValue value) {
        float first = value.get(keys.get(0)); boolean same = true;
        for (int i = 1; i < keys.size(); i++) if (Float.compare(first, value.get(keys.get(i))) != 0) { same = false; break; }
        field.setText(same ? String.valueOf(first) : ""); field.setHint(same ? field.getContentDescription() : "Mixed values — enter to apply");
    }
    private void updateApplicability() {
        boolean enabled = isEnabled() && !keys.isEmpty() && !readOnly;
        boolean single = enabled && keys.size() == 1;
        for (EditText field : fields) field.setEnabled(single);
        boolean coordinates = single && layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS;
        x.setEnabled(coordinates); y.setEnabled(coordinates);
        if (!coordinates) {
            x.setHint(layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? "Use Batch... for multiple keys" : "Not applicable to this layout");
            y.setHint(layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? "Use Batch... for multiple keys" : "Not applicable to this layout");
        }
        boolean literalEvents = single && key != null && !key.hasNonLiteralEventSource;
        EditText[] literalFields = {click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown, combo, composing, hasMenu, paging, ascii};
        for (EditText field : literalFields) field.setEnabled(literalEvents);
        eventGuidance.setText(!single ? "Select one editable key to change events. Mixed values remain visible for batch semantics."
                : literalEvents ? "Literal event values are editable and commit when focus leaves a field."
                : "Code-backed/nonliteral event source detected. Editing is disabled here to avoid replacing executable mappings; use the source/Event workflow.");
        stateGuidance.setText(!single ? "Select one editable key to change state overrides."
                : literalEvents ? "Literal state overrides are editable. Empty means unset/inherited from click."
                : "State mappings are code-backed or nonliteral and are source-managed/read-only here. No unsafe conversion will be attempted.");
        fill.setEnabled(false); text.setEnabled(false);
        fill.setHint("Read-only — edit the referenced key style"); text.setHint("Read-only — edit the referenced key style");
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private float number(EditText v, float fallback) { try { return Float.parseFloat(v.getText().toString()); } catch (Exception e) { return fallback; } }
    private String value(EditText field) { return field.getText().toString().trim(); }

    public void commit() {
        if (keys.isEmpty() || readOnly) return;
        if (keys.size() == 1) { commitSingle(); return; }
        boolean applicableXy = layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS;
        boolean changed = false;
        for (ThemeEditorModel.Key item : keys) if (wouldChange(item, applicableXy, true)) { changed = true; break; }
        if (!changed) { edited.clear(); return; }
        if (listener != null) listener.onPropertyChangeStarted();
        for (ThemeEditorModel.Key item : keys) apply(item, applicableXy, true);
        edited.clear(); if (listener != null) listener.onPropertyChanged();
    }
    private void commitSingle() {
        ThemeEditorModel.Key item = keys.get(0); boolean applicableXy = layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS;
        if (!wouldChange(item, applicableXy, false)) { edited.clear(); return; }
        if (listener != null) listener.onPropertyChangeStarted(); apply(item, applicableXy, false);
        edited.clear(); if (listener != null) listener.onPropertyChanged();
    }
    private boolean use(EditText field, boolean editedOnly) { return !editedOnly || edited.contains(field); }
    private boolean eventEditable() { return key == null || !key.hasNonLiteralEventSource; }
    private boolean wouldChange(ThemeEditorModel.Key item, boolean applicableXy, boolean editedOnly) {
        if (use(label, editedOnly) && !label.getText().toString().equals(item.label)) return true;
        if (eventEditable()) {
            if (use(click, editedOnly) && !value(click).equals(item.click)) return true;
            if (use(longClick, editedOnly) && !value(longClick).equals(item.longClick)) return true;
            if (use(swipeLeft, editedOnly) && !value(swipeLeft).equals(item.swipeLeft)) return true;
            if (use(swipeRight, editedOnly) && !value(swipeRight).equals(item.swipeRight)) return true;
            if (use(swipeUp, editedOnly) && !value(swipeUp).equals(item.swipeUp)) return true;
            if (use(swipeDown, editedOnly) && !value(swipeDown).equals(item.swipeDown)) return true;
            if (use(combo, editedOnly) && !value(combo).equals(item.combo)) return true;
            if (use(composing, editedOnly) && !value(composing).equals(item.composing)) return true;
            if (use(hasMenu, editedOnly) && !value(hasMenu).equals(item.hasMenu)) return true;
            if (use(paging, editedOnly) && !value(paging).equals(item.paging)) return true;
            if (use(ascii, editedOnly) && !value(ascii).equals(item.ascii)) return true;
        }
        if (use(keyStyle, editedOnly) && !value(keyStyle).equals(item.keyStyle)) return true;
        if (use(popup, editedOnly) && !value(popup).equals(item.popup)) return true;
        if (use(width, editedOnly) && number(width, item.width) != item.width) return true;
        if (use(height, editedOnly) && number(height, item.height) != item.height) return true;
        return applicableXy && ((use(x, editedOnly) && number(x, item.x) != item.x) || (use(y, editedOnly) && number(y, item.y) != item.y));
    }
    private void apply(ThemeEditorModel.Key item, boolean applicableXy, boolean editedOnly) {
        if (use(label, editedOnly)) item.label = label.getText().toString();
        if (eventEditable()) {
            if (use(click, editedOnly)) item.click = value(click); if (use(longClick, editedOnly)) item.longClick = value(longClick);
            if (use(swipeLeft, editedOnly)) item.swipeLeft = value(swipeLeft); if (use(swipeRight, editedOnly)) item.swipeRight = value(swipeRight);
            if (use(swipeUp, editedOnly)) item.swipeUp = value(swipeUp); if (use(swipeDown, editedOnly)) item.swipeDown = value(swipeDown);
            if (use(combo, editedOnly)) item.combo = value(combo); if (use(composing, editedOnly)) item.composing = value(composing);
            if (use(hasMenu, editedOnly)) item.hasMenu = value(hasMenu); if (use(paging, editedOnly)) item.paging = value(paging); if (use(ascii, editedOnly)) item.ascii = value(ascii);
        }
        if (use(keyStyle, editedOnly)) item.keyStyle = value(keyStyle);
        if (use(popup, editedOnly)) { String next = value(popup); if (!next.equals(item.popup)) item.popupArray = next.contains(","); item.popup = next; }
        if (use(width, editedOnly)) item.width = Math.max(1, number(width, item.width));
        if (use(height, editedOnly)) item.height = Math.max(1, number(height, item.height));
        if (applicableXy && use(x, editedOnly)) item.x = number(x, item.x);
        if (applicableXy && use(y, editedOnly)) item.y = number(y, item.y);
    }
}
