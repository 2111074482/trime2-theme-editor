/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

/**
 * R2: 从 ThemeEditorActivity 提取的纯静态样式映射逻辑。
 *
 * 职责：把主题文档(ThemeDocument)的样式字段映射到渲染模型(ThemeEditorModel)，
 * 以及字段取值辅助方法。无 Activity / View / 状态依赖，可独立单测。
 */
public final class ThemePreviewStyles {

    private ThemePreviewStyles() { }

    /** 将样式文档中的字段映射到渲染模型(对齐 Trime2 KeyView 渲染字段)。 */
    public static void applyStyleDocument(ThemeEditorModel model, com.osfans.trime.editor.core.ThemeDocument style) {
        model.backgroundColor = colorValue(style.get("keyboard.background"), colorValue(style.get("background"), model.backgroundColor));
        model.candidateBackgroundColor = colorValue(style.get("candidate.background"), model.candidateBackgroundColor);
        model.candidateTextColor = colorValue(style.get("candidate.text_color"), model.candidateTextColor);
        model.toolbarBackgroundColor = colorValue(style.get("toolbar.background"), model.candidateBackgroundColor);
        model.toolbarTextColor = model.candidateTextColor;
        model.preeditBackgroundColor = colorValue(style.get("preedit.background"), model.preeditBackgroundColor);
        model.preeditTextColor = colorValue(style.get("preedit.text_color"), model.preeditTextColor);
        model.compositionBackgroundColor = colorValue(style.get("composition.background"), model.compositionBackgroundColor);
        model.compositionTextColor = colorValue(style.get("composition.text_color"), model.compositionTextColor);
        model.symbolBackgroundColor = colorValue(style.get("symbol.background"), model.symbolBackgroundColor);
        model.symbolTabTextColor = colorValue(style.get("symbol.key.text_color"), model.symbolTabTextColor);
        model.symbolIndicatorColor = colorValue(style.get("symbol.tab_bar.indicator_color"), colorValue(style.get("symbol.indicator_color"), model.symbolIndicatorColor));
        model.pressedKeyBackgroundColor = colorValue(style.get("key.pressed.background"), model.pressedKeyBackgroundColor);
        model.pressedKeyTextColor = colorValue(style.get("key.pressed.text_color"), model.pressedKeyTextColor);
        model.pressedCandidateBackgroundColor = colorValue(style.get("candidate.pressed.background"), model.pressedCandidateBackgroundColor);
        model.pressedCandidateTextColor = colorValue(style.get("candidate.pressed.text_color"), model.pressedCandidateTextColor);
        model.candidateHeight = numberValue(style.get("candidate.height"), 48f) / 5.3f;
        model.toolbarHeight = model.candidateHeight;
        model.keyTextSize = Math.max(2f, numberValue(style.get("key.text_size"), 22f) / 4f);
        model.keyCornerRadius = Math.max(0f, numberValue(style.get("key.corner_radius"), 8f) / 5f);
        int fill = colorValue(style.get("key.background"), 0xfff5f5f5);
        int text = colorValue(style.get("key.text_color"), 0xff1e1e1e);
        // R1: 补 key 样式映射(对齐 Trime2 KeyView 渲染字段)
        float strokeWidth = numberValue(style.get("key.stroke_width"), 0f);
        int strokeColor = colorValue(style.get("key.stroke_color"), 0);
        float elevation = numberValue(style.get("key.elevation"), 0f);
        int shadowColor = colorValue(style.get("key.shadow_color"), 0);
        String keyFont = stringValue(style.get("key.font"), "");
        String keyGravity = stringValue(style.get("key.gravity"), "");
        float padL = numberValue(style.get("key.padding.left"), 0f);
        float padT = numberValue(style.get("key.padding.top"), 0f);
        float padR = numberValue(style.get("key.padding.right"), 0f);
        float padB = numberValue(style.get("key.padding.bottom"), 0f);
        boolean keyShow = booleanValue(style.get("key.show"), true);
        for (ThemeEditorModel.Key key : model.keys) {
            key.fillColor = fill; key.textColor = text;
            key.strokeWidth = strokeWidth; key.strokeColor = strokeColor;
            key.elevation = elevation; key.shadowColor = shadowColor;
            key.font = keyFont; key.gravity = keyGravity;
            key.paddingLeft = padL; key.paddingTop = padT; key.paddingRight = padR; key.paddingBottom = padB;
            key.show = keyShow;
        }
    }

    public static float numberValue(com.osfans.trime.editor.core.ThemeValue value, float fallback) {
        return value instanceof com.osfans.trime.editor.core.ThemeValue.LuaNumber
                ? (float) ((com.osfans.trime.editor.core.ThemeValue.LuaNumber) value).getValue() : fallback;
    }

    public static int colorValue(com.osfans.trime.editor.core.ThemeValue value, int fallback) {
        // R1.5 修复:double→int 在超出 int 范围时会饱和为 Integer.MAX_VALUE,
        // 导致所有 0xff000000+ 的 ARGB 颜色(不透明色)被错误映射为白色。
        // 必须经 long 中转做位截断,才能得到正确的有符号 int 颜色。
        return value instanceof com.osfans.trime.editor.core.ThemeValue.LuaNumber
                ? (int) (long) ((com.osfans.trime.editor.core.ThemeValue.LuaNumber) value).getValue() : fallback;
    }

    /** 字符串取值;font 等字段允许 LuaTable(取第一个字符串,如字体 fallback 数组)。 */
    public static String stringValue(com.osfans.trime.editor.core.ThemeValue value, String fallback) {
        if (value instanceof com.osfans.trime.editor.core.ThemeValue.LuaString) {
            return ((com.osfans.trime.editor.core.ThemeValue.LuaString) value).getValue();
        }
        if (value instanceof com.osfans.trime.editor.core.ThemeValue.LuaTable) {
            for (String key : ((com.osfans.trime.editor.core.ThemeValue.LuaTable) value).getFields().keySet()) {
                com.osfans.trime.editor.core.ThemeValue child =
                        ((com.osfans.trime.editor.core.ThemeValue.LuaTable) value).getFields().get(key);
                if (child instanceof com.osfans.trime.editor.core.ThemeValue.LuaString) {
                    return ((com.osfans.trime.editor.core.ThemeValue.LuaString) child).getValue();
                }
            }
        }
        return fallback;
    }

    public static boolean booleanValue(com.osfans.trime.editor.core.ThemeValue value, boolean fallback) {
        return value instanceof com.osfans.trime.editor.core.ThemeValue.LuaBoolean
                ? ((com.osfans.trime.editor.core.ThemeValue.LuaBoolean) value).getValue() : fallback;
    }
}
