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
    public interface Listener { void onKeySelected(ThemeEditorModel.Key key); void onKeyMoveStarted(); void onKeyMoved(); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ThemeEditorModel model = new ThemeEditorModel();
    private Listener listener;
    private ThemeEditorModel.Key selected;
    private float downX, downY, startX, startY;
    private boolean moved;

    public ThemeKeyboardCanvas(Context context) { super(context); setFocusable(true); setContentDescription("Keyboard theme preview canvas"); }
    public void setListener(Listener listener) { this.listener = listener; }
    public void setModel(ThemeEditorModel model) { this.model = model == null ? new ThemeEditorModel() : model; invalidate(); }
    public ThemeEditorModel.Key getSelectedKey() { return selected; }
    public void setSelectedKey(ThemeEditorModel.Key key) { selected = key; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(model.backgroundColor);
        float scale = Math.min(getWidth() / 100f, getHeight() / 80f);
        float left = (getWidth() - 100f * scale) / 2f;
        float top = (getHeight() - 80f * scale) / 2f;
        canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(0.45f); paint.setColor(0x553F4A52);
        canvas.drawRect(0, 0, 100, 80, paint);
        for (ThemeEditorModel.Key key : model.keys) {
            RectF bounds = new RectF(key.x, key.y, key.x + key.width, key.y + key.height);
            paint.setStyle(Paint.Style.FILL); paint.setColor(key.fillColor); canvas.drawRoundRect(bounds, 1.5f, 1.5f, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(key == selected ? 1.2f : .35f);
            paint.setColor(key == selected ? 0xff1565c0 : 0x884F5A60); canvas.drawRoundRect(bounds, 1.5f, 1.5f, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(key.textColor); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(5.5f, key.height * .42f));
            canvas.drawText(key.label, key.x + key.width / 2, key.y + key.height / 2 - (paint.ascent() + paint.descent()) / 2, paint);
        }
        canvas.restore();
    }

    private ThemeEditorModel.Key hit(float x, float y) {
        float scale = Math.min(getWidth() / 100f, getHeight() / 80f);
        float left = (getWidth() - 100f * scale) / 2f, top = (getHeight() - 80f * scale) / 2f;
        float modelX = (x - left) / scale, modelY = (y - top) / scale;
        for (int i = model.keys.size() - 1; i >= 0; i--) {
            ThemeEditorModel.Key key = model.keys.get(i);
            if (modelX >= key.x && modelX <= key.x + key.width && modelY >= key.y && modelY <= key.y + key.height) return key;
        }
        return null;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY(); selected = hit(downX, downY);
                if (selected != null) { startX = selected.x; startY = selected.y; moved = false; invalidate(); if (listener != null) listener.onKeySelected(selected); return true; }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (selected != null) {
                    if (!moved && listener != null) listener.onKeyMoveStarted();
                    float scale = Math.min(getWidth() / 100f, getHeight() / 80f);
                    selected.x = Math.max(0, Math.min(100 - selected.width, startX + (event.getX() - downX) / scale));
                    selected.y = Math.max(0, Math.min(80 - selected.height, startY + (event.getY() - downY) / scale));
                    moved = true; invalidate(); if (listener != null) listener.onKeyMoved(); return true;
                }
                return true;
            case MotionEvent.ACTION_UP: return selected != null || moved;
            default: return true;
        }
    }
}
