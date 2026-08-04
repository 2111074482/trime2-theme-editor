/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.ArrayDeque;
import java.util.Deque;

public final class ThemeEditorWorkspace extends LinearLayout {
    private final ThemeKeyboardCanvas canvas;
    private final ThemePropertyEditor properties;
    private final TextView status;
    private final TextView statusContext;
    private final TextView zoomValue;
    private final LinearLayout contextBar;
    private final LinearLayout structureContent;
    private final Button statisticsTab;
    private final Button layersTab;
    private final Button historyTab;
    private final Button selectModeButton;
    private final Button panModeButton;
    private final Button gridModeButton;
    private final Button canvasPreviewButton;
    private final View propertyPanel;
    private final boolean wideLayout;
    private final Deque<ThemeEditorModel> undo = new ArrayDeque<>();
    private final Deque<ThemeEditorModel> redo = new ArrayDeque<>();
    private ThemeEditorModel model;
    private ThemeEditorCallbacks callbacks;
    private boolean applying;
    private boolean readOnly;
    private boolean appendSelection;
    private boolean gridVisible = true;
    private boolean canvasPreviewMode;
    private boolean dirty;
    private String canvasMode = "select";
    private int structurePage;
    private String clipboardScope = "";
    private String panelPreviewSource;
    private boolean panelPreviewSourceAssigned;

    public ThemeEditorWorkspace(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#080b13"));
        wideLayout = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        ViewCompat.setOnApplyWindowInsetsListener(this, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(this);

        Button appendSelectButton = action("多选", "切换追加选择"); Button selectAllButton = action("全选", "选择全部按键"); Button invertButton = action("反选", "反选按键"); Button rowButton = action("行", "选择当前行"); Button batchButton = action("批量...", "批量编辑所选按键"); Button clipboardButton = action("剪贴板...", "编辑器内部剪贴板"); Button rowManageButton = action("行管理...", "管理行(rows)");
        Button previousPageButton = action("◀ 上一页", "上一按键映射页(key_maps)"); Button nextPageButton = action("下一页 ▶", "下一按键映射页(key_maps)"); Button pageAddButton = action("+ 新建页", "添加按键映射页(key_maps)"); Button pageDeleteButton = action("− 删除页", "删除按键映射页(key_maps)"); Button pageManageButton = action("页面...", "管理按键映射页(key_maps)");
        Button flexButton = action("弹性盒", "编辑所选弹性容器(flex)"); Button flexManageButton = action("弹性盒...", "管理弹性容器(flex)"); Button absoluteButton = action("按键...", "绝对定位按键工具");
        Button previewButton = action("预览", "预览设备设置"); Button stateButton = action("状态...", "详细预览状态"); Button eventButton = action("事件...", "管理所选按键事件"); Button modeButton = action("中文", "切换预览输入模式"); Button candidateButton = action("候选栏", "切换候选栏预览"); Button toolbarButton = action("工具栏", "切换工具栏预览"); Button compositionButton = action("组字", "切换组合窗预览"); Button pressedButton = action("按下", "切换按下状态预览");
        Button addButton = action("按键", "添加按键"); Button duplicateButton = action("复制", "复制所选按键"); Button deleteButton = action("删除", "删除所选按键");
        Button undoButton = action("↶", "撤销上次更改"); Button redoButton = action("↷", "重做上次更改"); Button saveButton = action(wideLayout ? "保存主题" : "保存", "保存主题"); Button moreButton = action("⋯", "全部编辑操作");

        LinearLayout topBar = panel(HORIZONTAL, 17); topBar.setGravity(Gravity.CENTER_VERTICAL); topBar.setPadding(dp(12), 0, dp(8), 0);
        TextView mark = label("T2", 14); mark.setGravity(Gravity.CENTER); mark.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); mark.setBackground(roundedBackground("#8b7cff", 10)); topBar.addView(mark, new LayoutParams(dp(34), dp(34)));
        LinearLayout brand = new LinearLayout(context); brand.setOrientation(VERTICAL); brand.setGravity(Gravity.CENTER_VERTICAL); TextView brandName = label(wideLayout ? "Trime2 主题工作台" : "Trime2", 13); brandName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); TextView project = label(wideLayout ? "主题工作台 · ● 实时" : "● 实时", 9); project.setTextColor(Color.parseColor("#929bb3")); brand.addView(brandName); brand.addView(project); LayoutParams brandParams = new LayoutParams(0, -1, 1); brandParams.leftMargin = dp(10); topBar.addView(brand, brandParams);
        styleTopAction(undoButton, false); styleTopAction(redoButton, false); styleTopAction(previewButton, false); styleTopAction(saveButton, true); styleTopAction(moreButton, false);
        topBar.addView(undoButton, topActionParams()); topBar.addView(redoButton, topActionParams());
        topBar.addView(previewButton, topActionParams()); topBar.addView(saveButton, new LayoutParams(dp(wideLayout ? 92 : 58), dp(38))); topBar.addView(moreButton, topActionParams());
        LayoutParams topParams = new LayoutParams(-1, dp(58)); topParams.setMargins(dp(wideLayout ? 104 : 10), dp(10), dp(wideLayout ? 332 : 10), dp(8)); addView(topBar, topParams);

