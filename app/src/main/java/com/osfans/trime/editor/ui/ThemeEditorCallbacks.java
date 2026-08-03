/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

public interface ThemeEditorCallbacks {
    void onSave(ThemeEditorModel model);
    void onModelChanged(ThemeEditorModel model);
    void onUndo(ThemeEditorModel model);
    void onRedo(ThemeEditorModel model);
    void onSelectionChanged(ThemeEditorModel.Key key);
    void onBatchStyleEntities(java.util.List<ThemeEditorModel.Key> keys, String background, String textColor);
    void onCopyStyleEntity(ThemeEditorModel.Key key);
    void onPasteStyleEntity(java.util.List<ThemeEditorModel.Key> keys);
    void onManageKeyEvents(ThemeEditorModel.Key key);
}
