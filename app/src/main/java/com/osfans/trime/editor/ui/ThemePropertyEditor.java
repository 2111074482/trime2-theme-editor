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

        title = makeText("请选择按键", 16, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(4), dp(3), dp(4), dp(8));
        addView(title, new LayoutParams(-1, dp(40)));

        LinearLayout tabBar = new LinearLayout(context);
        tabBar.setOrientation(HORIZONTAL);
        tabBar.setPadding(0, dp(4), 0, dp(8));
        addView(tabBar, new LayoutParams(-1, dp(44)));
        String[] tabNames = {"基础", "事件", "状态", "资源"};
        for (int i = 0; i < tabs.length; i++) {
            final int page = i;
            Button tab = new Button(context);
            tab.setText(tabNames[i]); tab.setAllCaps(false); tab.setTextSize(10);
            tab.setMinHeight(0); tab.setMinWidth(0); tab.setPadding(0, 0, 0, 0);
            tab.setContentDescription(tabNames[i] + "属性标签页");
            tab.setOnClickListener(v -> showPage(page));
            tabs[i] = tab;
            LayoutParams tabParams = new LayoutParams(0, dp(30), 1); tabParams.setMargins(dp(1), 0, dp(1), 0);
            tabBar.addView(tab, tabParams);
        }

        for (int i = 0; i < pages.length; i++) {
            pages[i] = new LinearLayout(context); pages[i].setOrientation(VERTICAL);
            addView(pages[i], new LayoutParams(-1, -2));
        }

        section(pages[0], "基本信息", "按键标识与显示内容");
        label = field(pages[0], "显示标签", "label", false);
        popup = field(pages[0], "弹出内容/逗号分隔项目", "popup", false);
        section(pages[0], "位置与尺寸", "坐标仅适用于绝对定位按键布局");
        LinearLayout position = pair(pages[0]);
        x = field(position, "横向坐标 X", "x", true); y = field(position, "纵向坐标 Y", "y", true);
        LinearLayout size = pair(pages[0]);
        width = field(size, "宽度", "width", true); height = field(size, "高度", "height", true);
        section(pages[0], "外观", "由样式提供的值仍由源文件管理");
        keyStyle = field(pages[0], "按键样式引用", "key_style", false);
        fill = field(pages[0], "解析后的填充颜色", "background", false);
        section(pages[0], "文字样式", "此处预览解析后的文字颜色;请通过引用的样式编辑");
        text = field(pages[0], "解析后的文字颜色", "text_color", false);
        fill.setEnabled(false); text.setEnabled(false);

        section(pages[1], "按键事件", "可在此安全编辑字面量事件值");
        eventGuidance = guidance(pages[1], "点击、长按与滑动绑定均为字面量值。");
        click = eventField(pages[1], "点击", "click");
        longClick = eventField(pages[1], "长按", "long_click");
        swipeLeft = eventField(pages[1], "向左滑动", "swipe_left");
        swipeRight = eventField(pages[1], "向右滑动", "swipe_right");
        swipeUp = eventField(pages[1], "向上滑动", "swipe_up");
        swipeDown = eventField(pages[1], "向下滑动", "swipe_down");

        section(pages[2], "状态覆盖", "各状态下的按键行为");
        stateGuidance = guidance(pages[2], "可编辑字面量状态覆盖;继承值或代码生成值仍由源文件管理。");
        ascii = field(pages[2], "美国信息交换标准代码 ASCII 模式覆盖", "ascii", false);
        composing = field(pages[2], "组字状态覆盖", "composing", false);
        paging = field(pages[2], "翻页状态覆盖", "paging", false);
        hasMenu = field(pages[2], "有菜单状态覆盖", "has_menu", false);
        combo = field(pages[2], "组合键状态覆盖", "combo", false);
        guidance(pages[2], "此模型未提供其他运行时状态(按下(pressed)、发送绑定(send_bindings)及继承映射)。请在主题源文件中管理;本检查器不会假装写入这些内容。");

        section(pages[3], "资源引用", "当前按键样式与解析后的资源");
        resourceReference = guidance(pages[3], "未选择按键。");
        guidance(pages[3], "图片、字体和声音由主题资源/源文件工作流管理。可通过引用的按键样式定位高级外观资源;本检查器不会导入或改写资源文件。");

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
        String displayName = title + "(" + sourceName + ")";
        TextView header = makeText(displayName, 10, 0xffbac2d2);
        header.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)); parent.addView(header, new LayoutParams(-1, dp(24)));
        return field(parent, title, sourceName, false);
    }
    private EditText field(LinearLayout parent, String hint, String sourceName, boolean number) {
        EditText view = new EditText(getContext()); fields.add(view);
        String displayName = hint + "(" + sourceName + ")";
        view.setHint(displayName); view.setSingleLine(true); view.setTextSize(11); view.setTextColor(TEXT); view.setHintTextColor(0xff727c94);
        view.setContentDescription(displayName); view.setSelectAllOnFocus(false);
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
        title.setText(keys.isEmpty() ? "请选择按键" : keys.size() == 1 ? "按键:" + key.id : "已选择 " + keys.size() + " 个按键");
        if (!keys.isEmpty()) {
            bindString(label, k -> k.label); bindString(click, k -> k.click); bindString(longClick, k -> k.longClick);
            bindString(swipeLeft, k -> k.swipeLeft); bindString(swipeRight, k -> k.swipeRight); bindString(swipeUp, k -> k.swipeUp); bindString(swipeDown, k -> k.swipeDown);
            bindString(combo, k -> k.combo); bindString(composing, k -> k.composing); bindString(hasMenu, k -> k.hasMenu); bindString(paging, k -> k.paging); bindString(ascii, k -> k.ascii);
            bindString(keyStyle, k -> k.keyStyle); bindString(popup, k -> k.popup);
            bindNumber(x, k -> k.x); bindNumber(y, k -> k.y); bindNumber(width, k -> k.width); bindNumber(height, k -> k.height);
            fill.setText(keys.size() == 1 ? String.format("#%08X", key.fillColor) : "");
            text.setText(keys.size() == 1 ? String.format("#%08X", key.textColor) : "");
            resourceReference.setText(keys.size() == 1
                    ? "当前按键样式(key_style):" + (safe(key.keyStyle).isEmpty() ? "(未设置/继承)" : key.keyStyle) + "\n解析后的颜色(background/text_color):" + fill.getText() + " / " + text.getText()
                    : "已选择多个按键。按键样式可能为混合值;请前往“基础 → 外观”应用共用引用。");
        } else {
            for (EditText field : fields) field.setText("");
            resourceReference.setText("未选择按键。请选择按键以检查其样式引用。");
        }
        edited.clear(); binding = false; updateApplicability();
    }

    private interface StringValue { String get(ThemeEditorModel.Key key); }
    private interface NumberValue { float get(ThemeEditorModel.Key key); }
    private void bindString(EditText field, StringValue value) {
        String first = safe(value.get(keys.get(0))); boolean same = true;
        for (int i = 1; i < keys.size(); i++) if (!first.equals(safe(value.get(keys.get(i))))) { same = false; break; }
        field.setText(same ? first : "");
        field.setHint(same
                ? first.isEmpty() ? field.getContentDescription() + ":未设置——输入内容即可应用" : field.getContentDescription()
                : field.getContentDescription() + ":混合值——输入内容即可应用");
    }
    private void bindNumber(EditText field, NumberValue value) {
        float first = value.get(keys.get(0)); boolean same = true;
        for (int i = 1; i < keys.size(); i++) if (Float.compare(first, value.get(keys.get(i))) != 0) { same = false; break; }
        field.setText(same ? String.valueOf(first) : "");
        field.setHint(same ? field.getContentDescription() : field.getContentDescription() + ":混合值——输入内容即可应用");
    }
    private void updateApplicability() {
        boolean enabled = isEnabled() && !keys.isEmpty() && !readOnly;
        boolean single = enabled && keys.size() == 1;
        for (EditText field : fields) field.setEnabled(single);
        boolean coordinates = single && layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS;
        x.setEnabled(coordinates); y.setEnabled(coordinates);
        if (!coordinates) {
            x.setHint(x.getContentDescription() + (layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? ":多个按键请使用‘批量...’" : ":不适用于当前布局"));
            y.setHint(y.getContentDescription() + (layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? ":多个按键请使用‘批量...’" : ":不适用于当前布局"));
        }
        boolean literalEvents = single && key != null && !key.hasNonLiteralEventSource;
        EditText[] literalFields = {click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown, combo, composing, hasMenu, paging, ascii};
        for (EditText field : literalFields) field.setEnabled(literalEvents);
        eventGuidance.setText(!single ? "请选择一个可编辑按键以修改事件。混合值仍会显示,以支持批量操作语义。"
                : literalEvents ? "字面量事件值可编辑,并在字段失去焦点时提交。"
                : "检测到代码生成或非字面量事件源。此处已禁止编辑,以免替换可执行映射;请使用源文件/事件工作流。");
        stateGuidance.setText(!single ? "请选择一个可编辑按键以修改状态覆盖。"
                : literalEvents ? "字面量状态覆盖可编辑。留空表示未设置/继承自点击(click)。"
                : "状态映射由代码生成或并非字面量,因此由源文件管理且在此只读。不会尝试任何有风险的转换。");
        fill.setEnabled(false); text.setEnabled(false);
        fill.setHint(fill.getContentDescription() + ":只读——请编辑引用的按键样式(key_style)");
        text.setHint(text.getContentDescription() + ":只读——请编辑引用的按键样式(key_style)");
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
