/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public final class ThemeKeyboardCanvas extends View {
    public enum InteractionMode { SELECT, PAN }

    public interface Listener { void onKeySelected(ThemeEditorModel.Key key); void onKeyMoveStarted(); void onKeyMoved(); void onKeyMoveFinished(ThemeEditorModel.Key key); default void onViewportChanged() {} }
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
    private boolean gridVisible = true;
    private boolean previewOnly;
    private InteractionMode interactionMode = InteractionMode.SELECT;
    private ThemeEditorModel.Key pressedKey;
    private float panStartX, panStartY;
    private float pinchStartDistance, pinchStartZoom;
    private boolean panning, pinching, viewportChanged;
    private final java.util.HashMap<String, float[]> dragStarts = new java.util.HashMap<>();
    private float transformScaleX, transformScaleY, transformLeft, transformTop;

    public ThemeKeyboardCanvas(Context context) { super(context); touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop(); setFocusable(true); setContentDescription("键盘主题预览画布"); }
    public void setListener(Listener listener) { this.listener = listener; }
    public void setReadOnly(boolean value) { readOnly = value; }
    public void setAppendSelection(boolean value) { appendSelection = value; }
    public void setModel(ThemeEditorModel model) { this.model = model == null ? new ThemeEditorModel() : model; reconcileSelection(); invalidate(); }
    public ThemeEditorModel.Key getSelectedKey() { return selected; }
    public void setSelectedKey(ThemeEditorModel.Key key) { selected = key; invalidate(); }

    /** Workspace-facing canvas controls. These affect only the viewport, never source structure. */
    public void setInteractionMode(InteractionMode mode) { interactionMode = mode == null ? InteractionMode.SELECT : mode; cancelGestureState(); invalidate(); }
    public void setInteractionMode(String mode) { setInteractionMode("pan".equalsIgnoreCase(mode) ? InteractionMode.PAN : InteractionMode.SELECT); }
    public void setCanvasMode(String mode) { setInteractionMode(mode); }
    public InteractionMode getInteractionMode() { return interactionMode; }
    public void setSelectionMode() { setInteractionMode(InteractionMode.SELECT); }
    public void setPanMode() { setInteractionMode(InteractionMode.PAN); }
    public void setGridVisible(boolean visible) { gridVisible = visible; invalidate(); }
    public boolean isGridVisible() { return gridVisible; }
    public void toggleGrid() { setGridVisible(!gridVisible); }
    public void setPreviewOnly(boolean value) { previewOnly = value; cancelGestureState(); invalidate(); }
    public void setPreviewMode(boolean value) { setPreviewOnly(value); }
    public boolean isPreviewOnly() { return previewOnly; }
    public float getZoom() { return safeZoom(model.previewZoom); }
    public void setZoom(float zoom) { model.previewZoom = clamp(zoom, .25f, 4f); invalidate(); }
    public void zoomIn() { setZoom(getZoom() + .1f); }
    public void zoomOut() { setZoom(getZoom() - .1f); }
    public void fitToCanvas() { model.previewZoom = 1f; model.previewPanX = 0f; model.previewPanY = 0f; invalidate(); }
    public void fitToViewport(boolean ignored) { fitToCanvas(); }
    public void resetViewport() { fitToCanvas(); }
    public void setViewportPan(float x, float y) { model.previewPanX = finite(x, 0f); model.previewPanY = finite(y, 0f); invalidate(); }
    public float getViewportPanX() { return model.previewPanX; }
    public float getViewportPanY() { return model.previewPanY; }

    private static float finite(float value, float fallback) { return Float.isNaN(value) || Float.isInfinite(value) ? fallback : value; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, finite(value, min))); }
    private static float safeZoom(float zoom) { return clamp(zoom, .25f, 4f); }

    private static final float STAGE_LEFT = -3f;
    private static final float STAGE_RIGHT = 116f;
    private static final float STAGE_TOP = -48f;
    private static final float MIN_STAGE_BOTTOM = 85f;
    private float stageBottom = MIN_STAGE_BOTTOM;

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawCanvasBackground(canvas);
        updateTransform();
        canvas.save();
        canvas.translate(transformLeft, transformTop);
        canvas.scale(transformScaleX, transformScaleY);
        drawDeviceMeta(canvas);
        drawKeyboardShell(canvas);
        canvas.restore();
    }

    private void drawCanvasBackground(Canvas canvas) {
        canvas.drawColor(0xff070a12);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(getWidth() * .52f, getHeight() * .48f,
                Math.max(1f, Math.max(getWidth(), getHeight()) * .72f),
                new int[]{0xff172033, 0xff0c111d, 0xff060810}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        if (gridVisible && !previewOnly) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            float minor = Math.max(12f, getResources().getDisplayMetrics().density * 8f);
            float major = minor * 5f;
            float ox = model.previewPanX % minor, oy = model.previewPanY % minor;
            paint.setColor(0x0cffffff);
            for (float x = ox; x <= getWidth(); x += minor) canvas.drawLine(x, 0, x, getHeight(), paint);
            for (float y = oy; y <= getHeight(); y += minor) canvas.drawLine(0, y, getWidth(), y, paint);
            paint.setColor(0x16ffffff);
            for (float x = model.previewPanX % major; x <= getWidth(); x += major) canvas.drawLine(x, 0, x, getHeight(), paint);
            for (float y = model.previewPanY % major; y <= getHeight(); y += major) canvas.drawLine(0, y, getWidth(), y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x19000000);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    private boolean usesPlaceholderKeyboard() {
        return model.layoutMode == ThemeEditorModel.LayoutMode.NONE || model.keys.isEmpty();
    }

    private void drawDeviceMeta(Canvas canvas) {
        drawMetaPill(canvas, 0f, -24f, 24f, "●  实时预览", 0xff6ce5ad);
        String orientation = model.previewHeight >= model.previewWidth ? "竖屏" : "横屏";
        String device = Math.round(model.previewWidth) + " × " + Math.round(model.previewHeight) + " 像素 · " + orientation;
        drawMetaPill(canvas, 25f, -24f, 42f, device, 0xff858da1);
        drawMetaPill(canvas, 68f, -24f, 32f, layoutModeLabel(model.layoutMode) + " · " + model.keys.size() + " 个按键", 0xff858da1);
        if (!previewOnly) drawDimensionIndicators(canvas);
    }

    private static String layoutModeLabel(ThemeEditorModel.LayoutMode mode) {
        if (mode == ThemeEditorModel.LayoutMode.ROWS) return "行布局(rows)";
        if (mode == ThemeEditorModel.LayoutMode.FLEX_BOX) return "弹性布局(flexbox)";
        if (mode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) return "绝对按键(keys)";
        if (mode == ThemeEditorModel.LayoutMode.KEY_MAPS) return "按键映射(key_maps)";
        return "无布局(none)";
    }

    private static String preeditInlineLabel(String mode) {
        if ("none".equals(mode)) return "关闭";
        if ("input".equals(mode)) return "输入区";
        if ("composition".equals(mode)) return "组合区";
        if ("preview".equals(mode)) return "预览区";
        return "自定义(" + safeText(mode, "未指定") + ")";
    }

    private static String compositionPositionLabel(String position) {
        if ("fixed".equals(position)) return "固定";
        if ("drag".equals(position)) return "可拖动";
        if ("left".equals(position)) return "左侧";
        if ("right".equals(position)) return "右侧";
        if ("top".equals(position)) return "顶部";
        if ("bottom".equals(position)) return "底部";
        if ("right_up".equals(position) || "top_right".equals(position)) return "右上";
        if ("bottom_right".equals(position)) return "右下";
        return "自定义(" + safeText(position, "未指定") + ")";
    }

    private static String compositionMovableLabel(String movable) {
        if ("true".equals(movable)) return "开启";
        if ("once".equals(movable)) return "单次";
        return "自定义(" + safeText(movable, "未指定") + ")";
    }

    private void drawDimensionIndicators(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(.25f); paint.setColor(0x668b7cff);
        canvas.drawLine(0f, -15.8f, 100f, -15.8f, paint); canvas.drawLine(0f, -17f, 0f, -14.6f, paint); canvas.drawLine(100f, -17f, 100f, -14.6f, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xff858da1); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(2f);
        canvas.drawText(Math.round(model.previewWidth) + " 像素", 50f, -16.5f, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0x5555d7ff); canvas.drawLine(103f, -12f, 103f, stageBottom, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xff758096); paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(Math.round(model.previewHeight) + " 像素", 104f, (stageBottom - 12f) / 2f, paint);
    }

    private void drawMetaPill(Canvas canvas, float x, float y, float width, String text, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xe610141f);
        canvas.drawRoundRect(new RectF(x, y, x + width, y + 6f), 1.6f, 1.6f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.25f);
        paint.setColor(0x334f5870);
        canvas.drawRoundRect(new RectF(x, y, x + width, y + 6f), 1.6f, 1.6f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(2.15f);
        canvas.drawText(text, x + width / 2f, y + 3f - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void drawKeyboardShell(Canvas canvas) {
        float contentBottom = placeholderBottom();
        if (!usesPlaceholderKeyboard()) {
            contentBottom = 0f;
            for (ThemeEditorModel.Key key : model.keys) contentBottom = Math.max(contentBottom, key.y + key.height);
        }
        stageBottom = Math.max(MIN_STAGE_BOTTOM, contentBottom + 5f);
        float chromeTop = -14.2f - (model.showToolbar ? Math.max(0f, model.toolbarHeight + 1f) : 0f)
                - (model.showComposition ? 9f : 0f);
        RectF shadow = new RectF(-2.2f, chromeTop, 102.2f, stageBottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        canvas.drawRoundRect(new RectF(shadow.left + 1.2f, shadow.top + 2f, shadow.right + 1.2f, shadow.bottom + 2f), 5.2f, 5.2f, paint);
        paint.setColor(0xff151923);
        canvas.drawRoundRect(shadow, 5.2f, 5.2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.35f);
        paint.setColor(0x38ffffff);
        canvas.drawRoundRect(shadow, 5.2f, 5.2f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x28ffffff);
        canvas.drawRoundRect(new RectF(44f, chromeTop + 1.8f, 56f, chromeTop + 2.8f), .6f, .6f, paint);
        float previewY = -10f;
        if (model.showCandidate) drawCandidatePreview(canvas, previewY); else drawShellCandidateBar(canvas);
        if (model.showToolbar) drawToolbarPreview(canvas, previewY - model.toolbarHeight - 1f);
        if (model.showComposition) {
            float compositionY = previewY - (model.showToolbar ? model.toolbarHeight + 1f : 0f) - 8f;
            if (model.compositionWindowEnabled) drawCompositionPreview(canvas, compositionY);
            else drawPlainPreeditPreview(canvas, compositionY);
        }

        if (model.previewPanel != ThemeEditorModel.PreviewPanel.KEYBOARD) {
            drawPanelPreview(canvas, model.previewPanel, 1f);
        } else if (usesPlaceholderKeyboard()) {
            drawPlaceholderKeyboard(canvas);
            drawUnavailableNotice(canvas);
        } else {
            drawModelKeys(canvas);
        }
        if (!previewOnly) drawCanvasAdornments(canvas);
    }

    private void drawCanvasAdornments(Canvas canvas) {
        int overflow = overflowCount();
        if (selected != null) {
            RectF tag = new RectF(Math.max(0f, selected.x), Math.max(-1f, selected.y - 4.2f),
                    Math.min(100f, selected.x + Math.max(18f, selected.width)), Math.max(2f, selected.y - .8f));
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xee241f3c); canvas.drawRoundRect(tag, 1f, 1f, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(.25f); paint.setColor(0x999b8cff); canvas.drawRoundRect(tag, 1f, 1f, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xffd3ccff); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(1.9f);
            canvas.drawText("按键 / " + safeText(selected.label, selected.id), tag.centerX(), tag.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
        }
        if (overflow > 0) {
            RectF badge = new RectF(73f, stageBottom + .8f, 100f, stageBottom + 6.5f);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xee351923); canvas.drawRoundRect(badge, 1.3f, 1.3f, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(.3f); paint.setColor(0x99ff6e87); canvas.drawRoundRect(badge, 1.3f, 1.3f, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xffff9cab); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(2f);
            canvas.drawText("△ " + overflow + " 个按键溢出", badge.centerX(), badge.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
        }
    }

    private int overflowCount() {
        int count = 0;
        for (ThemeEditorModel.Key key : model.keys) {
            String label = displayLabel(key);
            boolean outside = key.x < 0f || key.y < 0f || key.x + key.width > 100f || key.y + key.height > 80f;
            boolean cramped = key.width < 2.8f || (!label.isEmpty() && label.length() * Math.max(1.4f, model.keyTextSize * .52f) > key.width);
            if (outside || cramped) count++;
        }
        return count;
    }

    private static String safeText(String value, String fallback) { return value == null || value.isEmpty() ? (fallback == null ? "" : fallback) : value; }
    private String displayLabel(ThemeEditorModel.Key key) {
        if ("schema_name".equals(key.label)) return model.schemaName;
        if ("Enter".equals(key.label) || "Return".equals(key.click)) {
            String action = model.editorActionLabel;
            return "Enter".equals(action) ? "回车(Enter)" : action;
        }
        return key.label == null ? "" : key.label;
    }

    private void drawShellCandidateBar(Canvas canvas) {
        RectF bar = new RectF(1f, -10f, 99f, -1.5f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(model.showCandidate ? model.candidateBackgroundColor : 0xff202531);
        canvas.drawRoundRect(bar, 2f, 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.25f);
        paint.setColor(0x22ffffff);
        canvas.drawRoundRect(bar, 2f, 2f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(model.showCandidate ? model.candidateTextColor : 0xff747d91);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(2.1f, Math.min(3.5f, model.candidateTextSize / 7f)));
        String[] words = model.showCandidate ? new String[]{"⌘", "你好", "你", "输入", "⌄"}
                : new String[]{"", "", "候选栏已隐藏", "", ""};
        float[] centers = {6f, 25f, 48f, 72f, 94f};
        for (int i = 0; i < words.length; i++)
            canvas.drawText(words[i], centers[i], bar.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
        paint.setColor(0x18ffffff);
        canvas.drawRect(36f, bar.top + 2f, 36.2f, bar.bottom - 2f, paint);
        canvas.drawRect(60f, bar.top + 2f, 60.2f, bar.bottom - 2f, paint);
    }

    private void drawModelKeys(Canvas canvas) {
        for (ThemeEditorModel.Key key : model.keys) {
            RectF bounds = new RectF(key.x, key.y, key.x + key.width, key.y + key.height);
            boolean primary = key == selected;
            boolean pressed = key == pressedKey || (model.pressedPreview && primary);
            boolean overflow = key.x < 0f || key.y < 0f || key.x + key.width > 100f || key.y + key.height > 80f;
            boolean inSelection = model.selectedIds.contains(key.id);
            float radius = Math.max(.7f, Math.min(model.keyCornerRadius, Math.min(key.width, key.height) / 2f));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x66000000);
            canvas.drawRoundRect(new RectF(bounds.left, bounds.top + .8f, bounds.right, bounds.bottom + .8f), radius, radius, paint);
            paint.setColor(pressed ? model.pressedKeyBackgroundColor : key.fillColor);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(primary ? 1.05f : inSelection ? .7f : overflow ? .65f : .25f);
            paint.setColor(primary ? 0xffa797ff : inSelection ? 0xff8170e7 : overflow ? 0xffff6e87 : 0x35ffffff);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            if (primary) {
                paint.setStrokeWidth(.35f);
                paint.setColor(0x889783ff);
                canvas.drawRoundRect(new RectF(bounds.left - .6f, bounds.top - .6f, bounds.right + .6f, bounds.bottom + .6f), radius + .6f, radius + .6f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? model.pressedKeyTextColor : key.textColor);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.max(2f, Math.min(model.keyTextSize, key.height * .48f)));
            String displayLabel = displayLabel(key);
            canvas.save();
            canvas.clipRect(bounds);
            canvas.drawText(displayLabel, bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
            String hint = !key.longClick.isEmpty() ? key.longClick : !key.swipeUp.isEmpty() ? key.swipeUp : "";
            if (!hint.isEmpty() && !hint.equals(displayLabel)) {
                paint.setTextSize(Math.max(1.25f, Math.min(2.2f, key.height * .2f))); paint.setTextAlign(Paint.Align.RIGHT);
                paint.setColor(pressed ? model.pressedKeyTextColor : key.textColor);
                canvas.drawText(hint, bounds.right - .8f, bounds.top + 2.2f, paint);
            }
            canvas.restore();
        }
    }

    private float placeholderBottom() { return 78f; }

    private void drawPlaceholderKeyboard(Canvas canvas) {
        drawPlaceholderRow(canvas, "QWERTYUIOP", 2f, 4f, 9.1f, 14f);
        drawPlaceholderRow(canvas, "ASDFGHJKL", 6.6f, 21f, 9.1f, 14f);
        drawPlaceholderRow(canvas, new String[]{"⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫"}, 2f, 38f, 9.1f, 14f);
        String[] labels = {"符", "中/英", ",", "同文(Trime)", "。", "↵"};
        float[] widths = {11f, 15f, 9f, 35f, 9f, 15f};
        float x = 2f;
        for (int i = 0; i < labels.length; i++) {
            drawPlaceholderKey(canvas, x, 55f, widths[i], 14f, labels[i], i == labels.length - 1);
            x += widths[i] + 1.2f;
        }
    }

    private void drawPlaceholderRow(Canvas canvas, String labels, float x, float y, float width, float height) {
        String[] items = new String[labels.length()];
        for (int i = 0; i < labels.length(); i++) items[i] = String.valueOf(labels.charAt(i));
        drawPlaceholderRow(canvas, items, x, y, width, height);
    }

    private void drawPlaceholderRow(Canvas canvas, String[] labels, float x, float y, float width, float height) {
        for (String label : labels) { drawPlaceholderKey(canvas, x, y, width, height, label, false); x += width + .8f; }
    }

    private void drawPlaceholderKey(Canvas canvas, float x, float y, float width, float height, String label, boolean accent) {
        RectF bounds = new RectF(x, y, x + width, y + height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x77000000);
        canvas.drawRoundRect(new RectF(x, y + .8f, x + width, y + height + .8f), 2f, 2f, paint);
        paint.setColor(accent ? 0xff6959d0 : 0xff303542);
        canvas.drawRoundRect(bounds, 2f, 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.25f);
        paint.setColor(0x30ffffff);
        canvas.drawRoundRect(bounds, 2f, 2f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffe7e9f2);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(label.length() > 2 ? 2.4f : 3.7f);
        canvas.drawText(label, bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void drawUnavailableNotice(Canvas canvas) {
        RectF notice = new RectF(22f, 72f, 78f, 80f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xee201d32);
        canvas.drawRoundRect(notice, 2f, 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.3f);
        paint.setColor(0x779b8cff);
        canvas.drawRoundRect(notice, 2f, 2f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffc8c0ff);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(2.5f);
        canvas.drawText("示意占位 · 不可编辑 · 非源内容", notice.centerX(), notice.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
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
            canvas.drawText("注释", 97, y + 2.5f, paint);
        }
    }

    private float drawPlainPreeditPreview(Canvas canvas, float y) {
        float height = Math.max(6f, Math.min(12f, model.preeditTextSize / 3.5f));
        panel(canvas, 2, y, 96, height, model.compositionText + " | 预编辑",
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
        text.append(" | 内嵌:").append(preeditInlineLabel(model.preeditInlineMode));
        text.append(" | 位置:").append(compositionPositionLabel(model.compositionPosition));
        if (!"false".equals(model.compositionMovable))
            text.append(" | 移动:").append(compositionMovableLabel(model.compositionMovable));
        if (model.compositionMaxEntries == -1) text.append(" | 条目:全部");
        else text.append(" | 条目:").append(model.compositionMaxEntries);
        if (model.compositionMaxLength > 0) text.append(" | 换行长度:").append(model.compositionMaxLength);
        if (model.compositionStickyLines > 0) text.append(" | 固定行:").append(model.compositionStickyLines);
        if (model.compositionAllPhrases) text.append(" | 显示全部短语");
        if (!model.compositionUseCursor) text.append(" | 隐藏光标");
        if (model.inputMode == ThemeEditorModel.InputMode.ASCII) text.append(" | 英文模式(ASCII)");
        if (model.previewPaging) text.append(" | 分页状态");
        if (model.previewHasMenu) text.append(" | 菜单状态");
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
        if (tab != null) content = drawPanelBar(canvas, content, tab, "标签页  常用  最近  更多", background, textColor, true);
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
        if (keys == null || keys.isEmpty()) return "(无按键)";
        StringBuilder result = new StringBuilder();
        for (String key : keys) {
            if (result.length() > 0) result.append("  ");
            result.append(panelKeyLabel(key));
            if (result.length() > 48) { result.append(" ..."); break; }
        }
        return result.toString();
    }

    private static String panelKeyLabel(String key) {
        if (key == null || key.isEmpty()) return "∅";
        if ("hide".equals(key)) return "隐藏(hide)";
        if ("page_up".equals(key)) return "上一页(page_up)";
        if ("page_down".equals(key)) return "下一页(page_down)";
        if ("char_filter".equals(key)) return "字符筛选(char_filter)";
        if ("BackSpace".equals(key)) return "退格(BackSpace)";
        if ("undo".equals(key)) return "撤销(undo)";
        return "按键(" + key + ")";
    }

    private void panel(Canvas canvas, float x, float y, float width, float height, String text, int color, int textColor) {
        panel(canvas, x, y, width, height, text, color, textColor, 3.3f);
    }

    private void panel(Canvas canvas, float x, float y, float width, float height, String text, int color, int textColor, float textSize) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); canvas.drawRoundRect(new RectF(x, y, x + width, y + height), 1.2f, 1.2f, paint);
        paint.setColor(textColor); paint.setTextAlign(Paint.Align.LEFT); paint.setTextSize(textSize); canvas.save(); canvas.clipRect(x, y, x + width, y + height); canvas.drawText(text, x + 2, y + height / 2 - (paint.ascent() + paint.descent()) / 2, paint); canvas.restore();
    }

    private float contentStageBottom() {
        if (usesPlaceholderKeyboard()) return MIN_STAGE_BOTTOM;
        float bottom = MIN_STAGE_BOTTOM;
        for (ThemeEditorModel.Key key : model.keys) bottom = Math.max(bottom, key.y + key.height + 5f);
        return bottom;
    }

    private void updateTransform() {
        stageBottom = contentStageBottom();
        float availableWidth = Math.max(1f, getWidth() - getPaddingLeft() - getPaddingRight());
        float availableHeight = Math.max(1f, getHeight() - getPaddingTop() - getPaddingBottom());
        float visualBottom = stageBottom + 7f; // Includes the shell shadow and overflow badge.
        float stageWidth = STAGE_RIGHT - STAGE_LEFT;
        float stageHeight = visualBottom - STAGE_TOP;
        float fitScale = Math.min(availableWidth / stageWidth, availableHeight / stageHeight);
        float zoom = Float.isNaN(model.previewZoom) || Float.isInfinite(model.previewZoom)
                ? 1f : Math.max(.1f, model.previewZoom);
        transformScaleX = transformScaleY = fitScale * zoom;
        float centerModelX = (STAGE_LEFT + STAGE_RIGHT) / 2f;
        float centerModelY = (STAGE_TOP + visualBottom) / 2f;
        transformLeft = getPaddingLeft() + availableWidth / 2f - centerModelX * transformScaleX + model.previewPanX;
        transformTop = getPaddingTop() + availableHeight / 2f - centerModelY * transformScaleY + model.previewPanY;
    }

    private ThemeEditorModel.Key hit(float x, float y) {
        if (model.previewPanel != ThemeEditorModel.PreviewPanel.KEYBOARD || usesPlaceholderKeyboard()) return null;
        updateTransform();
        float modelX = (x - transformLeft) / transformScaleX, modelY = (y - transformTop) / transformScaleY;
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

    private void cancelGestureState() {
        dragStarts.clear(); removeSelectionOnTap = false; moved = false; panning = false; pinching = false; viewportChanged = false; pressedKey = null;
    }

    private static float pointerDistance(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0f;
        float dx = event.getX(1) - event.getX(0), dy = event.getY(1) - event.getY(0);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void beginPan(float x, float y) {
        downX = x; downY = y; panStartX = model.previewPanX; panStartY = model.previewPanY;
        panning = true; moved = false; pressedKey = null; dragStarts.clear(); removeSelectionOnTap = false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            pinchStartDistance = Math.max(1f, pointerDistance(event)); pinchStartZoom = getZoom();
            pinching = true; panning = false; pressedKey = null; dragStarts.clear(); removeSelectionOnTap = false;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && pinching && event.getPointerCount() >= 2) {
            setZoom(pinchStartZoom * pointerDistance(event) / pinchStartDistance); viewportChanged = true;
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP && pinching) {
            pinching = false;
            int remaining = event.getActionIndex() == 0 ? 1 : 0;
            if (remaining < event.getPointerCount()) beginPan(event.getX(remaining), event.getY(remaining));
            return true;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); moved = false;
                if (interactionMode == InteractionMode.PAN || previewOnly) { beginPan(downX, downY); return true; }
                ThemeEditorModel.Key hitKey = hit(downX, downY);
                pressedKey = hitKey;
                if (hitKey != null) {
                    removeSelectionOnTap = appendSelection && model.selectedIds.contains(hitKey.id);
                    if (appendSelection) model.selectedIds.add(hitKey.id); else { model.selectedIds.clear(); model.selectedIds.add(hitKey.id); }
                    selected = hitKey; startX = selected.x; startY = selected.y; dragStarts.clear();
                    if (model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS && model.selectedIds.contains(selected.id)) {
                        for (ThemeEditorModel.Key key : model.keys) if (model.selectedIds.contains(key.id) && !key.editorLocked) dragStarts.put(key.id, new float[]{key.x, key.y});
                    }
                    if (dragStarts.isEmpty() && !selected.editorLocked) dragStarts.put(selected.id, new float[]{selected.x, selected.y});
                    invalidate(); if (listener != null) listener.onKeySelected(selected); return true;
                }
                removeSelectionOnTap = false;
                if (!appendSelection) { model.selectedIds.clear(); selected = null; }
                invalidate(); if (listener != null) listener.onKeySelected(null); return true;
            case MotionEvent.ACTION_MOVE:
                if (panning) {
                    float dx = event.getX() - downX, dy = event.getY() - downY;
                    if (!moved && dx * dx + dy * dy < touchSlop * touchSlop) return true;
                    model.previewPanX = panStartX + dx; model.previewPanY = panStartY + dy; moved = true; viewportChanged = true; invalidate(); return true;
                }
                if (selected != null && !readOnly && !previewOnly && interactionMode == InteractionMode.SELECT && !dragStarts.isEmpty()) {
                    float screenDx = event.getX() - downX, screenDy = event.getY() - downY;
                    if (!moved && screenDx * screenDx + screenDy * screenDy < touchSlop * touchSlop) return true;
                    if (!moved && listener != null) listener.onKeyMoveStarted();
                    updateTransform(); float dx = screenDx / transformScaleX, dy = screenDy / transformScaleY;
                    for (ThemeEditorModel.Key key : model.keys) {
                        float[] start = dragStarts.get(key.id);
                        if (start != null) { key.x = Math.max(0, Math.min(100 - key.width, start[0] + dx)); key.y = Math.max(0, Math.min(80 - key.height, start[1] + dy)); }
                    }
                    moved = true; pressedKey = null; invalidate(); if (listener != null) listener.onKeyMoved();
                }
                return true;
            case MotionEvent.ACTION_UP:
                pressedKey = null;
                if (!panning && moved && selected != null && listener != null) listener.onKeyMoveFinished(selected);
                else if (!panning && removeSelectionOnTap && selected != null) {
                    model.selectedIds.remove(selected.id); selected = lastSelectedKey(); if (listener != null) listener.onKeySelected(selected);
                }
                if (viewportChanged && listener != null) listener.onViewportChanged();
                cancelGestureState(); invalidate(); return true;
            case MotionEvent.ACTION_CANCEL:
                pressedKey = null;
                if (!panning && moved && selected != null && listener != null) listener.onKeyMoveFinished(selected);
                if (viewportChanged && listener != null) listener.onViewportChanged();
                cancelGestureState(); invalidate(); return true;
            default: return true;
        }
    }

}
