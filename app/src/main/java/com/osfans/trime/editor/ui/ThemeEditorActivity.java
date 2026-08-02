/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.osfans.trime.editor.core.ThemeEditor;
import com.osfans.trime.editor.core.ThemeValue;
import com.osfans.trime.editor.project.FileThemeProjectRepository;
import com.osfans.trime.editor.project.ThemeProjectRepository;
import com.osfans.trime.editor.project.UriThemeProjectRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Native entry point for the first integrated theme editor milestone. */
public class ThemeEditorActivity extends Activity {
    public static final String EXTRA_THEME = "com.osfans.trime.editor.ui.THEME";
    private static final int REQUEST_OPEN = 10;
    private static final int REQUEST_SAVE = 11;

    private ThemeEditorWorkspace workspace;
    private ThemeEditor editor;
    private ThemeProjectRepository repository;
    private Uri currentUri;
    private boolean layoutEditable;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        workspace = new ThemeEditorWorkspace(this);
        setContentView(workspace);
        workspace.setCallbacks(new ThemeEditorCallbacks() {
            @Override public void onSave(ThemeEditorModel model) { saveModel(model); }
            @Override public void onUndo(ThemeEditorModel model) { }
            @Override public void onRedo(ThemeEditorModel model) { }
            @Override public void onSelectionChanged(ThemeEditorModel.Key key) { }
        });
        Uri data = getIntent().getData();
        if (data != null) loadUri(data);
        else if (getIntent().hasExtra(EXTRA_THEME)) loadFile(new File(getIntent().getStringExtra(EXTRA_THEME)));
        else editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
    }

    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add("Open Lua").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if ("Open Lua".contentEquals(item.getTitle())) {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("text/*").addCategory(Intent.CATEGORY_OPENABLE), REQUEST_OPEN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadFile(File file) {
        if (file == null || !file.isFile()) return;
        repository = new FileThemeProjectRepository(file);
        currentUri = Uri.fromFile(file);
        loadRepository();
    }

    private void loadUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        currentUri = uri;
        repository = new UriThemeProjectRepository(uri,
                getContentResolver()::openInputStream,
                getContentResolver()::openOutputStream);
        loadRepository();
    }

    private void loadRepository() {
        try {
            editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            com.osfans.trime.editor.core.ParseResult parsed = editor.load(repository);
            layoutEditable = editor.getDocument().get("rows") != null;
            if (layoutEditable) workspace.setModel(toUiModel(editor.getDocument()));
            workspace.setStatus("Loaded " + currentUri + " (" + parsed.getDiagnostics().size() + " diagnostics)" + (layoutEditable ? "" : "; choose a keyboard Lua file to edit"));
        } catch (Exception error) {
            workspace.setStatus("Load failed: " + error.getMessage());
            Toast.makeText(this, "Unable to load theme", Toast.LENGTH_LONG).show();
        }
    }

    private void saveModel(ThemeEditorModel model) {
        if (repository == null) {
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("text/x-lua").putExtra(Intent.EXTRA_TITLE, "main.lua"), REQUEST_SAVE);
            return;
        }
        try {
            if (editor == null) editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            if (!layoutEditable || editor.getDocument().get("rows") == null) {
                workspace.setStatus("Main theme metadata is read-only; open a keyboard Lua file");
                Toast.makeText(this, "Open a keyboard Lua file before editing", Toast.LENGTH_LONG).show();
                return;
            }
            editor.set("rows", new ThemeValue.RawLuaNode(rowsSource(model), 0));
            editor.save(repository);
            workspace.setStatus("Saved");
        } catch (Exception error) {
            workspace.setStatus("Save failed: " + error.getMessage());
            Toast.makeText(this, "Unable to save theme", Toast.LENGTH_LONG).show();
        }
    }

    private ThemeEditorModel toUiModel(com.osfans.trime.editor.core.ThemeDocument document) {
        ThemeEditorModel model = new ThemeEditorModel();
        com.osfans.trime.editor.core.ThemeValue rows = document.get("rows");
        if (!(rows instanceof com.osfans.trime.editor.core.ThemeValue.LuaTable)) return model;
        com.osfans.trime.editor.core.ThemeValue.LuaTable rowTable = (com.osfans.trime.editor.core.ThemeValue.LuaTable) rows;
        int rowIndex = 0;
        for (com.osfans.trime.editor.core.ThemeValue rowValue : rowTable.getFields().values()) {
            if (!(rowValue instanceof com.osfans.trime.editor.core.ThemeValue.LuaTable)) continue;
            com.osfans.trime.editor.core.ThemeValue.LuaTable row = (com.osfans.trime.editor.core.ThemeValue.LuaTable) rowValue;
            com.osfans.trime.editor.core.ThemeValue keysValue = row.getFields().get("keys");
            if (!(keysValue instanceof com.osfans.trime.editor.core.ThemeValue.LuaTable)) continue;
            int keyIndex = 0;
            for (com.osfans.trime.editor.core.ThemeValue keyValue : ((com.osfans.trime.editor.core.ThemeValue.LuaTable) keysValue).getFields().values()) {
                if (!(keyValue instanceof com.osfans.trime.editor.core.ThemeValue.LuaTable)) continue;
                com.osfans.trime.editor.core.ThemeValue.LuaTable key = (com.osfans.trime.editor.core.ThemeValue.LuaTable) keyValue;
                String label = stringValue(key.getFields().get("label"));
                if (label.isEmpty()) label = stringValue(key.getFields().get("click"));
                if (label.isEmpty()) label = "?";
                float width = numberValue(key.getFields().get("width"), 9.5f);
                float height = numberValue(key.getFields().get("height"), 16f);
                model.keys.add(new ThemeEditorModel.Key("row_" + rowIndex + "_key_" + keyIndex, label,
                        keyIndex * width, 8 + rowIndex * 18, width, height));
                keyIndex++;
            }
            rowIndex++;
        }
        if (model.keys.isEmpty()) return ThemeEditorModel.sample();
        return model;
    }

    private static String stringValue(com.osfans.trime.editor.core.ThemeValue value) {
        return value instanceof com.osfans.trime.editor.core.ThemeValue.LuaString
                ? ((com.osfans.trime.editor.core.ThemeValue.LuaString) value).getValue() : "";
    }

    private static float numberValue(com.osfans.trime.editor.core.ThemeValue value, float fallback) {
        return value instanceof com.osfans.trime.editor.core.ThemeValue.LuaNumber
                ? (float) ((com.osfans.trime.editor.core.ThemeValue.LuaNumber) value).getValue() : fallback;
    }

    private String rowsSource(ThemeEditorModel model) {
        StringBuilder out = new StringBuilder("{\n");
        for (ThemeEditorModel.Key key : model.keys) {
            out.append("  { click = ").append(luaString(key.label))
                    .append(", x = ").append(trim(key.x))
                    .append(", y = ").append(trim(key.y))
                    .append(", width = ").append(trim(key.width))
                    .append(", height = ").append(trim(key.height)).append(" },\n");
        }
        return out.append("}").toString();
    }

    private static String trim(float value) {
        return value == (long) value ? Long.toString((long) value) : Float.toString(value);
    }

    private static String luaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_OPEN) loadUri(data.getData());
        else if (requestCode == REQUEST_SAVE) {
            loadUri(data.getData());
            saveModel(workspace.getModel());
        }
    }

    public ThemeEditorWorkspace getWorkspace() { return workspace; }
    public void setCallbacks(ThemeEditorCallbacks callbacks) { workspace.setCallbacks(callbacks); }
    public void setThemeModel(ThemeEditorModel model) { workspace.setModel(model); }
}
