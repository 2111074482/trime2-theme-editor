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

    /**
     * Opens the style properties page for the selected property.
     *
     * <p>The default implementation keeps existing callback implementations source-compatible.
     */
    default void onOpenStyleProperties(ThemeEditorModel.Key key) {}

    /** Opens the key event configuration page for the selected property. */
    default void onOpenKeyEvents(ThemeEditorModel.Key key) {}

    /** Opens the resource browser for the selected property. */
    default void onOpenResources(ThemeEditorModel.Key key) {}

    /** Opens the Lua source editor from the advanced page. */
    default void onOpenLuaSource() {}

    /** Persists a stable inspector page id: basic, events, states or resources. */
    default void onInspectorPageChanged(String pageId) {}

    /** Records preview-only state without dirtying the theme model. */
    default void onPreviewStateChanged() {}
}
