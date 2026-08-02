/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

public interface ThemeEditorCallbacks {
    void onSave(ThemeEditorModel model);
    void onUndo(ThemeEditorModel model);
    void onRedo(ThemeEditorModel model);
    void onSelectionChanged(ThemeEditorModel.Key key);
}
