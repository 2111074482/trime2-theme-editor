/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/** Minimal UI-owned theme contract; adapt core data at the integration boundary. */
public final class ThemeEditorModel {
    public static final class Key {
        public String id;
        public String label;
        public float x, y, width, height;
        public int fillColor = Color.rgb(245, 245, 245);
        public int textColor = Color.rgb(30, 30, 30);

        public Key(String id, String label, float x, float y, float width, float height) {
            this.id = id; this.label = label; this.x = x; this.y = y;
            this.width = width; this.height = height;
        }
        public Key copy() {
            Key k = new Key(id, label, x, y, width, height);
            k.fillColor = fillColor; k.textColor = textColor; return k;
        }
    }

    public final List<Key> keys = new ArrayList<>();
    public int backgroundColor = Color.rgb(224, 228, 232);

    public ThemeEditorModel copy() {
        ThemeEditorModel result = new ThemeEditorModel();
        result.backgroundColor = backgroundColor;
        for (Key key : keys) result.keys.add(key.copy());
        return result;
    }

    public Key find(String id) {
        for (Key key : keys) if (key.id.equals(id)) return key;
        return null;
    }

    public static ThemeEditorModel sample() {
        ThemeEditorModel model = new ThemeEditorModel();
        String[][] rows = {{"QWERTYUIOP"}, {"ASDFGHJKL"}, {"ZXCVBNM"}};
        for (int row = 0; row < rows.length; row++) {
            String letters = rows[row][0];
            float offset = row == 0 ? 0 : row == 1 ? 5 : 10;
            for (int col = 0; col < letters.length(); col++) {
                model.keys.add(new Key("key_" + letters.charAt(col),
                        String.valueOf(letters.charAt(col)), offset + col * 10.1f,
                        8 + row * 18, 9.5f, 16));
            }
        }
        model.keys.add(new Key("key_space", "space", 25, 62, 50, 15));
        return model;
    }
}
