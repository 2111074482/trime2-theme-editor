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
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProvider;

import com.osfans.trime.editor.core.ThemeEditor;
import com.osfans.trime.editor.core.ThemeValue;
import com.osfans.trime.editor.project.FileThemeProjectRepository;
import com.osfans.trime.editor.project.ThemeProjectRepository;
import com.osfans.trime.editor.project.UriThemeProjectRepository;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

/** Native entry point for the first integrated theme editor milestone. */
public class ThemeEditorActivity extends Activity {
    public static final String EXTRA_THEME = "com.osfans.trime.editor.ui.THEME";
    private static final int REQUEST_OPEN = 10;
    private static final int REQUEST_SAVE = 11;

    private ThemeEditorWorkspace workspace;
    private ThemeEditor editor;
    private ThemeEditorViewModel viewModel;
    private ThemeProjectRepository repository;
    private Uri currentUri;
    private boolean layoutEditable;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        viewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory(getApplication(), this, state)
        ).get(ThemeEditorViewModel.class);
        workspace = new ThemeEditorWorkspace(this);
        setContentView(workspace);
        workspace.setCallbacks(new ThemeEditorCallbacks() {
            @Override public void onSave(ThemeEditorModel model) { saveModel(model); }
            @Override public void onModelChanged(ThemeEditorModel model) { syncModel(model); viewModel.setDirty(true); }
            @Override public void onUndo(ThemeEditorModel model) { syncModel(model); viewModel.setDirty(true); }
            @Override public void onRedo(ThemeEditorModel model) { syncModel(model); viewModel.setDirty(true); }
            @Override public void onSelectionChanged(ThemeEditorModel.Key key) { }
        });
        Uri data = getIntent().getData();
        if (data != null) loadUri(data);
        else if (viewModel.getCurrentUri() != null) loadUri(viewModel.getCurrentUri());
        else if (getIntent().hasExtra(EXTRA_THEME)) loadFile(new File(getIntent().getStringExtra(EXTRA_THEME)));
        else {
            editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            workspace.setModel(toUiModel(editor.getDocument()));
        }
    }

    @Override public void onBackPressed() {
        if (viewModel.getDirty()) {
            new android.app.AlertDialog.Builder(this)
                    .setMessage("Unsaved theme changes")
                    .setPositiveButton("Save", (dialog, which) -> saveModel(workspace.getModel()))
                    .setNegativeButton("Discard", (dialog, which) -> finish())
                    .setNeutralButton("Cancel", null)
                    .show();
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add("Open Lua").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if ("Open Lua".contentEquals(item.getTitle())) {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), REQUEST_OPEN);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadFile(File file) {
        if (file == null || !file.isFile()) return;
        repository = new FileThemeProjectRepository(file);
        currentUri = Uri.fromFile(file);
        viewModel.setCurrentUri(currentUri);
        loadRepository();
    }

    private void loadUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        String name = String.valueOf(uri).toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".zip")) {
            loadZip(uri);
            return;
        }
        currentUri = uri;
        viewModel.setCurrentUri(uri);
        repository = new UriThemeProjectRepository(uri,
                getContentResolver()::openInputStream,
                getContentResolver()::openOutputStream);
        loadRepository();
    }

    private void loadZip(Uri uri) {
        try {
            File root = new File(getCacheDir(), "theme-editor-import-" + System.nanoTime());
            root.mkdirs();
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Cannot open ZIP");
                com.osfans.trime.editor.project.ThemeProjectArchive.extractZip(input, root);
            }
            File main = findMainLua(root);
            if (main == null) throw new IOException("ZIP does not contain main.lua");
            loadFile(main);
            workspace.setStatus("Imported ZIP: " + main.getParentFile());
        } catch (Exception error) {
            workspace.setStatus("ZIP import failed: " + error.getMessage());
            Toast.makeText(this, "Unable to import ZIP", Toast.LENGTH_LONG).show();
        }
    }

    private static File findMainLua(File root) {
        File direct = new File(root, "main.lua");
        if (direct.isFile()) return direct;
        File[] children = root.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isFile() && child.getName().equals("main.lua")) return child;
            if (child.isDirectory()) {
                File nested = findMainLua(child);
                if (nested != null) return nested;
            }
        }
        return null;
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

    private void syncModel(ThemeEditorModel model) {
        if (editor == null) return;
        com.osfans.trime.editor.core.ThemeValue current = editor.getDocument().get("rows");
        if (current instanceof com.osfans.trime.editor.core.ThemeValue.RawLuaNode) {
            workspace.setStatus("Rows uses dynamic Lua; edit it in the Lua source");
            return;
        }
        editor.set("rows", rowsValue(model));
    }

    private void saveModel(ThemeEditorModel model) {
        if (repository == null) {
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("text/x-lua").putExtra(Intent.EXTRA_TITLE, "main.lua"), REQUEST_SAVE);
            return;
        }
        try {
            if (editor == null) editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            if (editor.getDocument().get("rows") == null) {
                editor.set("rows", rowsValue(model));
                layoutEditable = true;
            }
            if (!layoutEditable) {
                workspace.setStatus("This Lua file has no editable rows layout");
                Toast.makeText(this, "Open a keyboard Lua file before editing", Toast.LENGTH_LONG).show();
                return;
            }
            syncModel(model);
            editor.save(repository);
            viewModel.setDirty(false);
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

    private ThemeValue rowsValue(ThemeEditorModel model) {
        java.util.LinkedHashMap<String, ThemeValue> rows = new java.util.LinkedHashMap<>();
        com.osfans.trime.editor.core.ThemeValue existingRows = editor == null ? null : editor.getDocument().get("rows");
        com.osfans.trime.editor.core.ThemeValue.LuaTable existing = existingRows instanceof com.osfans.trime.editor.core.ThemeValue.LuaTable
                ? (com.osfans.trime.editor.core.ThemeValue.LuaTable) existingRows : null;
        java.util.LinkedHashMap<Integer, java.util.LinkedHashMap<String, ThemeValue>> grouped = new java.util.LinkedHashMap<>();
        for (ThemeEditorModel.Key key : model.keys) {
            int rowIndex = indexPart(key.id, "row_");
            if (rowIndex < 0) rowIndex = 0;
            java.util.LinkedHashMap<String, ThemeValue> rowKeys = grouped.get(rowIndex);
            if (rowKeys == null) { rowKeys = new java.util.LinkedHashMap<>(); grouped.put(rowIndex, rowKeys); }
            int column = indexPart(key.id, "_key_");
            ThemeValue originalKey = null;
            if (existing != null && rowIndex >= 0) {
                ThemeValue originalRow = existing.getFields().get("#" + (rowIndex + 1));
                if (originalRow instanceof ThemeValue.LuaTable) {
                    ThemeValue originalKeys = ((ThemeValue.LuaTable) originalRow).getFields().get("keys");
                    if (originalKeys instanceof ThemeValue.LuaTable && column >= 0) {
                        originalKey = ((ThemeValue.LuaTable) originalKeys).getFields().get("#" + (column + 1));
                    }
                }
            }
            java.util.LinkedHashMap<String, ThemeValue> keyFields = new java.util.LinkedHashMap<>();
            if (originalKey instanceof ThemeValue.LuaTable) keyFields.putAll(((ThemeValue.LuaTable) originalKey).getFields());
            String labelField = keyFields.containsKey("label") ? "label" : "click";
            keyFields.put(labelField, new ThemeValue.LuaString(key.label));
            keyFields.put("width", new ThemeValue.LuaNumber(key.width));
            keyFields.put("height", new ThemeValue.LuaNumber(key.height));
            rowKeys.put("#" + (rowKeys.size() + 1), new ThemeValue.LuaTable(keyFields));
        }
        for (java.util.Map.Entry<Integer, java.util.LinkedHashMap<String, ThemeValue>> entry : grouped.entrySet()) {
            java.util.LinkedHashMap<String, ThemeValue> rowFields = new java.util.LinkedHashMap<>();
            int rowIndex = entry.getKey();
            if (existing != null) {
                ThemeValue oldRow = existing.getFields().get("#" + (rowIndex + 1));
                if (oldRow instanceof ThemeValue.LuaTable) rowFields.putAll(((ThemeValue.LuaTable) oldRow).getFields());
            }
            rowFields.put("keys", new ThemeValue.LuaTable(entry.getValue()));
            rows.put("#" + (rows.size() + 1), new ThemeValue.LuaTable(rowFields));
        }
        return new ThemeValue.LuaTable(rows);
    }

    private static int indexPart(String id, String prefix) {
        int start = id.indexOf(prefix);
        if (start < 0) return -1;
        start += prefix.length();
        int end = id.indexOf('_', start);
        String value = end < 0 ? id.substring(start) : id.substring(start, end);
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return -1; }
    }

    private static String trim(float value) {
        return value == (long) value ? Long.toString((long) value) : Float.toString(value);
    }

    private static String luaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (currentUri != null) viewModel.setCurrentUri(currentUri);
        super.onSaveInstanceState(outState);
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
