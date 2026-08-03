/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Process-local clipboard. It never serializes URIs, private paths, or project repositories. */
final class ThemeEditorClipboard {
    enum Type { KEYS, ROW, FLEX_SUBTREE, KEY_MAP_PAGE, KEY_STYLE, STYLE_ENTITY, EVENTS }

    static final class Payload {
        final Type type;
        final String projectIdentity;
        final List<ThemeEditorModel.Key> keys;
        final ThemeEditorModel.Row row;
        final List<ThemeEditorModel.FlexContainer> containers;
        final ThemeEditorModel.KeyMapPage page;
        final String keyStyle;
        final ThemeStyleEntities.Snapshot styleEntity;

        Payload(
                Type type,
                String projectIdentity,
                List<ThemeEditorModel.Key> keys,
                ThemeEditorModel.Row row,
                List<ThemeEditorModel.FlexContainer> containers,
                ThemeEditorModel.KeyMapPage page,
                String keyStyle
        ) { this(type, projectIdentity, keys, row, containers, page, keyStyle, null); }

        Payload(
                Type type,
                String projectIdentity,
                List<ThemeEditorModel.Key> keys,
                ThemeEditorModel.Row row,
                List<ThemeEditorModel.FlexContainer> containers,
                ThemeEditorModel.KeyMapPage page,
                String keyStyle,
                ThemeStyleEntities.Snapshot styleEntity
        ) {
            this.type = type;
            this.projectIdentity = projectIdentity == null ? "" : projectIdentity;
            this.keys = copyKeys(keys);
            this.row = row == null ? null : row.copy();
            this.containers = copyContainers(containers);
            this.page = page == null ? null : page.copy();
            this.keyStyle = keyStyle == null ? "" : keyStyle;
            this.styleEntity = copyStyleEntity(styleEntity);
        }
    }

    private static Payload payload;

    private ThemeEditorClipboard() { }

    static synchronized void put(Payload value) { payload = value; }

    static synchronized Payload get() {
        if (payload == null) return null;
        return new Payload(payload.type, payload.projectIdentity, payload.keys, payload.row, payload.containers, payload.page, payload.keyStyle, payload.styleEntity);
    }

    private static ThemeStyleEntities.Snapshot copyStyleEntity(ThemeStyleEntities.Snapshot source) {
        if (source == null) return null;
        return new ThemeStyleEntities.Snapshot(source.getId(), source.getFragment(), source.getCloneParent(), new ArrayList<>(source.getReferencedResources()));
    }

    private static List<ThemeEditorModel.Key> copyKeys(List<ThemeEditorModel.Key> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        ArrayList<ThemeEditorModel.Key> result = new ArrayList<>();
        for (ThemeEditorModel.Key key : source) result.add(key.copy());
        return result;
    }

    private static List<ThemeEditorModel.FlexContainer> copyContainers(List<ThemeEditorModel.FlexContainer> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        ArrayList<ThemeEditorModel.FlexContainer> result = new ArrayList<>();
        for (ThemeEditorModel.FlexContainer container : source) result.add(container.copy());
        return result;
    }
}