        canvas = new ThemeKeyboardCanvas(context); canvas.setBackgroundColor(Color.parseColor("#080b13"));
        properties = new ThemePropertyEditor(context); themePropertyEditor(properties);
        ScrollView propertyScroll = new ScrollView(context); propertyScroll.setFillViewport(true); propertyScroll.addView(properties, new ScrollView.LayoutParams(-1, -2));
        LinearLayout propertyContainer = panel(VERTICAL, 20); LinearLayout propertyHeader = new LinearLayout(context); propertyHeader.setGravity(Gravity.CENTER_VERTICAL); propertyHeader.setPadding(dp(16), 0, dp(8), 0); LinearLayout titles = new LinearLayout(context); titles.setOrientation(VERTICAL); titles.setGravity(Gravity.CENTER_VERTICAL); TextView propertyTitle = label("属性检查器", 14); propertyTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); TextView propertyHint = label("所选对象属性", 9); propertyHint.setTextColor(Color.parseColor("#929bb3")); titles.addView(propertyTitle); titles.addView(propertyHint); propertyHeader.addView(titles, new LayoutParams(0, dp(54), 1)); Button closeProperties = action(wideLayout ? "‹" : "关闭", "关闭属性检查器"); styleTopAction(closeProperties, false); propertyHeader.addView(closeProperties, new LayoutParams(dp(wideLayout ? 40 : 64), dp(38))); propertyContainer.addView(propertyHeader, new LayoutParams(-1, dp(58))); propertyContainer.addView(propertyScroll, new LayoutParams(-1, 0, 1)); propertyPanel = propertyContainer;

        selectModeButton = action("选择", "选择并移动对象"); panModeButton = action("平移", "平移画布"); gridModeButton = action("网格", "切换网格"); canvasPreviewButton = action("预览", "切换纯净预览");
        LinearLayout canvasTools = panel(HORIZONTAL, 11); canvasTools.setGravity(Gravity.CENTER); canvasTools.setPadding(dp(4), dp(4), dp(4), dp(4)); for (Button b : new Button[]{selectModeButton, panModeButton, gridModeButton, canvasPreviewButton}) { styleCanvasAction(b); canvasTools.addView(b, new LayoutParams(dp(64), dp(32))); }
        contextBar = panel(HORIZONTAL, 11); contextBar.setGravity(Gravity.CENTER); contextBar.setPadding(dp(4), dp(3), dp(4), dp(3)); Button contextCopy = action("复制", "复制所选对象"); Button contextLeft = action("←", "向左移动所选对象"); Button contextRight = action("→", "向右移动所选对象"); Button contextStyle = action("样式", "所选样式操作"); Button contextDelete = action("删除", "删除所选对象"); for (Button b : new Button[]{contextCopy, contextLeft, contextRight, contextStyle, contextDelete}) { styleCanvasAction(b); contextBar.addView(b, new LayoutParams(dp(58), dp(30))); } contextBar.setVisibility(INVISIBLE);

        LinearLayout previewStates = panel(VERTICAL, 15); previewStates.setPadding(dp(10), dp(8), dp(10), dp(9)); TextView stateTitle = label("预览状态", 9); stateTitle.setTextColor(Color.parseColor("#929bb3")); previewStates.addView(stateTitle, new LayoutParams(-1, dp(24))); LinearLayout stateRow1 = new LinearLayout(context), stateRow2 = new LinearLayout(context); Button chineseChip = chip("中文"), asciiChip = chip("ASCII"), composeChip = chip("组字"), pagingChip = chip("翻页"), pressedChip = chip("按下"), detailChip = chip("更多..."); for (Button b : new Button[]{chineseChip, asciiChip, composeChip}) stateRow1.addView(b, new LayoutParams(0, dp(30), 1)); for (Button b : new Button[]{pagingChip, pressedChip, detailChip}) stateRow2.addView(b, new LayoutParams(0, dp(30), 1)); previewStates.addView(stateRow1); previewStates.addView(stateRow2);

        LinearLayout structurePanel = panel(VERTICAL, 15); structurePanel.setPadding(dp(10), dp(8), dp(10), dp(9)); TextView structureTitle = label("结构", 9); structureTitle.setTextColor(Color.parseColor("#929bb3")); structurePanel.addView(structureTitle, new LayoutParams(-1, dp(22))); LinearLayout tabs = new LinearLayout(context); statisticsTab = chip("统计"); layersTab = chip("图层"); historyTab = chip("历史"); tabs.addView(statisticsTab, new LayoutParams(0, dp(29), 1)); tabs.addView(layersTab, new LayoutParams(0, dp(29), 1)); tabs.addView(historyTab, new LayoutParams(0, dp(29), 1)); structurePanel.addView(tabs); structureContent = new LinearLayout(context); structureContent.setOrientation(VERTICAL); structurePanel.addView(structureContent, new LayoutParams(-1, -2));
        if (!wideLayout) { propertyContainer.removeView(propertyScroll); propertyContainer.addView(previewStates, new LayoutParams(-1, dp(102))); propertyContainer.addView(structurePanel, new LayoutParams(-1, -2)); propertyContainer.addView(propertyScroll, new LayoutParams(-1, 0, 1)); }

        LinearLayout toolbox = panel(wideLayout ? VERTICAL : HORIZONTAL, 20); toolbox.setGravity(Gravity.CENTER); toolbox.setPadding(dp(6), dp(6), dp(6), dp(6)); TextView toolboxTitle = label(wideLayout ? "组件" : "", 8); toolboxTitle.setTextColor(Color.parseColor("#7f879c")); if (wideLayout) toolbox.addView(toolboxTitle, new LayoutParams(-1, dp(28))); Button propertiesButton = action("属性", "打开属性检查器"); for (Button b : new Button[]{appendSelectButton, addButton, rowButton, candidateButton, propertiesButton}) { styleCompactAction(b); toolbox.addView(b, wideLayout ? new LayoutParams(dp(68), dp(50)) : compactActionParams()); }

        FrameLayout stage = new FrameLayout(context); stage.setClipChildren(false); stage.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams toolsParams = new FrameLayout.LayoutParams(dp(272), dp(40), Gravity.TOP | Gravity.CENTER_HORIZONTAL); toolsParams.topMargin = dp(4); stage.addView(canvasTools, toolsParams);
        FrameLayout.LayoutParams contextParams = new FrameLayout.LayoutParams(dp(300), dp(38), Gravity.TOP | Gravity.CENTER_HORIZONTAL); contextParams.topMargin = dp(48); stage.addView(contextBar, contextParams);
        if (wideLayout) { FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(dp(184), dp(102), Gravity.TOP | Gravity.START); previewParams.setMargins(dp(12), dp(62), 0, 0); stage.addView(previewStates, previewParams); FrameLayout.LayoutParams structureParams = new FrameLayout.LayoutParams(dp(200), -2, Gravity.BOTTOM | Gravity.START); structureParams.setMargins(dp(12), 0, 0, dp(12)); stage.addView(structurePanel, structureParams); }

        LinearLayout body = new LinearLayout(context); body.setOrientation(HORIZONTAL); body.setClipChildren(false);
        if (wideLayout) { LayoutParams toolboxParams = new LayoutParams(dp(82), -1); toolboxParams.setMargins(dp(18), dp(8), dp(8), dp(8)); body.addView(toolbox, toolboxParams); body.addView(stage, new LayoutParams(0, -1, 1)); LayoutParams inspectorParams = new LayoutParams(dp(300), -1); inspectorParams.setMargins(dp(8), dp(8), dp(18), dp(8)); body.addView(propertyContainer, inspectorParams); }
        else { propertyContainer.setVisibility(GONE); FrameLayout.LayoutParams drawer = new FrameLayout.LayoutParams(dp(300), -1, Gravity.END); drawer.setMargins(dp(12), dp(52), dp(10), dp(10)); stage.addView(propertyContainer, drawer); body.addView(stage, new LayoutParams(0, -1, 1)); }
        addView(body, new LayoutParams(-1, 0, 1));
        if (!wideLayout) { HorizontalScrollView bottomScroll = new HorizontalScrollView(context); bottomScroll.setHorizontalScrollBarEnabled(false); bottomScroll.addView(toolbox, new HorizontalScrollView.LayoutParams(-2, dp(62))); LayoutParams bottomTools = new LayoutParams(-1, dp(66)); bottomTools.setMargins(dp(10), dp(3), dp(10), dp(3)); addView(bottomScroll, bottomTools); }

        LinearLayout statusBar = panel(HORIZONTAL, 13); statusBar.setGravity(Gravity.CENTER_VERTICAL); statusBar.setPadding(dp(12), 0, dp(6), 0); status = label("所有更改已保存", 10); status.setTextColor(Color.parseColor("#8d95a9")); status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END); statusBar.addView(status, new LayoutParams(0, -1, 1)); statusContext = label("", 9); statusContext.setTextColor(Color.parseColor("#697287")); if (wideLayout) statusBar.addView(statusContext, new LayoutParams(-2, -1)); Button zoomOut = action("−", "缩小"); Button zoomIn = action("+", "放大"); Button fit = action("适应", "使画布适应窗口"); zoomValue = label("100%", 10); zoomValue.setGravity(Gravity.CENTER); for (Button b : new Button[]{zoomOut, zoomIn, fit}) styleCanvasAction(b); statusBar.addView(zoomOut, new LayoutParams(dp(32), dp(30))); statusBar.addView(zoomValue, new LayoutParams(dp(48), dp(30))); statusBar.addView(zoomIn, new LayoutParams(dp(32), dp(30))); statusBar.addView(fit, new LayoutParams(dp(42), dp(30))); LayoutParams statusParams = new LayoutParams(-1, dp(42)); statusParams.setMargins(dp(wideLayout ? 120 : 10), dp(4), dp(wideLayout ? 340 : 10), dp(10)); addView(statusBar, statusParams);

        final Button[] selectionActions = {appendSelectButton, selectAllButton, invertButton, rowButton, batchButton}; final Button[] structureActions = {addButton, duplicateButton, deleteButton, rowManageButton, flexButton, flexManageButton, absoluteButton}; final Button[] pageActions = {previousPageButton, nextPageButton, pageAddButton, pageDeleteButton, pageManageButton}; final Button[] previewActions = {previewButton, stateButton, eventButton, modeButton, candidateButton, toolbarButton, compositionButton, pressedButton}; final Button[] dataActions = {clipboardButton};
        moreButton.setOnClickListener(v -> showActionGroups(selectionActions, structureActions, pageActions, previewActions, dataActions)); propertiesButton.setOnClickListener(v -> showProperties(true)); closeProperties.setOnClickListener(v -> showProperties(false));
        appendSelectButton.setOnClickListener(v -> { appendSelection = !appendSelection; canvas.setAppendSelection(appendSelection); appendSelectButton.setText(appendSelection ? "✓ 多选" : "多选"); setStatus(appendSelection ? "已启用追加选择" : "已启用单选"); }); selectAllButton.setOnClickListener(v -> selectAllKeys()); invertButton.setOnClickListener(v -> invertSelection()); rowButton.setOnClickListener(v -> selectCurrentRow()); batchButton.setOnClickListener(v -> showBatchEditor()); clipboardButton.setOnClickListener(v -> showClipboardActions()); rowManageButton.setOnClickListener(v -> manageRows());
        previousPageButton.setOnClickListener(v -> switchKeyMapPage(-1)); nextPageButton.setOnClickListener(v -> switchKeyMapPage(1)); pageAddButton.setOnClickListener(v -> addKeyMapPage()); pageDeleteButton.setOnClickListener(v -> deleteKeyMapPage()); pageManageButton.setOnClickListener(v -> manageKeyMapPage()); flexButton.setOnClickListener(v -> editSelectedFlex()); flexManageButton.setOnClickListener(v -> manageFlexContainers()); absoluteButton.setOnClickListener(v -> manageAbsoluteKeys());
        previewButton.setOnClickListener(v -> showPreviewSettings()); stateButton.setOnClickListener(v -> showPreviewState()); eventButton.setOnClickListener(v -> { ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) setStatus("请先选择按键"); else if (callbacks != null) callbacks.onManageKeyEvents(key.copy()); else showSelectedEventPreview(); });
        modeButton.setOnClickListener(v -> cycleInputMode(modeButton)); candidateButton.setOnClickListener(v -> toggleCandidate()); toolbarButton.setOnClickListener(v -> { model.showToolbar = !model.showToolbar; canvas.invalidate(); setStatus("工具栏预览已" + (model.showToolbar ? "开启" : "关闭")); }); compositionButton.setOnClickListener(v -> toggleComposition()); pressedButton.setOnClickListener(v -> togglePressed());
        addButton.setOnClickListener(v -> addKey()); duplicateButton.setOnClickListener(v -> duplicateSelected()); deleteButton.setOnClickListener(v -> deleteSelected()); undoButton.setOnClickListener(v -> undo()); redoButton.setOnClickListener(v -> redo()); saveButton.setOnClickListener(v -> { if (!canEdit()) return; properties.commit(); if (callbacks != null) callbacks.onSave(model.copy()); dirty = false; setStatus("所有更改已保存"); });
        contextCopy.setOnClickListener(v -> duplicateSelected()); contextLeft.setOnClickListener(v -> moveSelection(-1)); contextRight.setOnClickListener(v -> moveSelection(1)); contextStyle.setOnClickListener(v -> showStyleActions()); contextDelete.setOnClickListener(v -> deleteSelected());
        selectModeButton.setOnClickListener(v -> setCanvasMode("select")); panModeButton.setOnClickListener(v -> setCanvasMode("pan")); gridModeButton.setOnClickListener(v -> toggleGrid()); canvasPreviewButton.setOnClickListener(v -> toggleCanvasPreview()); zoomOut.setOnClickListener(v -> setCanvasZoom(model.previewZoom - .1f)); zoomIn.setOnClickListener(v -> setCanvasZoom(model.previewZoom + .1f)); fit.setOnClickListener(v -> fitCanvas());
        chineseChip.setOnClickListener(v -> { model.inputMode = ThemeEditorModel.InputMode.CHINESE; canvas.invalidate(); setStatus("预览状态:中文"); }); asciiChip.setOnClickListener(v -> { model.inputMode = ThemeEditorModel.InputMode.ASCII; canvas.invalidate(); setStatus("预览状态:ASCII"); }); composeChip.setOnClickListener(v -> toggleComposition()); pagingChip.setOnClickListener(v -> { model.previewPaging = !model.previewPaging; canvas.invalidate(); setStatus("翻页状态已" + (model.previewPaging ? "开启" : "关闭")); }); pressedChip.setOnClickListener(v -> togglePressed()); detailChip.setOnClickListener(v -> showPreviewState()); statisticsTab.setOnClickListener(v -> showStructurePage(0)); layersTab.setOnClickListener(v -> showStructurePage(1)); historyTab.setOnClickListener(v -> showStructurePage(2));
        canvas.setListener(new ThemeKeyboardCanvas.Listener() { public void onKeySelected(ThemeEditorModel.Key key) { properties.commit(); refreshSelectionEditor(key); if (key != null && !wideLayout) showProperties(true); if (key != null && model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX && !key.ownerId.isEmpty()) model.selectedFlexContainerId = key.ownerId; contextBar.setVisibility(key == null && model.selectedIds.isEmpty() ? INVISIBLE : VISIBLE); setStatus(key == null ? "未选择" : selectedKeys().size() > 1 ? "已选择 " + selectedKeys().size() + " 个按键" : "已选择 " + key.label); if (callbacks != null) callbacks.onSelectionChanged(key); } public void onKeyMoveStarted() { changeStarted(); } public void onKeyMoved() { canvas.invalidate(); setStatus("正在移动所选对象"); } public void onKeyMoveFinished(ThemeEditorModel.Key key) { finishKeyMove(key); } });
        properties.setListener(new ThemePropertyEditor.Listener() {
            public void onPropertyChangeStarted() { changeStarted(); }
            public void onPropertyChanged() { canvas.invalidate(); dirty = true; setStatus("已编辑 " + (canvas.getSelectedKey() == null ? "主题" : canvas.getSelectedKey().label)); if (callbacks != null) callbacks.onModelChanged(model.copy()); }
            public void onOpenStyleProperties(ThemeEditorModel.Key key) { if (callbacks != null) callbacks.onOpenStyleProperties(key); else setStatus("无法打开样式属性:未设置编辑器回调"); }
            public void onOpenKeyEvents(ThemeEditorModel.Key key) { if (callbacks != null) callbacks.onOpenKeyEvents(key); else setStatus("无法打开按键事件:未设置编辑器回调"); }
            public void onOpenResources(ThemeEditorModel.Key key) { if (callbacks != null) callbacks.onOpenResources(key); else setStatus("无法打开资源浏览器:未设置编辑器回调"); }
            public void onOpenLuaSource() { if (callbacks != null) callbacks.onOpenLuaSource(); else setStatus("无法打开 Lua 源码:未设置编辑器回调"); }
        });
        setModel(ThemeEditorModel.sample()); setCanvasMode("select"); showStructurePage(0);
    }


    private LinearLayout panel(int orientation, int radius) {
        LinearLayout view = new LinearLayout(getContext()); view.setOrientation(orientation);
        android.graphics.drawable.GradientDrawable background = roundedBackground("#d9121726", radius);
        background.setStroke(dp(1), Color.parseColor("#2bffffff")); view.setBackground(background); view.setElevation(dp(8)); return view;
    }

    private Button chip(String text) { Button button = action(text, text); styleCanvasAction(button); button.setTextSize(9); return button; }
    private void styleCanvasAction(Button button) { button.setTextSize(10); button.setTextColor(Color.parseColor("#b8bfd0")); button.setBackground(roundedBackground("#191f30", 8)); button.setPadding(dp(5), 0, dp(5), 0); }

    /** Canvas owners may implement these optional methods without coupling this workspace to a new API revision. */
    private void canvasCommand(String method, Class<?> type, Object value) {
        try { canvas.getClass().getMethod(method, type).invoke(canvas, value); }
        catch (ReflectiveOperationException ignored) { canvas.invalidate(); }
    }
    private void setCanvasMode(String mode) {
        canvasMode = mode; canvasCommand("setInteractionMode", String.class, mode); canvasCommand("setCanvasMode", String.class, mode);
        boolean select = "select".equals(mode); selectModeButton.setText(select ? "✓ 选择" : "选择"); panModeButton.setText(select ? "平移" : "✓ 平移");
        canvas.setReadOnly(readOnly || !select); setStatus(select ? "选择模式" : "画布平移模式");
    }
    private void toggleGrid() { gridVisible = !gridVisible; canvasCommand("setGridVisible", boolean.class, gridVisible); gridModeButton.setText(gridVisible ? "✓ 网格" : "网格"); setStatus("网格已" + (gridVisible ? "显示" : "隐藏")); }
    private void toggleCanvasPreview() { canvasPreviewMode = !canvasPreviewMode; canvasCommand("setPreviewMode", boolean.class, canvasPreviewMode); canvasPreviewButton.setText(canvasPreviewMode ? "✓ 预览" : "预览"); contextBar.setVisibility(canvasPreviewMode ? INVISIBLE : selectedKeys().isEmpty() ? INVISIBLE : VISIBLE); setStatus(canvasPreviewMode ? "纯净预览模式" : "编辑器预览模式"); }
    private void setCanvasZoom(float zoom) { model.previewZoom = Math.max(.5f, Math.min(4f, zoom)); canvasCommand("setZoom", float.class, model.previewZoom); canvas.invalidate(); zoomValue.setText(Math.round(model.previewZoom * 100) + "%"); setStatus("缩放至 " + zoomValue.getText()); }
    private void fitCanvas() { model.previewZoom = 1f; model.previewPanX = 0; model.previewPanY = 0; canvasCommand("fitToViewport", boolean.class, true); canvas.invalidate(); zoomValue.setText("100%"); setStatus("画布已适应窗口"); }

    private void cycleInputMode(Button source) { model.inputMode = ThemeEditorModel.InputMode.values()[(model.inputMode.ordinal() + 1) % ThemeEditorModel.InputMode.values().length]; source.setText(model.inputMode.name()); canvas.invalidate(); setStatus("预览输入模式:" + model.inputMode.name()); }
    private void toggleCandidate() { model.showCandidate = !model.showCandidate; canvas.invalidate(); setStatus("候选栏预览已" + (model.showCandidate ? "开启" : "关闭")); }
    private void toggleComposition() { model.showComposition = !model.showComposition; canvas.invalidate(); setStatus("组合窗预览已" + (model.showComposition ? "开启" : "关闭")); }
    private void togglePressed() { model.pressedPreview = !model.pressedPreview; canvas.invalidate(); setStatus("按下状态预览已" + (model.pressedPreview ? "开启" : "关闭")); }

    private void moveSelection(int direction) {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("请先选择一个或多个按键"); return; }
        if (!changeStarted()) return;
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) for (ThemeEditorModel.Key key : keys) if (!key.editorLocked) { key.x += direction; clampAbsolute(key); }
        else { ThemeEditorModel.Key primary = canvas.getSelectedKey(); int from = model.keys.indexOf(primary), to = Math.max(0, Math.min(model.keys.size() - 1, from + direction)); if (from >= 0 && from != to) java.util.Collections.swap(model.keys, from, to); }
        persistCurrentKeyMapPage(); notifyModelChanged(direction < 0 ? "已向左移动所选对象" : "已向右移动所选对象");
    }
    private void showStyleActions() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) { setStatus("请先选择按键"); return; }
        String[] actions = {"复制完整样式", "粘贴样式", "批量样式...", "在属性检查器中编辑样式引用"};
        new android.app.AlertDialog.Builder(getContext()).setTitle("样式 · " + key.label).setItems(actions, (dialog, which) -> {
            if (which == 0) copySelectedStyle(); else if (which == 1) pasteClipboard(); else if (which == 2) showBatchEditor(); else showProperties(true);
        }).setNegativeButton("关闭", null).show();
    }

    private void showStructurePage(int page) {
        structurePage = page; statisticsTab.setText(page == 0 ? "✓ 统计" : "统计"); layersTab.setText(page == 1 ? "✓ 图层" : "图层"); historyTab.setText(page == 2 ? "✓ 历史" : "历史"); refreshStructurePanel();
    }
    private String layoutName() {
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) return "行布局(rows)";
        if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) return "弹性盒(flex_box)";
        if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) return "按键映射(key_maps)";
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) return "绝对定位按键(absolute_keys)";
        return "无布局(none)";
    }
    private void refreshStructurePanel() {
        if (structureContent == null || model == null) return; structureContent.removeAllViews();
        if (structurePage == 0) {
            addStructureRow("布局", layoutName()); addStructureRow("按键数", String.valueOf(model.keys.size())); addStructureRow("已选择", String.valueOf(selectedKeys().size()));
            int groups = model.layoutMode == ThemeEditorModel.LayoutMode.ROWS ? model.rows.size() : model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX ? model.flexContainers.size() : model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS ? model.keyMapPages.size() : 0; addStructureRow("分组数", String.valueOf(groups)); addStructureRow("预览尺寸", Math.round(model.previewWidth) + "×" + Math.round(model.previewHeight));
        } else if (structurePage == 1) {
            addStructureAction("候选栏", model.showCandidate ? "显示" : "隐藏", v -> toggleCandidate());
            addStructureAction("工具栏", model.showToolbar ? "显示" : "隐藏", v -> { model.showToolbar = !model.showToolbar; canvas.invalidate(); setStatus("工具栏预览已" + (model.showToolbar ? "开启" : "关闭")); });
            addStructureAction("组合窗", model.showComposition ? "显示" : "隐藏", v -> toggleComposition());
            if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) for (ThemeEditorModel.Row row : model.rows) addStructureAction("↳ 行(rows):" + row.id, countOwner(row.id) + " 个按键", v -> selectOwner(row.id));
            else if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) for (ThemeEditorModel.FlexContainer flex : model.flexContainers) addStructureAction("↳ 弹性容器(flex):" + flex.id, countOwner(flex.id) + " 个按键", v -> { model.selectedFlexContainerId = flex.id; selectOwner(flex.id); });
            else if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) for (int i = 0; i < model.keyMapPages.size(); i++) { final int page = i; ThemeEditorModel.KeyMapPage item = model.keyMapPages.get(i); addStructureAction((i == model.selectedKeyMapPage ? "● " : "↳ ") + "按键映射页(key_maps):" + item.name, item.keys.size() + " 个按键", v -> selectKeyMapPage(page)); }
        } else {
            addStructureRow("当前状态", dirty ? "已修改" : "已保存");
            addStructureAction("可撤销步骤", String.valueOf(undo.size()), v -> undo());
            addStructureAction("可重做步骤", String.valueOf(redo.size()), v -> redo());
            ThemeEditorModel.Key key = canvas.getSelectedKey(); addStructureRow("当前选择", key == null ? "无" : key.label);
            Button undoAction = action("撤销", "撤销最近一次真实更改"); Button redoAction = action("重做", "重做最近一次真实更改"); styleCanvasAction(undoAction); styleCanvasAction(redoAction); undoAction.setEnabled(!undo.isEmpty() && !readOnly); redoAction.setEnabled(!redo.isEmpty() && !readOnly); undoAction.setOnClickListener(v -> undo()); redoAction.setOnClickListener(v -> redo()); LinearLayout actions = new LinearLayout(getContext()); actions.addView(undoAction, new LayoutParams(0, dp(32), 1)); actions.addView(redoAction, new LayoutParams(0, dp(32), 1)); structureContent.addView(actions);
        }
    }
    private void selectOwner(String owner) { ThemeEditorModel.Key first = null; model.selectedIds.clear(); for (ThemeEditorModel.Key key : model.keys) if (owner.equals(key.ownerId)) { model.selectedIds.add(key.id); if (first == null) first = key; } canvas.setModel(model); refreshSelectionEditor(first); setStatus(first == null ? "该结构中没有按键" : "已定位并选择 " + model.selectedIds.size() + " 个按键"); }
    private void selectKeyMapPage(int page) { if (page < 0 || page >= model.keyMapPages.size()) return; persistCurrentKeyMapPage(); model.selectedKeyMapPage = page; model.keys.clear(); for (ThemeEditorModel.Key key : model.keyMapPages.get(page).keys) model.keys.add(key.copy()); model.selectedIds.clear(); canvas.setModel(model); ThemeEditorModel.Key first = model.keys.isEmpty() ? null : model.keys.get(0); if (first != null) model.selectedIds.add(first.id); refreshSelectionEditor(first); setStatus("已切换到按键映射页(key_maps):" + model.keyMapPages.get(page).name); }
    private int countOwner(String owner) { int count = 0; for (ThemeEditorModel.Key key : model.keys) if (owner.equals(key.ownerId)) count++; return count; }
    private void addStructureRow(String name, String value) { addStructureAction(name, value, null); }
    private void addStructureAction(String name, String value, View.OnClickListener listener) { LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL); TextView left = label(name, 9), right = label(value, 9); left.setTextColor(Color.parseColor("#858da1")); right.setTextColor(Color.parseColor("#c7cedc")); right.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); row.addView(left, new LayoutParams(0, dp(25), 1)); row.addView(right, new LayoutParams(-2, dp(25))); if (listener != null) { row.setOnClickListener(listener); row.setClickable(true); row.setContentDescription(name + ",当前" + value + ",点击操作"); } structureContent.addView(row); }

    private void showPreviewState() {
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(VERTICAL); fields.setPadding(24, 8, 24, 8);
        android.widget.EditText candidateCount = dialogField(fields, "候选数量(0~20)", String.valueOf(model.candidateCount)); android.widget.EditText composition = dialogField(fields, "组合窗文本", model.compositionText); android.widget.EditText action = dialogField(fields, "编辑器动作标签", model.editorActionLabel); android.widget.EditText schema = dialogField(fields, "方案名称", model.schemaName);
        android.widget.CheckBox comments = new android.widget.CheckBox(getContext()); comments.setText("候选注释"); comments.setChecked(model.candidateComments); fields.addView(comments); android.widget.CheckBox paging = new android.widget.CheckBox(getContext()); paging.setText("翻页状态"); paging.setChecked(model.previewPaging); fields.addView(paging); android.widget.CheckBox menu = new android.widget.CheckBox(getContext()); menu.setText("存在菜单状态"); menu.setChecked(model.previewHasMenu); fields.addView(menu);
        android.widget.Spinner panel = new android.widget.Spinner(getContext()); panel.setAdapter(new android.widget.ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"键盘", "展开候选", "符号面板", "剪贴板面板"})); panel.setSelection(model.previewPanel.ordinal()); fields.addView(panel);
        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext()); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(getContext()).setTitle("预览状态").setView(scroll).setNegativeButton("取消", null).setNeutralButton("重置", (dialog, which) -> { model.candidateCount = 4; model.candidateComments = false; model.previewPaging = false; model.previewHasMenu = false; model.compositionText = "拼音"; model.editorActionLabel = "回车"; model.schemaName = "方案"; model.previewPanel = ThemeEditorModel.PreviewPanel.KEYBOARD; canvas.invalidate(); setStatus("预览状态已重置;主题未更改"); }).setPositiveButton("应用", (dialog, which) -> { model.candidateCount = Math.max(0, Math.min(20, (int) parseFloat(candidateCount, model.candidateCount))); model.candidateComments = comments.isChecked(); model.previewPaging = paging.isChecked(); model.previewHasMenu = menu.isChecked(); model.compositionText = composition.getText().toString(); model.editorActionLabel = action.getText().toString(); model.schemaName = schema.getText().toString(); model.previewPanel = ThemeEditorModel.PreviewPanel.values()[panel.getSelectedItemPosition()]; canvas.invalidate(); setStatus("预览状态已应用;主题未更改"); }).show();
    }

    private void showSelectedEventPreview() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) { setStatus("请先选择按键"); return; }
        String[] labels = {"点击:" + eventName(key.click), "长按:" + eventName(key.longClick), "左滑:" + eventName(key.swipeLeft), "右滑:" + eventName(key.swipeRight), "上滑:" + eventName(key.swipeUp), "下滑:" + eventName(key.swipeDown)};
        new android.app.AlertDialog.Builder(getContext()).setTitle("模拟字面按键事件").setItems(labels, (dialog, which) -> { String value = which == 0 ? key.click : which == 1 ? key.longClick : which == 2 ? key.swipeLeft : which == 3 ? key.swipeRight : which == 4 ? key.swipeUp : key.swipeDown; setStatus(value.isEmpty() ? "未指定字面事件" : "已模拟事件标签:" + value + ";未执行命令和脚本"); }).setNegativeButton("关闭", null).show();
    }
    private static String eventName(String value) { return value == null || value.isEmpty() ? "(无)" : value; }

    private void showPreviewSettings() {
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(VERTICAL); fields.setPadding(24, 8, 24, 8);
        android.widget.EditText width = dialogField(fields, "预览宽度(dp)", String.valueOf(model.previewWidth)); android.widget.EditText height = dialogField(fields, "预览高度(dp)", String.valueOf(model.previewHeight)); android.widget.EditText zoom = dialogField(fields, "缩放(0.5~4)", String.valueOf(model.previewZoom)); android.widget.EditText panX = dialogField(fields, "水平平移(px)", String.valueOf(model.previewPanX)); android.widget.EditText panY = dialogField(fields, "垂直平移(px)", String.valueOf(model.previewPanY));
        String[] presets = {"手机竖屏 360×300", "手机横屏 720×260", "平板竖屏 600×420", "平板横屏 960×360", "自定义值", "重置缩放和平移"};
        new android.app.AlertDialog.Builder(getContext()).setTitle("预览设备").setSingleChoiceItems(presets, 4, (dialog, which) -> {
            if (which == 0) { width.setText("360"); height.setText("300"); } else if (which == 1) { width.setText("720"); height.setText("260"); } else if (which == 2) { width.setText("600"); height.setText("420"); } else if (which == 3) { width.setText("960"); height.setText("360"); } else if (which == 5) { zoom.setText("1"); panX.setText("0"); panY.setText("0"); }
        }).setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> {
            model.previewWidth = Math.max(120, parseFloat(width, model.previewWidth)); model.previewHeight = Math.max(100, parseFloat(height, model.previewHeight)); model.previewZoom = Math.max(.5f, Math.min(4f, parseFloat(zoom, model.previewZoom))); model.previewPanX = parseFloat(panX, model.previewPanX); model.previewPanY = parseFloat(panY, model.previewPanY); canvas.invalidate(); setStatus("预览 " + (int) model.previewWidth + "×" + (int) model.previewHeight + ",缩放 " + trimPreview(model.previewZoom) + "×;主题未更改");
        }).show();
    }
    private static String trimPreview(float value) { return value == (int) value ? Integer.toString((int) value) : Float.toString(value); }

    private void switchKeyMapPage(int delta) {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.isEmpty()) { setStatus("当前不是按键映射(key_maps)键盘"); return; }
        persistCurrentKeyMapPage();
        int count = model.keyMapPages.size();
        model.selectedKeyMapPage = (model.selectedKeyMapPage + delta + count) % count;
        model.keys.clear();
        for (ThemeEditorModel.Key key : model.keyMapPages.get(model.selectedKeyMapPage).keys) model.keys.add(key.copy());
        model.selectedIds.clear(); canvas.setModel(model); canvas.setSelectedKey(null); properties.bind(null);
        setStatus("页面 " + (model.selectedKeyMapPage + 1) + ":" + model.keyMapPages.get(model.selectedKeyMapPage).name);
    }

    private void persistCurrentKeyMapPage() {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.isEmpty()) return;
        ThemeEditorModel.KeyMapPage page = model.keyMapPages.get(model.selectedKeyMapPage);
        page.keys.clear();
        for (ThemeEditorModel.Key key : model.keys) page.keys.add(key.copy());
    }

    private void addKeyMapPage() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS) { setStatus("当前不是按键映射(key_maps)键盘"); return; }
        if (!changeStarted()) return; persistCurrentKeyMapPage();
        ThemeEditorModel.KeyMapPage page = new ThemeEditorModel.KeyMapPage("key_map_new_" + model.keyMapPages.size(), "页面 " + (model.keyMapPages.size() + 1));
        model.keyMapPages.add(page); model.selectedKeyMapPage = model.keyMapPages.size() - 1; model.keys.clear(); model.selectedIds.clear(); canvas.setSelectedKey(null); properties.bind(null); canvas.setModel(model);
        if (callbacks != null) callbacks.onModelChanged(model.copy()); setStatus("已添加符号页");
    }

    private void deleteKeyMapPage() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.size() <= 1) { setStatus("至少需要一个符号页"); return; }
        if (!changeStarted()) return; model.keyMapPages.remove(model.selectedKeyMapPage); model.selectedKeyMapPage = Math.min(model.selectedKeyMapPage, model.keyMapPages.size() - 1);
        model.keys.clear(); for (ThemeEditorModel.Key key : model.keyMapPages.get(model.selectedKeyMapPage).keys) model.keys.add(key.copy()); model.selectedIds.clear(); canvas.setSelectedKey(null); properties.bind(null); canvas.setModel(model);
        if (callbacks != null) callbacks.onModelChanged(model.copy()); setStatus("已删除符号页");
    }

    private void manageKeyMapPage() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.isEmpty()) { setStatus("当前不是按键映射(key_maps)键盘"); return; }
        persistCurrentKeyMapPage(); ThemeEditorModel.KeyMapPage page = model.keyMapPages.get(model.selectedKeyMapPage);
        String[] actions = {"重命名", "复制", "前移", "后移", "追加字符或动作", "移除重复按键"};
        new android.app.AlertDialog.Builder(getContext()).setTitle(page.name).setItems(actions, (dialog, which) -> {
            if (which == 0) renameKeyMapPage(page); else if (which == 1) duplicateKeyMapPage(page); else if (which == 2 || which == 3) moveKeyMapPage(which == 2 ? -1 : 1); else if (which == 4) appendKeyMapItems(page); else removeDuplicateKeyMapItems(page);
        }).setNegativeButton("取消", null).show();
    }

    private void appendKeyMapItems(ThemeEditorModel.KeyMapPage page) {
        LinearLayout fields = new LinearLayout(getContext()); android.widget.EditText input = dialogField(fields, "字符,或使用逗号/换行分隔的动作", ""); input.setSingleLine(false); input.setMinLines(3);
        new android.app.AlertDialog.Builder(getContext()).setTitle("追加符号按键").setView(fields).setNegativeButton("取消", null).setPositiveButton("追加", (dialog, which) -> {
            String value = input.getText().toString(); if (value.trim().isEmpty() || !changeStarted()) return; persistCurrentKeyMapPage();
            java.util.ArrayList<String> items = new java.util.ArrayList<>();
            if (value.contains(",") || value.contains("\n")) for (String item : value.split("[,\n]")) { String trimmed = item.trim(); if (!trimmed.isEmpty()) items.add(trimmed); }
            else { int offset = 0; while (offset < value.length()) { int codePoint = value.codePointAt(offset); items.add(new String(Character.toChars(codePoint))); offset += Character.charCount(codePoint); } }
            for (String item : items) { ThemeEditorModel.Key key = new ThemeEditorModel.Key(page.id + "_key_new_" + System.nanoTime(), item, 0, 0, 11.5f, 9.5f); key.click = item; key.sourceClick = ""; key.ownerId = page.id; page.keys.add(key); }
            model.keys.clear(); for (ThemeEditorModel.Key key : page.keys) model.keys.add(key.copy()); layoutCurrentKeyMap(); notifyModelChanged("已追加 " + items.size() + " 个符号按键");
        }).show();
    }

    private void removeDuplicateKeyMapItems(ThemeEditorModel.KeyMapPage page) {
        persistCurrentKeyMapPage(); java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>(); java.util.ArrayList<ThemeEditorModel.Key> unique = new java.util.ArrayList<>();
        for (ThemeEditorModel.Key key : page.keys) { String identity = key.click + "\u0000" + key.label; if (seen.add(identity)) unique.add(key); }
        if (unique.size() == page.keys.size()) { setStatus("没有重复的符号按键"); return; } if (!changeStarted()) return;
        page.keys.clear(); page.keys.addAll(unique); model.keys.clear(); for (ThemeEditorModel.Key key : page.keys) model.keys.add(key.copy()); layoutCurrentKeyMap(); notifyModelChanged("已移除重复符号按键");
    }

    private void layoutCurrentKeyMap() {
        for (int i = 0; i < model.keys.size(); i++) { ThemeEditorModel.Key key = model.keys.get(i); key.x = (i % 8) * 12.3f; key.y = 10f + (i / 8) * 11f; }
        persistCurrentKeyMapPage(); canvas.setModel(model);
    }

    private void renameKeyMapPage(ThemeEditorModel.KeyMapPage page) {
        android.widget.EditText name = dialogField(new LinearLayout(getContext()), "页面名称", page.name);
        LinearLayout parent = (LinearLayout) name.getParent(); parent.setPadding(24, 8, 24, 8);
        new android.app.AlertDialog.Builder(getContext()).setTitle("重命名符号页").setView(parent).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { String value = name.getText().toString().trim(); if (!value.isEmpty()) { if (!changeStarted()) return; page.name = value; notifyModelChanged("已重命名符号页"); } }).show();
    }

    private void duplicateKeyMapPage(ThemeEditorModel.KeyMapPage page) {
        if (!changeStarted()) return; ThemeEditorModel.KeyMapPage copy = page.copy(); copy.id = "key_map_copy_" + System.nanoTime(); copy.name = page.name + " copy";
        for (int i = 0; i < copy.keys.size(); i++) { copy.keys.get(i).id = copy.id + "_key_" + i; copy.keys.get(i).ownerId = copy.id; }
        model.keyMapPages.add(model.selectedKeyMapPage + 1, copy); model.selectedKeyMapPage++; model.keys.clear(); for (ThemeEditorModel.Key key : copy.keys) model.keys.add(key.copy()); canvas.setModel(model); notifyModelChanged("已复制符号页");
    }

    private void moveKeyMapPage(int delta) {
        int target = model.selectedKeyMapPage + delta; if (target < 0 || target >= model.keyMapPages.size()) { setStatus("符号页已位于边界"); return; }
        if (!changeStarted()) return; java.util.Collections.swap(model.keyMapPages, model.selectedKeyMapPage, target); model.selectedKeyMapPage = target; notifyModelChanged("已调整符号页顺序");
    }

    private ThemeEditorModel.FlexContainer selectedFlex() {
        if (model.flexContainers.isEmpty()) return null;
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) if (container.id.equals(model.selectedFlexContainerId)) return container;
        model.selectedFlexContainerId = model.flexContainers.get(0).id;
        return model.flexContainers.get(0);
    }

    private void editSelectedFlex() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.FLEX_BOX) { setStatus("当前不是弹性盒(flex_box)键盘"); return; }
        ThemeEditorModel.FlexContainer container = selectedFlex(); if (container == null) return;
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(VERTICAL); fields.setPadding(24, 8, 24, 8);
        android.widget.EditText direction = dialogField(fields, "方向:行(row)或列(column)", container.direction);
        android.widget.EditText width = dialogField(fields, "宽度(dp,-1 表示弹性)", String.valueOf(container.width));
        android.widget.EditText height = dialogField(fields, "高度(dp,-1 表示弹性)", String.valueOf(container.height));
        android.widget.EditText grow = dialogField(fields, "伸展系数(grow)", String.valueOf(container.grow));
        android.widget.EditText style = dialogField(fields, "样式", container.style);
        new android.app.AlertDialog.Builder(getContext()).setTitle("弹性容器(flex):" + container.id).setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> {
            if (!changeStarted()) return; container.direction = "column".equals(direction.getText().toString().trim()) ? "column" : "row";
            container.width = parseFloat(width, container.width); container.height = parseFloat(height, container.height); container.grow = Math.max(0, parseFloat(grow, container.grow)); container.style = style.getText().toString().trim();
            if (("row".equals(container.direction) && container.width > 0) || ("column".equals(container.direction) && container.height > 0)) container.grow = 0;
            notifyModelChanged("已编辑弹性容器(flex)");
        }).show();
    }

    private void manageFlexContainers() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.FLEX_BOX || model.flexContainers.isEmpty()) { setStatus("当前不是弹性盒(flex_box)键盘"); return; }
        String[] labels = new String[model.flexContainers.size()];
        for (int i = 0; i < labels.length; i++) { ThemeEditorModel.FlexContainer c = model.flexContainers.get(i); labels[i] = (c.id.equals(model.selectedFlexContainerId) ? "✓ " : "") + c.id + "  " + ("column".equals(c.direction) ? "列(column)" : "行(row)") + " 伸展(grow)=" + c.grow; }
        new android.app.AlertDialog.Builder(getContext()).setTitle("选择弹性容器(flex)").setItems(labels, (dialog, which) -> {
            model.selectedFlexContainerId = model.flexContainers.get(which).id; showFlexActions();
        }).setNegativeButton("关闭", null).show();
    }

    private void showFlexActions() {
        ThemeEditorModel.FlexContainer selected = selectedFlex(); if (selected == null) return;
        String[] actions = selected.parentId == null ? new String[]{"编辑", "添加子容器", "将所选按键移到此处"} : new String[]{"编辑", "添加子容器", "将所选按键移到此处", "更改容器父级", "复制子树", "删除子树", "前移", "后移"};
        new android.app.AlertDialog.Builder(getContext()).setTitle("弹性容器(flex):" + selected.id).setItems(actions, (dialog, which) -> {
            if (which == 0) editSelectedFlex(); else if (which == 1) addFlexChild(selected); else if (which == 2) moveSelectedKeysToFlex(selected);
            else if (which == 3) chooseFlexParent(selected); else if (which == 4) duplicateFlexSubtree(selected); else if (which == 5) deleteFlexSubtree(selected);
            else moveFlexSibling(selected, which == 6 ? -1 : 1);
        }).setNegativeButton("取消", null).show();
    }

    private void moveSelectedKeysToFlex(ThemeEditorModel.FlexContainer target) {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("请先选择一个或多个按键"); return; }
        if (!changeStarted()) return;
        java.util.HashSet<String> ids = new java.util.HashSet<>(); for (ThemeEditorModel.Key key : keys) ids.add(key.id);
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) for (int i = container.keyIds.size() - 1; i >= 0; i--) if (ids.contains(container.keyIds.get(i))) container.keyIds.remove(i);
        for (ThemeEditorModel.Key key : keys) { key.ownerId = target.id; target.keyIds.add(key.id); }
        model.selectedFlexContainerId = target.id; notifyModelChanged("已移动 " + keys.size() + " 个按键到 " + target.id);
    }

    private void chooseFlexParent(ThemeEditorModel.FlexContainer child) {
        if (child.parentId == null) { setStatus("根弹性容器(flex)不能更改父级"); return; }
        java.util.HashSet<String> subtree = flexSubtreeIds(child.id); java.util.ArrayList<ThemeEditorModel.FlexContainer> candidates = new java.util.ArrayList<>();
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) if (!subtree.contains(container.id)) candidates.add(container);
        String[] labels = new String[candidates.size()]; for (int i = 0; i < labels.length; i++) labels[i] = candidates.get(i).id;
        new android.app.AlertDialog.Builder(getContext()).setTitle("将容器移至此容器下").setItems(labels, (dialog, which) -> {
            ThemeEditorModel.FlexContainer parent = candidates.get(which); if (parent.id.equals(child.parentId)) { setStatus("容器已属于该父级"); return; }
            if (!changeStarted()) return; child.parentId = parent.id; model.selectedFlexContainerId = child.id; notifyModelChanged("已更改弹性容器(flex)父级");
        }).setNegativeButton("取消", null).show();
    }

    private void addFlexChild(ThemeEditorModel.FlexContainer parent) {
        if (!changeStarted()) return; ThemeEditorModel.FlexContainer child = new ThemeEditorModel.FlexContainer("flex_new_" + System.nanoTime(), parent.id); child.direction = "row";
        int insert = lastDescendantIndex(parent.id) + 1; model.flexContainers.add(Math.min(insert, model.flexContainers.size()), child); model.selectedFlexContainerId = child.id; notifyModelChanged("已添加弹性子容器(flex)");
    }

    private int lastDescendantIndex(String id) {
        int last = -1; java.util.HashSet<String> parents = new java.util.HashSet<>(); parents.add(id);
        for (int i = 0; i < model.flexContainers.size(); i++) { ThemeEditorModel.FlexContainer item = model.flexContainers.get(i); if (item.id.equals(id) || (item.parentId != null && parents.contains(item.parentId))) { parents.add(item.id); last = i; } }
        return last < 0 ? model.flexContainers.size() - 1 : last;
    }

    private void duplicateFlexSubtree(ThemeEditorModel.FlexContainer selected) {
        if (!changeStarted()) return; java.util.LinkedHashMap<String, String> ids = new java.util.LinkedHashMap<>(); java.util.ArrayList<ThemeEditorModel.FlexContainer> copies = new java.util.ArrayList<>(); java.util.ArrayList<ThemeEditorModel.Key> keyCopies = new java.util.ArrayList<>();
        java.util.HashSet<String> subtree = flexSubtreeIds(selected.id); int seed = (int) (System.nanoTime() & 0x7fffffff);
        for (ThemeEditorModel.FlexContainer original : model.flexContainers) if (subtree.contains(original.id)) ids.put(original.id, "flex_copy_" + seed + "_" + ids.size());
        for (ThemeEditorModel.FlexContainer original : model.flexContainers) if (subtree.contains(original.id)) {
            ThemeEditorModel.FlexContainer copy = original.copy(); copy.id = ids.get(original.id); copy.parentId = original.id.equals(selected.id) ? selected.parentId : ids.get(original.parentId); copy.keyIds.clear();
            for (String keyId : original.keyIds) { ThemeEditorModel.Key key = model.find(keyId); if (key == null) continue; ThemeEditorModel.Key keyCopy = key.copy(); keyCopy.id = copy.id + "_key_" + copy.keyIds.size(); keyCopy.ownerId = copy.id; copy.keyIds.add(keyCopy.id); keyCopies.add(keyCopy); }
            copies.add(copy);
        }
        int insert = lastDescendantIndex(selected.id) + 1; model.flexContainers.addAll(Math.min(insert, model.flexContainers.size()), copies); model.keys.addAll(keyCopies); model.selectedFlexContainerId = copies.get(0).id; notifyModelChanged("已复制弹性子树(flex)");
    }

    private java.util.HashSet<String> flexSubtreeIds(String rootId) {
        java.util.HashSet<String> result = new java.util.HashSet<>(); result.add(rootId); boolean changed;
        do { changed = false; for (ThemeEditorModel.FlexContainer item : model.flexContainers) if (item.parentId != null && result.contains(item.parentId) && result.add(item.id)) changed = true; } while (changed);
        return result;
    }

    private void deleteFlexSubtree(ThemeEditorModel.FlexContainer selected) {
        if (selected.parentId == null) { setStatus("不能删除根弹性容器(flex)"); return; }
        if (!changeStarted()) return; java.util.HashSet<String> ids = flexSubtreeIds(selected.id); java.util.HashSet<String> keyIds = new java.util.HashSet<>();
        for (ThemeEditorModel.FlexContainer item : model.flexContainers) if (ids.contains(item.id)) keyIds.addAll(item.keyIds);
        for (int i = model.keys.size() - 1; i >= 0; i--) if (keyIds.contains(model.keys.get(i).id)) model.keys.remove(i);
        for (int i = model.flexContainers.size() - 1; i >= 0; i--) if (ids.contains(model.flexContainers.get(i).id)) model.flexContainers.remove(i); model.selectedFlexContainerId = selected.parentId; canvas.setModel(model); notifyModelChanged("已删除弹性子树(flex)");
    }

    private void moveFlexSibling(ThemeEditorModel.FlexContainer selected, int delta) {
        java.util.ArrayList<ThemeEditorModel.FlexContainer> siblings = new java.util.ArrayList<>(); for (ThemeEditorModel.FlexContainer item : model.flexContainers) if (java.util.Objects.equals(item.parentId, selected.parentId)) siblings.add(item);
        int index = siblings.indexOf(selected), target = index + delta; if (target < 0 || target >= siblings.size()) { setStatus("弹性容器(flex)已位于边界"); return; }
        if (!changeStarted()) return; ThemeEditorModel.FlexContainer other = siblings.get(target); int a = model.flexContainers.indexOf(selected), b = model.flexContainers.indexOf(other); java.util.Collections.swap(model.flexContainers, a, b); notifyModelChanged("已调整弹性容器(flex)顺序");
    }

    private void selectAllKeys() {
        properties.commit(); model.selectedIds.clear();
        for (ThemeEditorModel.Key key : model.keys) model.selectedIds.add(key.id);
        canvas.setModel(model); refreshSelectionEditor(canvas.getSelectedKey()); setStatus("已选择全部 " + model.selectedIds.size() + " 个按键");
    }

    private void invertSelection() {
        properties.commit(); java.util.LinkedHashSet<String> inverted = new java.util.LinkedHashSet<>();
        for (ThemeEditorModel.Key key : model.keys) if (!model.selectedIds.contains(key.id)) inverted.add(key.id);
        model.selectedIds.clear(); model.selectedIds.addAll(inverted); canvas.setModel(model); refreshSelectionEditor(canvas.getSelectedKey());
        setStatus(model.selectedIds.isEmpty() ? "已清除选择" : "已选择 " + model.selectedIds.size() + " 个按键(反选后)");
    }

    private void refreshSelectionEditor(ThemeEditorModel.Key primary) {
        java.util.List<ThemeEditorModel.Key> selection = selectedKeys();
        ThemeEditorModel.Key next = primary != null && selection.contains(primary) ? primary : selection.isEmpty() ? null : selection.get(selection.size() - 1);
        canvas.setSelectedKey(next); properties.bindSelection(selection, next);
    }

    private static final class BatchField {
        final android.widget.CheckBox apply;
        final android.widget.EditText value;
        BatchField(android.widget.CheckBox apply, android.widget.EditText value) { this.apply = apply; this.value = value; }
    }

    private BatchField batchField(LinearLayout parent, String title, String state, String value) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.CheckBox apply = new android.widget.CheckBox(getContext()); apply.setText(title + " — " + state); row.addView(apply, new LayoutParams(0, -2, 1));
        android.widget.EditText input = new android.widget.EditText(getContext()); input.setSingleLine(true); input.setText(value); input.setEnabled(false); apply.setOnCheckedChangeListener((button, checked) -> input.setEnabled(checked)); row.addView(input, new LayoutParams(0, -2, 1)); parent.addView(row, new LayoutParams(-1, -2));
        return new BatchField(apply, input);
    }

    private interface BatchString { String get(ThemeEditorModel.Key key); }
    private interface BatchNumber { float get(ThemeEditorModel.Key key); }
    private String commonString(java.util.List<ThemeEditorModel.Key> keys, BatchString value) {
        if (keys.isEmpty()) return ""; String first = value.get(keys.get(0));
        for (int i = 1; i < keys.size(); i++) if (!java.util.Objects.equals(first, value.get(keys.get(i)))) return null;
        return first == null ? "" : first;
    }
    private Float commonNumber(java.util.List<ThemeEditorModel.Key> keys, BatchNumber value) {
        if (keys.isEmpty()) return null; float first = value.get(keys.get(0));
        for (int i = 1; i < keys.size(); i++) if (Float.compare(first, value.get(keys.get(i))) != 0) return null;
        return first;
    }
    private static String valueState(String value) { return value == null ? "混合" : value.isEmpty() ? "未设置" : "一致"; }
    private static String numberState(Float value) { return value == null ? "混合" : "一致"; }

    private void showBatchEditor() {
        properties.commit(); java.util.List<ThemeEditorModel.Key> keys = selectedKeys();
        if (keys.isEmpty()) { setStatus("请先选择一个或多个按键"); return; }
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(VERTICAL); fields.setPadding(24, 8, 24, 8);
        TextView impact = label("影响范围:" + keys.size() + " 个所选按键。仅勾选要替换的属性;勾选后的空文本会清除该属性。", 14); fields.addView(impact, new LayoutParams(-1, -2));
        String styleValue = commonString(keys, key -> key.keyStyle), clickValue = commonString(keys, key -> key.click), longValue = commonString(keys, key -> key.longClick), leftValue = commonString(keys, key -> key.swipeLeft), rightValue = commonString(keys, key -> key.swipeRight), upValue = commonString(keys, key -> key.swipeUp), downValue = commonString(keys, key -> key.swipeDown), popupValue = commonString(keys, key -> key.popup);
        Float widthValue = commonNumber(keys, key -> key.width), heightValue = commonNumber(keys, key -> key.height);
        BatchField style = batchField(fields, "样式", valueState(styleValue), styleValue == null ? "" : styleValue);
        BatchField width = batchField(fields, "Width", numberState(widthValue), widthValue == null ? "" : String.valueOf(widthValue));
        BatchField height = batchField(fields, "Height", numberState(heightValue), heightValue == null ? "" : String.valueOf(heightValue));
        BatchField click = batchField(fields, "Click", valueState(clickValue), clickValue == null ? "" : clickValue);
        BatchField longClick = batchField(fields, "Long click", valueState(longValue), longValue == null ? "" : longValue);
        BatchField swipeLeft = batchField(fields, "Swipe left", valueState(leftValue), leftValue == null ? "" : leftValue);
        BatchField swipeRight = batchField(fields, "Swipe right", valueState(rightValue), rightValue == null ? "" : rightValue);
        BatchField swipeUp = batchField(fields, "Swipe up", valueState(upValue), upValue == null ? "" : upValue);
        BatchField swipeDown = batchField(fields, "Swipe down", valueState(downValue), downValue == null ? "" : downValue);
        BatchField popup = batchField(fields, "Popup/resource literal", valueState(popupValue), popupValue == null ? "" : popupValue);
        BatchField background = batchField(fields, "Referenced style background", "style entity", "");
        BatchField textColor = batchField(fields, "Referenced style text color", "style entity", "");
        TextView colors = label("颜色会更新引用的样式实体,而不是键盘节点。背景接受 #AARRGGBB 或项目相对资源路径;勾选后的空值会清除样式覆盖。", 12); fields.addView(colors, new LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(getContext()); scroll.addView(fields, new ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(getContext()).setTitle("批量属性 — " + keys.size() + " 个按键").setView(scroll).setNegativeButton("取消", null).setPositiveButton("检查", (dialog, which) -> reviewBatch(keys, style, width, height, click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown, popup, background, textColor)).show();
    }

    private void reviewBatch(java.util.List<ThemeEditorModel.Key> keys, BatchField style, BatchField width, BatchField height, BatchField click, BatchField longClick, BatchField swipeLeft, BatchField swipeRight, BatchField swipeUp, BatchField swipeDown, BatchField popup, BatchField background, BatchField textColor) {
        int fields = 0; for (BatchField field : new BatchField[]{style, width, height, click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown, popup, background, textColor}) if (field.apply.isChecked()) fields++;
        if (fields == 0) { setStatus("未选择批量属性"); return; }
        Float nextWidth = width.apply.isChecked() ? positiveNumber(width.value) : null, nextHeight = height.apply.isChecked() ? positiveNumber(height.value) : null;
        if ((width.apply.isChecked() && nextWidth == null) || (height.apply.isChecked() && nextHeight == null)) { setStatus("批量宽度和高度必须为正数"); return; }
        int count = keys.size(), fieldCount = fields; boolean keyFields = style.apply.isChecked() || width.apply.isChecked() || height.apply.isChecked() || click.apply.isChecked() || longClick.apply.isChecked() || swipeLeft.apply.isChecked() || swipeRight.apply.isChecked() || swipeUp.apply.isChecked() || swipeDown.apply.isChecked() || popup.apply.isChecked();
        String transaction = keyFields && (background.apply.isChecked() || textColor.apply.isChecked()) ? "键盘字段占用一个撤销步骤;样式实体使用单独确认的项目文件事务。" : keyFields ? "一次撤销可恢复所有受影响按键。" : "引用的样式实体将在一个可安全回滚的项目文件事务中更改。";
        new android.app.AlertDialog.Builder(getContext()).setTitle("检查批量编辑").setMessage("将替换 " + fieldCount + " 个属性,涉及 " + count + " 个所选按键。" + transaction).setNegativeButton("取消", null).setPositiveButton("继续", (dialog, which) -> {
            if (keyFields) {
                if (!changeStarted()) return;
                for (ThemeEditorModel.Key key : keys) {
                    if (style.apply.isChecked()) key.keyStyle = style.value.getText().toString().trim();
                    if (width.apply.isChecked()) key.width = nextWidth;
                    if (height.apply.isChecked()) key.height = nextHeight;
                    if (click.apply.isChecked()) key.click = click.value.getText().toString().trim();
                    if (longClick.apply.isChecked()) key.longClick = longClick.value.getText().toString().trim();
                    if (swipeLeft.apply.isChecked()) key.swipeLeft = swipeLeft.value.getText().toString().trim();
                    if (swipeRight.apply.isChecked()) key.swipeRight = swipeRight.value.getText().toString().trim();
                    if (swipeUp.apply.isChecked()) key.swipeUp = swipeUp.value.getText().toString().trim();
                    if (swipeDown.apply.isChecked()) key.swipeDown = swipeDown.value.getText().toString().trim();
                    if (popup.apply.isChecked()) { key.popup = popup.value.getText().toString().trim(); key.popupArray = key.popup.contains(","); }
                    if (model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) clampAbsolute(key);
                }
                persistCurrentKeyMapPage(); refreshSelectionEditor(canvas.getSelectedKey()); notifyModelChanged("已批量编辑 " + count + " 个按键(一个撤销步骤)");
            }
            if ((background.apply.isChecked() || textColor.apply.isChecked()) && callbacks != null) callbacks.onBatchStyleEntities(copyKeys(keys), background.apply.isChecked() ? background.value.getText().toString().trim() : null, textColor.apply.isChecked() ? textColor.value.getText().toString().trim() : null);
        }).show();
    }

    private Float positiveNumber(android.widget.EditText input) {
        try { float value = Float.parseFloat(input.getText().toString()); return value > 0 && !Float.isNaN(value) && !Float.isInfinite(value) ? value : null; }
        catch (Exception ignored) { return null; }
    }

    private void showClipboardActions() {
        properties.commit(); String[] actions = {"复制所选按键", "复制所选行", "复制所选弹性子树(flex)", "复制当前符号页", "复制所选样式", "复制所选事件", "粘贴"};
        new android.app.AlertDialog.Builder(getContext()).setTitle("编辑器内部剪贴板").setItems(actions, (dialog, which) -> {
            if (which == 0) copySelectedKeys(); else if (which == 1) copySelectedRow(); else if (which == 2) copySelectedFlex(); else if (which == 3) copyCurrentPage(); else if (which == 4) copySelectedStyle(); else if (which == 5) copySelectedEvents(); else pasteClipboard();
        }).setNegativeButton("取消", null).show();
    }

    private void copySelectedKeys() {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("请先选择一个或多个按键"); return; }
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.KEYS, clipboardScope, keys, null, null, null, null)); setStatus("已复制 " + keys.size() + " 个按键到编辑器专用剪贴板");
    }
    private void copySelectedRow() {
        int index = selectedRowIndex(); if (model.layoutMode != ThemeEditorModel.LayoutMode.ROWS || index < 0) { setStatus("请在要复制的行中选择按键"); return; }
        ThemeEditorModel.Row row = model.rows.get(index); java.util.ArrayList<ThemeEditorModel.Key> keys = new java.util.ArrayList<>(); for (ThemeEditorModel.Key key : model.keys) if (row.id.equals(key.ownerId)) keys.add(key);
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.ROW, clipboardScope, keys, row, null, null, null)); setStatus("已复制行,共 " + keys.size() + " 个按键");
    }
    private void copySelectedFlex() {
        ThemeEditorModel.FlexContainer root = selectedFlex(); if (model.layoutMode != ThemeEditorModel.LayoutMode.FLEX_BOX || root == null) { setStatus("请先选择弹性容器(flex)"); return; }
        java.util.HashSet<String> ids = flexSubtreeIds(root.id); java.util.ArrayList<ThemeEditorModel.FlexContainer> containers = new java.util.ArrayList<>(); java.util.ArrayList<ThemeEditorModel.Key> keys = new java.util.ArrayList<>(); containers.add(root);
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) if (ids.contains(container.id) && container != root) containers.add(container);
        for (ThemeEditorModel.Key key : model.keys) if (ids.contains(key.ownerId)) keys.add(key);
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.FLEX_SUBTREE, clipboardScope, keys, null, containers, null, null)); setStatus("已复制弹性子树(flex),共 " + containers.size() + " 个容器和 " + keys.size() + " 个按键");
    }
    private void copyCurrentPage() {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.isEmpty()) { setStatus("当前不是按键映射(key_maps)键盘"); return; }
        persistCurrentKeyMapPage(); ThemeEditorModel.KeyMapPage page = model.keyMapPages.get(model.selectedKeyMapPage);
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.KEY_MAP_PAGE, clipboardScope, null, null, null, page, null)); setStatus("已复制符号页 " + page.name);
    }
    private void copySelectedStyle() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) { setStatus("请先选择按键"); return; }
        if (callbacks == null) { setStatus("样式实体提供程序不可用"); return; }
        callbacks.onCopyStyleEntity(key.copy());
    }
    private void copySelectedEvents() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) { setStatus("请先选择按键"); return; }
        if (key.hasNonLiteralEventSource) { setStatus("此按键包含内联、完整按键或原始 Lua(Raw Lua)事件源;请逐槽位使用‘事件...’以避免有损复制"); return; }
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.EVENTS, clipboardScope, java.util.Collections.singletonList(key), null, null, null, null)); setStatus("已复制所有字面事件和状态替换字段;未执行任何事件");
    }

    private void pasteClipboard() {
        ThemeEditorClipboard.Payload payload = ThemeEditorClipboard.get(); if (payload == null) { setStatus("内部剪贴板为空"); return; }
        boolean crossScope = !java.util.Objects.equals(payload.projectIdentity, clipboardScope);
        String dependencies = dependencySummary(payload);
        int targetCount = selectedKeys().size(); String warning = "Paste " + payload.type + " into the current " + model.layoutMode + " layout?" + (payload.type == ThemeEditorClipboard.Type.EVENTS ? "\nAffected target keys: " + targetCount : "");
        if (crossScope) warning += "\nCross-project copy: new node IDs will be generated. Source URIs and paths are not retained.";
        if (!dependencies.isEmpty()) warning += "\nDependencies are not auto-mapped: " + dependencies;
        new android.app.AlertDialog.Builder(getContext()).setTitle("确认粘贴目标").setMessage(warning).setNegativeButton("取消", null).setPositiveButton("粘贴", (dialog, which) -> applyClipboard(payload, crossScope)).show();
    }

    private String dependencySummary(ThemeEditorClipboard.Payload payload) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        if (payload.type == ThemeEditorClipboard.Type.KEY_STYLE && !payload.keyStyle.isEmpty()) values.add("style=" + payload.keyStyle);
        if (payload.type == ThemeEditorClipboard.Type.STYLE_ENTITY && payload.styleEntity != null) { values.add("style entity=" + payload.styleEntity.getId()); if (payload.styleEntity.getCloneParent() != null) values.add("inherits=" + payload.styleEntity.getCloneParent()); values.addAll(payload.styleEntity.getReferencedResources()); }
        for (ThemeEditorModel.Key key : payload.keys) {
            if (!key.keyStyle.isEmpty()) values.add("style=" + key.keyStyle);
            if (!key.click.isEmpty() || !key.longClick.isEmpty() || !key.popup.isEmpty()) values.add("event/popup literals");
        }
        return android.text.TextUtils.join(", ", values);
    }

    private void applyClipboard(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        if (!canEdit()) return;
        if (payload.type == ThemeEditorClipboard.Type.KEYS) pasteKeys(payload.keys, crossScope);
        else if (payload.type == ThemeEditorClipboard.Type.ROW) pasteRow(payload, crossScope);
        else if (payload.type == ThemeEditorClipboard.Type.FLEX_SUBTREE) pasteFlex(payload, crossScope);
        else if (payload.type == ThemeEditorClipboard.Type.KEY_MAP_PAGE) pastePage(payload, crossScope);
        else if (payload.type == ThemeEditorClipboard.Type.KEY_STYLE) pasteStyle(payload, crossScope);
        else if (payload.type == ThemeEditorClipboard.Type.STYLE_ENTITY) { if (callbacks == null) setStatus("样式实体提供程序不可用"); else callbacks.onPasteStyleEntity(copyKeys(selectedKeys())); }
        else pasteEvents(payload, crossScope);
    }

    private void pasteKeys(java.util.List<ThemeEditorModel.Key> source, boolean crossScope) {
        if (model.layoutMode == ThemeEditorModel.LayoutMode.NONE || source.isEmpty()) { setStatus("剪贴板按键无法粘贴到此文件"); return; }
        if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX && selectedFlex() == null) { setStatus("粘贴按键前请添加或选择弹性容器(flex)"); return; }
        if (!changeStarted()) return;
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS && model.rows.isEmpty()) model.rows.add(new ThemeEditorModel.Row("pasted_row_" + System.nanoTime(), 18));
        if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS && model.keyMapPages.isEmpty()) { model.keyMapPages.add(new ThemeEditorModel.KeyMapPage("pasted_page_" + System.nanoTime(), "Pasted")); model.selectedKeyMapPage = 0; }
        String owner = pasteOwner(); java.util.ArrayList<ThemeEditorModel.Key> pasted = new java.util.ArrayList<>();
        int index = 0; for (ThemeEditorModel.Key original : source) { ThemeEditorModel.Key key = detachedKey(original, "pasted_key_" + System.nanoTime() + "_" + index++); if (crossScope) key.keyStyle = ""; key.ownerId = owner; key.x = Math.min(100 - key.width, key.x + 2); key.y = Math.min(80 - key.height, key.y + 2); model.keys.add(key); pasted.add(key); if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) { ThemeEditorModel.FlexContainer container = selectedFlex(); if (container != null) container.keyIds.add(key.id); } }
        selectPasted(pasted); persistCurrentKeyMapPage(); notifyModelChanged("已粘贴 " + pasted.size() + " 个按键" + (crossScope ? ";请检查列出的依赖项" : ""));
    }
    private String pasteOwner() {
        ThemeEditorModel.Key selected = canvas.getSelectedKey();
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) return selected != null && !selected.ownerId.isEmpty() ? selected.ownerId : model.rows.isEmpty() ? "" : model.rows.get(model.rows.size() - 1).id;
        if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) { ThemeEditorModel.FlexContainer flex = selectedFlex(); return flex == null ? "" : flex.id; }
        if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS && !model.keyMapPages.isEmpty()) return model.keyMapPages.get(model.selectedKeyMapPage).id;
        return "";
    }
    private void pasteRow(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.ROWS || payload.row == null) { setStatus("行只能粘贴到行布局(rows)键盘"); return; }
        if (!changeStarted()) return; ThemeEditorModel.Row row = payload.row.copy(); row.id = "pasted_row_" + System.nanoTime(); row.sourcePath = ""; row.sourceHeight = Float.NaN; row.sourceWidth = Float.NaN; int insert = selectedRowIndex(); insert = insert < 0 ? model.rows.size() : insert + 1; model.rows.add(insert, row);
        java.util.ArrayList<ThemeEditorModel.Key> pasted = new java.util.ArrayList<>(); int i = 0; for (ThemeEditorModel.Key original : payload.keys) { ThemeEditorModel.Key key = detachedKey(original, row.id + "_key_" + i++); if (crossScope) key.keyStyle = ""; key.ownerId = row.id; model.keys.add(key); pasted.add(key); }
        layoutRows(); selectPasted(pasted); notifyModelChanged("已粘贴行,共 " + pasted.size() + " 个按键" + (crossScope ? ";请检查列出的依赖项" : ""));
    }
    private void pasteFlex(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.FLEX_BOX || payload.containers.isEmpty()) { setStatus("弹性子树(flex)只能粘贴到弹性盒(flex_box)键盘"); return; }
        ThemeEditorModel.FlexContainer target = selectedFlex(); if (target == null || !changeStarted()) return; java.util.LinkedHashMap<String, String> ids = new java.util.LinkedHashMap<>(); long seed = System.nanoTime();
        for (ThemeEditorModel.FlexContainer original : payload.containers) ids.put(original.id, "pasted_flex_" + seed + "_" + ids.size());
        String sourceRoot = payload.containers.get(0).id; java.util.ArrayList<ThemeEditorModel.FlexContainer> pastedContainers = new java.util.ArrayList<>();
        for (ThemeEditorModel.FlexContainer original : payload.containers) { ThemeEditorModel.FlexContainer copy = original.copy(); copy.id = ids.get(original.id); copy.parentId = original.id.equals(sourceRoot) ? target.id : ids.get(original.parentId); copy.sourcePath = ""; if (crossScope) copy.style = ""; copy.keyIds.clear(); pastedContainers.add(copy); }
        java.util.ArrayList<ThemeEditorModel.Key> pastedKeys = new java.util.ArrayList<>(); int index = 0; for (ThemeEditorModel.Key original : payload.keys) { ThemeEditorModel.Key key = detachedKey(original, "pasted_flex_key_" + seed + "_" + index++); if (crossScope) key.keyStyle = ""; key.ownerId = ids.get(original.ownerId); if (key.ownerId == null) key.ownerId = pastedContainers.get(0).id; for (ThemeEditorModel.FlexContainer container : pastedContainers) if (container.id.equals(key.ownerId)) container.keyIds.add(key.id); model.keys.add(key); pastedKeys.add(key); }
        int insert = lastDescendantIndex(target.id) + 1; model.flexContainers.addAll(Math.min(insert, model.flexContainers.size()), pastedContainers); model.selectedFlexContainerId = pastedContainers.get(0).id; selectPasted(pastedKeys); notifyModelChanged("已粘贴弹性子树(flex)" + (crossScope ? ";请检查列出的依赖项" : ""));
    }
    private void pastePage(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || payload.page == null) { setStatus("符号页只能粘贴到按键映射(key_maps)键盘"); return; }
        if (!changeStarted()) return; persistCurrentKeyMapPage(); ThemeEditorModel.KeyMapPage page = payload.page.copy(); page.id = "pasted_page_" + System.nanoTime(); page.name = page.name + " copy"; page.sourcePath = "";
        java.util.ArrayList<ThemeEditorModel.Key> remapped = new java.util.ArrayList<>(); int index = 0; for (ThemeEditorModel.Key original : page.keys) { ThemeEditorModel.Key key = detachedKey(original, page.id + "_key_" + index++); if (crossScope) key.keyStyle = ""; key.ownerId = page.id; remapped.add(key); } page.keys.clear(); page.keys.addAll(remapped);
        model.keyMapPages.add(model.selectedKeyMapPage + 1, page); model.selectedKeyMapPage++; model.keys.clear(); for (ThemeEditorModel.Key key : page.keys) model.keys.add(key.copy()); selectPasted(model.keys); notifyModelChanged("已粘贴符号页" + (crossScope ? ";请检查列出的依赖项" : ""));
    }
    private void pasteStyle(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("请先选择目标按键"); return; }
        if (crossScope && !payload.keyStyle.isEmpty()) { setStatus("未粘贴跨项目样式,因为样式 ID 不会自动映射"); return; }
        if (!changeStarted()) return; for (ThemeEditorModel.Key key : keys) key.keyStyle = payload.keyStyle; persistCurrentKeyMapPage(); refreshSelectionEditor(canvas.getSelectedKey()); notifyModelChanged("已将样式粘贴到 " + keys.size() + " 个按键(一个撤销步骤)");
    }
    private void pasteEvents(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty() || payload.keys.isEmpty()) { setStatus("请先选择目标按键"); return; }
        ThemeEditorModel.Key source = payload.keys.get(0); if (!changeStarted()) return;
        for (ThemeEditorModel.Key key : keys) { key.click = source.click; key.longClick = source.longClick; key.swipeLeft = source.swipeLeft; key.swipeRight = source.swipeRight; key.swipeUp = source.swipeUp; key.swipeDown = source.swipeDown; key.combo = source.combo; key.composing = source.composing; key.hasMenu = source.hasMenu; key.paging = source.paging; key.ascii = source.ascii; key.popup = source.popup; key.popupArray = source.popupArray; }
        persistCurrentKeyMapPage(); refreshSelectionEditor(canvas.getSelectedKey()); notifyModelChanged("已将字面事件粘贴到 " + keys.size() + " 个按键" + (crossScope ? ";预设/资源名称未映射" : ""));
    }
    private ThemeEditorModel.Key detachedKey(ThemeEditorModel.Key source, String id) {
        ThemeEditorModel.Key key = source.copy(); key.id = id; key.sourcePath = ""; key.sourceLabel = ""; key.sourceClick = ""; key.sourceLongClick = ""; key.sourceSwipeLeft = ""; key.sourceSwipeRight = ""; key.sourceSwipeUp = ""; key.sourceSwipeDown = ""; key.sourceCombo = ""; key.sourceComposing = ""; key.sourceHasMenu = ""; key.sourcePaging = ""; key.sourceAscii = ""; key.sourceKeyStyle = ""; key.sourcePopup = ""; key.sourceX = Float.NaN; key.sourceY = Float.NaN; key.sourceWidth = Float.NaN; key.sourceHeight = Float.NaN; key.hasNonLiteralEventSource = false; key.editorLocked = false; return key;
    }
    private void selectPasted(java.util.List<ThemeEditorModel.Key> keys) {
        model.selectedIds.clear(); ThemeEditorModel.Key primary = null; for (ThemeEditorModel.Key key : keys) { model.selectedIds.add(key.id); primary = key; } canvas.setModel(model); canvas.setSelectedKey(primary); refreshSelectionEditor(primary);
    }

    private android.widget.EditText dialogField(LinearLayout parent, String hint, String value) { android.widget.EditText field = new android.widget.EditText(getContext()); field.setHint(hint); field.setText(value); parent.addView(field, new LayoutParams(-1, -2)); return field; }
    private float parseFloat(android.widget.EditText field, float fallback) { try { return Float.parseFloat(field.getText().toString()); } catch (Exception ignored) { return fallback; } }

    private void manageAbsoluteKeys() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) { setStatus("当前不是绝对定位按键布局"); return; }
        String[] actions = {"吸附到 2 单位网格", "左对齐", "右对齐", "顶部对齐", "底部对齐", "水平平均分布", "垂直平均分布", "切换锁定"};
        new android.app.AlertDialog.Builder(getContext()).setTitle("绝对定位按键工具").setItems(actions, (dialog, which) -> {
            java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("请先选择一个或多个按键"); return; }
            if (which >= 1 && which <= 6 && keys.size() < 2) { setStatus("请至少选择两个按键"); return; }
            if ((which == 5 || which == 6) && unlockedCount(keys) < 3) { setStatus("平均分布至少需要三个未锁定按键"); return; }
            if (which == 7) { if (!canEdit()) return; for (ThemeEditorModel.Key key : keys) key.editorLocked = !key.editorLocked; canvas.setModel(model); setStatus("已切换仅编辑器锁定;主题源未更改"); return; }
            if (!changeStarted()) return;
            if (which == 0) for (ThemeEditorModel.Key key : keys) if (!key.editorLocked) { key.x = snap(key.x, 2); key.y = snap(key.y, 2); key.width = Math.max(2, snap(key.width, 2)); key.height = Math.max(2, snap(key.height, 2)); clampAbsolute(key); }
            else if (which == 1) alignAbsolute(keys, 0); else if (which == 2) alignAbsolute(keys, 1); else if (which == 3) alignAbsolute(keys, 2); else if (which == 4) alignAbsolute(keys, 3);
            else if (which == 5) distributeAbsolute(keys, true); else distributeAbsolute(keys, false);
            notifyModelChanged("已应用绝对定位按键工具");
        }).setNegativeButton("取消", null).show();
    }

    private static java.util.List<ThemeEditorModel.Key> copyKeys(java.util.List<ThemeEditorModel.Key> source) { java.util.ArrayList<ThemeEditorModel.Key> result = new java.util.ArrayList<>(); for (ThemeEditorModel.Key key : source) result.add(key.copy()); return result; }

    private java.util.List<ThemeEditorModel.Key> selectedKeys() {
        java.util.ArrayList<ThemeEditorModel.Key> result = new java.util.ArrayList<>(); ThemeEditorModel.Key current = canvas.getSelectedKey();
        if (!model.selectedIds.isEmpty()) for (ThemeEditorModel.Key key : model.keys) if (model.selectedIds.contains(key.id)) result.add(key);
        else if (current != null) result.add(current);
        return result;
    }

    private static float snap(float value, float grid) { return Math.round(value / grid) * grid; }
    private static int unlockedCount(java.util.List<ThemeEditorModel.Key> keys) { int count = 0; for (ThemeEditorModel.Key key : keys) if (!key.editorLocked) count++; return count; }
    private void alignAbsolute(java.util.List<ThemeEditorModel.Key> keys, int edge) {
        float target = edge == 0 || edge == 2 ? Float.MAX_VALUE : -Float.MAX_VALUE;
        for (ThemeEditorModel.Key key : keys) { float value = edge == 0 ? key.x : edge == 1 ? key.x + key.width : edge == 2 ? key.y : key.y + key.height; target = edge == 0 || edge == 2 ? Math.min(target, value) : Math.max(target, value); }
        for (ThemeEditorModel.Key key : keys) if (!key.editorLocked) { if (edge == 0) key.x = target; else if (edge == 1) key.x = target - key.width; else if (edge == 2) key.y = target; else key.y = target - key.height; clampAbsolute(key); }
    }

    private void distributeAbsolute(java.util.List<ThemeEditorModel.Key> keys, boolean horizontal) {
        java.util.ArrayList<ThemeEditorModel.Key> movable = new java.util.ArrayList<>(); for (ThemeEditorModel.Key key : keys) if (!key.editorLocked) movable.add(key); if (movable.size() < 3) return;
        java.util.Collections.sort(movable, (a, b) -> Float.compare(horizontal ? a.x : a.y, horizontal ? b.x : b.y));
        ThemeEditorModel.Key first = movable.get(0), last = movable.get(movable.size() - 1); float start = horizontal ? first.x : first.y, end = horizontal ? last.x : last.y, step = (end - start) / (movable.size() - 1);
        for (int i = 1; i < movable.size() - 1; i++) { if (horizontal) movable.get(i).x = start + step * i; else movable.get(i).y = start + step * i; clampAbsolute(movable.get(i)); }
    }
    private static void clampAbsolute(ThemeEditorModel.Key key) { key.x = Math.max(0, Math.min(100 - key.width, key.x)); key.y = Math.max(0, Math.min(80 - key.height, key.y)); }

    private void finishKeyMove(ThemeEditorModel.Key key) {
        if (key == null) return;
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) finishRowMove(key);
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) finishFlexMove(key);
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) finishKeyMapMove(key);
        notifyModelChanged(model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? "已移动绝对定位按键" : "已调整按键顺序");
    }

    private void finishRowMove(ThemeEditorModel.Key moved) {
        if (model.rows.isEmpty()) return;
        float center = moved.y + moved.height / 2f, y = 8f; ThemeEditorModel.Row target = model.rows.get(model.rows.size() - 1);
        for (ThemeEditorModel.Row row : model.rows) { if (center < y + row.height) { target = row; break; } y += row.height; }
        moved.ownerId = target.id;
        sortOwnerKeysByX(target.id); layoutRows();
    }

    private void finishFlexMove(ThemeEditorModel.Key moved) {
        ThemeEditorModel.FlexContainer owner = null; for (ThemeEditorModel.FlexContainer container : model.flexContainers) if (container.keyIds.contains(moved.id)) { owner = container; break; }
        if (owner == null) return; sortOwnerKeysByX(owner.id); owner.keyIds.clear(); for (ThemeEditorModel.Key key : model.keys) if (owner.id.equals(key.ownerId)) owner.keyIds.add(key.id);
    }

    private void finishKeyMapMove(ThemeEditorModel.Key moved) { if (!model.keyMapPages.isEmpty()) { moved.ownerId = model.keyMapPages.get(model.selectedKeyMapPage).id; sortOwnerKeysByX(moved.ownerId); persistCurrentKeyMapPage(); } }

    private void sortOwnerKeysByX(String ownerId) {
        java.util.ArrayList<ThemeEditorModel.Key> owned = new java.util.ArrayList<>(); for (ThemeEditorModel.Key key : model.keys) if (ownerId.equals(key.ownerId)) owned.add(key);
        java.util.Collections.sort(owned, (a, b) -> { int row = Float.compare(a.y, b.y); return row != 0 ? row : Float.compare(a.x, b.x); });
        java.util.ArrayList<ThemeEditorModel.Key> reordered = new java.util.ArrayList<>(); for (ThemeEditorModel.Key key : model.keys) if (!ownerId.equals(key.ownerId)) reordered.add(key); reordered.addAll(owned); model.keys.clear(); model.keys.addAll(reordered);
    }

    private void selectCurrentRow() {
        ThemeEditorModel.Key selected = canvas.getSelectedKey();
        if (selected == null) { setStatus("请先选择按键"); return; }
        model.selectedIds.clear();
        String row = selected.ownerId;
        for (ThemeEditorModel.Key key : model.keys) if (java.util.Objects.equals(key.ownerId, row)) model.selectedIds.add(key.id);
        canvas.setModel(model); refreshSelectionEditor(selected); setStatus("已选择当前行,共 " + model.selectedIds.size() + " 个按键");
    }

    private void addKey() {
        properties.commit();
        if (model.layoutMode == ThemeEditorModel.LayoutMode.NONE) { setStatus("此文件没有可编辑的键盘布局"); return; }
        if (!changeStarted()) return; int index = model.keys.size(); String id;
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) {
            if (model.rows.isEmpty()) model.rows.add(new ThemeEditorModel.Row("row_0", 18));
            int row = model.rows.size() - 1; id = "row_" + row + "_key_new_" + System.nanoTime();
        } else if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) id = "page_" + model.selectedKeyMapPage + "_key_new_" + System.nanoTime();
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) id = "flex_key_new_" + System.nanoTime();
        else id = "absolute_key_new_" + System.nanoTime();
        ThemeEditorModel.Key key = new ThemeEditorModel.Key(id, "new", 10 + (index % 8) * 11, 8 + (index / 8) * 18, 9.5f, 16);
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) key.ownerId = model.rows.get(model.rows.size() - 1).id;
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS && !model.keyMapPages.isEmpty()) key.ownerId = model.keyMapPages.get(model.selectedKeyMapPage).id;
        model.keys.add(key);
        if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) { ThemeEditorModel.FlexContainer container = selectedFlex(); if (container != null) { container.keyIds.add(key.id); key.ownerId = container.id; } }
        persistCurrentKeyMapPage(); selectOnly(key); notifyModelChanged("已添加按键");
    }

    private void duplicateSelected() {
        properties.commit();
        ThemeEditorModel.Key selected = canvas.getSelectedKey(); if (selected == null) { setStatus("请先选择按键"); return; }
        if (!changeStarted()) return; ThemeEditorModel.Key copy = selected.copy(); copy.id = selected.id + "_copy_" + System.nanoTime(); copy.x = Math.min(100 - copy.width, copy.x + 2); model.keys.add(copy);
        if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) for (ThemeEditorModel.FlexContainer container : model.flexContainers) { int index = container.keyIds.indexOf(selected.id); if (index >= 0) { container.keyIds.add(index + 1, copy.id); break; } }
        persistCurrentKeyMapPage(); selectOnly(copy); notifyModelChanged("已复制按键");
    }

    private void deleteSelected() {
        properties.commit();
        ThemeEditorModel.Key selected = canvas.getSelectedKey(); if (selected == null && model.selectedIds.isEmpty()) { setStatus("请先选择按键"); return; }
        if (!changeStarted()) return; java.util.HashSet<String> deleting = new java.util.HashSet<>(model.selectedIds); if (selected != null) deleting.add(selected.id);
        for (int i = model.keys.size() - 1; i >= 0; i--) if (deleting.contains(model.keys.get(i).id)) model.keys.remove(i);
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) for (int i = container.keyIds.size() - 1; i >= 0; i--) if (deleting.contains(container.keyIds.get(i))) container.keyIds.remove(i);
        model.selectedIds.clear(); persistCurrentKeyMapPage(); canvas.setSelectedKey(null); canvas.setModel(model); properties.bind(null); notifyModelChanged("已删除所选按键");
    }

    private void manageRows() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.ROWS) { setStatus("当前不是行布局(rows)键盘"); return; }
        int selected = selectedRowIndex(); String[] actions = {"添加行", "复制所选行", "删除所选行", "上移行", "下移行", "设置行高", "设置默认按键宽度", "平均分布按键"};
        new android.app.AlertDialog.Builder(getContext()).setTitle(selected < 0 ? "行布局(rows)" : "第 " + (selected + 1) + " 行").setItems(actions, (dialog, which) -> {
            if (which == 0) addRow(); else if (selected < 0) setStatus("请先在目标行中选择按键"); else if (which == 1) duplicateRow(selected); else if (which == 2) deleteRow(selected); else if (which == 3) moveRow(selected, -1); else if (which == 4) moveRow(selected, 1); else if (which == 5) editRowHeight(selected); else if (which == 6) editRowWidth(selected); else distributeRow(selected);
        }).setNegativeButton("取消", null).show();
    }

    private int selectedRowIndex() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) return -1;
        for (int i = 0; i < model.rows.size(); i++) if (model.rows.get(i).id.equals(key.ownerId)) return i;
        return -1;
    }

    private void addRow() { if (!changeStarted()) return; int row = model.rows.size(); model.rows.add(new ThemeEditorModel.Row("row_new_" + System.nanoTime(), 18)); reindexRows(); notifyModelChanged("已添加行"); }
    private void duplicateRow(int row) {
        if (!changeStarted()) return; ThemeEditorModel.Row copyRow = model.rows.get(row).copy(); copyRow.id = "row_copy_" + System.nanoTime(); model.rows.add(row + 1, copyRow);
        java.util.ArrayList<ThemeEditorModel.Key> copies = new java.util.ArrayList<>(); int copyIndex = 0; for (ThemeEditorModel.Key key : model.keys) if (model.rows.get(row).id.equals(key.ownerId)) { ThemeEditorModel.Key copy = key.copy(); copy.id = copyRow.id + "_key_" + copyIndex++; copy.ownerId = copyRow.id; copies.add(copy); }
        model.keys.addAll(copies); reindexRows(); notifyModelChanged("已复制行");
    }
    private void deleteRow(int row) {
        if (model.rows.size() <= 1) { setStatus("至少需要一行"); return; } if (!changeStarted()) return; String owner = model.rows.get(row).id; model.rows.remove(row);
        for (int i = model.keys.size() - 1; i >= 0; i--) if (owner.equals(model.keys.get(i).ownerId)) model.keys.remove(i); model.selectedIds.clear(); reindexRows(); canvas.setSelectedKey(null); properties.bind(null); notifyModelChanged("已删除行");
    }
    private void moveRow(int row, int delta) {
        int target = row + delta; if (target < 0 || target >= model.rows.size()) { setStatus("行已位于边界"); return; }
        if (!changeStarted()) return; java.util.Collections.swap(model.rows, row, target); layoutRows(); notifyModelChanged("已调整行顺序");
    }
    private void editRowHeight(int row) {
        LinearLayout fields = new LinearLayout(getContext()); android.widget.EditText height = dialogField(fields, "行高百分比", String.valueOf(model.rows.get(row).height));
        new android.app.AlertDialog.Builder(getContext()).setTitle("行高").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { if (!changeStarted()) return; model.rows.get(row).height = Math.max(1, parseFloat(height, model.rows.get(row).height)); layoutRows(); notifyModelChanged("已更新行高"); }).show();
    }
    private void editRowWidth(int row) {
        LinearLayout fields = new LinearLayout(getContext()); android.widget.EditText width = dialogField(fields, "默认按键宽度;-1 表示继承", String.valueOf(model.rows.get(row).width));
        new android.app.AlertDialog.Builder(getContext()).setTitle("行默认宽度").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { if (!changeStarted()) return; model.rows.get(row).width = parseFloat(width, model.rows.get(row).width); notifyModelChanged("已更新行默认宽度"); }).show();
    }
    private void distributeRow(int row) {
        java.util.ArrayList<ThemeEditorModel.Key> keys = new java.util.ArrayList<>(); String owner = model.rows.get(row).id; for (ThemeEditorModel.Key key : model.keys) if (owner.equals(key.ownerId)) keys.add(key);
        if (keys.isEmpty()) { setStatus("此行没有按键"); return; } if (!changeStarted()) return; float width = 100f / keys.size(); for (ThemeEditorModel.Key key : keys) key.width = width; model.rows.get(row).width = width; layoutRows(); notifyModelChanged("已平均分布行内按键");
    }

    private void reindexRows() { layoutRows(); }
    private void layoutRows() {
        float y = 8f;
        for (int row = 0; row < model.rows.size(); row++) {
            ThemeEditorModel.Row rowModel = model.rows.get(row); float x = 0;
            for (ThemeEditorModel.Key key : model.keys) if (rowModel.id.equals(key.ownerId)) { key.x = x; key.y = y; x += key.width; }
            y += rowModel.height;
        }
    }

    private void selectOnly(ThemeEditorModel.Key key) { model.selectedIds.clear(); if (key != null) model.selectedIds.add(key.id); canvas.setModel(model); canvas.setSelectedKey(key); properties.bind(key); }

    private void notifyModelChanged(String message) { dirty = true; canvas.setModel(model); refreshSelectionEditor(canvas.getSelectedKey()); if (callbacks != null) callbacks.onModelChanged(model.copy()); setStatus(message); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private android.graphics.drawable.GradientDrawable roundedBackground(String color, int radiusDp) { android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable(); drawable.setColor(Color.parseColor(color)); drawable.setCornerRadius(dp(radiusDp)); return drawable; }
    private TextView label(String text, float size) { TextView v = new TextView(getContext()); v.setText(text); v.setTextSize(size); v.setTextColor(Color.parseColor("#f4f6ff")); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private Button action(String text, String description) { Button b = new Button(getContext()); b.setText(text); b.setAllCaps(false); b.setContentDescription(description); b.setMinWidth(0); b.setMinHeight(0); b.setPadding(dp(8), 0, dp(8), 0); return b; }
    private void styleTopAction(Button button, boolean primary) { button.setTextSize(11); button.setTextColor(Color.WHITE); button.setBackground(roundedBackground(primary ? "#8b7cff" : "#1a2032", 9)); }
    private LayoutParams topActionParams() { LayoutParams params = new LayoutParams(dp(wideLayout ? 58 : 46), dp(38)); params.setMargins(dp(wideLayout ? 2 : 1), 0, dp(wideLayout ? 2 : 1), 0); return params; }
    private void styleCompactAction(Button button) { button.setTextSize(11); button.setTextColor(Color.parseColor("#d9dced")); button.setBackground(roundedBackground("#1a2032", 10)); }
    private LayoutParams compactActionParams() { LayoutParams params = new LayoutParams(dp(66), dp(46)); params.setMargins(dp(4), dp(6), dp(4), dp(6)); return params; }
    private LinearLayout compactTools(Context context, Button... buttons) { LinearLayout tools = new LinearLayout(context); tools.setGravity(Gravity.CENTER); tools.setPadding(dp(4), 0, dp(4), 0); tools.setBackgroundColor(Color.parseColor("#121726")); for (Button button : buttons) { styleCompactAction(button); tools.addView(button, compactActionParams()); } return tools; }
    private void themePropertyEditor(View view) {
        if (view instanceof android.widget.EditText) {
            android.widget.EditText input = (android.widget.EditText) view;
            input.setTextColor(Color.parseColor("#f4f6ff")); input.setHintTextColor(Color.parseColor("#727c94"));
            android.graphics.drawable.GradientDrawable field = roundedBackground("#0d111d", 9); field.setStroke(dp(1), Color.parseColor("#343a50")); input.setBackground(field); input.setPadding(dp(12), 0, dp(12), 0);
        } else if (view instanceof TextView) {
            TextView text = (TextView) view; text.setTextColor(Color.parseColor("#f4f6ff")); text.setHintTextColor(Color.parseColor("#929bb3"));
        } else if (view instanceof android.view.ViewGroup) view.setBackgroundColor(Color.TRANSPARENT);
        if (view instanceof android.view.ViewGroup) { android.view.ViewGroup group = (android.view.ViewGroup) view; for (int i = 0; i < group.getChildCount(); i++) themePropertyEditor(group.getChildAt(i)); }
    }
    private void showProperties(boolean visible) { propertyPanel.setVisibility(visible ? VISIBLE : GONE); if (visible) propertyPanel.bringToFront(); }
    private void showActionGroups(Button[] selection, Button[] structure, Button[] pages, Button[] preview, Button[] data) { String[] groups = {"选择与批量", "按键与布局", "按键映射页(key_maps)", "预览与事件", "剪贴板"}; Button[][] actions = {selection, structure, pages, preview, data}; new android.app.AlertDialog.Builder(getContext()).setTitle("编辑操作").setItems(groups, (dialog, which) -> showActionList(groups[which], actions[which])).setNegativeButton("关闭", null).show(); }
    private void showActionList(String title, Button[] actions) { String[] labels = new String[actions.length]; for (int i = 0; i < actions.length; i++) labels[i] = actions[i].getText().toString(); new android.app.AlertDialog.Builder(getContext()).setTitle(title).setItems(labels, (dialog, which) -> actions[which].performClick()).setNegativeButton("关闭", null).show(); }
    public void setCallbacks(ThemeEditorCallbacks callbacks) { this.callbacks = callbacks; }
    public void setClipboardScope(String value) { clipboardScope = value == null ? "" : java.util.UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(); }

    /**
     * Supplies the selected style's Lua text for conservative static panel preview parsing.
     * Callers may clear it with null when no style source is available. No Lua is executed here.
     */
    public void setPanelPreviewSource(String source) {
        panelPreviewSource = source;
        panelPreviewSourceAssigned = true;
        applyPanelPreviewSource(model);
        canvas.invalidate();
    }

    /** Parses a source snapshot without retaining it for subsequent model replacements. */
    public void refreshPanelPreview(String source) {
        String retained = panelPreviewSource;
        boolean assigned = panelPreviewSourceAssigned;
        panelPreviewSource = source;
        panelPreviewSourceAssigned = true;
        applyPanelPreviewSource(model);
        panelPreviewSource = retained;
        panelPreviewSourceAssigned = assigned;
        canvas.invalidate();
    }

    private void applyPanelPreviewSource(ThemeEditorModel target) {
        if (target == null || !panelPreviewSourceAssigned) return;
        resetPanelPreview(target);
        if (panelPreviewSource == null) return;
        applyToolbarKeysPreview(target);
        applyVisualComponentPreview(target);
        applyCompositionPreview(target);
        try {
            ThemePanelComponents.FilterBar filter = ThemePanelComponents.readCandidateFilter(panelPreviewSource);
            ThemePanelComponents.Toolbar candidate = ThemePanelComponents.readToolbar(panelPreviewSource, ThemePanelComponents.Panel.CANDIDATE_EXPANDED);
            ThemePanelComponents.TabBar symbolTab = ThemePanelComponents.readTabBar(panelPreviewSource, ThemePanelComponents.Panel.SYMBOL);
            ThemePanelComponents.Toolbar symbolTool = ThemePanelComponents.readToolbar(panelPreviewSource, ThemePanelComponents.Panel.SYMBOL);
            ThemePanelComponents.TabBar clipboardTab = ThemePanelComponents.readTabBar(panelPreviewSource, ThemePanelComponents.Panel.CLIPBOARD);
            ThemePanelComponents.Toolbar clipboardTool = ThemePanelComponents.readToolbar(panelPreviewSource, ThemePanelComponents.Panel.CLIPBOARD);
            copyFilterPreview(target.candidateExpandedFilterBar, filter);
            copyToolbarPreview(target.candidateExpandedToolBar, candidate, 40f);
            copyTabPreview(target.symbolTabBar, symbolTab, "top", 48f);
            copyToolbarPreview(target.symbolToolBar, symbolTool, 48f);
            copyTabPreview(target.clipboardTabBar, clipboardTab, "top", 48f);
            copyToolbarPreview(target.clipboardToolBar, clipboardTool, 48f);
            String structuralWarning = panelPreviewResolved(target) ? ""
                    : "Inherited panel fields were not evaluated; showing literal overrides and static defaults";
            if (!structuralWarning.isEmpty()) target.panelPreviewWarning = target.panelPreviewWarning == null || target.panelPreviewWarning.isEmpty()
                    ? structuralWarning : target.panelPreviewWarning + "; " + structuralWarning;
        } catch (RuntimeException error) {
            target.panelPreviewWarning = "Dynamic or ambiguous panel source was not previewed";
        } catch (LinkageError error) {
            target.panelPreviewWarning = "Static panel reader is unavailable; showing preview defaults";
        }
    }

    private static void resetPanelPreview(ThemeEditorModel target) {
        target.toolbarKeys.clear();
        target.toolbarKeysSourceResolved = false;
        target.toolbarPreviewWarning = panelPreviewDefaultMessage();
        resetVisualComponentPreview(target);
        resetCompositionPreview(target);
        target.candidateExpandedFilterBar.copyFrom(null);
        target.candidateExpandedToolBar.copyFrom(null, "right", 40f, "hide", "page_up", "page_down", "char_filter");
        target.symbolTabBar.copyFrom(null, "top", 48f);
        target.symbolToolBar.copyFrom(null, "right", 48f, "hide", "page_up", "page_down", "BackSpace");
        target.clipboardTabBar.copyFrom(null, "top", 48f);
        target.clipboardToolBar.copyFrom(null, "right", 48f, "hide", "page_up", "page_down", "undo");
        target.panelPreviewWarning = panelPreviewDefaultMessage();
    }

    private static void resetVisualComponentPreview(ThemeEditorModel target) {
        int globalKeyBackground = target.keys.isEmpty() ? 0xffffffff : target.keys.get(0).fillColor;
        int globalKeyText = target.keys.isEmpty() ? 0xff000000 : target.keys.get(0).textColor;
        target.candidateBackgroundColor = 0xffe8edf1; target.candidateTextColor = 0xff263238;
        target.pressedCandidateBackgroundColor = target.candidateBackgroundColor; target.pressedCandidateTextColor = target.candidateTextColor;
        target.candidateTextSize = 22f; target.candidateCommentTextColor = 0xff444444;
        target.candidatePressedCommentTextColor = target.candidateCommentTextColor; target.candidateCommentTextSize = 12f;
        target.candidateKeyBackgroundColor = globalKeyBackground; target.candidateKeyTextColor = globalKeyText;
        target.candidateKeyPressedBackgroundColor = globalKeyBackground; target.candidateKeyPressedTextColor = globalKeyText;
        target.expandedCandidateBackgroundColor = target.candidateBackgroundColor;
        target.expandedCandidateTextColor = target.candidateTextColor; target.expandedCandidatePressedTextColor = target.candidateTextColor;
        target.expandedCandidateCommentTextColor = target.candidateCommentTextColor;
        target.expandedCandidatePressedCommentTextColor = target.candidateCommentTextColor;
        target.expandedCandidateTextSize = target.candidateTextSize; target.expandedCandidateCommentTextSize = target.candidateCommentTextSize;
        target.expandedCandidateKeyBackgroundColor = globalKeyBackground; target.expandedCandidateKeyTextColor = globalKeyText;
        target.expandedCandidateKeyPressedBackgroundColor = globalKeyBackground; target.expandedCandidateKeyPressedTextColor = globalKeyText;
        target.toolbarBackgroundColor = 0xffd8e5ee; target.toolbarTextColor = 0xff263238; target.toolbarHeight = 40f / 5.3f;
        target.toolbarKeyBackgroundColor = globalKeyBackground; target.toolbarKeyTextColor = globalKeyText;
        target.toolbarKeyPressedBackgroundColor = globalKeyBackground; target.toolbarKeyPressedTextColor = globalKeyText;
        target.toolbarHideBackgroundColor = globalKeyBackground; target.toolbarHideTextColor = globalKeyText;
        target.toolbarHidePressedBackgroundColor = globalKeyBackground; target.toolbarHidePressedTextColor = globalKeyText;
        target.symbolBackgroundColor = 0x00000000; target.symbolKeyBackgroundColor = globalKeyBackground;
        target.symbolKeyTextColor = globalKeyText; target.symbolKeyPressedBackgroundColor = globalKeyBackground;
        target.symbolKeyPressedTextColor = globalKeyText; target.symbolTextBackgroundColor = globalKeyBackground;
        target.symbolTextColor = globalKeyText; target.symbolTextPressedBackgroundColor = globalKeyBackground;
        target.symbolTextPressedColor = globalKeyText; target.symbolIndicatorColor = globalKeyText;
        target.clipboardBackgroundColor = 0x00000000;
        target.clipboardKeyBackgroundColor = globalKeyBackground; target.clipboardKeyTextColor = globalKeyText;
        target.clipboardKeyPressedBackgroundColor = target.pressedKeyBackgroundColor; target.clipboardKeyPressedTextColor = target.pressedKeyTextColor;
        target.clipboardItemBackgroundColor = 0xffffffff; target.clipboardItemTextColor = 0xff000000;
        target.clipboardItemPressedBackgroundColor = target.clipboardItemBackgroundColor; target.clipboardItemPressedTextColor = target.clipboardItemTextColor;
        target.clipboardIndicatorColor = globalKeyText;
    }

    private int keyStyleColor(String path, String leaf, String fallbackPath, int fallback,
                              java.util.List<String> warnings) {
        Boolean local = staticComponentTable(path, warnings);
        if (Boolean.TRUE.equals(local)) return componentColor(path + "." + leaf, fallback, warnings);
        return fallbackPath == null ? fallback : componentColor(fallbackPath + "." + leaf, fallback, warnings);
    }

    private int keyStylePressedColor(String path, String leaf, String fallbackPath, int normal,
                                     int fallbackPressed, java.util.List<String> warnings) {
        Boolean local = staticComponentTable(path, warnings);
        String owner = Boolean.TRUE.equals(local) ? path : fallbackPath;
        if (owner == null) return normal;
        Boolean pressed = staticComponentTable(owner + ".pressed", warnings);
        return Boolean.TRUE.equals(pressed) ? componentColor(owner + ".pressed." + leaf, normal, warnings)
                : Boolean.TRUE.equals(local) ? normal : fallbackPressed;
    }

    private void applyVisualComponentPreview(ThemeEditorModel target) {
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
        try {
            int globalKeyBackground = target.keys.isEmpty() ? 0xffffffff : target.keys.get(0).fillColor;
            int globalKeyText = target.keys.isEmpty() ? 0xff000000 : target.keys.get(0).textColor;
            target.candidateBackgroundColor = componentColor("candidate.background", target.candidateBackgroundColor, warnings);
            target.candidateTextColor = componentColor("candidate.text_color", target.candidateTextColor, warnings);
            target.candidateTextSize = componentInt("candidate.text_size", 22, warnings);
            target.candidateCommentTextColor = componentColor("candidate.comment.text_color", 0xff444444, warnings);
            target.candidateCommentTextSize = componentInt("candidate.comment.text_size", 12, warnings);
            target.pressedCandidateBackgroundColor = componentColor("candidate.pressed.background", target.candidateBackgroundColor, warnings);
            target.pressedCandidateTextColor = componentColor("candidate.pressed.text_color", target.candidateTextColor, warnings);
            target.candidatePressedCommentTextColor = componentColor("candidate.comment.pressed.text_color", target.candidateCommentTextColor, warnings);
            target.candidateKeyBackgroundColor = keyStyleColor("candidate.key", "background", "key", globalKeyBackground, warnings);
            target.candidateKeyTextColor = keyStyleColor("candidate.key", "text_color", "key", globalKeyText, warnings);
            target.candidateKeyPressedBackgroundColor = keyStylePressedColor("candidate.key", "background", "key", target.candidateKeyBackgroundColor, target.pressedKeyBackgroundColor, warnings);
            target.candidateKeyPressedTextColor = keyStylePressedColor("candidate.key", "text_color", "key", target.candidateKeyTextColor, target.pressedKeyTextColor, warnings);
            target.candidateHeight = componentInt("candidate.height", 48, warnings) / 5.3f;

            target.expandedCandidateBackgroundColor = componentColor("candidate.expanded.background", target.candidateBackgroundColor, warnings);
            target.expandedCandidateTextColor = target.candidateTextColor;
            target.expandedCandidateTextSize = target.candidateTextSize;
            target.expandedCandidateCommentTextColor = target.candidateCommentTextColor;
            target.expandedCandidateCommentTextSize = target.candidateCommentTextSize;
            target.expandedCandidatePressedBackgroundColor = target.expandedCandidateBackgroundColor;
            target.expandedCandidatePressedTextColor = target.pressedCandidateTextColor;
            target.expandedCandidatePressedCommentTextColor = target.candidatePressedCommentTextColor;
            target.expandedCandidateKeyBackgroundColor = keyStyleColor("candidate.expanded.key", "background", "key", globalKeyBackground, warnings);
            target.expandedCandidateKeyTextColor = keyStyleColor("candidate.expanded.key", "text_color", "key", globalKeyText, warnings);
            target.expandedCandidateKeyPressedBackgroundColor = keyStylePressedColor("candidate.expanded.key", "background", "key", target.expandedCandidateKeyBackgroundColor, target.pressedKeyBackgroundColor, warnings);
            target.expandedCandidateKeyPressedTextColor = keyStylePressedColor("candidate.expanded.key", "text_color", "key", target.expandedCandidateKeyTextColor, target.pressedKeyTextColor, warnings);

            target.toolbarBackgroundColor = componentColor("toolbar.background", target.toolbarBackgroundColor, warnings);
            target.toolbarHeight = target.candidateHeight;
            target.toolbarKeyBackgroundColor = keyStyleColor("toolbar.key", "background", "key", globalKeyBackground, warnings);
            target.toolbarKeyTextColor = keyStyleColor("toolbar.key", "text_color", "key", globalKeyText, warnings);
            target.toolbarTextColor = target.toolbarKeyTextColor;
            target.toolbarKeyPressedBackgroundColor = keyStylePressedColor("toolbar.key", "background", "key", target.toolbarKeyBackgroundColor, target.pressedKeyBackgroundColor, warnings);
            target.toolbarKeyPressedTextColor = keyStylePressedColor("toolbar.key", "text_color", "key", target.toolbarKeyTextColor, target.pressedKeyTextColor, warnings);
            target.toolbarHideBackgroundColor = keyStyleColor("toolbar.hide", "background", "toolbar.key", target.toolbarKeyBackgroundColor, warnings);
            target.toolbarHideTextColor = keyStyleColor("toolbar.hide", "text_color", "toolbar.key", target.toolbarKeyTextColor, warnings);
            target.toolbarHidePressedBackgroundColor = keyStylePressedColor("toolbar.hide", "background", "toolbar.key", target.toolbarHideBackgroundColor, target.toolbarKeyPressedBackgroundColor, warnings);
            target.toolbarHidePressedTextColor = keyStylePressedColor("toolbar.hide", "text_color", "toolbar.key", target.toolbarHideTextColor, target.toolbarKeyPressedTextColor, warnings);

            target.symbolBackgroundColor = componentColor("symbol.background", target.symbolBackgroundColor, warnings);
            target.symbolKeyBackgroundColor = keyStyleColor("symbol.key", "background", "key", globalKeyBackground, warnings);
            target.symbolKeyTextColor = keyStyleColor("symbol.key", "text_color", "key", globalKeyText, warnings);
            target.symbolKeyPressedBackgroundColor = keyStylePressedColor("symbol.key", "background", "key", target.symbolKeyBackgroundColor, target.pressedKeyBackgroundColor, warnings);
            target.symbolKeyPressedTextColor = keyStylePressedColor("symbol.key", "text_color", "key", target.symbolKeyTextColor, target.pressedKeyTextColor, warnings);
            target.symbolTextBackgroundColor = keyStyleColor("symbol.text", "background", "key", globalKeyBackground, warnings);
            target.symbolTextColor = keyStyleColor("symbol.text", "text_color", "key", globalKeyText, warnings);
            target.symbolTextPressedBackgroundColor = keyStylePressedColor("symbol.text", "background", "key", target.symbolTextBackgroundColor, target.pressedKeyBackgroundColor, warnings);
            target.symbolTextPressedColor = keyStylePressedColor("symbol.text", "text_color", "key", target.symbolTextColor, target.pressedKeyTextColor, warnings);
            int symbolFallbackIndicator = componentColor("symbol.indicator_color", target.symbolKeyPressedTextColor, warnings);
            target.symbolIndicatorColor = componentColor("symbol.tab_bar.indicator_color", symbolFallbackIndicator, warnings);

            target.clipboardBackgroundColor = componentColor("clipboard.background", target.clipboardBackgroundColor, warnings);
            target.clipboardKeyBackgroundColor = keyStyleColor("clipboard.key", "background", "key", globalKeyBackground, warnings);
            target.clipboardKeyTextColor = keyStyleColor("clipboard.key", "text_color", "key", globalKeyText, warnings);
            target.clipboardKeyPressedBackgroundColor = keyStylePressedColor("clipboard.key", "background", "key", target.clipboardKeyBackgroundColor, target.pressedKeyBackgroundColor, warnings);
            target.clipboardKeyPressedTextColor = keyStylePressedColor("clipboard.key", "text_color", "key", target.clipboardKeyTextColor, target.pressedKeyTextColor, warnings);
            target.clipboardItemBackgroundColor = keyStyleColor("clipboard.item", "background", null, 0xffffffff, warnings);
            target.clipboardItemTextColor = keyStyleColor("clipboard.item", "text_color", null, 0xff000000, warnings);
            target.clipboardItemPressedBackgroundColor = keyStylePressedColor("clipboard.item", "background", null, target.clipboardItemBackgroundColor, target.clipboardItemBackgroundColor, warnings);
            target.clipboardItemPressedTextColor = keyStylePressedColor("clipboard.item", "text_color", null, target.clipboardItemTextColor, target.clipboardItemTextColor, warnings);
            int clipboardFallbackIndicator = componentColor("clipboard.indicator_color", target.clipboardKeyPressedTextColor, warnings);
            target.clipboardIndicatorColor = componentColor("clipboard.tab_bar.indicator_color", clipboardFallbackIndicator, warnings);
            if (!warnings.isEmpty()) {
                String componentWarning = android.text.TextUtils.join("; ", warnings);
                target.panelPreviewWarning = target.panelPreviewWarning == null || target.panelPreviewWarning.isEmpty()
                        ? componentWarning : target.panelPreviewWarning + "; " + componentWarning;
            }
        } catch (RuntimeException error) {
            target.panelPreviewWarning = "Dynamic or ambiguous component style source was not previewed";
        } catch (LinkageError error) {
            target.panelPreviewWarning = "Static component style reader is unavailable";
        }
    }

    private static final java.util.Set<String> COMPOSITION_POSITIONS = new java.util.LinkedHashSet<>(java.util.Arrays.asList(
            "left", "right", "left_up", "right_up", "drag", "fixed",
            "bottom_left", "bottom_right", "top_left", "top_right"));
    private static final java.util.Set<String> COMPOSITION_MOVABLE = new java.util.LinkedHashSet<>(java.util.Arrays.asList("false", "true", "once"));

    private static void resetCompositionPreview(ThemeEditorModel target) {
        target.preeditInlineSource = "none"; target.preeditInlineMode = "none";
        target.compositionPositionSource = "fixed"; target.compositionPosition = "fixed";
        target.compositionMovableSource = "false"; target.compositionMovable = "false";
        target.compositionWindowEnabled = false;
        target.compositionMinLength = 0; target.compositionMaxLength = 5; target.compositionStickyLines = 0;
        target.compositionMaxEntries = 5; target.compositionCloudMaxEntries = 0;
        target.compositionAllPhrases = false; target.compositionUseCursor = true;
        target.compositionMinWidth = 10f; target.compositionMinHeight = 10f;
        target.compositionMaxWidth = 10000f; target.compositionMaxHeight = 1000f;
        target.compositionPaddingLeft = 0f; target.compositionPaddingTop = 0f;
        target.compositionPaddingRight = 0f; target.compositionPaddingBottom = 0f;
        target.compositionLineSpacing = 1f; target.compositionLineSpacingMultiplier = 1f;
        target.preeditBackgroundColor = 0xff888888; target.preeditTextColor = 0xffaaaaaa; target.preeditTextSize = 18f;
        target.compositionBackgroundColor = 0x00000000; target.compositionTextColor = 0xff000000;
        target.compositionTextSize = 18f;
        int globalKeyBackground = target.keys.isEmpty() ? 0xffffffff : target.keys.get(0).fillColor;
        int globalKeyText = target.keys.isEmpty() ? 0xff000000 : target.keys.get(0).textColor;
        target.compositionPressedBackgroundColor = target.compositionBackgroundColor;
        target.compositionPressedTextColor = target.compositionTextColor;
        target.compositionKeyBackgroundColor = globalKeyBackground; target.compositionKeyTextColor = globalKeyText;
        target.compositionKeyTextSize = target.keyTextSize * 4f;
        target.compositionKeyPressedBackgroundColor = target.pressedKeyBackgroundColor;
        target.compositionKeyPressedTextColor = target.pressedKeyTextColor;
        target.compositionKeyHintTextColor = globalKeyText; target.compositionKeyHintTextSize = 12f;
        target.compositionKeyPressedHintTextColor = target.pressedKeyTextColor; target.compositionKeyPressedHintTextSize = 12f;
        target.compositionPreviewSourceResolved = false;
        target.compositionPreviewWarning = panelPreviewDefaultMessage();
    }

    private void applyCompositionPreview(ThemeEditorModel target) {
        try {
            java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
            Boolean compositionTable = staticComponentTable("composition", warnings);
            target.compositionWindowEnabled = Boolean.TRUE.equals(compositionTable);
            ThemeComponentStyles.Value inline = componentValue("preedit.inline", warnings);
            if (inline.getLiteral() instanceof com.osfans.trime.editor.core.ThemeValue.LuaBoolean) {
                boolean value = Boolean.TRUE.equals(inline.getBooleanValue());
                target.preeditInlineSource = Boolean.toString(value);
                target.preeditInlineMode = "none";
            } else if (inline.getStringValue() != null) {
                target.preeditInlineSource = inline.getStringValue();
                String value = inline.getStringValue();
                target.preeditInlineMode = "preview".equals(value) || "true".equals(value) ? "preview"
                        : "preedit".equals(value) || "composition".equals(value) ? "composition"
                        : "input".equals(value) ? "input" : "none";
            }
            ThemeComponentStyles.Value position = componentValue("composition.position", warnings);
            if (position.getStringValue() != null) target.compositionPositionSource = position.getStringValue();
            String normalizedPosition = target.compositionPositionSource.toLowerCase(java.util.Locale.ROOT);
            target.compositionPosition = COMPOSITION_POSITIONS.contains(normalizedPosition) ? normalizedPosition : "fixed";
            ThemeComponentStyles.Value movable = componentValue("composition.movable", warnings);
            if (movable.getStringValue() != null) target.compositionMovableSource = movable.getStringValue();
            target.compositionMovable = "false".equals(target.compositionMovableSource) ? "false"
                    : "once".equals(target.compositionMovableSource) ? "once" : "true";

            ThemeComponentStyles.Value compositionShow = componentValue("composition.show", warnings);
            if (!compositionShow.getDynamic() && compositionShow.getBooleanValue() != null) {
                target.showComposition = compositionShow.getBooleanValue();
            } else if (!compositionShow.getDynamic() && compositionShow.getLiteral() == null) {
                target.showComposition = componentBoolean("preedit.show", true, warnings);
            } else {
                target.showComposition = true;
            }
            target.compositionMinLength = componentInt("composition.min_length", 0, warnings);
            target.compositionMaxLength = componentInt("composition.max_length", 5, warnings);
            target.compositionStickyLines = componentInt("composition.sticky_lines", 0, warnings);
            target.compositionMaxEntries = componentInt("composition.max_entries", 5, warnings);
            target.compositionCloudMaxEntries = componentInt("composition.cloud_max_entries", 0, warnings);
            target.compositionAllPhrases = componentBoolean("composition.all_phrases", false, warnings);
            target.compositionUseCursor = componentBoolean("composition.use_cursor", true, warnings);
            target.compositionMinWidth = componentInt("composition.min_width", 10, warnings);
            target.compositionMinHeight = componentInt("composition.min_height", 10, warnings);
            target.compositionMaxWidth = componentInt("composition.max_width", 10000, warnings);
            target.compositionMaxHeight = componentInt("composition.max_height", 1000, warnings);
            target.compositionPaddingLeft = componentInt("composition.padding.left", 0, warnings);
            target.compositionPaddingTop = componentInt("composition.padding.top", 0, warnings);
            target.compositionPaddingRight = componentInt("composition.padding.right", 0, warnings);
            target.compositionPaddingBottom = componentInt("composition.padding.bottom", 0, warnings);
            target.compositionLineSpacing = componentFloat("composition.line_spacing", 1f, warnings);
            float multiplier = componentFloat("composition.line_spacing_multiplier", 1f, warnings);
            target.compositionLineSpacingMultiplier = multiplier == 0f ? 1f : multiplier;
            target.preeditBackgroundColor = componentColor("preedit.background", target.preeditBackgroundColor, warnings);
            target.preeditTextColor = componentColor("preedit.text_color", target.preeditTextColor, warnings);
            target.compositionBackgroundColor = componentColor("composition.background", target.compositionBackgroundColor, warnings);
            target.compositionTextColor = componentColor("composition.text_color", target.compositionTextColor, warnings);
            target.compositionPressedBackgroundColor = componentColor(
                    "composition.pressed.background", target.compositionBackgroundColor, warnings);
            target.compositionPressedTextColor = componentColor(
                    "composition.pressed.text_color", target.compositionTextColor, warnings);
            int globalKeyBackground = componentColor("key.background", target.compositionKeyBackgroundColor, warnings);
            int globalKeyText = componentColor("key.text_color", target.compositionKeyTextColor, warnings);
            int globalKeyTextSize = componentInt(
                    "key.text_size", Math.max(1, Math.round(target.compositionKeyTextSize)), warnings);
            Boolean compositionKeyTable = staticComponentTable("composition.key", warnings);
            target.compositionKeyBackgroundColor = componentColor(
                    "composition.key.background", globalKeyBackground, warnings);
            target.compositionKeyTextColor = componentColor(
                    "composition.key.text_color", globalKeyText, warnings);
            target.compositionKeyTextSize = componentInt("composition.key.text_size", globalKeyTextSize, warnings);

            Boolean localHintTable = staticComponentTable("composition.key.hint", warnings);
            Boolean globalHintTable = staticComponentTable("key.hint", warnings);
            String normalHintPath;
            int normalHintColorFallback;
            int normalHintSizeFallback;
            if (Boolean.TRUE.equals(compositionKeyTable)) {
                normalHintPath = Boolean.TRUE.equals(localHintTable) ? "composition.key.hint" : null;
                normalHintColorFallback = target.compositionKeyTextColor;
                normalHintSizeFallback = Math.max(1, Math.round(target.compositionKeyTextSize));
            } else {
                normalHintPath = Boolean.TRUE.equals(globalHintTable) ? "key.hint" : null;
                normalHintColorFallback = globalKeyText;
                normalHintSizeFallback = globalKeyTextSize;
            }
            target.compositionKeyHintTextColor = normalHintPath == null ? normalHintColorFallback
                    : componentColor(normalHintPath + ".text_color", normalHintColorFallback, warnings);
            target.compositionKeyHintTextSize = normalHintPath == null ? normalHintSizeFallback
                    : componentInt(normalHintPath + ".text_size", normalHintSizeFallback, warnings);

            Boolean localPressedTable = staticComponentTable("composition.key.pressed", warnings);
            Boolean globalPressedTable = staticComponentTable("key.pressed", warnings);
            String pressedPath;
            int pressedBackgroundFallback;
            int pressedTextFallback;
            if (Boolean.TRUE.equals(compositionKeyTable)) {
                pressedPath = Boolean.TRUE.equals(localPressedTable) ? "composition.key.pressed" : null;
                pressedBackgroundFallback = target.compositionKeyBackgroundColor;
                pressedTextFallback = target.compositionKeyTextColor;
            } else {
                pressedPath = Boolean.TRUE.equals(globalPressedTable) ? "key.pressed" : null;
                pressedBackgroundFallback = globalKeyBackground;
                pressedTextFallback = globalKeyText;
            }
            target.compositionKeyPressedBackgroundColor = pressedPath == null ? pressedBackgroundFallback
                    : componentColor(pressedPath + ".background", pressedBackgroundFallback, warnings);
            target.compositionKeyPressedTextColor = pressedPath == null ? pressedTextFallback
                    : componentColor(pressedPath + ".text_color", pressedTextFallback, warnings);

            String pressedHintPath;
            int pressedHintColorFallback;
            int pressedHintSizeFallback;
            if (pressedPath == null) {
                pressedHintPath = normalHintPath;
                pressedHintColorFallback = normalHintColorFallback;
                pressedHintSizeFallback = normalHintSizeFallback;
            } else {
                Boolean ownPressedHint = staticComponentTable(pressedPath + ".hint", warnings);
                pressedHintPath = Boolean.TRUE.equals(ownPressedHint) ? pressedPath + ".hint" : null;
                pressedHintColorFallback = target.compositionKeyPressedTextColor;
                pressedHintSizeFallback = pressedPath.startsWith("composition.")
                        ? Math.max(1, Math.round(target.compositionKeyTextSize)) : globalKeyTextSize;
            }
            target.compositionKeyPressedHintTextColor = pressedHintPath == null ? pressedHintColorFallback
                    : componentColor(pressedHintPath + ".text_color", pressedHintColorFallback, warnings);
            target.compositionKeyPressedHintTextSize = pressedHintPath == null ? pressedHintSizeFallback
                    : componentInt(pressedHintPath + ".text_size", pressedHintSizeFallback, warnings);
            target.preeditTextSize = componentInt("preedit.text_size", 18, warnings);
            target.compositionTextSize = componentInt("composition.text_size", 18, warnings);
            target.compositionPreviewSourceResolved = warnings.isEmpty();
            target.compositionPreviewWarning = warnings.isEmpty() ? "" : android.text.TextUtils.join("; ", warnings);
        } catch (RuntimeException error) {
            target.compositionPreviewSourceResolved = false;
            target.compositionPreviewWarning = "Dynamic or ambiguous preedit/composition source was not previewed";
        } catch (LinkageError error) {
            target.compositionPreviewSourceResolved = false;
            target.compositionPreviewWarning = "Static preedit/composition reader is unavailable";
        }
    }

    private Boolean staticComponentTable(String path, java.util.List<String> warnings) {
        Boolean value = ThemeComponentStyles.staticTablePresence(panelPreviewSource, path);
        if (value == null) warnings.add(path + " table is dynamic or ambiguous");
        return value;
    }

    private ThemeComponentStyles.Value componentValue(String path, java.util.List<String> warnings) {
        ThemeComponentStyles.Value value = ThemeComponentStyles.read(panelPreviewSource, path);
        if (value.getDynamic()) warnings.add(path + " is dynamic");
        if (value.getCompatibilityDiagnostic() != null) warnings.add(value.getCompatibilityDiagnostic());
        return value;
    }

    private int componentColor(String path, int fallback, java.util.List<String> warnings) {
        ThemeComponentStyles.Value value = componentValue(path, warnings);
        if (value.getDynamic() || value.getLiteral() == null) return fallback;
        if (value.getColorValue() != null) return (int) (long) value.getColorValue();
        if (value.getResourceValue() != null) warnings.add(path + " resource drawable is not loaded in static preview");
        return fallback;
    }

    private boolean componentBoolean(String path, boolean fallback, java.util.List<String> warnings) {
        ThemeComponentStyles.Value value = componentValue(path, warnings);
        return value.getDynamic() || value.getBooleanValue() == null ? fallback : value.getBooleanValue();
    }

    private int componentInt(String path, int fallback, java.util.List<String> warnings) {
        ThemeComponentStyles.Value value = componentValue(path, warnings);
        if (value.getDynamic() || value.getNumberValue() == null) return fallback;
        double number = value.getNumberValue();
        if (number != Math.rint(number)) { warnings.add(path + " uses runtime integer fallback"); return fallback; }
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) { warnings.add(path + " exceeds runtime integer range"); return fallback; }
        return (int) number;
    }

    private float componentFloat(String path, float fallback, java.util.List<String> warnings) {
        ThemeComponentStyles.Value value = componentValue(path, warnings);
        if (value.getDynamic() || value.getNumberValue() == null) return fallback;
        double number = value.getNumberValue();
        return Double.isFinite(number) ? (float) number : fallback;
    }

    private void applyToolbarKeysPreview(ThemeEditorModel target) {
        try {
            java.util.List<ThemeToolbarKeys.Item> items = ThemeToolbarKeys.list(panelPreviewSource);
            target.toolbarKeys.clear();
            boolean rawLua = false;
            for (ThemeToolbarKeys.Item item : items) {
                target.toolbarKeys.add(toolbarPreviewLabel(item));
                rawLua |= item.getSource() == ThemeToolbarKeys.Source.RAW_LUA;
            }
            target.toolbarKeysSourceResolved = !rawLua;
            target.toolbarPreviewWarning = rawLua
                    ? "Raw Lua toolbar items were not evaluated; showing static placeholders" : "";
        } catch (RuntimeException error) {
            target.toolbarKeys.clear();
            target.toolbarKeys.add("[动态/歧义 toolbar.keys]");
            target.toolbarKeysSourceResolved = false;
            target.toolbarPreviewWarning = "Dynamic or ambiguous toolbar.keys was not previewed";
        } catch (LinkageError error) {
            target.toolbarKeys.clear();
            target.toolbarKeys.add("[toolbar.keys 不可用]");
            target.toolbarKeysSourceResolved = false;
            target.toolbarPreviewWarning = "Static toolbar reader is unavailable";
        }
    }

    private static String toolbarPreviewLabel(ThemeToolbarKeys.Item item) {
        if (item == null) return "[未知]";
        if (item.getSource() == ThemeToolbarKeys.Source.STRING) return nonEmpty(item.getLiteral(), "[空字符串]");
        if (item.getSource() == ThemeToolbarKeys.Source.FULL_KEY) return "[完整按键]";
        if (item.getSource() == ThemeToolbarKeys.Source.RAW_LUA) return "[Raw Lua]";
        if (item.getSource() == ThemeToolbarKeys.Source.SCHEMA_SWITCH) {
            ThemeToolbarKeys.SchemaSwitch value = item.getSchemaSwitch();
            if (value == null) return "[方案开关]";
            return value.getStates().isEmpty() ? nonEmpty(value.getName(), "[方案开关]")
                    : nonEmpty(value.getStates().get(0), value.getName());
        }
        ThemePresetEvents.Event event = item.getEvent();
        if (event == null) return "[事件]";
        if (!event.getLabel().isEmpty()) return event.getLabel();
        if (!event.getPreview().isEmpty()) return event.getPreview();
        if (!event.getSend().isEmpty()) return event.getSend();
        if (!event.getText().isEmpty()) return event.getText();
        if (!event.getCommit().isEmpty()) return event.getCommit();
        return "[事件]";
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static void copyFilterPreview(ThemeEditorModel.FilterBarPreview target, ThemePanelComponents.FilterBar source) {
        target.show = source.getShow();
        target.gravity = source.getGravity();
        target.showExplicit = source.getShowExplicit();
        target.gravityExplicit = source.getGravityExplicit();
        target.inherited = source.getInherited();
        target.sourceResolved = !source.getInherited()
                || source.getShowExplicit() && source.getGravityExplicit();
    }

    private static void copyToolbarPreview(ThemeEditorModel.PanelBarPreview target, ThemePanelComponents.Toolbar source, float fallbackHeight) {
        target.gravity = source.getGravity() == null ? "right" : source.getGravity();
        target.height = previewHeight(source.getHeight(), fallbackHeight);
        target.replaceKeys(source.getKeys());
        target.gravityExplicit = source.getGravityExplicit();
        target.heightExplicit = source.getHeightExplicit();
        target.keysExplicit = source.getKeysExplicit();
        target.inherited = source.getInherited();
        target.sourceResolved = !source.getInherited()
                || source.getGravityExplicit() && source.getKeysExplicit()
                && (source.getHeight() == null || source.getHeightExplicit());
    }

    private static void copyTabPreview(ThemeEditorModel.PanelBarPreview target, ThemePanelComponents.TabBar source, String fallbackGravity, float fallbackHeight) {
        target.gravity = source.getGravity() == null ? fallbackGravity : source.getGravity();
        target.height = previewHeight(source.getHeight(), fallbackHeight);
        target.gravityExplicit = source.getGravityExplicit();
        target.heightExplicit = source.getHeightExplicit();
        target.keysExplicit = false;
        target.inherited = source.getInherited();
        target.sourceResolved = !source.getInherited()
                || source.getGravityExplicit() && source.getHeightExplicit();
    }

    private static boolean panelPreviewResolved(ThemeEditorModel target) {
        return target.candidateExpandedFilterBar.sourceResolved
                && target.candidateExpandedToolBar.sourceResolved
                && target.symbolTabBar.sourceResolved
                && target.symbolToolBar.sourceResolved
                && target.clipboardTabBar.sourceResolved
                && target.clipboardToolBar.sourceResolved;
    }

    private static float previewHeight(Double value, float fallback) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value) || value < 0) return fallback;
        return (float) Math.min(value, 1000d);
    }

    private static String panelPreviewDefaultMessage() {
        return "未提供静态面板源;显示预览默认值";
    }
    void storeStyleEntityClipboard(ThemeStyleEntities.Snapshot snapshot) { ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.STYLE_ENTITY, clipboardScope, null, null, null, null, snapshot == null ? "" : snapshot.getId(), snapshot)); setStatus(snapshot == null ? "样式实体复制失败" : "已复制完整样式实体 " + snapshot.getId() + " 到编辑器专用剪贴板"); }
    ThemeEditorClipboard.Payload styleEntityClipboard() { ThemeEditorClipboard.Payload value = ThemeEditorClipboard.get(); return value != null && value.type == ThemeEditorClipboard.Type.STYLE_ENTITY ? value : null; }
    boolean isCrossProjectClipboard(ThemeEditorClipboard.Payload value) { return value != null && !java.util.Objects.equals(value.projectIdentity, clipboardScope); }
    void applyStyleEntityReference(java.util.List<ThemeEditorModel.Key> keys, String styleId) { if (keys == null || keys.isEmpty()) { setStatus("已创建样式实体;未选择目标按键"); return; } if (!changeStarted()) return; int count = 0; for (ThemeEditorModel.Key snapshot : keys) { ThemeEditorModel.Key key = model.find(snapshot.id); if (key != null) { key.keyStyle = styleId; count++; } } persistCurrentKeyMapPage(); refreshSelectionEditor(canvas.getSelectedKey()); notifyModelChanged("已应用粘贴的样式实体 " + styleId + " 到 " + count + " 个按键(一个撤销步骤)"); }
    public void setReadOnly(boolean value) { readOnly = value; canvas.setReadOnly(value || !"select".equals(canvasMode)); properties.setReadOnly(value); if (value) setStatus("只读会话"); }
    private boolean canEdit() { if (!readOnly) return true; setStatus("只读:另一个会话正在占用此项目"); return false; }
    public void setModel(ThemeEditorModel value) { model = value == null ? ThemeEditorModel.sample() : value.copy(); applyPanelPreviewSource(model); undo.clear(); redo.clear(); dirty = false; canvas.setModel(model); properties.setLayoutMode(model.layoutMode); properties.bind(null); zoomValue.setText(Math.round(model.previewZoom * 100) + "%"); contextBar.setVisibility(INVISIBLE); setStatus("就绪"); }
    public void setModelKeepingHistory(ThemeEditorModel value) { if (value == null) return; String selectedId = canvas.getSelectedKey() == null ? null : canvas.getSelectedKey().id; java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>(model.selectedIds); model = value.copy(); applyPanelPreviewSource(model); model.selectedIds.clear(); for (String id : selected) if (model.find(id) != null) model.selectedIds.add(id); canvas.setModel(model); ThemeEditorModel.Key key = selectedId == null ? null : model.find(selectedId); canvas.setSelectedKey(key); properties.setLayoutMode(model.layoutMode); refreshSelectionEditor(key); setStatus("工作台已刷新"); }
    public void updatePreviewColors(ThemeEditorModel value) {
        if (value == null) return;
        model.backgroundColor = value.backgroundColor;
        model.candidateBackgroundColor = value.candidateBackgroundColor;
        model.candidateTextColor = value.candidateTextColor;
        model.toolbarBackgroundColor = value.toolbarBackgroundColor;
        model.toolbarTextColor = value.toolbarTextColor;
        model.preeditBackgroundColor = value.preeditBackgroundColor; model.preeditTextColor = value.preeditTextColor; model.preeditTextSize = value.preeditTextSize;
        model.compositionBackgroundColor = value.compositionBackgroundColor;
        model.compositionTextColor = value.compositionTextColor;
        model.symbolBackgroundColor = value.symbolBackgroundColor;
        model.symbolTabTextColor = value.symbolTabTextColor;
        model.symbolIndicatorColor = value.symbolIndicatorColor;
        for (ThemeEditorModel.Key source : value.keys) { ThemeEditorModel.Key target = model.find(source.id); if (target != null) { target.fillColor = source.fillColor; target.textColor = source.textColor; } }
        applyPanelPreviewSource(model);
        canvas.invalidate(); refreshStructurePanel();
    }
    public boolean replaceModelAsAtomic(ThemeEditorModel value, String message) { if (value == null) return false; properties.commit(); if (!changeStarted()) return false; model = value.copy(); applyPanelPreviewSource(model); model.selectedIds.clear(); canvas.setModel(model); canvas.setSelectedKey(null); properties.setLayoutMode(model.layoutMode); properties.bind(null); if (callbacks != null) callbacks.onModelChanged(model.copy()); setStatus(message); return true; }
    public ThemeEditorModel getModel() { properties.commit(); persistCurrentKeyMapPage(); return model.copy(); }
    private boolean changeStarted() { if (!canEdit()) return false; dirty = true; if (applying) return true; if (undo.isEmpty() || !same(undo.peek(), model)) undo.push(model.copy()); redo.clear(); return true; }
    private boolean same(ThemeEditorModel a, ThemeEditorModel b) {
        if (a.layoutMode != b.layoutMode || a.selectedKeyMapPage != b.selectedKeyMapPage || !java.util.Objects.equals(a.selectedFlexContainerId, b.selectedFlexContainerId) || a.flexContainers.size() != b.flexContainers.size() || a.keyMapPages.size() != b.keyMapPages.size() || a.rows.size() != b.rows.size() || a.backgroundColor != b.backgroundColor || a.keys.size() != b.keys.size()) return false;
        for (int i = 0; i < a.rows.size(); i++) { ThemeEditorModel.Row x = a.rows.get(i), y = b.rows.get(i); if (!x.id.equals(y.id) || x.height != y.height || x.sourceHeight != y.sourceHeight || x.width != y.width) return false; }
        for (int i = 0; i < a.flexContainers.size(); i++) { ThemeEditorModel.FlexContainer x = a.flexContainers.get(i), y = b.flexContainers.get(i); if (!x.id.equals(y.id) || !java.util.Objects.equals(x.parentId, y.parentId) || !x.direction.equals(y.direction) || !x.style.equals(y.style) || x.width != y.width || x.height != y.height || x.grow != y.grow || !x.keyIds.equals(y.keyIds)) return false; }
        for (int i = 0; i < a.keyMapPages.size(); i++) { ThemeEditorModel.KeyMapPage x = a.keyMapPages.get(i), y = b.keyMapPages.get(i); if (!x.id.equals(y.id) || !x.name.equals(y.name) || x.keys.size() != y.keys.size()) return false; }
        for (int i = 0; i < a.keys.size(); i++) { ThemeEditorModel.Key x = a.keys.get(i), y = b.keys.get(i); if (!x.id.equals(y.id) || !x.ownerId.equals(y.ownerId) || x.x != y.x || x.y != y.y || x.width != y.width || x.height != y.height || !x.label.equals(y.label) || !x.click.equals(y.click) || !x.longClick.equals(y.longClick) || !x.swipeLeft.equals(y.swipeLeft) || !x.swipeRight.equals(y.swipeRight) || !x.swipeUp.equals(y.swipeUp) || !x.swipeDown.equals(y.swipeDown) || !x.keyStyle.equals(y.keyStyle) || !x.popup.equals(y.popup) || x.editorLocked != y.editorLocked) return false; }
        return true;
    }
    private void restore(ThemeEditorModel value) { applying = true; String selectedId = canvas.getSelectedKey() == null ? null : canvas.getSelectedKey().id; model = value.copy(); applyPanelPreviewSource(model); canvas.setModel(model); ThemeEditorModel.Key restored = selectedId == null ? null : model.find(selectedId); canvas.setSelectedKey(restored); refreshSelectionEditor(restored); applying = false; }
    public void undo() { if (!canEdit()) return; properties.commit(); if (undo.isEmpty()) { setStatus("没有可撤销的操作"); return; } redo.push(model.copy()); restore(undo.pop()); if (callbacks != null) callbacks.onUndo(model.copy()); setStatus("已撤销"); }
    public void redo() { if (!canEdit()) return; properties.commit(); if (redo.isEmpty()) { setStatus("没有可重做的操作"); return; } undo.push(model.copy()); restore(redo.pop()); if (callbacks != null) callbacks.onRedo(model.copy()); setStatus("已重做"); }
    public void setStatus(String message) { status.setText((dirty ? "● 已修改 · " : "● 已保存 · ") + message); if (statusContext != null && model != null) statusContext.setText(layoutName() + " · " + model.keys.size() + " 个按键 · 已选择 " + selectedKeys().size() + " 个   "); refreshStructurePanel(); }
}
