/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/** Minimal UI-owned theme contract; adapt core data at the integration boundary. */
public final class ThemeEditorModel {
    private static int opaqueRgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }
    public static final class Key {
        public String id;
        public String sourcePath = "";
        public String ownerId = "";
        public String label;
        public String sourceLabel;
        public String click = "", sourceClick = "";
        public String longClick = "", sourceLongClick = "";
        public String swipeLeft = "", sourceSwipeLeft = "";
        public String swipeRight = "", sourceSwipeRight = "";
        public String swipeUp = "", sourceSwipeUp = "";
        public String swipeDown = "", sourceSwipeDown = "";
        public String combo = "", sourceCombo = "";
        public String composing = "", sourceComposing = "";
        public String hasMenu = "", sourceHasMenu = "";
        public String paging = "", sourcePaging = "";
        public String ascii = "", sourceAscii = "";
        public String keyStyle = "", sourceKeyStyle = "";
        public String popup = "", sourcePopup = "";
        public boolean popupArray;
        public boolean hasNonLiteralEventSource;
        public float x, y, width, height;
        public float sourceX, sourceY, sourceWidth, sourceHeight;
        public int fillColor = opaqueRgb(245, 245, 245);
        public boolean editorLocked;
        public int textColor = opaqueRgb(30, 30, 30);
        // --- R1: 对齐 Trime2 KeyView 渲染的样式字段 ---
        public float strokeWidth;                 // key.stroke_width (px 语义,画布单位换算)
        public int strokeColor = 0x00000000;      // key.stroke_color
        public float elevation;                   // key.elevation (阴影高度)
        public int shadowColor = 0x00000000;      // key.shadow_color
        public String font = "";                  // key.font (字体文件名,空=系统默认)
        public String gravity = "";               // key.gravity (center/top/bottom/left/right/start/end,可 | 组合)
        public float paddingLeft, paddingTop, paddingRight, paddingBottom; // key.padding
        public boolean show = true;               // key.show (文字层显示)
        public String hintText;                   // key.hint (助记文字,渲染于底部)

        public Key(String id, String label, float x, float y, float width, float height) {
            this.id = id; this.label = label; this.sourceLabel = label; this.x = x; this.y = y;
            this.width = width; this.height = height; this.sourceX = x; this.sourceY = y; this.sourceWidth = width; this.sourceHeight = height;
        }
        public Key copy() {
            Key k = new Key(id, label, x, y, width, height);
            k.sourcePath = sourcePath;
            k.ownerId = ownerId;
            k.sourceLabel = sourceLabel; k.click = click; k.sourceClick = sourceClick; k.longClick = longClick; k.sourceLongClick = sourceLongClick;
            k.swipeLeft = swipeLeft; k.sourceSwipeLeft = sourceSwipeLeft; k.swipeRight = swipeRight; k.sourceSwipeRight = sourceSwipeRight; k.swipeUp = swipeUp; k.sourceSwipeUp = sourceSwipeUp; k.swipeDown = swipeDown; k.sourceSwipeDown = sourceSwipeDown;
            k.combo = combo; k.sourceCombo = sourceCombo; k.composing = composing; k.sourceComposing = sourceComposing; k.hasMenu = hasMenu; k.sourceHasMenu = sourceHasMenu; k.paging = paging; k.sourcePaging = sourcePaging; k.ascii = ascii; k.sourceAscii = sourceAscii;
            k.keyStyle = keyStyle; k.sourceKeyStyle = sourceKeyStyle; k.popup = popup; k.sourcePopup = sourcePopup; k.popupArray = popupArray; k.hasNonLiteralEventSource = hasNonLiteralEventSource;
            k.sourceX = sourceX; k.sourceY = sourceY; k.sourceWidth = sourceWidth; k.sourceHeight = sourceHeight;
            k.fillColor = fillColor; k.editorLocked = editorLocked; k.textColor = textColor;
            k.strokeWidth = strokeWidth; k.strokeColor = strokeColor; k.elevation = elevation; k.shadowColor = shadowColor;
            k.font = font; k.gravity = gravity;
            k.paddingLeft = paddingLeft; k.paddingTop = paddingTop; k.paddingRight = paddingRight; k.paddingBottom = paddingBottom;
            k.show = show; k.hintText = hintText; return k;
        }
    }

    public enum InputMode { CHINESE, ASCII, NUMBER, SYMBOL }
    public enum PreviewPanel { KEYBOARD, CANDIDATE_EXPANDED, SYMBOL, CLIPBOARD }
    public enum LayoutMode { ROWS, FLEX_BOX, ABSOLUTE_KEYS, KEY_MAPS, NONE }

    /** Source-only candidate filter-bar snapshot with documented runtime fallbacks. */
    public static final class FilterBarPreview {
        public boolean show = true;
        public String gravity = "left";
        public boolean showExplicit;
        public boolean gravityExplicit;
        public boolean inherited;
        public boolean sourceResolved;

        public FilterBarPreview copy() {
            FilterBarPreview result = new FilterBarPreview();
            result.show = show;
            result.gravity = safeGravity(gravity, "left");
            result.showExplicit = showExplicit;
            result.gravityExplicit = gravityExplicit;
            result.inherited = inherited;
            result.sourceResolved = sourceResolved;
            return result;
        }

        public void copyFrom(FilterBarPreview source) {
            FilterBarPreview safe = source == null ? new FilterBarPreview() : source;
            show = safe.show;
            gravity = safeGravity(safe.gravity, "left");
            showExplicit = safe.showExplicit;
            gravityExplicit = safe.gravityExplicit;
            inherited = safe.inherited;
            sourceResolved = safe.sourceResolved;
        }
    }

    /** Static tab/tool-bar values. Height stays in source dp and is scaled only while drawing. */
    public static final class PanelBarPreview {
        public String gravity;
        public float height;
        public final List<String> keys = new ArrayList<>();
        public boolean gravityExplicit;
        public boolean heightExplicit;
        public boolean keysExplicit;
        public boolean inherited;
        public boolean sourceResolved;

        public PanelBarPreview(String gravity, float height, String... defaultKeys) {
            this.gravity = safeGravity(gravity, "top");
            this.height = safeHeight(height, 48f);
            replaceKeys(defaultKeys == null ? null : java.util.Arrays.asList(defaultKeys));
        }

        public void replaceKeys(java.util.Collection<String> values) {
            keys.clear();
            if (values == null) return;
            for (String value : values) keys.add(value == null ? "" : value);
        }

        public PanelBarPreview copy() {
            PanelBarPreview result = new PanelBarPreview(gravity, height);
            result.replaceKeys(keys);
            result.gravityExplicit = gravityExplicit;
            result.heightExplicit = heightExplicit;
            result.keysExplicit = keysExplicit;
            result.inherited = inherited;
            result.sourceResolved = sourceResolved;
            return result;
        }

        public void copyFrom(PanelBarPreview source, String fallbackGravity, float fallbackHeight, String... fallbackKeys) {
            PanelBarPreview safe = source == null ? new PanelBarPreview(fallbackGravity, fallbackHeight, fallbackKeys) : source;
            gravity = safeGravity(safe.gravity, fallbackGravity);
            height = safeHeight(safe.height, fallbackHeight);
            replaceKeys(safe.keys);
            if (source == null && safe.keys.isEmpty() && fallbackKeys != null) replaceKeys(java.util.Arrays.asList(fallbackKeys));
            gravityExplicit = safe.gravityExplicit;
            heightExplicit = safe.heightExplicit;
            keysExplicit = safe.keysExplicit;
            inherited = safe.inherited;
            sourceResolved = safe.sourceResolved;
        }
    }

    private static String safeGravity(String value, String fallback) {
        if (value == null) return fallback;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return "left".equals(normalized) || "top".equals(normalized) || "right".equals(normalized) || "bottom".equals(normalized)
                ? normalized : fallback;
    }

    private static float safeHeight(float value, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) || value < 0 ? fallback : Math.min(value, 1000f);
    }
    public static final class FlexContainer {
        public String id;
        public String parentId;
        public String sourcePath = "";
        public String direction = "row";
        public String style = "";
        public float width = -1, height = -1, grow = 1;
        public final List<String> keyIds = new ArrayList<>();
        public FlexContainer(String id, String parentId) { this.id = id; this.parentId = parentId; }
        public FlexContainer copy() { FlexContainer c = new FlexContainer(id, parentId); c.sourcePath = sourcePath; c.direction = direction; c.style = style; c.width = width; c.height = height; c.grow = grow; c.keyIds.addAll(keyIds); return c; }
    }
    public static final class KeyMapPage {
        public String id;
        public String name;
        public String sourcePath = "";
        public final List<Key> keys = new ArrayList<>();
        public KeyMapPage(String id, String name) { this.id = id; this.name = name; }
        public KeyMapPage copy() { KeyMapPage page = new KeyMapPage(id, name); page.sourcePath = sourcePath; for (Key key : keys) page.keys.add(key.copy()); return page; }
    }
    public static final class Row {
        public String id;
        public String sourcePath = "";
        public float height = 18;
        public float sourceHeight = 18;
        public float width = -1;
        public float sourceWidth = -1;
        public Row(String id, float height) { this.id = id; this.height = height; this.sourceHeight = height; }
        public Row copy() { Row row = new Row(id, height); row.sourcePath = sourcePath; row.sourceHeight = sourceHeight; row.width = width; row.sourceWidth = sourceWidth; return row; }
    }
    public LayoutMode layoutMode = LayoutMode.NONE;
    public final List<Row> rows = new ArrayList<>();
    public final List<FlexContainer> flexContainers = new ArrayList<>();
    public final List<KeyMapPage> keyMapPages = new ArrayList<>();
    public int selectedKeyMapPage;
    public String selectedFlexContainerId;
    public final List<Key> keys = new ArrayList<>();
    public final java.util.LinkedHashSet<String> selectedIds = new java.util.LinkedHashSet<>();
    public int backgroundColor = opaqueRgb(224, 228, 232);
    public int candidateBackgroundColor = opaqueRgb(232, 237, 241);
    public int candidateTextColor = opaqueRgb(38, 50, 56);
    public int toolbarBackgroundColor = opaqueRgb(216, 229, 238);
    public int toolbarTextColor = opaqueRgb(38, 50, 56);
    public int preeditBackgroundColor = opaqueRgb(136, 136, 136);
    public int preeditTextColor = opaqueRgb(170, 170, 170);
    public float preeditTextSize = 18f;
    public int compositionBackgroundColor = Color.TRANSPARENT;
    public int compositionTextColor = Color.BLACK;
    public int symbolBackgroundColor = opaqueRgb(217, 231, 255);
    public int symbolTabTextColor = opaqueRgb(38, 50, 56);
    public int symbolIndicatorColor = opaqueRgb(21, 101, 192);
    public int pressedKeyBackgroundColor = opaqueRgb(144, 202, 249);
    public int pressedKeyTextColor = opaqueRgb(30, 30, 30);
    public int pressedCandidateBackgroundColor = opaqueRgb(200, 210, 218);
    public int pressedCandidateTextColor = opaqueRgb(38, 50, 56);
    public float candidateTextSize = 22f;
    public int candidateCommentTextColor = opaqueRgb(68, 68, 68);
    public int candidatePressedCommentTextColor = opaqueRgb(68, 68, 68);
    public float candidateCommentTextSize = 12f;
    public int candidateKeyBackgroundColor = Color.WHITE;
    public int candidateKeyTextColor = Color.BLACK;
    public int candidateKeyPressedBackgroundColor = Color.WHITE;
    public int candidateKeyPressedTextColor = Color.BLACK;
    public int expandedCandidateBackgroundColor = Color.TRANSPARENT;
    public int expandedCandidateTextColor = Color.BLACK;
    public int expandedCandidatePressedBackgroundColor = Color.TRANSPARENT;
    public int expandedCandidatePressedTextColor = Color.BLACK;
    public int expandedCandidateCommentTextColor = opaqueRgb(68, 68, 68);
    public int expandedCandidatePressedCommentTextColor = opaqueRgb(68, 68, 68);
    public float expandedCandidateTextSize = 22f;
    public float expandedCandidateCommentTextSize = 12f;
    public int expandedCandidateKeyBackgroundColor = Color.WHITE;
    public int expandedCandidateKeyTextColor = Color.BLACK;
    public int expandedCandidateKeyPressedBackgroundColor = Color.WHITE;
    public int expandedCandidateKeyPressedTextColor = Color.BLACK;
    public int toolbarKeyBackgroundColor = Color.WHITE;
    public int toolbarKeyTextColor = Color.BLACK;
    public int toolbarKeyPressedBackgroundColor = Color.WHITE;
    public int toolbarKeyPressedTextColor = Color.BLACK;
    public int toolbarHideBackgroundColor = Color.WHITE;
    public int toolbarHideTextColor = Color.BLACK;
    public int toolbarHidePressedBackgroundColor = Color.WHITE;
    public int toolbarHidePressedTextColor = Color.BLACK;
    public int symbolKeyBackgroundColor = Color.WHITE;
    public int symbolKeyTextColor = Color.BLACK;
    public int symbolKeyPressedBackgroundColor = Color.WHITE;
    public int symbolKeyPressedTextColor = Color.BLACK;
    public int symbolTextBackgroundColor = Color.WHITE;
    public int symbolTextColor = Color.BLACK;
    public int symbolTextPressedBackgroundColor = Color.WHITE;
    public int symbolTextPressedColor = Color.BLACK;
    public int clipboardBackgroundColor = Color.TRANSPARENT;
    public int clipboardKeyBackgroundColor = Color.WHITE;
    public int clipboardKeyTextColor = Color.BLACK;
    public int clipboardKeyPressedBackgroundColor = Color.WHITE;
    public int clipboardKeyPressedTextColor = Color.BLACK;
    public int clipboardItemBackgroundColor = Color.WHITE;
    public int clipboardItemTextColor = Color.BLACK;
    public int clipboardItemPressedBackgroundColor = Color.WHITE;
    public int clipboardItemPressedTextColor = Color.BLACK;
    public int clipboardIndicatorColor = Color.BLACK;
    public float candidateHeight = 9;
    public float toolbarHeight = 7;
    public final List<String> toolbarKeys = new ArrayList<>();
    public boolean toolbarKeysSourceResolved;
    public String toolbarPreviewWarning = "";
    public float keyTextSize = 5.5f;
    public float keyCornerRadius = 1.5f;
    public InputMode inputMode = InputMode.CHINESE;
    public boolean showCandidate = true;
    public boolean showToolbar = true;
    public boolean showComposition = true;
    public boolean pressedPreview;
    public String compositionText = "拼音";
    // Static-only runtime semantics. Source spellings remain separate from normalized preview values.
    public String preeditInlineSource = "none";
    public String preeditInlineMode = "none";
    public String compositionPositionSource = "fixed";
    public String compositionPosition = "fixed";
    public String compositionMovableSource = "false";
    public String compositionMovable = "false";
    public boolean compositionWindowEnabled;
    public int compositionMinLength;
    public int compositionMaxLength = 5;
    public int compositionStickyLines;
    public int compositionMaxEntries = 5;
    public int compositionCloudMaxEntries;
    public boolean compositionAllPhrases;
    public boolean compositionUseCursor = true;
    public float compositionMinWidth = 10f;
    public float compositionMinHeight = 10f;
    public float compositionMaxWidth = 10000f;
    public float compositionMaxHeight = 1000f;
    public float compositionPaddingLeft;
    public float compositionPaddingTop;
    public float compositionPaddingRight;
    public float compositionPaddingBottom;
    public float compositionLineSpacing = 1f;
    public float compositionLineSpacingMultiplier = 1f;
    public float compositionTextSize = 18f;
    public int compositionPressedBackgroundColor = opaqueRgb(204, 204, 204);
    public int compositionPressedTextColor = opaqueRgb(38, 50, 56);
    public int compositionKeyBackgroundColor = Color.WHITE;
    public int compositionKeyTextColor = Color.BLACK;
    public float compositionKeyTextSize = 18f;
    public int compositionKeyPressedBackgroundColor = opaqueRgb(144, 202, 249);
    public int compositionKeyPressedTextColor = Color.BLACK;
    public int compositionKeyHintTextColor = Color.BLACK;
    public float compositionKeyHintTextSize = 12f;
    public int compositionKeyPressedHintTextColor = Color.BLACK;
    public float compositionKeyPressedHintTextSize = 12f;
    public boolean compositionPreviewSourceResolved;
    public String compositionPreviewWarning = "";
    public int candidateCount = 4;
    public boolean candidateComments;
    public boolean previewPaging;
    public boolean previewHasMenu;
    public String editorActionLabel = "Enter";
    public String schemaName = "方案";
    public PreviewPanel previewPanel = PreviewPanel.KEYBOARD;
    public float previewWidth = 360;
    public float previewHeight = 300;
    public float previewZoom = 1;
    public float previewPanX;
    public float previewPanY;
    // --- R1: 键盘/候选渲染语义 ---
    /** 键盘区域高度(画布 0~100 百分比语义,溢出判断依据)。 */
    public float keyboardHeight = 100f;
    /** 候选词预览内容(可配置,替代硬编码示意)。 */
    public final java.util.List<String> candidateWords = new java.util.ArrayList<>();

    // Missing tab/tool heights use an explicit editor-only 48 dp preview default. The runtime may
    // inherit a component height; heightExplicit/sourceResolved keep that distinction visible.
    public final FilterBarPreview candidateExpandedFilterBar = new FilterBarPreview();
    public final PanelBarPreview candidateExpandedToolBar = new PanelBarPreview(
            "right", 40f, "hide", "page_up", "page_down", "char_filter");
    public final PanelBarPreview symbolTabBar = new PanelBarPreview("top", 48f);
    public final PanelBarPreview symbolToolBar = new PanelBarPreview(
            "right", 48f, "hide", "page_up", "page_down", "BackSpace");
    public final PanelBarPreview clipboardTabBar = new PanelBarPreview("top", 48f);
    public final PanelBarPreview clipboardToolBar = new PanelBarPreview(
            "right", 48f, "hide", "page_up", "page_down", "undo");
    public String panelPreviewWarning = "";

    public ThemeEditorModel copy() {
        ThemeEditorModel result = new ThemeEditorModel();
        result.backgroundColor = backgroundColor;
        result.candidateBackgroundColor = candidateBackgroundColor;
        result.candidateTextColor = candidateTextColor;
        result.toolbarBackgroundColor = toolbarBackgroundColor;
        result.toolbarTextColor = toolbarTextColor;
        result.preeditBackgroundColor = preeditBackgroundColor; result.preeditTextColor = preeditTextColor; result.preeditTextSize = preeditTextSize;
        result.compositionBackgroundColor = compositionBackgroundColor;
        result.compositionTextColor = compositionTextColor;
        result.symbolBackgroundColor = symbolBackgroundColor;
        result.symbolTabTextColor = symbolTabTextColor;
        result.symbolIndicatorColor = symbolIndicatorColor;
        result.pressedKeyBackgroundColor = pressedKeyBackgroundColor;
        result.pressedKeyTextColor = pressedKeyTextColor;
        result.pressedCandidateBackgroundColor = pressedCandidateBackgroundColor;
        result.pressedCandidateTextColor = pressedCandidateTextColor;
        result.candidateTextSize = candidateTextSize; result.candidateCommentTextColor = candidateCommentTextColor;
        result.candidatePressedCommentTextColor = candidatePressedCommentTextColor; result.candidateCommentTextSize = candidateCommentTextSize;
        result.candidateKeyBackgroundColor = candidateKeyBackgroundColor; result.candidateKeyTextColor = candidateKeyTextColor;
        result.candidateKeyPressedBackgroundColor = candidateKeyPressedBackgroundColor; result.candidateKeyPressedTextColor = candidateKeyPressedTextColor;
        result.expandedCandidateBackgroundColor = expandedCandidateBackgroundColor; result.expandedCandidateTextColor = expandedCandidateTextColor;
        result.expandedCandidatePressedBackgroundColor = expandedCandidatePressedBackgroundColor; result.expandedCandidatePressedTextColor = expandedCandidatePressedTextColor;
        result.expandedCandidateCommentTextColor = expandedCandidateCommentTextColor; result.expandedCandidatePressedCommentTextColor = expandedCandidatePressedCommentTextColor;
        result.expandedCandidateTextSize = expandedCandidateTextSize; result.expandedCandidateCommentTextSize = expandedCandidateCommentTextSize;
        result.expandedCandidateKeyBackgroundColor = expandedCandidateKeyBackgroundColor; result.expandedCandidateKeyTextColor = expandedCandidateKeyTextColor;
        result.expandedCandidateKeyPressedBackgroundColor = expandedCandidateKeyPressedBackgroundColor; result.expandedCandidateKeyPressedTextColor = expandedCandidateKeyPressedTextColor;
        result.toolbarKeyBackgroundColor = toolbarKeyBackgroundColor; result.toolbarKeyTextColor = toolbarKeyTextColor;
        result.toolbarKeyPressedBackgroundColor = toolbarKeyPressedBackgroundColor; result.toolbarKeyPressedTextColor = toolbarKeyPressedTextColor;
        result.toolbarHideBackgroundColor = toolbarHideBackgroundColor; result.toolbarHideTextColor = toolbarHideTextColor;
        result.toolbarHidePressedBackgroundColor = toolbarHidePressedBackgroundColor; result.toolbarHidePressedTextColor = toolbarHidePressedTextColor;
        result.symbolKeyBackgroundColor = symbolKeyBackgroundColor; result.symbolKeyTextColor = symbolKeyTextColor;
        result.symbolKeyPressedBackgroundColor = symbolKeyPressedBackgroundColor; result.symbolKeyPressedTextColor = symbolKeyPressedTextColor;
        result.symbolTextBackgroundColor = symbolTextBackgroundColor; result.symbolTextColor = symbolTextColor;
        result.symbolTextPressedBackgroundColor = symbolTextPressedBackgroundColor; result.symbolTextPressedColor = symbolTextPressedColor;
        result.clipboardBackgroundColor = clipboardBackgroundColor;
        result.clipboardKeyBackgroundColor = clipboardKeyBackgroundColor; result.clipboardKeyTextColor = clipboardKeyTextColor;
        result.clipboardKeyPressedBackgroundColor = clipboardKeyPressedBackgroundColor; result.clipboardKeyPressedTextColor = clipboardKeyPressedTextColor;
        result.clipboardItemBackgroundColor = clipboardItemBackgroundColor; result.clipboardItemTextColor = clipboardItemTextColor;
        result.clipboardItemPressedBackgroundColor = clipboardItemPressedBackgroundColor; result.clipboardItemPressedTextColor = clipboardItemPressedTextColor;
        result.clipboardIndicatorColor = clipboardIndicatorColor;
        result.candidateHeight = candidateHeight;
        result.toolbarHeight = toolbarHeight;
        result.toolbarKeys.addAll(toolbarKeys);
        result.toolbarKeysSourceResolved = toolbarKeysSourceResolved;
        result.toolbarPreviewWarning = toolbarPreviewWarning == null ? "" : toolbarPreviewWarning;
        result.keyTextSize = keyTextSize;
        result.keyCornerRadius = keyCornerRadius;
        result.layoutMode = layoutMode;
        result.selectedKeyMapPage = selectedKeyMapPage;
        result.selectedFlexContainerId = selectedFlexContainerId;
        for (Row row : rows) result.rows.add(row.copy());
        for (FlexContainer container : flexContainers) result.flexContainers.add(container.copy());
        for (KeyMapPage page : keyMapPages) result.keyMapPages.add(page.copy());
        result.selectedIds.addAll(selectedIds);
        result.inputMode = inputMode;
        result.showCandidate = showCandidate;
        result.showToolbar = showToolbar;
        result.showComposition = showComposition;
        result.pressedPreview = pressedPreview;
        result.compositionText = compositionText;
        result.preeditInlineSource = preeditInlineSource; result.preeditInlineMode = preeditInlineMode;
        result.compositionPositionSource = compositionPositionSource; result.compositionPosition = compositionPosition;
        result.compositionMovableSource = compositionMovableSource; result.compositionMovable = compositionMovable;
        result.compositionWindowEnabled = compositionWindowEnabled;
        result.compositionMinLength = compositionMinLength; result.compositionMaxLength = compositionMaxLength;
        result.compositionStickyLines = compositionStickyLines; result.compositionMaxEntries = compositionMaxEntries;
        result.compositionCloudMaxEntries = compositionCloudMaxEntries; result.compositionAllPhrases = compositionAllPhrases;
        result.compositionUseCursor = compositionUseCursor; result.compositionMinWidth = compositionMinWidth;
        result.compositionMinHeight = compositionMinHeight; result.compositionMaxWidth = compositionMaxWidth;
        result.compositionMaxHeight = compositionMaxHeight; result.compositionPaddingLeft = compositionPaddingLeft;
        result.compositionPaddingTop = compositionPaddingTop; result.compositionPaddingRight = compositionPaddingRight;
        result.compositionPaddingBottom = compositionPaddingBottom; result.compositionLineSpacing = compositionLineSpacing;
        result.compositionLineSpacingMultiplier = compositionLineSpacingMultiplier; result.compositionTextSize = compositionTextSize;
        result.compositionPressedBackgroundColor = compositionPressedBackgroundColor; result.compositionPressedTextColor = compositionPressedTextColor;
        result.compositionKeyBackgroundColor = compositionKeyBackgroundColor; result.compositionKeyTextColor = compositionKeyTextColor;
        result.compositionKeyTextSize = compositionKeyTextSize; result.compositionKeyPressedBackgroundColor = compositionKeyPressedBackgroundColor;
        result.compositionKeyPressedTextColor = compositionKeyPressedTextColor; result.compositionKeyHintTextColor = compositionKeyHintTextColor;
        result.compositionKeyHintTextSize = compositionKeyHintTextSize;
        result.compositionKeyPressedHintTextColor = compositionKeyPressedHintTextColor;
        result.compositionKeyPressedHintTextSize = compositionKeyPressedHintTextSize;
        result.compositionPreviewSourceResolved = compositionPreviewSourceResolved;
        result.compositionPreviewWarning = compositionPreviewWarning == null ? "" : compositionPreviewWarning;
        result.candidateCount = candidateCount; result.candidateComments = candidateComments; result.previewPaging = previewPaging; result.previewHasMenu = previewHasMenu; result.editorActionLabel = editorActionLabel; result.schemaName = schemaName; result.previewPanel = previewPanel;
        result.previewWidth = previewWidth; result.previewHeight = previewHeight; result.previewZoom = previewZoom; result.previewPanX = previewPanX; result.previewPanY = previewPanY;
        result.keyboardHeight = keyboardHeight;
        result.candidateWords.addAll(candidateWords);
        result.candidateExpandedFilterBar.copyFrom(candidateExpandedFilterBar);
        result.candidateExpandedToolBar.copyFrom(candidateExpandedToolBar, "right", 40f, "hide", "page_up", "page_down", "char_filter");
        result.symbolTabBar.copyFrom(symbolTabBar, "top", 48f);
        result.symbolToolBar.copyFrom(symbolToolBar, "right", 48f, "hide", "page_up", "page_down", "BackSpace");
        result.clipboardTabBar.copyFrom(clipboardTabBar, "top", 48f);
        result.clipboardToolBar.copyFrom(clipboardToolBar, "right", 48f, "hide", "page_up", "page_down", "undo");
        result.panelPreviewWarning = panelPreviewWarning == null ? "" : panelPreviewWarning;
        for (Key key : keys) result.keys.add(key.copy());
        return result;
    }

    public Key find(String id) {
        for (Key key : keys) if (key.id.equals(id)) return key;
        return null;
    }

    public static ThemeEditorModel sample() {
        ThemeEditorModel model = new ThemeEditorModel();
        model.layoutMode = LayoutMode.ROWS;
        String[][] rows = {{"QWERTYUIOP"}, {"ASDFGHJKL"}, {"ZXCVBNM"}};
        for (int row = 0; row < rows.length; row++) {
            model.rows.add(new Row("row_" + row, 18));
            String letters = rows[row][0];
            float offset = row == 0 ? 0 : row == 1 ? 5 : 10;
            for (int col = 0; col < letters.length(); col++) {
                Key key = new Key("key_" + letters.charAt(col), String.valueOf(letters.charAt(col)), offset + col * 10.1f, 8 + row * 18, 9.5f, 16);
                key.ownerId = "row_" + row; model.keys.add(key);
            }
        }
        model.rows.add(new Row("row_3", 15));
        Key space = new Key("key_space", "space", 25, 62, 50, 15); space.ownerId = "row_3"; model.keys.add(space);
        return model;
    }
}
