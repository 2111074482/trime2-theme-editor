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
import android.widget.ScrollView;
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
    private boolean readOnly;
    private boolean appendSelection;
    private String clipboardScope = "";
    private String panelPreviewSource;
    private boolean panelPreviewSourceAssigned;

    public ThemeEditorWorkspace(Context context) {
        super(context); setOrientation(VERTICAL); setBackgroundColor(0xfff1f3f4);
        LinearLayout toolbar = new LinearLayout(context); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(8, 6, 8, 6); toolbar.setBackgroundColor(Color.WHITE);
        TextView heading = label("Theme editor", 19); toolbar.addView(heading, new LayoutParams(0, 52, 1));
        Button appendSelectButton = action("+Select", "Toggle tap-to-add or remove selection"); Button selectAllButton = action("All", "Select all keys"); Button invertButton = action("Invert", "Invert key selection"); Button rowButton = action("Row", "Select row"); Button batchButton = action("Batch...", "Batch edit selected keys"); Button clipboardButton = action("Clip...", "Internal clipboard for keys, rows, flex subtrees, pages, styles, and events"); Button rowManageButton = action("Row...", "Add copy delete or reorder rows");
        Button previousPageButton = action("◀Page", "Previous key map page"); Button nextPageButton = action("Page▶", "Next key map page"); Button pageAddButton = action("+Page", "Add key map page"); Button pageDeleteButton = action("-Page", "Delete key map page"); Button pageManageButton = action("Page...", "Rename copy or reorder key map page");
        Button flexButton = action("Flex", "Edit selected flex container"); Button flexManageButton = action("Flex...", "Manage flex containers"); Button absoluteButton = action("Keys...", "Absolute key alignment distribution grid and lock tools");
        Button previewButton = action("Preview...", "Preview device size orientation zoom and pan"); Button stateButton = action("State...", "Preview candidates composition panels actions and schema state"); Button eventButton = action("Event...", "Simulate selected key literal event without executing scripts"); Button modeButton = action("中文", "Switch preview input mode"); Button candidateButton = action("候选", "Toggle candidate preview"); Button toolbarButton = action("工具栏", "Toggle toolbar preview"); Button compositionButton = action("组字", "Toggle composition preview"); Button pressedButton = action("按下", "Toggle pressed preview");
        Button addButton = action("Add", "Add key"); Button duplicateButton = action("Copy", "Copy selected key"); Button deleteButton = action("Delete", "Delete selected key");
        Button undoButton = action("Undo", "Undo last change"); Button redoButton = action("Redo", "Redo last change"); Button saveButton = action("Save", "Save theme");
        toolbar.addView(appendSelectButton); toolbar.addView(selectAllButton); toolbar.addView(invertButton); toolbar.addView(rowButton); toolbar.addView(batchButton); toolbar.addView(clipboardButton); toolbar.addView(rowManageButton); toolbar.addView(previousPageButton); toolbar.addView(nextPageButton); toolbar.addView(pageAddButton); toolbar.addView(pageDeleteButton); toolbar.addView(pageManageButton); toolbar.addView(flexButton); toolbar.addView(flexManageButton); toolbar.addView(absoluteButton); toolbar.addView(previewButton); toolbar.addView(stateButton); toolbar.addView(eventButton); toolbar.addView(modeButton); toolbar.addView(candidateButton); toolbar.addView(toolbarButton); toolbar.addView(compositionButton); toolbar.addView(pressedButton); toolbar.addView(addButton); toolbar.addView(duplicateButton); toolbar.addView(deleteButton); toolbar.addView(undoButton); toolbar.addView(redoButton); toolbar.addView(saveButton);
        HorizontalScrollView toolbarScroll = new HorizontalScrollView(context); toolbarScroll.setFillViewport(false); toolbarScroll.addView(toolbar, new HorizontalScrollView.LayoutParams(-2, -2)); addView(toolbarScroll, new LayoutParams(-1, -2));
        boolean wide = getResources().getConfiguration().smallestScreenWidthDp >= 600 || getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout body = new LinearLayout(context); body.setOrientation(wide ? HORIZONTAL : VERTICAL);
        canvas = new ThemeKeyboardCanvas(context); body.addView(canvas, wide ? new LayoutParams(0, -1, 1) : new LayoutParams(-1, 0, 1));
        properties = new ThemePropertyEditor(context); ScrollView propertyScroll = new ScrollView(context); propertyScroll.setFillViewport(true); propertyScroll.addView(properties, new ScrollView.LayoutParams(-1, -2)); int propertySize = (int) (300 * getResources().getDisplayMetrics().density); body.addView(propertyScroll, wide ? new LayoutParams(propertySize, -1) : new LayoutParams(-1, Math.min(propertySize, (int) (300 * getResources().getDisplayMetrics().density)))); addView(body, new LayoutParams(-1, 0, 1));
        status = label("Ready", 13); status.setPadding(12, 5, 12, 5); status.setContentDescription("Editor status"); addView(status, new LayoutParams(-1, -2));
        appendSelectButton.setOnClickListener(v -> { appendSelection = !appendSelection; canvas.setAppendSelection(appendSelection); appendSelectButton.setText(appendSelection ? "✓+Select" : "+Select"); setStatus(appendSelection ? "Tap keys to add or remove them from selection" : "Tap selects one key"); });
        selectAllButton.setOnClickListener(v -> selectAllKeys()); invertButton.setOnClickListener(v -> invertSelection());
        rowButton.setOnClickListener(v -> selectCurrentRow()); batchButton.setOnClickListener(v -> showBatchEditor()); clipboardButton.setOnClickListener(v -> showClipboardActions()); rowManageButton.setOnClickListener(v -> manageRows());
        previousPageButton.setOnClickListener(v -> switchKeyMapPage(-1)); nextPageButton.setOnClickListener(v -> switchKeyMapPage(1));
        pageAddButton.setOnClickListener(v -> addKeyMapPage()); pageDeleteButton.setOnClickListener(v -> deleteKeyMapPage()); pageManageButton.setOnClickListener(v -> manageKeyMapPage());
        flexButton.setOnClickListener(v -> editSelectedFlex()); flexManageButton.setOnClickListener(v -> manageFlexContainers()); absoluteButton.setOnClickListener(v -> manageAbsoluteKeys()); previewButton.setOnClickListener(v -> showPreviewSettings()); stateButton.setOnClickListener(v -> showPreviewState()); eventButton.setOnClickListener(v -> { ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) setStatus("Select a key first"); else if (callbacks != null) callbacks.onManageKeyEvents(key.copy()); else showSelectedEventPreview(); });
        modeButton.setOnClickListener(v -> { model.inputMode = ThemeEditorModel.InputMode.values()[(model.inputMode.ordinal() + 1) % ThemeEditorModel.InputMode.values().length]; modeButton.setText(model.inputMode.name()); canvas.invalidate(); setStatus("Preview mode: " + model.inputMode.name()); });
        candidateButton.setOnClickListener(v -> { model.showCandidate = !model.showCandidate; canvas.invalidate(); setStatus("Candidate preview " + (model.showCandidate ? "on" : "off")); });
        toolbarButton.setOnClickListener(v -> { model.showToolbar = !model.showToolbar; canvas.invalidate(); setStatus("Toolbar preview " + (model.showToolbar ? "on" : "off")); });
        compositionButton.setOnClickListener(v -> { model.showComposition = !model.showComposition; canvas.invalidate(); setStatus("Composition preview " + (model.showComposition ? "on" : "off")); });
        pressedButton.setOnClickListener(v -> { model.pressedPreview = !model.pressedPreview; canvas.invalidate(); setStatus("Pressed preview " + (model.pressedPreview ? "on" : "off")); });
        addButton.setOnClickListener(v -> addKey()); duplicateButton.setOnClickListener(v -> duplicateSelected()); deleteButton.setOnClickListener(v -> deleteSelected());
        undoButton.setOnClickListener(v -> undo()); redoButton.setOnClickListener(v -> redo()); saveButton.setOnClickListener(v -> { if (!canEdit()) return; properties.commit(); if (callbacks != null) callbacks.onSave(model.copy()); });
        canvas.setListener(new ThemeKeyboardCanvas.Listener() { public void onKeySelected(ThemeEditorModel.Key key) { properties.commit(); refreshSelectionEditor(key); if (key != null && model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX && !key.ownerId.isEmpty()) model.selectedFlexContainerId = key.ownerId; int count = selectedKeys().size(); setStatus(count == 0 ? "No selection" : count == 1 ? "Selected " + key.label : "Selected " + count + " keys"); if (callbacks != null) callbacks.onSelectionChanged(key); } public void onKeyMoveStarted() { changeStarted(); } public void onKeyMoved() { canvas.invalidate(); setStatus("Move key, release to finish"); } public void onKeyMoveFinished(ThemeEditorModel.Key key) { finishKeyMove(key); } });
        properties.setListener(new ThemePropertyEditor.Listener() { public void onPropertyChangeStarted() { changeStarted(); } public void onPropertyChanged() { canvas.invalidate(); setStatus("Edited " + (canvas.getSelectedKey() == null ? "theme" : canvas.getSelectedKey().label)); if (callbacks != null) callbacks.onModelChanged(model.copy()); } });
        setModel(ThemeEditorModel.sample());
    }
    private void showPreviewState() {
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(VERTICAL); fields.setPadding(24, 8, 24, 8);
        android.widget.EditText candidateCount = dialogField(fields, "Candidate count 0..20", String.valueOf(model.candidateCount)); android.widget.EditText composition = dialogField(fields, "Composition text", model.compositionText); android.widget.EditText action = dialogField(fields, "Editor action label", model.editorActionLabel); android.widget.EditText schema = dialogField(fields, "Schema name", model.schemaName);
        android.widget.CheckBox comments = new android.widget.CheckBox(getContext()); comments.setText("Candidate comments"); comments.setChecked(model.candidateComments); fields.addView(comments); android.widget.CheckBox paging = new android.widget.CheckBox(getContext()); paging.setText("Paging state"); paging.setChecked(model.previewPaging); fields.addView(paging); android.widget.CheckBox menu = new android.widget.CheckBox(getContext()); menu.setText("Has menu state"); menu.setChecked(model.previewHasMenu); fields.addView(menu);
        android.widget.Spinner panel = new android.widget.Spinner(getContext()); panel.setAdapter(new android.widget.ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"Keyboard", "Expanded candidates", "Symbol panel", "Clipboard panel"})); panel.setSelection(model.previewPanel.ordinal()); fields.addView(panel);
        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext()); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(getContext()).setTitle("Preview state").setView(scroll).setNegativeButton("Cancel", null).setNeutralButton("Reset", (dialog, which) -> { model.candidateCount = 4; model.candidateComments = false; model.previewPaging = false; model.previewHasMenu = false; model.compositionText = "拼音"; model.editorActionLabel = "Enter"; model.schemaName = "方案"; model.previewPanel = ThemeEditorModel.PreviewPanel.KEYBOARD; canvas.invalidate(); setStatus("Preview state reset; theme unchanged"); }).setPositiveButton("Apply", (dialog, which) -> { model.candidateCount = Math.max(0, Math.min(20, (int) parseFloat(candidateCount, model.candidateCount))); model.candidateComments = comments.isChecked(); model.previewPaging = paging.isChecked(); model.previewHasMenu = menu.isChecked(); model.compositionText = composition.getText().toString(); model.editorActionLabel = action.getText().toString(); model.schemaName = schema.getText().toString(); model.previewPanel = ThemeEditorModel.PreviewPanel.values()[panel.getSelectedItemPosition()]; canvas.invalidate(); setStatus("Preview state applied; theme unchanged"); }).show();
    }

    private void showSelectedEventPreview() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) { setStatus("Select a key first"); return; }
        String[] labels = {"Click: " + eventName(key.click), "Long click: " + eventName(key.longClick), "Swipe left: " + eventName(key.swipeLeft), "Swipe right: " + eventName(key.swipeRight), "Swipe up: " + eventName(key.swipeUp), "Swipe down: " + eventName(key.swipeDown)};
        new android.app.AlertDialog.Builder(getContext()).setTitle("Simulate literal key event").setItems(labels, (dialog, which) -> { String value = which == 0 ? key.click : which == 1 ? key.longClick : which == 2 ? key.swipeLeft : which == 3 ? key.swipeRight : which == 4 ? key.swipeUp : key.swipeDown; setStatus(value.isEmpty() ? "No literal event assigned" : "Simulated event label: " + value + "; commands and scripts were not executed"); }).setNegativeButton("Close", null).show();
    }
    private static String eventName(String value) { return value == null || value.isEmpty() ? "(none)" : value; }

    private void showPreviewSettings() {
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(VERTICAL); fields.setPadding(24, 8, 24, 8);
        android.widget.EditText width = dialogField(fields, "Preview width dp", String.valueOf(model.previewWidth)); android.widget.EditText height = dialogField(fields, "Preview height dp", String.valueOf(model.previewHeight)); android.widget.EditText zoom = dialogField(fields, "Zoom 0.5 to 4", String.valueOf(model.previewZoom)); android.widget.EditText panX = dialogField(fields, "Pan X px", String.valueOf(model.previewPanX)); android.widget.EditText panY = dialogField(fields, "Pan Y px", String.valueOf(model.previewPanY));
        String[] presets = {"Phone portrait 360×300", "Phone landscape 720×260", "Tablet portrait 600×420", "Tablet landscape 960×360", "Custom values", "Reset zoom and pan"};
        new android.app.AlertDialog.Builder(getContext()).setTitle("Preview device").setSingleChoiceItems(presets, 4, (dialog, which) -> {
            if (which == 0) { width.setText("360"); height.setText("300"); } else if (which == 1) { width.setText("720"); height.setText("260"); } else if (which == 2) { width.setText("600"); height.setText("420"); } else if (which == 3) { width.setText("960"); height.setText("360"); } else if (which == 5) { zoom.setText("1"); panX.setText("0"); panY.setText("0"); }
        }).setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> {
            model.previewWidth = Math.max(120, parseFloat(width, model.previewWidth)); model.previewHeight = Math.max(100, parseFloat(height, model.previewHeight)); model.previewZoom = Math.max(.5f, Math.min(4f, parseFloat(zoom, model.previewZoom))); model.previewPanX = parseFloat(panX, model.previewPanX); model.previewPanY = parseFloat(panY, model.previewPanY); canvas.invalidate(); setStatus("Preview " + (int) model.previewWidth + "×" + (int) model.previewHeight + " at " + trimPreview(model.previewZoom) + "×; theme unchanged");
        }).show();
    }
    private static String trimPreview(float value) { return value == (int) value ? Integer.toString((int) value) : Float.toString(value); }

    private void switchKeyMapPage(int delta) {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.isEmpty()) { setStatus("This is not a key_maps keyboard"); return; }
        persistCurrentKeyMapPage();
        int count = model.keyMapPages.size();
        model.selectedKeyMapPage = (model.selectedKeyMapPage + delta + count) % count;
        model.keys.clear();
        for (ThemeEditorModel.Key key : model.keyMapPages.get(model.selectedKeyMapPage).keys) model.keys.add(key.copy());
        model.selectedIds.clear(); canvas.setModel(model); canvas.setSelectedKey(null); properties.bind(null);
        setStatus("Page " + (model.selectedKeyMapPage + 1) + ": " + model.keyMapPages.get(model.selectedKeyMapPage).name);
    }

    private void persistCurrentKeyMapPage() {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.isEmpty()) return;
        ThemeEditorModel.KeyMapPage page = model.keyMapPages.get(model.selectedKeyMapPage);
        page.keys.clear();
        for (ThemeEditorModel.Key key : model.keys) page.keys.add(key.copy());
    }

    private void addKeyMapPage() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS) { setStatus("This is not a key_maps keyboard"); return; }
        if (!changeStarted()) return; persistCurrentKeyMapPage();
        ThemeEditorModel.KeyMapPage page = new ThemeEditorModel.KeyMapPage("key_map_new_" + model.keyMapPages.size(), "Page " + (model.keyMapPages.size() + 1));
        model.keyMapPages.add(page); model.selectedKeyMapPage = model.keyMapPages.size() - 1; model.keys.clear(); model.selectedIds.clear(); canvas.setSelectedKey(null); properties.bind(null); canvas.setModel(model);
        if (callbacks != null) callbacks.onModelChanged(model.copy()); setStatus("Added symbol page");
    }

    private void deleteKeyMapPage() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.size() <= 1) { setStatus("At least one symbol page is required"); return; }
        if (!changeStarted()) return; model.keyMapPages.remove(model.selectedKeyMapPage); model.selectedKeyMapPage = Math.min(model.selectedKeyMapPage, model.keyMapPages.size() - 1);
        model.keys.clear(); for (ThemeEditorModel.Key key : model.keyMapPages.get(model.selectedKeyMapPage).keys) model.keys.add(key.copy()); model.selectedIds.clear(); canvas.setSelectedKey(null); properties.bind(null); canvas.setModel(model);
        if (callbacks != null) callbacks.onModelChanged(model.copy()); setStatus("Deleted symbol page");
    }

    private void manageKeyMapPage() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.isEmpty()) { setStatus("This is not a key_maps keyboard"); return; }
        persistCurrentKeyMapPage(); ThemeEditorModel.KeyMapPage page = model.keyMapPages.get(model.selectedKeyMapPage);
        String[] actions = {"Rename", "Duplicate", "Move before", "Move after", "Append characters or actions", "Remove duplicate keys"};
        new android.app.AlertDialog.Builder(getContext()).setTitle(page.name).setItems(actions, (dialog, which) -> {
            if (which == 0) renameKeyMapPage(page); else if (which == 1) duplicateKeyMapPage(page); else if (which == 2 || which == 3) moveKeyMapPage(which == 2 ? -1 : 1); else if (which == 4) appendKeyMapItems(page); else removeDuplicateKeyMapItems(page);
        }).setNegativeButton("Cancel", null).show();
    }

    private void appendKeyMapItems(ThemeEditorModel.KeyMapPage page) {
        LinearLayout fields = new LinearLayout(getContext()); android.widget.EditText input = dialogField(fields, "Characters, or comma/newline-separated actions", ""); input.setSingleLine(false); input.setMinLines(3);
        new android.app.AlertDialog.Builder(getContext()).setTitle("Append symbol keys").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Append", (dialog, which) -> {
            String value = input.getText().toString(); if (value.trim().isEmpty() || !changeStarted()) return; persistCurrentKeyMapPage();
            java.util.ArrayList<String> items = new java.util.ArrayList<>();
            if (value.contains(",") || value.contains("\n")) for (String item : value.split("[,\n]")) { String trimmed = item.trim(); if (!trimmed.isEmpty()) items.add(trimmed); }
            else { int offset = 0; while (offset < value.length()) { int codePoint = value.codePointAt(offset); items.add(new String(Character.toChars(codePoint))); offset += Character.charCount(codePoint); } }
            for (String item : items) { ThemeEditorModel.Key key = new ThemeEditorModel.Key(page.id + "_key_new_" + System.nanoTime(), item, 0, 0, 11.5f, 9.5f); key.click = item; key.sourceClick = ""; key.ownerId = page.id; page.keys.add(key); }
            model.keys.clear(); for (ThemeEditorModel.Key key : page.keys) model.keys.add(key.copy()); layoutCurrentKeyMap(); notifyModelChanged("Appended " + items.size() + " symbol keys");
        }).show();
    }

    private void removeDuplicateKeyMapItems(ThemeEditorModel.KeyMapPage page) {
        persistCurrentKeyMapPage(); java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>(); java.util.ArrayList<ThemeEditorModel.Key> unique = new java.util.ArrayList<>();
        for (ThemeEditorModel.Key key : page.keys) { String identity = key.click + "\u0000" + key.label; if (seen.add(identity)) unique.add(key); }
        if (unique.size() == page.keys.size()) { setStatus("No duplicate symbol keys"); return; } if (!changeStarted()) return;
        page.keys.clear(); page.keys.addAll(unique); model.keys.clear(); for (ThemeEditorModel.Key key : page.keys) model.keys.add(key.copy()); layoutCurrentKeyMap(); notifyModelChanged("Removed duplicate symbol keys");
    }

    private void layoutCurrentKeyMap() {
        for (int i = 0; i < model.keys.size(); i++) { ThemeEditorModel.Key key = model.keys.get(i); key.x = (i % 8) * 12.3f; key.y = 10f + (i / 8) * 11f; }
        persistCurrentKeyMapPage(); canvas.setModel(model);
    }

    private void renameKeyMapPage(ThemeEditorModel.KeyMapPage page) {
        android.widget.EditText name = dialogField(new LinearLayout(getContext()), "Page name", page.name);
        LinearLayout parent = (LinearLayout) name.getParent(); parent.setPadding(24, 8, 24, 8);
        new android.app.AlertDialog.Builder(getContext()).setTitle("Rename symbol page").setView(parent).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { String value = name.getText().toString().trim(); if (!value.isEmpty()) { if (!changeStarted()) return; page.name = value; notifyModelChanged("Renamed symbol page"); } }).show();
    }

    private void duplicateKeyMapPage(ThemeEditorModel.KeyMapPage page) {
        if (!changeStarted()) return; ThemeEditorModel.KeyMapPage copy = page.copy(); copy.id = "key_map_copy_" + System.nanoTime(); copy.name = page.name + " copy";
        for (int i = 0; i < copy.keys.size(); i++) { copy.keys.get(i).id = copy.id + "_key_" + i; copy.keys.get(i).ownerId = copy.id; }
        model.keyMapPages.add(model.selectedKeyMapPage + 1, copy); model.selectedKeyMapPage++; model.keys.clear(); for (ThemeEditorModel.Key key : copy.keys) model.keys.add(key.copy()); canvas.setModel(model); notifyModelChanged("Duplicated symbol page");
    }

    private void moveKeyMapPage(int delta) {
        int target = model.selectedKeyMapPage + delta; if (target < 0 || target >= model.keyMapPages.size()) { setStatus("Symbol page is already at the edge"); return; }
        if (!changeStarted()) return; java.util.Collections.swap(model.keyMapPages, model.selectedKeyMapPage, target); model.selectedKeyMapPage = target; notifyModelChanged("Reordered symbol pages");
    }

    private ThemeEditorModel.FlexContainer selectedFlex() {
        if (model.flexContainers.isEmpty()) return null;
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) if (container.id.equals(model.selectedFlexContainerId)) return container;
        model.selectedFlexContainerId = model.flexContainers.get(0).id;
        return model.flexContainers.get(0);
    }

    private void editSelectedFlex() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.FLEX_BOX) { setStatus("This is not a flex_box keyboard"); return; }
        ThemeEditorModel.FlexContainer container = selectedFlex(); if (container == null) return;
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(VERTICAL); fields.setPadding(24, 8, 24, 8);
        android.widget.EditText direction = dialogField(fields, "Direction: row or column", container.direction);
        android.widget.EditText width = dialogField(fields, "Width dp (-1 flexible)", String.valueOf(container.width));
        android.widget.EditText height = dialogField(fields, "Height dp (-1 flexible)", String.valueOf(container.height));
        android.widget.EditText grow = dialogField(fields, "Grow", String.valueOf(container.grow));
        android.widget.EditText style = dialogField(fields, "Style", container.style);
        new android.app.AlertDialog.Builder(getContext()).setTitle("Flex container: " + container.id).setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> {
            if (!changeStarted()) return; container.direction = "column".equals(direction.getText().toString().trim()) ? "column" : "row";
            container.width = parseFloat(width, container.width); container.height = parseFloat(height, container.height); container.grow = Math.max(0, parseFloat(grow, container.grow)); container.style = style.getText().toString().trim();
            if (("row".equals(container.direction) && container.width > 0) || ("column".equals(container.direction) && container.height > 0)) container.grow = 0;
            notifyModelChanged("Edited flex container");
        }).show();
    }

    private void manageFlexContainers() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.FLEX_BOX || model.flexContainers.isEmpty()) { setStatus("This is not a flex_box keyboard"); return; }
        String[] labels = new String[model.flexContainers.size()];
        for (int i = 0; i < labels.length; i++) { ThemeEditorModel.FlexContainer c = model.flexContainers.get(i); labels[i] = (c.id.equals(model.selectedFlexContainerId) ? "✓ " : "") + c.id + "  " + c.direction + " grow=" + c.grow; }
        new android.app.AlertDialog.Builder(getContext()).setTitle("Select flex container").setItems(labels, (dialog, which) -> {
            model.selectedFlexContainerId = model.flexContainers.get(which).id; showFlexActions();
        }).setNegativeButton("Close", null).show();
    }

    private void showFlexActions() {
        ThemeEditorModel.FlexContainer selected = selectedFlex(); if (selected == null) return;
        String[] actions = selected.parentId == null ? new String[]{"Edit", "Add child", "Move selected keys here"} : new String[]{"Edit", "Add child", "Move selected keys here", "Reparent container", "Duplicate subtree", "Delete subtree", "Move before", "Move after"};
        new android.app.AlertDialog.Builder(getContext()).setTitle(selected.id).setItems(actions, (dialog, which) -> {
            String action = actions[which];
            if ("Edit".equals(action)) editSelectedFlex(); else if ("Add child".equals(action)) addFlexChild(selected); else if ("Move selected keys here".equals(action)) moveSelectedKeysToFlex(selected);
            else if ("Reparent container".equals(action)) chooseFlexParent(selected); else if ("Duplicate subtree".equals(action)) duplicateFlexSubtree(selected); else if ("Delete subtree".equals(action)) deleteFlexSubtree(selected);
            else moveFlexSibling(selected, "Move before".equals(action) ? -1 : 1);
        }).setNegativeButton("Cancel", null).show();
    }

    private void moveSelectedKeysToFlex(ThemeEditorModel.FlexContainer target) {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("Select one or more keys first"); return; }
        if (!changeStarted()) return;
        java.util.HashSet<String> ids = new java.util.HashSet<>(); for (ThemeEditorModel.Key key : keys) ids.add(key.id);
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) for (int i = container.keyIds.size() - 1; i >= 0; i--) if (ids.contains(container.keyIds.get(i))) container.keyIds.remove(i);
        for (ThemeEditorModel.Key key : keys) { key.ownerId = target.id; target.keyIds.add(key.id); }
        model.selectedFlexContainerId = target.id; notifyModelChanged("Moved " + keys.size() + " keys into " + target.id);
    }

    private void chooseFlexParent(ThemeEditorModel.FlexContainer child) {
        if (child.parentId == null) { setStatus("Root flex container cannot be reparented"); return; }
        java.util.HashSet<String> subtree = flexSubtreeIds(child.id); java.util.ArrayList<ThemeEditorModel.FlexContainer> candidates = new java.util.ArrayList<>();
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) if (!subtree.contains(container.id)) candidates.add(container);
        String[] labels = new String[candidates.size()]; for (int i = 0; i < labels.length; i++) labels[i] = candidates.get(i).id;
        new android.app.AlertDialog.Builder(getContext()).setTitle("Move container under").setItems(labels, (dialog, which) -> {
            ThemeEditorModel.FlexContainer parent = candidates.get(which); if (parent.id.equals(child.parentId)) { setStatus("Container already has that parent"); return; }
            if (!changeStarted()) return; child.parentId = parent.id; model.selectedFlexContainerId = child.id; notifyModelChanged("Reparented flex container");
        }).setNegativeButton("Cancel", null).show();
    }

    private void addFlexChild(ThemeEditorModel.FlexContainer parent) {
        if (!changeStarted()) return; ThemeEditorModel.FlexContainer child = new ThemeEditorModel.FlexContainer("flex_new_" + System.nanoTime(), parent.id); child.direction = "row";
        int insert = lastDescendantIndex(parent.id) + 1; model.flexContainers.add(Math.min(insert, model.flexContainers.size()), child); model.selectedFlexContainerId = child.id; notifyModelChanged("Added flex child container");
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
        int insert = lastDescendantIndex(selected.id) + 1; model.flexContainers.addAll(Math.min(insert, model.flexContainers.size()), copies); model.keys.addAll(keyCopies); model.selectedFlexContainerId = copies.get(0).id; notifyModelChanged("Duplicated flex subtree");
    }

    private java.util.HashSet<String> flexSubtreeIds(String rootId) {
        java.util.HashSet<String> result = new java.util.HashSet<>(); result.add(rootId); boolean changed;
        do { changed = false; for (ThemeEditorModel.FlexContainer item : model.flexContainers) if (item.parentId != null && result.contains(item.parentId) && result.add(item.id)) changed = true; } while (changed);
        return result;
    }

    private void deleteFlexSubtree(ThemeEditorModel.FlexContainer selected) {
        if (selected.parentId == null) { setStatus("Root flex container cannot be deleted"); return; }
        if (!changeStarted()) return; java.util.HashSet<String> ids = flexSubtreeIds(selected.id); java.util.HashSet<String> keyIds = new java.util.HashSet<>();
        for (ThemeEditorModel.FlexContainer item : model.flexContainers) if (ids.contains(item.id)) keyIds.addAll(item.keyIds);
        for (int i = model.keys.size() - 1; i >= 0; i--) if (keyIds.contains(model.keys.get(i).id)) model.keys.remove(i);
        for (int i = model.flexContainers.size() - 1; i >= 0; i--) if (ids.contains(model.flexContainers.get(i).id)) model.flexContainers.remove(i); model.selectedFlexContainerId = selected.parentId; canvas.setModel(model); notifyModelChanged("Deleted flex subtree");
    }

    private void moveFlexSibling(ThemeEditorModel.FlexContainer selected, int delta) {
        java.util.ArrayList<ThemeEditorModel.FlexContainer> siblings = new java.util.ArrayList<>(); for (ThemeEditorModel.FlexContainer item : model.flexContainers) if (java.util.Objects.equals(item.parentId, selected.parentId)) siblings.add(item);
        int index = siblings.indexOf(selected), target = index + delta; if (target < 0 || target >= siblings.size()) { setStatus("Flex container is already at the edge"); return; }
        if (!changeStarted()) return; ThemeEditorModel.FlexContainer other = siblings.get(target); int a = model.flexContainers.indexOf(selected), b = model.flexContainers.indexOf(other); java.util.Collections.swap(model.flexContainers, a, b); notifyModelChanged("Reordered flex containers");
    }

    private void selectAllKeys() {
        properties.commit(); model.selectedIds.clear();
        for (ThemeEditorModel.Key key : model.keys) model.selectedIds.add(key.id);
        canvas.setModel(model); refreshSelectionEditor(canvas.getSelectedKey()); setStatus("Selected all " + model.selectedIds.size() + " keys");
    }

    private void invertSelection() {
        properties.commit(); java.util.LinkedHashSet<String> inverted = new java.util.LinkedHashSet<>();
        for (ThemeEditorModel.Key key : model.keys) if (!model.selectedIds.contains(key.id)) inverted.add(key.id);
        model.selectedIds.clear(); model.selectedIds.addAll(inverted); canvas.setModel(model); refreshSelectionEditor(canvas.getSelectedKey());
        setStatus(model.selectedIds.isEmpty() ? "Selection cleared" : "Selected " + model.selectedIds.size() + " keys after inversion");
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
    private static String valueState(String value) { return value == null ? "mixed" : value.isEmpty() ? "unset" : "uniform"; }
    private static String numberState(Float value) { return value == null ? "mixed" : "uniform"; }

    private void showBatchEditor() {
        properties.commit(); java.util.List<ThemeEditorModel.Key> keys = selectedKeys();
        if (keys.isEmpty()) { setStatus("Select one or more keys first"); return; }
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(VERTICAL); fields.setPadding(24, 8, 24, 8);
        TextView impact = label("Impact: " + keys.size() + " selected keys. Check only properties to replace. Empty checked text clears that property.", 14); fields.addView(impact, new LayoutParams(-1, -2));
        String styleValue = commonString(keys, key -> key.keyStyle), clickValue = commonString(keys, key -> key.click), longValue = commonString(keys, key -> key.longClick), leftValue = commonString(keys, key -> key.swipeLeft), rightValue = commonString(keys, key -> key.swipeRight), upValue = commonString(keys, key -> key.swipeUp), downValue = commonString(keys, key -> key.swipeDown), popupValue = commonString(keys, key -> key.popup);
        Float widthValue = commonNumber(keys, key -> key.width), heightValue = commonNumber(keys, key -> key.height);
        BatchField style = batchField(fields, "Style", valueState(styleValue), styleValue == null ? "" : styleValue);
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
        TextView colors = label("Colors update the referenced style entities, not keyboard nodes. Background accepts #AARRGGBB or a project-relative resource path. Empty checked values clear the style override.", 12); fields.addView(colors, new LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(getContext()); scroll.addView(fields, new ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(getContext()).setTitle("Batch properties — " + keys.size() + " keys").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Review", (dialog, which) -> reviewBatch(keys, style, width, height, click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown, popup, background, textColor)).show();
    }

    private void reviewBatch(java.util.List<ThemeEditorModel.Key> keys, BatchField style, BatchField width, BatchField height, BatchField click, BatchField longClick, BatchField swipeLeft, BatchField swipeRight, BatchField swipeUp, BatchField swipeDown, BatchField popup, BatchField background, BatchField textColor) {
        int fields = 0; for (BatchField field : new BatchField[]{style, width, height, click, longClick, swipeLeft, swipeRight, swipeUp, swipeDown, popup, background, textColor}) if (field.apply.isChecked()) fields++;
        if (fields == 0) { setStatus("No batch properties selected"); return; }
        Float nextWidth = width.apply.isChecked() ? positiveNumber(width.value) : null, nextHeight = height.apply.isChecked() ? positiveNumber(height.value) : null;
        if ((width.apply.isChecked() && nextWidth == null) || (height.apply.isChecked() && nextHeight == null)) { setStatus("Batch width and height must be positive numbers"); return; }
        int count = keys.size(), fieldCount = fields; boolean keyFields = style.apply.isChecked() || width.apply.isChecked() || height.apply.isChecked() || click.apply.isChecked() || longClick.apply.isChecked() || swipeLeft.apply.isChecked() || swipeRight.apply.isChecked() || swipeUp.apply.isChecked() || swipeDown.apply.isChecked() || popup.apply.isChecked();
        String transaction = keyFields && (background.apply.isChecked() || textColor.apply.isChecked()) ? "Keyboard fields use one Undo; style entities use a separately confirmed project-file transaction." : keyFields ? "One Undo restores every affected key." : "Referenced style entities are changed in one rollback-safe project-file transaction.";
        new android.app.AlertDialog.Builder(getContext()).setTitle("Review batch edit").setMessage("This will replace " + fieldCount + " properties for " + count + " selected keys. " + transaction).setNegativeButton("Cancel", null).setPositiveButton("Continue", (dialog, which) -> {
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
                persistCurrentKeyMapPage(); refreshSelectionEditor(canvas.getSelectedKey()); notifyModelChanged("Batch-edited " + count + " keys as one undo step");
            }
            if ((background.apply.isChecked() || textColor.apply.isChecked()) && callbacks != null) callbacks.onBatchStyleEntities(copyKeys(keys), background.apply.isChecked() ? background.value.getText().toString().trim() : null, textColor.apply.isChecked() ? textColor.value.getText().toString().trim() : null);
        }).show();
    }

    private Float positiveNumber(android.widget.EditText input) {
        try { float value = Float.parseFloat(input.getText().toString()); return value > 0 && !Float.isNaN(value) && !Float.isInfinite(value) ? value : null; }
        catch (Exception ignored) { return null; }
    }

    private void showClipboardActions() {
        properties.commit(); String[] actions = {"Copy selected keys", "Copy selected row", "Copy selected Flex subtree", "Copy current symbol page", "Copy selected style", "Copy selected events", "Paste"};
        new android.app.AlertDialog.Builder(getContext()).setTitle("Internal editor clipboard").setItems(actions, (dialog, which) -> {
            if (which == 0) copySelectedKeys(); else if (which == 1) copySelectedRow(); else if (which == 2) copySelectedFlex(); else if (which == 3) copyCurrentPage(); else if (which == 4) copySelectedStyle(); else if (which == 5) copySelectedEvents(); else pasteClipboard();
        }).setNegativeButton("Cancel", null).show();
    }

    private void copySelectedKeys() {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("Select one or more keys first"); return; }
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.KEYS, clipboardScope, keys, null, null, null, null)); setStatus("Copied " + keys.size() + " keys to the private editor clipboard");
    }
    private void copySelectedRow() {
        int index = selectedRowIndex(); if (model.layoutMode != ThemeEditorModel.LayoutMode.ROWS || index < 0) { setStatus("Select a key in the row to copy"); return; }
        ThemeEditorModel.Row row = model.rows.get(index); java.util.ArrayList<ThemeEditorModel.Key> keys = new java.util.ArrayList<>(); for (ThemeEditorModel.Key key : model.keys) if (row.id.equals(key.ownerId)) keys.add(key);
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.ROW, clipboardScope, keys, row, null, null, null)); setStatus("Copied row with " + keys.size() + " keys");
    }
    private void copySelectedFlex() {
        ThemeEditorModel.FlexContainer root = selectedFlex(); if (model.layoutMode != ThemeEditorModel.LayoutMode.FLEX_BOX || root == null) { setStatus("Select a Flex container first"); return; }
        java.util.HashSet<String> ids = flexSubtreeIds(root.id); java.util.ArrayList<ThemeEditorModel.FlexContainer> containers = new java.util.ArrayList<>(); java.util.ArrayList<ThemeEditorModel.Key> keys = new java.util.ArrayList<>(); containers.add(root);
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) if (ids.contains(container.id) && container != root) containers.add(container);
        for (ThemeEditorModel.Key key : model.keys) if (ids.contains(key.ownerId)) keys.add(key);
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.FLEX_SUBTREE, clipboardScope, keys, null, containers, null, null)); setStatus("Copied Flex subtree with " + containers.size() + " containers and " + keys.size() + " keys");
    }
    private void copyCurrentPage() {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || model.keyMapPages.isEmpty()) { setStatus("This is not a key_maps keyboard"); return; }
        persistCurrentKeyMapPage(); ThemeEditorModel.KeyMapPage page = model.keyMapPages.get(model.selectedKeyMapPage);
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.KEY_MAP_PAGE, clipboardScope, null, null, null, page, null)); setStatus("Copied symbol page " + page.name);
    }
    private void copySelectedStyle() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) { setStatus("Select a key first"); return; }
        if (callbacks == null) { setStatus("Style entity provider is unavailable"); return; }
        callbacks.onCopyStyleEntity(key.copy());
    }
    private void copySelectedEvents() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) { setStatus("Select a key first"); return; }
        if (key.hasNonLiteralEventSource) { setStatus("This key contains inline, full-key, or Raw Lua event sources; use Event... per slot instead of lossy clipboard copy"); return; }
        ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.EVENTS, clipboardScope, java.util.Collections.singletonList(key), null, null, null, null)); setStatus("Copied all literal event and state-replacement fields; no event was executed");
    }

    private void pasteClipboard() {
        ThemeEditorClipboard.Payload payload = ThemeEditorClipboard.get(); if (payload == null) { setStatus("Internal clipboard is empty"); return; }
        boolean crossScope = !java.util.Objects.equals(payload.projectIdentity, clipboardScope);
        String dependencies = dependencySummary(payload);
        int targetCount = selectedKeys().size(); String warning = "Paste " + payload.type + " into the current " + model.layoutMode + " layout?" + (payload.type == ThemeEditorClipboard.Type.EVENTS ? "\nAffected target keys: " + targetCount : "");
        if (crossScope) warning += "\nCross-project copy: new node IDs will be generated. Source URIs and paths are not retained.";
        if (!dependencies.isEmpty()) warning += "\nDependencies are not auto-mapped: " + dependencies;
        new android.app.AlertDialog.Builder(getContext()).setTitle("Paste target confirmation").setMessage(warning).setNegativeButton("Cancel", null).setPositiveButton("Paste", (dialog, which) -> applyClipboard(payload, crossScope)).show();
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
        else if (payload.type == ThemeEditorClipboard.Type.STYLE_ENTITY) { if (callbacks == null) setStatus("Style entity provider is unavailable"); else callbacks.onPasteStyleEntity(copyKeys(selectedKeys())); }
        else pasteEvents(payload, crossScope);
    }

    private void pasteKeys(java.util.List<ThemeEditorModel.Key> source, boolean crossScope) {
        if (model.layoutMode == ThemeEditorModel.LayoutMode.NONE || source.isEmpty()) { setStatus("Clipboard keys cannot be pasted into this file"); return; }
        if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX && selectedFlex() == null) { setStatus("Add or select a Flex container before pasting keys"); return; }
        if (!changeStarted()) return;
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS && model.rows.isEmpty()) model.rows.add(new ThemeEditorModel.Row("pasted_row_" + System.nanoTime(), 18));
        if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS && model.keyMapPages.isEmpty()) { model.keyMapPages.add(new ThemeEditorModel.KeyMapPage("pasted_page_" + System.nanoTime(), "Pasted")); model.selectedKeyMapPage = 0; }
        String owner = pasteOwner(); java.util.ArrayList<ThemeEditorModel.Key> pasted = new java.util.ArrayList<>();
        int index = 0; for (ThemeEditorModel.Key original : source) { ThemeEditorModel.Key key = detachedKey(original, "pasted_key_" + System.nanoTime() + "_" + index++); if (crossScope) key.keyStyle = ""; key.ownerId = owner; key.x = Math.min(100 - key.width, key.x + 2); key.y = Math.min(80 - key.height, key.y + 2); model.keys.add(key); pasted.add(key); if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) { ThemeEditorModel.FlexContainer container = selectedFlex(); if (container != null) container.keyIds.add(key.id); } }
        selectPasted(pasted); persistCurrentKeyMapPage(); notifyModelChanged("Pasted " + pasted.size() + " keys" + (crossScope ? "; review listed dependencies" : ""));
    }
    private String pasteOwner() {
        ThemeEditorModel.Key selected = canvas.getSelectedKey();
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) return selected != null && !selected.ownerId.isEmpty() ? selected.ownerId : model.rows.isEmpty() ? "" : model.rows.get(model.rows.size() - 1).id;
        if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) { ThemeEditorModel.FlexContainer flex = selectedFlex(); return flex == null ? "" : flex.id; }
        if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS && !model.keyMapPages.isEmpty()) return model.keyMapPages.get(model.selectedKeyMapPage).id;
        return "";
    }
    private void pasteRow(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.ROWS || payload.row == null) { setStatus("A row can only be pasted into a rows keyboard"); return; }
        if (!changeStarted()) return; ThemeEditorModel.Row row = payload.row.copy(); row.id = "pasted_row_" + System.nanoTime(); row.sourcePath = ""; row.sourceHeight = Float.NaN; row.sourceWidth = Float.NaN; int insert = selectedRowIndex(); insert = insert < 0 ? model.rows.size() : insert + 1; model.rows.add(insert, row);
        java.util.ArrayList<ThemeEditorModel.Key> pasted = new java.util.ArrayList<>(); int i = 0; for (ThemeEditorModel.Key original : payload.keys) { ThemeEditorModel.Key key = detachedKey(original, row.id + "_key_" + i++); if (crossScope) key.keyStyle = ""; key.ownerId = row.id; model.keys.add(key); pasted.add(key); }
        layoutRows(); selectPasted(pasted); notifyModelChanged("Pasted row with " + pasted.size() + " keys" + (crossScope ? "; review listed dependencies" : ""));
    }
    private void pasteFlex(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.FLEX_BOX || payload.containers.isEmpty()) { setStatus("A Flex subtree can only be pasted into a flex_box keyboard"); return; }
        ThemeEditorModel.FlexContainer target = selectedFlex(); if (target == null || !changeStarted()) return; java.util.LinkedHashMap<String, String> ids = new java.util.LinkedHashMap<>(); long seed = System.nanoTime();
        for (ThemeEditorModel.FlexContainer original : payload.containers) ids.put(original.id, "pasted_flex_" + seed + "_" + ids.size());
        String sourceRoot = payload.containers.get(0).id; java.util.ArrayList<ThemeEditorModel.FlexContainer> pastedContainers = new java.util.ArrayList<>();
        for (ThemeEditorModel.FlexContainer original : payload.containers) { ThemeEditorModel.FlexContainer copy = original.copy(); copy.id = ids.get(original.id); copy.parentId = original.id.equals(sourceRoot) ? target.id : ids.get(original.parentId); copy.sourcePath = ""; if (crossScope) copy.style = ""; copy.keyIds.clear(); pastedContainers.add(copy); }
        java.util.ArrayList<ThemeEditorModel.Key> pastedKeys = new java.util.ArrayList<>(); int index = 0; for (ThemeEditorModel.Key original : payload.keys) { ThemeEditorModel.Key key = detachedKey(original, "pasted_flex_key_" + seed + "_" + index++); if (crossScope) key.keyStyle = ""; key.ownerId = ids.get(original.ownerId); if (key.ownerId == null) key.ownerId = pastedContainers.get(0).id; for (ThemeEditorModel.FlexContainer container : pastedContainers) if (container.id.equals(key.ownerId)) container.keyIds.add(key.id); model.keys.add(key); pastedKeys.add(key); }
        int insert = lastDescendantIndex(target.id) + 1; model.flexContainers.addAll(Math.min(insert, model.flexContainers.size()), pastedContainers); model.selectedFlexContainerId = pastedContainers.get(0).id; selectPasted(pastedKeys); notifyModelChanged("Pasted Flex subtree" + (crossScope ? "; review listed dependencies" : ""));
    }
    private void pastePage(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        if (model.layoutMode != ThemeEditorModel.LayoutMode.KEY_MAPS || payload.page == null) { setStatus("A symbol page can only be pasted into a key_maps keyboard"); return; }
        if (!changeStarted()) return; persistCurrentKeyMapPage(); ThemeEditorModel.KeyMapPage page = payload.page.copy(); page.id = "pasted_page_" + System.nanoTime(); page.name = page.name + " copy"; page.sourcePath = "";
        java.util.ArrayList<ThemeEditorModel.Key> remapped = new java.util.ArrayList<>(); int index = 0; for (ThemeEditorModel.Key original : page.keys) { ThemeEditorModel.Key key = detachedKey(original, page.id + "_key_" + index++); if (crossScope) key.keyStyle = ""; key.ownerId = page.id; remapped.add(key); } page.keys.clear(); page.keys.addAll(remapped);
        model.keyMapPages.add(model.selectedKeyMapPage + 1, page); model.selectedKeyMapPage++; model.keys.clear(); for (ThemeEditorModel.Key key : page.keys) model.keys.add(key.copy()); selectPasted(model.keys); notifyModelChanged("Pasted symbol page" + (crossScope ? "; review listed dependencies" : ""));
    }
    private void pasteStyle(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("Select target keys first"); return; }
        if (crossScope && !payload.keyStyle.isEmpty()) { setStatus("Cross-project style was not pasted because style IDs are not auto-mapped"); return; }
        if (!changeStarted()) return; for (ThemeEditorModel.Key key : keys) key.keyStyle = payload.keyStyle; persistCurrentKeyMapPage(); refreshSelectionEditor(canvas.getSelectedKey()); notifyModelChanged("Pasted style onto " + keys.size() + " keys as one undo step");
    }
    private void pasteEvents(ThemeEditorClipboard.Payload payload, boolean crossScope) {
        java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty() || payload.keys.isEmpty()) { setStatus("Select target keys first"); return; }
        ThemeEditorModel.Key source = payload.keys.get(0); if (!changeStarted()) return;
        for (ThemeEditorModel.Key key : keys) { key.click = source.click; key.longClick = source.longClick; key.swipeLeft = source.swipeLeft; key.swipeRight = source.swipeRight; key.swipeUp = source.swipeUp; key.swipeDown = source.swipeDown; key.combo = source.combo; key.composing = source.composing; key.hasMenu = source.hasMenu; key.paging = source.paging; key.ascii = source.ascii; key.popup = source.popup; key.popupArray = source.popupArray; }
        persistCurrentKeyMapPage(); refreshSelectionEditor(canvas.getSelectedKey()); notifyModelChanged("Pasted literal events onto " + keys.size() + " keys" + (crossScope ? "; preset/resource names were not mapped" : ""));
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
        if (model.layoutMode != ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) { setStatus("This is not an absolute keys keyboard"); return; }
        String[] actions = {"Snap to 2-unit grid", "Align left", "Align right", "Align top", "Align bottom", "Distribute horizontally", "Distribute vertically", "Toggle lock"};
        new android.app.AlertDialog.Builder(getContext()).setTitle("Absolute key tools").setItems(actions, (dialog, which) -> {
            java.util.List<ThemeEditorModel.Key> keys = selectedKeys(); if (keys.isEmpty()) { setStatus("Select one or more keys first"); return; }
            if (which >= 1 && which <= 6 && keys.size() < 2) { setStatus("Select at least two keys"); return; }
            if ((which == 5 || which == 6) && unlockedCount(keys) < 3) { setStatus("At least three unlocked keys are required for distribution"); return; }
            if (which == 7) { if (!canEdit()) return; for (ThemeEditorModel.Key key : keys) key.editorLocked = !key.editorLocked; canvas.setModel(model); setStatus("Toggled editor-only lock; theme source unchanged"); return; }
            if (!changeStarted()) return;
            if (which == 0) for (ThemeEditorModel.Key key : keys) if (!key.editorLocked) { key.x = snap(key.x, 2); key.y = snap(key.y, 2); key.width = Math.max(2, snap(key.width, 2)); key.height = Math.max(2, snap(key.height, 2)); clampAbsolute(key); }
            else if (which == 1) alignAbsolute(keys, 0); else if (which == 2) alignAbsolute(keys, 1); else if (which == 3) alignAbsolute(keys, 2); else if (which == 4) alignAbsolute(keys, 3);
            else if (which == 5) distributeAbsolute(keys, true); else distributeAbsolute(keys, false);
            notifyModelChanged("Applied absolute key tool");
        }).setNegativeButton("Cancel", null).show();
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
        notifyModelChanged(model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS ? "Moved absolute key" : "Reordered key");
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
        if (selected == null) { setStatus("Select a key first"); return; }
        model.selectedIds.clear();
        String row = selected.ownerId;
        for (ThemeEditorModel.Key key : model.keys) if (java.util.Objects.equals(key.ownerId, row)) model.selectedIds.add(key.id);
        canvas.setModel(model); refreshSelectionEditor(selected); setStatus("Selected row with " + model.selectedIds.size() + " keys");
    }

    private void addKey() {
        properties.commit();
        if (model.layoutMode == ThemeEditorModel.LayoutMode.NONE) { setStatus("This file has no editable keyboard layout"); return; }
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
        persistCurrentKeyMapPage(); selectOnly(key); notifyModelChanged("Added key");
    }

    private void duplicateSelected() {
        properties.commit();
        ThemeEditorModel.Key selected = canvas.getSelectedKey(); if (selected == null) { setStatus("Select a key first"); return; }
        if (!changeStarted()) return; ThemeEditorModel.Key copy = selected.copy(); copy.id = selected.id + "_copy_" + System.nanoTime(); copy.x = Math.min(100 - copy.width, copy.x + 2); model.keys.add(copy);
        if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) for (ThemeEditorModel.FlexContainer container : model.flexContainers) { int index = container.keyIds.indexOf(selected.id); if (index >= 0) { container.keyIds.add(index + 1, copy.id); break; } }
        persistCurrentKeyMapPage(); selectOnly(copy); notifyModelChanged("Copied key");
    }

    private void deleteSelected() {
        properties.commit();
        ThemeEditorModel.Key selected = canvas.getSelectedKey(); if (selected == null && model.selectedIds.isEmpty()) { setStatus("Select a key first"); return; }
        if (!changeStarted()) return; java.util.HashSet<String> deleting = new java.util.HashSet<>(model.selectedIds); if (selected != null) deleting.add(selected.id);
        for (int i = model.keys.size() - 1; i >= 0; i--) if (deleting.contains(model.keys.get(i).id)) model.keys.remove(i);
        for (ThemeEditorModel.FlexContainer container : model.flexContainers) for (int i = container.keyIds.size() - 1; i >= 0; i--) if (deleting.contains(container.keyIds.get(i))) container.keyIds.remove(i);
        model.selectedIds.clear(); persistCurrentKeyMapPage(); canvas.setSelectedKey(null); canvas.setModel(model); properties.bind(null); notifyModelChanged("Deleted selected keys");
    }

    private void manageRows() {
        properties.commit();
        if (model.layoutMode != ThemeEditorModel.LayoutMode.ROWS) { setStatus("This is not a rows keyboard"); return; }
        int selected = selectedRowIndex(); String[] actions = {"Add row", "Duplicate selected row", "Delete selected row", "Move row up", "Move row down", "Set row height", "Set default key width", "Distribute keys evenly"};
        new android.app.AlertDialog.Builder(getContext()).setTitle(selected < 0 ? "Rows" : "Row " + (selected + 1)).setItems(actions, (dialog, which) -> {
            if (which == 0) addRow(); else if (selected < 0) setStatus("Select a key in the target row first"); else if (which == 1) duplicateRow(selected); else if (which == 2) deleteRow(selected); else if (which == 3) moveRow(selected, -1); else if (which == 4) moveRow(selected, 1); else if (which == 5) editRowHeight(selected); else if (which == 6) editRowWidth(selected); else distributeRow(selected);
        }).setNegativeButton("Cancel", null).show();
    }

    private int selectedRowIndex() {
        ThemeEditorModel.Key key = canvas.getSelectedKey(); if (key == null) return -1;
        for (int i = 0; i < model.rows.size(); i++) if (model.rows.get(i).id.equals(key.ownerId)) return i;
        return -1;
    }

    private void addRow() { if (!changeStarted()) return; int row = model.rows.size(); model.rows.add(new ThemeEditorModel.Row("row_new_" + System.nanoTime(), 18)); reindexRows(); notifyModelChanged("Added row"); }
    private void duplicateRow(int row) {
        if (!changeStarted()) return; ThemeEditorModel.Row copyRow = model.rows.get(row).copy(); copyRow.id = "row_copy_" + System.nanoTime(); model.rows.add(row + 1, copyRow);
        java.util.ArrayList<ThemeEditorModel.Key> copies = new java.util.ArrayList<>(); int copyIndex = 0; for (ThemeEditorModel.Key key : model.keys) if (model.rows.get(row).id.equals(key.ownerId)) { ThemeEditorModel.Key copy = key.copy(); copy.id = copyRow.id + "_key_" + copyIndex++; copy.ownerId = copyRow.id; copies.add(copy); }
        model.keys.addAll(copies); reindexRows(); notifyModelChanged("Duplicated row");
    }
    private void deleteRow(int row) {
        if (model.rows.size() <= 1) { setStatus("At least one row is required"); return; } if (!changeStarted()) return; String owner = model.rows.get(row).id; model.rows.remove(row);
        for (int i = model.keys.size() - 1; i >= 0; i--) if (owner.equals(model.keys.get(i).ownerId)) model.keys.remove(i); model.selectedIds.clear(); reindexRows(); canvas.setSelectedKey(null); properties.bind(null); notifyModelChanged("Deleted row");
    }
    private void moveRow(int row, int delta) {
        int target = row + delta; if (target < 0 || target >= model.rows.size()) { setStatus("Row is already at the edge"); return; }
        if (!changeStarted()) return; java.util.Collections.swap(model.rows, row, target); layoutRows(); notifyModelChanged("Reordered rows");
    }
    private void editRowHeight(int row) {
        LinearLayout fields = new LinearLayout(getContext()); android.widget.EditText height = dialogField(fields, "Row height percent", String.valueOf(model.rows.get(row).height));
        new android.app.AlertDialog.Builder(getContext()).setTitle("Row height").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { if (!changeStarted()) return; model.rows.get(row).height = Math.max(1, parseFloat(height, model.rows.get(row).height)); layoutRows(); notifyModelChanged("Updated row height"); }).show();
    }
    private void editRowWidth(int row) {
        LinearLayout fields = new LinearLayout(getContext()); android.widget.EditText width = dialogField(fields, "Default key width; -1 inherits", String.valueOf(model.rows.get(row).width));
        new android.app.AlertDialog.Builder(getContext()).setTitle("Row default width").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { if (!changeStarted()) return; model.rows.get(row).width = parseFloat(width, model.rows.get(row).width); notifyModelChanged("Updated row default width"); }).show();
    }
    private void distributeRow(int row) {
        java.util.ArrayList<ThemeEditorModel.Key> keys = new java.util.ArrayList<>(); String owner = model.rows.get(row).id; for (ThemeEditorModel.Key key : model.keys) if (owner.equals(key.ownerId)) keys.add(key);
        if (keys.isEmpty()) { setStatus("Row has no keys"); return; } if (!changeStarted()) return; float width = 100f / keys.size(); for (ThemeEditorModel.Key key : keys) key.width = width; model.rows.get(row).width = width; layoutRows(); notifyModelChanged("Distributed row keys evenly");
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

    private void notifyModelChanged(String message) { canvas.setModel(model); refreshSelectionEditor(canvas.getSelectedKey()); if (callbacks != null) callbacks.onModelChanged(model.copy()); setStatus(message); }

    private TextView label(String text, float size) { TextView v = new TextView(getContext()); v.setText(text); v.setTextSize(size); v.setTextColor(0xff263238); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private Button action(String text, String description) { Button b = new Button(getContext()); b.setText(text); b.setAllCaps(false); b.setContentDescription(description); b.setMinWidth(0); b.setPadding(10, 0, 10, 0); return b; }
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
        return "No static panel source supplied; showing preview defaults";
    }
    void storeStyleEntityClipboard(ThemeStyleEntities.Snapshot snapshot) { ThemeEditorClipboard.put(new ThemeEditorClipboard.Payload(ThemeEditorClipboard.Type.STYLE_ENTITY, clipboardScope, null, null, null, null, snapshot == null ? "" : snapshot.getId(), snapshot)); setStatus(snapshot == null ? "Style entity copy failed" : "Copied complete style entity " + snapshot.getId() + " to the private editor clipboard"); }
    ThemeEditorClipboard.Payload styleEntityClipboard() { ThemeEditorClipboard.Payload value = ThemeEditorClipboard.get(); return value != null && value.type == ThemeEditorClipboard.Type.STYLE_ENTITY ? value : null; }
    boolean isCrossProjectClipboard(ThemeEditorClipboard.Payload value) { return value != null && !java.util.Objects.equals(value.projectIdentity, clipboardScope); }
    void applyStyleEntityReference(java.util.List<ThemeEditorModel.Key> keys, String styleId) { if (keys == null || keys.isEmpty()) { setStatus("Style entity created; no target keys were selected"); return; } if (!changeStarted()) return; int count = 0; for (ThemeEditorModel.Key snapshot : keys) { ThemeEditorModel.Key key = model.find(snapshot.id); if (key != null) { key.keyStyle = styleId; count++; } } persistCurrentKeyMapPage(); refreshSelectionEditor(canvas.getSelectedKey()); notifyModelChanged("Applied pasted style entity " + styleId + " to " + count + " keys as one undo step"); }
    public void setReadOnly(boolean value) { readOnly = value; canvas.setReadOnly(value); properties.setReadOnly(value); if (value) setStatus("Read-only session"); }
    private boolean canEdit() { if (!readOnly) return true; setStatus("Read-only: another session owns this project"); return false; }
    public void setModel(ThemeEditorModel value) { model = value == null ? ThemeEditorModel.sample() : value.copy(); applyPanelPreviewSource(model); undo.clear(); redo.clear(); canvas.setModel(model); properties.setLayoutMode(model.layoutMode); properties.bind(null); setStatus("Ready"); }
    public void setModelKeepingHistory(ThemeEditorModel value) { if (value == null) return; String selectedId = canvas.getSelectedKey() == null ? null : canvas.getSelectedKey().id; java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>(model.selectedIds); model = value.copy(); applyPanelPreviewSource(model); model.selectedIds.clear(); for (String id : selected) if (model.find(id) != null) model.selectedIds.add(id); canvas.setModel(model); ThemeEditorModel.Key key = selectedId == null ? null : model.find(selectedId); canvas.setSelectedKey(key); properties.setLayoutMode(model.layoutMode); refreshSelectionEditor(key); }
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
        canvas.invalidate();
    }
    public boolean replaceModelAsAtomic(ThemeEditorModel value, String message) { if (value == null) return false; properties.commit(); if (!changeStarted()) return false; model = value.copy(); applyPanelPreviewSource(model); model.selectedIds.clear(); canvas.setModel(model); canvas.setSelectedKey(null); properties.setLayoutMode(model.layoutMode); properties.bind(null); if (callbacks != null) callbacks.onModelChanged(model.copy()); setStatus(message); return true; }
    public ThemeEditorModel getModel() { properties.commit(); persistCurrentKeyMapPage(); return model.copy(); }
    private boolean changeStarted() { if (!canEdit()) return false; if (applying) return true; if (undo.isEmpty() || !same(undo.peek(), model)) undo.push(model.copy()); redo.clear(); return true; }
    private boolean same(ThemeEditorModel a, ThemeEditorModel b) {
        if (a.layoutMode != b.layoutMode || a.selectedKeyMapPage != b.selectedKeyMapPage || !java.util.Objects.equals(a.selectedFlexContainerId, b.selectedFlexContainerId) || a.flexContainers.size() != b.flexContainers.size() || a.keyMapPages.size() != b.keyMapPages.size() || a.rows.size() != b.rows.size() || a.backgroundColor != b.backgroundColor || a.keys.size() != b.keys.size()) return false;
        for (int i = 0; i < a.rows.size(); i++) { ThemeEditorModel.Row x = a.rows.get(i), y = b.rows.get(i); if (!x.id.equals(y.id) || x.height != y.height || x.sourceHeight != y.sourceHeight || x.width != y.width) return false; }
        for (int i = 0; i < a.flexContainers.size(); i++) { ThemeEditorModel.FlexContainer x = a.flexContainers.get(i), y = b.flexContainers.get(i); if (!x.id.equals(y.id) || !java.util.Objects.equals(x.parentId, y.parentId) || !x.direction.equals(y.direction) || !x.style.equals(y.style) || x.width != y.width || x.height != y.height || x.grow != y.grow || !x.keyIds.equals(y.keyIds)) return false; }
        for (int i = 0; i < a.keyMapPages.size(); i++) { ThemeEditorModel.KeyMapPage x = a.keyMapPages.get(i), y = b.keyMapPages.get(i); if (!x.id.equals(y.id) || !x.name.equals(y.name) || x.keys.size() != y.keys.size()) return false; }
        for (int i = 0; i < a.keys.size(); i++) { ThemeEditorModel.Key x = a.keys.get(i), y = b.keys.get(i); if (!x.id.equals(y.id) || !x.ownerId.equals(y.ownerId) || x.x != y.x || x.y != y.y || x.width != y.width || x.height != y.height || !x.label.equals(y.label) || !x.click.equals(y.click) || !x.longClick.equals(y.longClick) || !x.swipeLeft.equals(y.swipeLeft) || !x.swipeRight.equals(y.swipeRight) || !x.swipeUp.equals(y.swipeUp) || !x.swipeDown.equals(y.swipeDown) || !x.keyStyle.equals(y.keyStyle) || !x.popup.equals(y.popup) || x.editorLocked != y.editorLocked) return false; }
        return true;
    }
    private void restore(ThemeEditorModel value) { applying = true; String selectedId = canvas.getSelectedKey() == null ? null : canvas.getSelectedKey().id; model = value.copy(); applyPanelPreviewSource(model); canvas.setModel(model); ThemeEditorModel.Key restored = selectedId == null ? null : model.find(selectedId); canvas.setSelectedKey(restored); refreshSelectionEditor(restored); applying = false; }
    public void undo() { if (!canEdit()) return; properties.commit(); if (undo.isEmpty()) { setStatus("Nothing to undo"); return; } redo.push(model.copy()); restore(undo.pop()); if (callbacks != null) callbacks.onUndo(model.copy()); setStatus("Undone"); }
    public void redo() { if (!canEdit()) return; properties.commit(); if (redo.isEmpty()) { setStatus("Nothing to redo"); return; } undo.push(model.copy()); restore(redo.pop()); if (callbacks != null) callbacks.onRedo(model.copy()); setStatus("Redone"); }
    public void setStatus(String message) { status.setText(message); }
}
