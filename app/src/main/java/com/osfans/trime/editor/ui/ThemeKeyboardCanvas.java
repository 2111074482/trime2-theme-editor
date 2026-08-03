/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class ThemeKeyboardCanvas extends View {
    public interface Listener { void onKeySelected(ThemeEditorModel.Key key); void onKeyMoveStarted(); void onKeyMoved(); void onKeyMoveFinished(ThemeEditorModel.Key key); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int touchSlop;
    private ThemeEditorModel model = new ThemeEditorModel();
    private Listener listener;
    private ThemeEditorModel.Key selected;
    private float downX, downY, startX, startY;
    private boolean moved;
    private boolean readOnly;
    private boolean appendSelection;
    private boolean removeSelectionOnTap;
    private final java.util.HashMap<String, float[]> dragStarts = new java.util.HashMap<>();
    private float transformScaleX, transformScaleY, transformLeft, transformTop;

    public ThemeKeyboardCanvas(Context context) { super(context); touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop(); setFocusable(true); setContentDescription("Keyboard theme preview canvas"); }
    public void setListener(Listener listener) { this.listener = listener; }
    public void setReadOnly(boolean value) { readOnly = value; }
    public void setAppendSelection(boolean value) { appendSelection = value; }
    public void setModel(ThemeEditorModel model) { this.model = model == null ? new ThemeEditorModel() : model; reconcileSelection(); invalidate(); }
    public ThemeEditorModel.Key getSelectedKey() { return selected; }
    public void setSelectedKey(ThemeEditorModel.Key key) { selected = key; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(model.backgroundColor);
        updateTransform();
        canvas.save(); canvas.translate(transformLeft, transformTop); canvas.scale(transformScaleX, transformScaleY);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.45f); paint.setColor(0x553F4A52);
        canvas.drawRect(0, 0, 100, 94, paint);
        float y = 2;
        if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS && !model.keyMapPages.isEmpty()) {
            StringBuilder tabs = new StringBuilder();
            for (int i = 0; i < model.keyMapPages.size(); i++) {
                if (i > 0) tabs.append("   ");
                if (i == model.selectedKeyMapPage) tabs.append("[").append(model.keyMapPages.get(i).name).append("]"); else tabs.append(model.keyMapPages.get(i).name);
                if (tabs.length() > 42) { tabs.append(" ..."); break; }
            }
            panel(canvas, 2, y, 96, 7, tabs.toString(), model.symbolBackgroundColor, model.symbolTabTextColor); y += 8;
        }
        if (model.showToolbar) { drawToolbarPreview(canvas, y); y += model.toolbarHeight + 1; }
        if (model.showCandidate) { drawCandidatePreview(canvas, y); y += model.candidateHeight + 1; }
        if (!"none".equals(model.preeditInlineMode)) {
            float preeditHeight = Math.max(5f, Math.min(10f, model.preeditTextSize / 4f));
            panel(canvas, 2, y, 96, preeditHeight, "编辑器内联 " + model.preeditInlineMode + " [source=" + model.preeditInlineSource + "]", model.preeditBackgroundColor, model.preeditTextColor, Math.max(2f, Math.min(4.5f, model.preeditTextSize / 5.5f)));
            y += preeditHeight + 1;
        }
        if (model.showComposition) y = model.compositionWindowEnabled
                ? drawCompositionPreview(canvas, y) : drawPlainPreeditPreview(canvas, y);
        if (model.previewPanel != ThemeEditorModel.PreviewPanel.KEYBOARD) {
            drawPanelPreview(canvas, model.previewPanel, y);
        }
        if (model.previewPanel == ThemeEditorModel.PreviewPanel.KEYBOARD) for (ThemeEditorModel.Key key : model.keys) {
            RectF bounds = new RectF(key.x, key.y + 8, key.x + key.width, key.y + key.height + 8);
            paint.setStyle(Paint.Style.FILL); paint.setColor(model.pressedPreview && key == selected ? model.pressedKeyBackgroundColor : key.fillColor); canvas.drawRoundRect(bounds, model.keyCornerRadius, model.keyCornerRadius, paint);
            boolean inSelection = model.selectedIds.contains(key.id);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(key == selected ? 1.35f : inSelection ? .85f : .35f);
            paint.setColor(key == selected ? 0xff1565c0 : inSelection ? 0xff42a5f5 : 0x884F5A60); canvas.drawRoundRect(bounds, model.keyCornerRadius, model.keyCornerRadius, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(model.pressedPreview && key == selected ? model.pressedKeyTextColor : key.textColor); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(model.keyTextSize, key.height * .55f));
            String displayLabel = "schema_name".equals(key.label) ? model.schemaName : "Enter".equals(key.label) || "Return".equals(key.click) ? model.editorActionLabel : key.label;
            canvas.drawText(displayLabel, key.x + key.width / 2, key.y + 8 + key.height / 2 - (paint.ascent() + paint.descent()) / 2, paint);
        }
        canvas.restore();
    }

    private void drawToolbarPreview(Canvas canvas, float y) {
        panel(canvas, 2, y, 96, model.toolbarHeight, "", model.toolbarBackgroundColor, model.toolbarTextColor);
        float hideWidth = Math.max(8f, Math.min(16f, model.toolbarHeight * 1.8f));
        RectF list = new RectF(2, y, 98 - hideWidth, y + model.toolbarHeight);
        paint.setStyle(Paint.Style.FILL); paint.setColor(model.pressedPreview ? model.toolbarKeyPressedBackgroundColor : model.toolbarKeyBackgroundColor);
        canvas.drawRoundRect(list, .8f, .8f, paint);
        paint.setColor(model.pressedPreview ? model.toolbarKeyPressedTextColor : model.toolbarKeyTextColor);
        paint.setTextAlign(Paint.Align.LEFT); paint.setTextSize(Math.max(2f, Math.min(3.4f, model.toolbarHeight * .45f)));
        canvas.save(); canvas.clipRect(list); canvas.drawText(keySummary(model.toolbarKeys), list.left + 2, list.centerY() - (paint.ascent() + paint.descent()) / 2, paint); canvas.restore();
        RectF hide = new RectF(list.right + 1, y, 98, y + model.toolbarHeight);
        paint.setColor(model.pressedPreview ? model.toolbarHidePressedBackgroundColor : model.toolbarHideBackgroundColor); canvas.drawRoundRect(hide, .8f, .8f, paint);
        paint.setColor(model.pressedPreview ? model.toolbarHidePressedTextColor : model.toolbarHideTextColor); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("▽", hide.centerX(), hide.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
    }

    private void drawCandidatePreview(Canvas canvas, float y) {
        StringBuilder candidates = new StringBuilder();
        for (int i = 0; i < model.candidateCount; i++) {
            if (i > 0) candidates.append("   ");
            candidates.append(i + 1).append(' ').append(i == 0 ? "你好" : i == 1 ? "你" : i == 2 ? "输入" : "主题");
            if (model.candidateComments) candidates.append("·注");
        }
        if (model.candidateCount == 0) candidates.append("无候选");
        int background = model.pressedPreview ? model.pressedCandidateBackgroundColor : model.candidateBackgroundColor;
        int text = model.pressedPreview ? model.pressedCandidateTextColor : model.candidateTextColor;
        panel(canvas, 2, y, 96, model.candidateHeight, candidates.toString(), background, text,
                Math.max(2f, Math.min(4.2f, model.candidateTextSize / 6f)));
        float keyWidth = Math.max(7f, Math.min(13f, model.candidateHeight * 1.5f));
        RectF hide = new RectF(98f - keyWidth, y, 98f, y + model.candidateHeight);
        paint.setStyle(Paint.Style.FILL); paint.setColor(model.pressedPreview ? model.candidateKeyPressedBackgroundColor : model.candidateKeyBackgroundColor);
        canvas.drawRoundRect(hide, .8f, .8f, paint); paint.setColor(model.pressedPreview ? model.candidateKeyPressedTextColor : model.candidateKeyTextColor);
        paint.setTextAlign(Paint.Align.CENTER); canvas.drawText("▽", hide.centerX(), hide.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
        if (model.candidateComments) {
            paint.setTextAlign(Paint.Align.RIGHT); paint.setTextSize(Math.max(1.5f, Math.min(2.6f, model.candidateCommentTextSize / 5f)));
            paint.setColor(model.pressedPreview ? model.candidatePressedCommentTextColor : model.candidateCommentTextColor);
            canvas.drawText("comment", 97, y + 2.5f, paint);
        }
    }

    private float drawPlainPreeditPreview(Canvas canvas, float y) {
        float height = Math.max(6f, Math.min(12f, model.preeditTextSize / 3.5f));
        panel(canvas, 2, y, 96, height, model.compositionText + " | preedit",
                model.preeditBackgroundColor, model.preeditTextColor,
                Math.max(2f, Math.min(4.5f, model.preeditTextSize / 5.5f)));
        if (!model.compositionPreviewSourceResolved) {
            paint.setColor(0xff8d6e63); paint.setTextAlign(Paint.Align.RIGHT); paint.setTextSize(2.05f);
            canvas.drawText("静态未解析", 97f, y + height - 1f, paint);
        }
        return y + height + 1f;
    }

    private float drawCompositionPreview(Canvas canvas, float y) {
        float sourceWidth = Math.max(model.compositionMinWidth, Math.min(model.compositionMaxWidth, 480f));
        float width = Math.max(28f, Math.min(96f, sourceWidth / 5f));
        float sourceHeight = Math.max(model.compositionMinHeight,
                model.compositionTextSize + model.compositionPaddingTop + model.compositionPaddingBottom
                        + Math.max(0f, model.compositionLineSpacing) * model.compositionLineSpacingMultiplier);
        sourceHeight = Math.min(model.compositionMaxHeight, sourceHeight);
        float height = Math.max(7f, Math.min(18f, sourceHeight / 4.5f));
        float x = compositionPreviewX(width);
        StringBuilder text = new StringBuilder(model.compositionText);
        text.append(" | inline:").append(model.preeditInlineMode);
        text.append(" | ").append(model.compositionPosition);
        if (!"false".equals(model.compositionMovable)) text.append(" | move:").append(model.compositionMovable);
        if (model.compositionMaxEntries == -1) text.append(" | entries:all");
        else text.append(" | entries:").append(model.compositionMaxEntries);
        if (model.compositionMaxLength > 0) text.append(" | wrap:").append(model.compositionMaxLength);
        if (model.compositionStickyLines > 0) text.append(" | sticky:").append(model.compositionStickyLines);
        if (model.compositionAllPhrases) text.append(" | phrases");
        if (!model.compositionUseCursor) text.append(" | no-cursor");
        if (model.inputMode == ThemeEditorModel.InputMode.ASCII) text.append(" | ASCII");
        if (model.previewPaging) text.append(" | paging");
        if (model.previewHasMenu) text.append(" | menu");
        int panelBackground = model.pressedPreview ? model.compositionPressedBackgroundColor : model.compositionBackgroundColor;
        int panelText = model.pressedPreview ? model.compositionPressedTextColor : model.compositionTextColor;
        panel(canvas, x, y, width, height, text.toString(), panelBackground, panelText, Math.max(2f, Math.min(4.5f, model.compositionTextSize / 5.5f)));
        float keySize = Math.max(5f, Math.min(9f, height - 2f));
        RectF key = new RectF(x + width - keySize - 1f, y + 1f, x + width - 1f, y + 1f + keySize);
        paint.setStyle(Paint.Style.FILL); paint.setColor(model.pressedPreview ? model.compositionKeyPressedBackgroundColor : model.compositionKeyBackgroundColor);
        canvas.drawRoundRect(key, .8f, .8f, paint);
        paint.setColor(model.pressedPreview ? model.compositionKeyPressedTextColor : model.compositionKeyTextColor);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(Math.max(2f, Math.min(3.2f, model.compositionKeyTextSize / 6f)));
        canvas.drawText("✎", key.centerX(), key.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
        paint.setColor(model.pressedPreview ? model.compositionKeyPressedHintTextColor : model.compositionKeyHintTextColor);
        paint.setTextSize(Math.max(1.5f, Math.min(2.4f, (model.pressedPreview ? model.compositionKeyPressedHintTextSize : model.compositionKeyHintTextSize) / 6f)));
        canvas.drawText("标", key.centerX(), Math.max(y + 2f, key.top - .5f), paint);
        if (!model.compositionPreviewSourceResolved) {
            paint.setColor(0xff8d6e63); paint.setTextAlign(Paint.Align.RIGHT); paint.setTextSize(2.05f);
            canvas.drawText("静态未解析", x + width - 1, y + height - 1, paint);
        }
        return y + height + 1;
    }

    private float compositionPreviewX(float width) {
        String position = model.compositionPosition;
        if ("right".equals(position) || "right_up".equals(position)
                || "bottom_right".equals(position) || "top_right".equals(position)) return 98f - width;
        if ("fixed".equals(position) || "drag".equals(position)) return 2f + (96f - width) / 2f;
        return 2f;
    }

    private void drawPanelPreview(Canvas canvas, ThemeEditorModel.PreviewPanel preview, float y) {
        float x = 2, width = 96, height = 50;
        int background = preview == ThemeEditorModel.PreviewPanel.SYMBOL ? model.symbolBackgroundColor
                : preview == ThemeEditorModel.PreviewPanel.CLIPBOARD ? model.clipboardBackgroundColor
                : model.pressedPreview ? model.expandedCandidatePressedBackgroundColor : model.expandedCandidateBackgroundColor;
        int textColor = preview == ThemeEditorModel.PreviewPanel.SYMBOL ? model.symbolKeyTextColor
                : preview == ThemeEditorModel.PreviewPanel.CLIPBOARD
                ? (model.pressedPreview ? model.clipboardKeyPressedTextColor : model.clipboardKeyTextColor)
                : model.pressedPreview ? model.expandedCandidatePressedTextColor : model.expandedCandidateTextColor;
        paint.setStyle(Paint.Style.FILL); paint.setColor(background);
        canvas.drawRoundRect(new RectF(x, y, x + width, y + height), 1.2f, 1.2f, paint);

        String title;
        ThemeEditorModel.PanelBarPreview tab = null;
        ThemeEditorModel.PanelBarPreview tool;
        if (preview == ThemeEditorModel.PreviewPanel.CANDIDATE_EXPANDED) {
            title = "展开候选";
            tool = model.candidateExpandedToolBar;
        } else if (preview == ThemeEditorModel.PreviewPanel.SYMBOL) {
            title = "符号";
            tab = model.symbolTabBar;
            tool = model.symbolToolBar;
        } else {
            title = "剪贴板";
            tab = model.clipboardTabBar;
            tool = model.clipboardToolBar;
        }

        paint.setColor(textColor); paint.setTextAlign(Paint.Align.LEFT); paint.setTextSize(3.2f);
        canvas.drawText(title, x + 2, y + 5, paint);
        RectF content = new RectF(x + 1, y + 7, x + width - 1, y + height - 1);
        if (tab != null) content = drawPanelBar(canvas, content, tab, "TAB  常用  最近  更多", background, textColor, true);
        if (preview == ThemeEditorModel.PreviewPanel.CANDIDATE_EXPANDED && model.candidateExpandedFilterBar.show) {
            content = drawFilterBar(canvas, content, model.candidateExpandedFilterBar, textColor);
        }
        int toolBackground = preview == ThemeEditorModel.PreviewPanel.CANDIDATE_EXPANDED
                ? (model.pressedPreview ? model.expandedCandidateKeyPressedBackgroundColor : model.expandedCandidateKeyBackgroundColor)
                : preview == ThemeEditorModel.PreviewPanel.SYMBOL
                ? (model.pressedPreview ? model.symbolKeyPressedBackgroundColor : model.symbolKeyBackgroundColor)
                : (model.pressedPreview ? model.clipboardKeyPressedBackgroundColor : model.clipboardKeyBackgroundColor);
        int toolText = preview == ThemeEditorModel.PreviewPanel.CANDIDATE_EXPANDED
                ? (model.pressedPreview ? model.expandedCandidateKeyPressedTextColor : model.expandedCandidateKeyTextColor)
                : preview == ThemeEditorModel.PreviewPanel.SYMBOL
                ? (model.pressedPreview ? model.symbolKeyPressedTextColor : model.symbolKeyTextColor)
                : (model.pressedPreview ? model.clipboardKeyPressedTextColor : model.clipboardKeyTextColor);
        content = drawPanelBar(canvas, content, tool, keySummary(tool.keys), toolBackground, toolText, false);
        drawPanelBody(canvas, content, preview, preview == ThemeEditorModel.PreviewPanel.SYMBOL ? model.symbolTextColor : textColor);

        String warning = model.panelPreviewWarning;
        if (warning != null && !warning.isEmpty()) {
            paint.setTextAlign(Paint.Align.RIGHT); paint.setTextSize(2.1f); paint.setColor(0xff8d6e63);
            canvas.drawText("静态默认", x + width - 2, y + height - 2, paint);
        }
    }

    private RectF drawFilterBar(Canvas canvas, RectF content, ThemeEditorModel.FilterBarPreview bar, int textColor) {
        float thickness = 6f;
        RectF bounds = edgeBounds(content, bar.gravity, thickness);
        paint.setStyle(Paint.Style.FILL); paint.setColor(model.compositionBackgroundColor); canvas.drawRoundRect(bounds, .8f, .8f, paint);
        paint.setColor(textColor); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(2.6f);
        canvas.save(); canvas.clipRect(bounds);
        if (isVerticalGravity(bar.gravity)) canvas.drawText("筛选", bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
        else { canvas.rotate(-90, bounds.centerX(), bounds.centerY()); canvas.drawText("筛选", bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2, paint); }
        canvas.restore();
        return insetEdge(content, bar.gravity, thickness + 1);
    }

    private RectF drawPanelBar(Canvas canvas, RectF content, ThemeEditorModel.PanelBarPreview bar, String text, int color, int textColor, boolean tab) {
        float thickness = previewBarThickness(bar.height);
        RectF bounds = edgeBounds(content, bar.gravity, thickness);
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawRoundRect(bounds, .8f, .8f, paint);
        if (tab) {
            paint.setColor(model.previewPanel == ThemeEditorModel.PreviewPanel.CLIPBOARD ? model.clipboardIndicatorColor : model.symbolIndicatorColor);
            if (isVerticalGravity(bar.gravity)) canvas.drawRect(bounds.left, bounds.bottom - .7f, bounds.right, bounds.bottom, paint);
            else canvas.drawRect(bounds.right - .7f, bounds.top, bounds.right, bounds.bottom, paint);
        }
        paint.setColor(textColor); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(2.55f);
        canvas.save(); canvas.clipRect(bounds);
        if (isVerticalGravity(bar.gravity)) canvas.drawText(text, bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2, paint);
        else { canvas.rotate(-90, bounds.centerX(), bounds.centerY()); canvas.drawText(text, bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2, paint); }
        canvas.restore();
        return insetEdge(content, bar.gravity, thickness + 1);
    }

    private void drawPanelBody(Canvas canvas, RectF content, ThemeEditorModel.PreviewPanel preview, int textColor) {
        if (content.width() <= 2 || content.height() <= 2) return;
        if (preview == ThemeEditorModel.PreviewPanel.SYMBOL) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(model.pressedPreview ? model.symbolTextPressedBackgroundColor : model.symbolTextBackgroundColor);
            canvas.drawRoundRect(content, .6f, .6f, paint);
            textColor = model.pressedPreview ? model.symbolTextPressedColor : model.symbolTextColor;
        } else if (preview == ThemeEditorModel.PreviewPanel.CLIPBOARD) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(model.pressedPreview ? model.clipboardItemPressedBackgroundColor : model.clipboardItemBackgroundColor);
            canvas.drawRoundRect(content, .6f, .6f, paint);
            textColor = model.pressedPreview ? model.clipboardItemPressedTextColor : model.clipboardItemTextColor;
        }
        paint.setColor(textColor); paint.setTextAlign(Paint.Align.LEFT); paint.setTextSize(2.8f);
        String text = preview == ThemeEditorModel.PreviewPanel.CANDIDATE_EXPANDED
                ? "你好·注   输入·说明   主题·样式"
                : preview == ThemeEditorModel.PreviewPanel.SYMBOL ? ",   。   ?   !   @   #" : "示例文本\n最近复制";
        String[] lines = text.split("\n", -1);
        float baseline = content.top + 4;
        for (String line : lines) { canvas.drawText(line, content.left + 2, baseline, paint); baseline += 5; }
    }

    private static float previewBarThickness(float sourceHeight) {
        if (Float.isNaN(sourceHeight) || Float.isInfinite(sourceHeight) || sourceHeight < 0) sourceHeight = 48;
        return Math.max(4f, Math.min(12f, sourceHeight / 6f));
    }

    private static boolean isVerticalGravity(String gravity) {
        return "top".equals(gravity) || "bottom".equals(gravity);
    }

    private static RectF edgeBounds(RectF content, String gravity, float thickness) {
        if ("bottom".equals(gravity)) return new RectF(content.left, Math.max(content.top, content.bottom - thickness), content.right, content.bottom);
        if ("left".equals(gravity)) return new RectF(content.left, content.top, Math.min(content.right, content.left + thickness), content.bottom);
        if ("right".equals(gravity)) return new RectF(Math.max(content.left, content.right - thickness), content.top, content.right, content.bottom);
        return new RectF(content.left, content.top, content.right, Math.min(content.bottom, content.top + thickness));
    }

    private static RectF insetEdge(RectF content, String gravity, float amount) {
        RectF result = new RectF(content);
        if ("bottom".equals(gravity)) result.bottom = Math.max(result.top, result.bottom - amount);
        else if ("left".equals(gravity)) result.left = Math.min(result.right, result.left + amount);
        else if ("right".equals(gravity)) result.right = Math.max(result.left, result.right - amount);
        else result.top = Math.min(result.bottom, result.top + amount);
        return result;
    }

    private static String keySummary(java.util.List<String> keys) {
        if (keys == null || keys.isEmpty()) return "(无 keys)";
        StringBuilder result = new StringBuilder();
        for (String key : keys) {
            if (result.length() > 0) result.append("  ");
            result.append(key == null || key.isEmpty() ? "∅" : key);
            if (result.length() > 48) { result.append(" ..."); break; }
        }
        return result.toString();
    }

    private void panel(Canvas canvas, float x, float y, float width, float height, String text, int color, int textColor) {
        panel(canvas, x, y, width, height, text, color, textColor, 3.3f);
    }

    private void panel(Canvas canvas, float x, float y, float width, float height, String text, int color, int textColor, float textSize) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawRoundRect(new RectF(x, y, x + width, y + height), 1.2f, 1.2f, paint);
        paint.setColor(textColor); paint.setTextAlign(Paint.Align.LEFT); paint.setTextSize(textSize); canvas.save(); canvas.clipRect(x, y, x + width, y + height); canvas.drawText(text, x + 2, y + height / 2 - (paint.ascent() + paint.descent()) / 2, paint); canvas.restore();
    }

    private void updateTransform() {
        float ratio = Math.max(.2f, Math.min(5f, model.previewWidth / Math.max(1f, model.previewHeight)));
        float availableWidth = Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight()), availableHeight = Math.max(1, getHeight() - getPaddingTop() - getPaddingBottom());
        float viewportWidth = availableWidth, viewportHeight = viewportWidth / ratio;
        if (viewportHeight > availableHeight) { viewportHeight = availableHeight; viewportWidth = viewportHeight * ratio; }
        transformScaleX = viewportWidth / 100f * model.previewZoom; transformScaleY = viewportHeight / 94f * model.previewZoom;
        transformLeft = (getWidth() - 100f * transformScaleX) / 2f + model.previewPanX;
        transformTop = (getHeight() - 94f * transformScaleY) / 2f + model.previewPanY;
    }

    private ThemeEditorModel.Key hit(float x, float y) {
        if (model.previewPanel != ThemeEditorModel.PreviewPanel.KEYBOARD) return null;
        updateTransform();
        float modelX = (x - transformLeft) / transformScaleX, modelY = (y - transformTop) / transformScaleY - 8;
        for (int i = model.keys.size() - 1; i >= 0; i--) {
            ThemeEditorModel.Key key = model.keys.get(i);
            if (modelX >= key.x && modelX <= key.x + key.width && modelY >= key.y && modelY <= key.y + key.height) return key;
        }
        return null;
    }
    private ThemeEditorModel.Key lastSelectedKey() {
        ThemeEditorModel.Key result = null;
        for (String id : model.selectedIds) { ThemeEditorModel.Key key = model.find(id); if (key != null) result = key; }
        return result;
    }

    private void reconcileSelection() {
        java.util.Iterator<String> iterator = model.selectedIds.iterator();
        while (iterator.hasNext()) if (model.find(iterator.next()) == null) iterator.remove();
        if (selected != null) selected = model.find(selected.id);
        if (selected == null || !model.selectedIds.contains(selected.id)) selected = lastSelectedKey();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); ThemeEditorModel.Key hitKey = hit(downX, downY);
                if (hitKey != null) {
                    removeSelectionOnTap = appendSelection && model.selectedIds.contains(hitKey.id);
                    if (appendSelection) model.selectedIds.add(hitKey.id); else { model.selectedIds.clear(); model.selectedIds.add(hitKey.id); }
                    selected = hitKey; startX = selected.x; startY = selected.y; moved = false; dragStarts.clear();
                    if (model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS && model.selectedIds.contains(selected.id)) {
                        for (ThemeEditorModel.Key key : model.keys) if (model.selectedIds.contains(key.id) && !key.editorLocked) dragStarts.put(key.id, new float[]{key.x, key.y});
                    }
                    if (dragStarts.isEmpty() && !selected.editorLocked) dragStarts.put(selected.id, new float[]{selected.x, selected.y});
                    invalidate(); if (listener != null) listener.onKeySelected(selected); return true;
                }
                removeSelectionOnTap = false;
                if (!appendSelection) { model.selectedIds.clear(); selected = null; }
                invalidate(); if (listener != null) listener.onKeySelected(selected); return true;
            case MotionEvent.ACTION_MOVE:
                if (selected != null && !readOnly && !dragStarts.isEmpty()) {
                    float screenDx = event.getX() - downX, screenDy = event.getY() - downY;
                    if (!moved && screenDx * screenDx + screenDy * screenDy < touchSlop * touchSlop) return true;
                    if (!moved && listener != null) listener.onKeyMoveStarted();
                    updateTransform(); float dx = screenDx / transformScaleX, dy = screenDy / transformScaleY;
                    for (ThemeEditorModel.Key key : model.keys) { float[] start = dragStarts.get(key.id); if (start != null) { key.x = Math.max(0, Math.min(100 - key.width, start[0] + dx)); key.y = Math.max(0, Math.min(80 - key.height, start[1] + dy)); } }
                    moved = true; invalidate(); if (listener != null) listener.onKeyMoved(); return true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (moved && selected != null && listener != null) listener.onKeyMoveFinished(selected);
                else if (removeSelectionOnTap && selected != null) { model.selectedIds.remove(selected.id); selected = lastSelectedKey(); invalidate(); if (listener != null) listener.onKeySelected(selected); }
                dragStarts.clear(); removeSelectionOnTap = false; return true;
            case MotionEvent.ACTION_CANCEL:
                if (moved && selected != null && listener != null) listener.onKeyMoveFinished(selected); dragStarts.clear(); removeSelectionOnTap = false;
                return true;
            default: return true;
        }
    }
}
