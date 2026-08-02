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
import android.widget.Space;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.Deque;

public final class ThemeEditorWorkspace extends LinearLayout {
    private final ThemeKeyboardCanvas canvas;
    private final ThemePropertyEditor properties;
    private final TextView status;
    private final Deque<ThemeEditorModel> undo = new ArrayDeque<>();
    private final Deque<ThemeEditorModel> redo = new ArrayDeque<>();
    private ThemeEditorModel model;
    private ThemeEditorCallbacks callbacks;
    private boolean applying;

    public ThemeEditorWorkspace(Context context) {
        super(context); setOrientation(VERTICAL); setBackgroundColor(0xfff1f3f4);
        LinearLayout toolbar = new LinearLayout(context); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(8, 6, 8, 6); toolbar.setBackgroundColor(Color.WHITE);
        TextView heading = label("Theme editor", 19); toolbar.addView(heading, new LayoutParams(0, 52, 1));
        Button undoButton = action("Undo", "Undo last change"); Button redoButton = action("Redo", "Redo last change"); Button saveButton = action("Save", "Save theme");
        toolbar.addView(undoButton); toolbar.addView(redoButton); toolbar.addView(saveButton); addView(toolbar, new LayoutParams(-1, -2));
        LinearLayout body = new LinearLayout(context); body.setOrientation(HORIZONTAL);
        canvas = new ThemeKeyboardCanvas(context); body.addView(canvas, new LayoutParams(0, -1, 1));
        properties = new ThemePropertyEditor(context); body.addView(properties, new LayoutParams(260, -1)); addView(body, new LayoutParams(-1, 0, 1));
        status = label("Ready", 13); status.setPadding(12, 5, 12, 5); status.setContentDescription("Editor status"); addView(status, new LayoutParams(-1, -2));
        undoButton.setOnClickListener(v -> undo()); redoButton.setOnClickListener(v -> redo()); saveButton.setOnClickListener(v -> { properties.commit(); if (callbacks != null) callbacks.onSave(model.copy()); setStatus("Saved"); });
        canvas.setListener(new ThemeKeyboardCanvas.Listener() { public void onKeySelected(ThemeEditorModel.Key key) { properties.bind(key); setStatus("Selected " + key.label); if (callbacks != null) callbacks.onSelectionChanged(key); } public void onKeyMoveStarted() { changeStarted(); } public void onKeyMoved() { setStatus("Move key, release to finish"); } });
        properties.setListener(new ThemePropertyEditor.Listener() { public void onPropertyChangeStarted() { changeStarted(); } public void onPropertyChanged() { canvas.invalidate(); setStatus("Edited " + (canvas.getSelectedKey() == null ? "theme" : canvas.getSelectedKey().label)); } });
        setModel(ThemeEditorModel.sample());
    }
    private TextView label(String text, float size) { TextView v = new TextView(getContext()); v.setText(text); v.setTextSize(size); v.setTextColor(0xff263238); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private Button action(String text, String description) { Button b = new Button(getContext()); b.setText(text); b.setAllCaps(false); b.setContentDescription(description); b.setMinWidth(0); b.setPadding(10, 0, 10, 0); return b; }
    public void setCallbacks(ThemeEditorCallbacks callbacks) { this.callbacks = callbacks; }
    public void setModel(ThemeEditorModel value) { model = value == null ? ThemeEditorModel.sample() : value.copy(); undo.clear(); redo.clear(); canvas.setModel(model); properties.bind(null); setStatus("Ready"); }
    public ThemeEditorModel getModel() { properties.commit(); return model.copy(); }
    private void changeStarted() { if (applying) return; if (undo.isEmpty() || !same(undo.peek(), model)) undo.push(model.copy()); redo.clear(); }
    private boolean same(ThemeEditorModel a, ThemeEditorModel b) { if (a.backgroundColor != b.backgroundColor || a.keys.size() != b.keys.size()) return false; for (int i = 0; i < a.keys.size(); i++) { ThemeEditorModel.Key x = a.keys.get(i), y = b.keys.get(i); if (!x.id.equals(y.id) || x.x != y.x || x.y != y.y || x.width != y.width || x.height != y.height || !x.label.equals(y.label) || x.fillColor != y.fillColor || x.textColor != y.textColor) return false; } return true; }
    private void restore(ThemeEditorModel value) { applying = true; String selectedId = canvas.getSelectedKey() == null ? null : canvas.getSelectedKey().id; model = value.copy(); canvas.setModel(model); ThemeEditorModel.Key restored = selectedId == null ? null : model.find(selectedId); canvas.setSelectedKey(restored); properties.bind(restored); applying = false; }
    public void undo() { properties.commit(); if (undo.isEmpty()) { setStatus("Nothing to undo"); return; } redo.push(model.copy()); restore(undo.pop()); if (callbacks != null) callbacks.onUndo(model.copy()); setStatus("Undone"); }
    public void redo() { properties.commit(); if (redo.isEmpty()) { setStatus("Nothing to redo"); return; } undo.push(model.copy()); restore(redo.pop()); if (callbacks != null) callbacks.onRedo(model.copy()); setStatus("Redone"); }
    public void setStatus(String message) { status.setText(message); }
}
