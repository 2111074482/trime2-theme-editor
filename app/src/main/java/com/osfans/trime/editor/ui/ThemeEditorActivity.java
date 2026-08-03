/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProvider;

import com.osfans.trime.editor.core.ThemeEditor;
import com.osfans.trime.editor.core.ThemeFieldRegistry;
import com.osfans.trime.editor.core.ThemeLuaParser;
import com.osfans.trime.editor.core.ThemeValue;
import com.osfans.trime.editor.project.FileThemeProjectRepository;
import com.osfans.trime.editor.project.ThemeProject;
import com.osfans.trime.editor.project.ThemeProjectFile;
import com.osfans.trime.editor.project.ThemeProjectRepository;
import com.osfans.trime.editor.project.ThemeProjectSelector;
import com.osfans.trime.editor.project.ThemeProjectSnapshot;
import com.osfans.trime.editor.project.ThemeProjectDiagnostics;
import com.osfans.trime.editor.project.ThemeSourceFingerprint;
import com.osfans.trime.editor.project.ThemeSaveCoordinator;
import com.osfans.trime.editor.project.SaveResult;
import com.osfans.trime.editor.project.DirectoryThemeProjectRepository;
import com.osfans.trime.editor.project.UriThemeProjectRepository;
import com.osfans.trime.editor.project.ThemeResource;
import com.osfans.trime.editor.project.ThemeResourceIndex;
import com.osfans.trime.editor.project.ThemeResourceManager;
import com.osfans.trime.editor.project.ThemeProjectCreator;
import com.osfans.trime.editor.project.ThemeProjectMutator;
import com.osfans.trime.editor.project.ResourceDeleteResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

/** Native entry point for the first integrated theme editor milestone. */
public class ThemeEditorActivity extends ComponentActivity {
    public static final String EXTRA_THEME = "com.osfans.trime.editor.ui.THEME";
    private static final int MENU_PAGES = 12;
    private static final int MENU_EXPORT = 13;
    private static final int MENU_SHARE = 14;
    private static final int MENU_DIAGNOSTICS = 15;
    private static final int MENU_RESOURCES = 16;
    private static final int MENU_COMPONENT_EDITOR = 18;
    private static final int MENU_CODE = 19;
    private static final int MENU_STYLE_EDITOR = 20;
    private static final int MENU_COMPOSITION_EDITOR = 21;
    private static final int MENU_ROLLBACK_INSTALL = 22;
    private static final int MENU_STYLE_BASE = 2000;
    private static final int MENU_KEYBOARD_BASE = 3000;
    private static final java.util.HashMap<String, String> ACTIVE_WRITE_SESSIONS = new java.util.HashMap<>();

    private final ActivityResultLauncher<Intent> openLuaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) loadUri(data.getData());
    });
    private final ActivityResultLauncher<Intent> openTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) return; Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
        loadTree(uri);
    });
    private final ActivityResultLauncher<Intent> createProjectTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); ThemeProjectCreator.Spec spec = pendingCreateSpec; pendingCreateSpec = null;
        if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null || spec == null) return; Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
        createProjectInTree(uri, spec);
    });
    private final ActivityResultLauncher<Intent> saveLuaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) savePendingSource(data.getData()); else pendingSaveSource = null;
    });
    private final ActivityResultLauncher<Intent> installTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) return; Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
        installToTree(uri);
    });
    private final ActivityResultLauncher<Intent> importResourceLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) importResource(data.getData()); else pendingResourceFolder = null;
    });
    private final ActivityResultLauncher<Intent> exportZipLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData();
        if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
            try (FileInputStream input = new FileInputStream(pendingExport); java.io.OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                if (output == null) throw new IOException("Cannot open export destination"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); workspace.setStatus("ZIP exported");
            } catch (Exception error) { workspace.setStatus("Export failed: " + error.getMessage()); }
        }
        if (pendingExport != null) pendingExport.delete(); pendingExport = null;
    });

    private ThemeEditorWorkspace workspace;
    private ThemeEditor editor;
    private ThemeEditorViewModel viewModel;
    private ThemeProjectRepository repository;
    private ThemeProject project;
    private ThemeProjectSnapshot projectSnapshot;
    private ThemeSourceFingerprint openedFingerprint;
    private String openedSourceFingerprint;
    private final ThemeSaveCoordinator saveCoordinator = new ThemeSaveCoordinator();
    private File pendingExport;
    private String pendingSaveSource;
    private ThemeProjectCreator.Spec pendingCreateSpec;
    private String pendingResourceFolder;
    private DocumentFile lastInstallTarget;
    private DocumentFile lastInstallBackup;
    private java.util.Map<String, Long> lastBackupManifest;
    private Uri currentUri;
    private boolean layoutEditable;
    private boolean recoveryPrompted;
    private boolean readOnlySession;
    private String sessionKey;
    private Uri importedProjectUri;
    private Uri importedProjectTreeUri;
    private String importedProjectTreePrefix;
    private String projectDisplayName;
    private String openedImportedFingerprint;
    private com.osfans.trime.editor.core.ThemeDocument migrationUndoDocument;
    private com.osfans.trime.editor.core.ThemeDocument migrationRedoDocument;
    private ThemeEditorModel.LayoutMode migrationSourceMode;
    private ThemeEditorModel.LayoutMode migrationTargetMode;
    private boolean applyingMigration;

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        viewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory(getApplication(), this, state)
        ).get(ThemeEditorViewModel.class);
        workspace = new ThemeEditorWorkspace(this);
        setContentView(workspace);
        restoreInstallJournal();
        workspace.setCallbacks(new ThemeEditorCallbacks() {
            @Override public void onSave(ThemeEditorModel model) { if (ensureWritable()) saveModel(model); }
            @Override public void onModelChanged(ThemeEditorModel model) { if (!ensureWritable()) return; if (applyingMigration || syncModel(model)) { applyPreviewStyles(model); workspace.updatePreviewColors(model); viewModel.setDirty(true); } }
            @Override public void onUndo(ThemeEditorModel model) { if (ensureWritable() && syncUndoModel(model)) { applyPreviewStyles(model); workspace.updatePreviewColors(model); viewModel.setDirty(true); } }
            @Override public void onRedo(ThemeEditorModel model) { if (ensureWritable() && syncRedoModel(model)) { applyPreviewStyles(model); workspace.updatePreviewColors(model); viewModel.setDirty(true); } }
            @Override public void onSelectionChanged(ThemeEditorModel.Key key) { }
            @Override public void onBatchStyleEntities(java.util.List<ThemeEditorModel.Key> keys, String background, String textColor) { reviewBatchStyleEntities(keys, background, textColor); }
            @Override public void onCopyStyleEntity(ThemeEditorModel.Key key) { copyStyleEntity(key); }
            @Override public void onPasteStyleEntity(java.util.List<ThemeEditorModel.Key> keys) { promptPasteStyleEntity(keys); }
            @Override public void onManageKeyEvents(ThemeEditorModel.Key key) { showKeyEventManager(key); }
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
                    .setNegativeButton("Discard", (dialog, which) -> { deleteRecoveryDraft(); finish(); })
                    .setNeutralButton("Cancel", null)
                    .show();
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, MENU_PAGES, 1, "Editor pages").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add("Open Lua").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add("Open theme folder").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_EXPORT, 20, "Export ZIP").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_SHARE, 21, "Share ZIP").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_DIAGNOSTICS, 22, "Diagnostics").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_RESOURCES, 23, "Resources").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 24, 24, "Install theme").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_CODE, 25, "Lua source").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_STYLE_EDITOR, 26, "Style properties").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_COMPONENT_EDITOR, 27, "Candidate / toolbar / panels").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_COMPOSITION_EDITOR, 28, "Preedit / composition").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_ROLLBACK_INSTALL, 29, "Rollback last install").setEnabled(lastInstallBackup != null).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        if (project != null) {
            android.view.Menu styles = menu.addSubMenu("Style");
            for (int i = 0; i < project.getStyles().size(); i++) {
                styles.add(0, MENU_STYLE_BASE + i, i, project.getStyles().get(i).getName());
            }
            android.view.Menu keyboards = menu.addSubMenu("Keyboard");
            for (int i = 0; i < project.getKeyboards().size(); i++) {
                keyboards.add(0, MENU_KEYBOARD_BASE + i, i, project.getKeyboards().get(i).getName());
            }
        }
        return true;
    }

    @Override public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == MENU_PAGES) { showEditorPages(); return true; }
        if ("Open Lua".contentEquals(item.getTitle())) {
            openLuaLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE));
            return true;
        }
        if ("Open theme folder".contentEquals(item.getTitle())) {
            openTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
            return true;
        }
        if (item.getItemId() == MENU_EXPORT) { exportZip(false); return true; }
        if (item.getItemId() == MENU_SHARE) { exportZip(true); return true; }
        if (item.getItemId() == MENU_DIAGNOSTICS) { showDiagnostics(); return true; }
        if (item.getItemId() == MENU_RESOURCES) { showResources(); return true; }
        if (item.getItemId() == 24) { chooseInstallTarget(); return true; }
        if (item.getItemId() == MENU_CODE) { showCodeEditor(); return true; }
        if (item.getItemId() == MENU_STYLE_EDITOR) { showStyleEditor(); return true; }
        if (item.getItemId() == MENU_COMPONENT_EDITOR) { showVisualComponentStyleEditor(); return true; }
        if (item.getItemId() == MENU_COMPOSITION_EDITOR) { showCompositionStyleEditor(); return true; }
        if (item.getItemId() == MENU_ROLLBACK_INSTALL) { rollbackLastInstall(true); return true; }
        if (project != null && item.getItemId() >= MENU_STYLE_BASE && item.getItemId() < MENU_KEYBOARD_BASE) {
            requestProjectFileSwitch(project.getStyles().get(item.getItemId() - MENU_STYLE_BASE));
            return true;
        }
        if (project != null && item.getItemId() >= MENU_KEYBOARD_BASE) {
            requestProjectFileSwitch(project.getKeyboards().get(item.getItemId() - MENU_KEYBOARD_BASE));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showEditorPages() {
        String[] pages = {"Project home", "New project", "Recent projects", "Keyboard assets", "Style assets", "Theme settings", "Keyboard structure", "Style properties", "Candidate / toolbar / panels", "Preedit / composition", "Preview workspace", "Resources", "Diagnostics", "Lua source", "Export and install", "Recovery status"};
        new android.app.AlertDialog.Builder(this).setTitle("Theme editor pages").setItems(pages, (dialog, which) -> {
            if (which == 0) showProjectHome(); else if (which == 1) showNewProjectWizard(); else if (which == 2) showRecentProjects(); else if (which == 3) showKeyboardAssets(); else if (which == 4) showStyleAssets(); else if (which == 5) showThemeSettings(); else if (which == 6) showStructurePage(); else if (which == 7) showStyleEditor(); else if (which == 8) showVisualComponentStyleEditor(); else if (which == 9) showCompositionStyleEditor(); else if (which == 10) workspace.setStatus("Preview workspace active; use Preview... for device controls"); else if (which == 11) showResources(); else if (which == 12) showDiagnostics(); else if (which == 13) showCodeEditor(); else if (which == 14) showExportInstallPage(); else showRecoveryStatus();
        }).setNegativeButton("Close", null).show();
    }

    private void showProjectHome() {
        StringBuilder text = new StringBuilder();
        if (project == null) text.append("Single Lua file or unsaved draft"); else text.append("Project: ").append(projectDisplayName == null ? project.getRoot().getName() : projectDisplayName).append("\nStyles: ").append(project.getStyles().size()).append("\nKeyboards: ").append(project.getKeyboards().size()).append("\nResources: ").append(project.getResources().size());
        text.append("\nCurrent: ").append(currentUri == null ? "unsaved" : currentUri).append("\nMode: ").append(readOnlySession ? "read-only second session" : "writable").append("\nDirty: ").append(viewModel.getDirty());
        new android.app.AlertDialog.Builder(this).setTitle("Project home").setMessage(text.toString()).setPositiveButton("Close", null).show();
    }

    private void showNewProjectWizard() {
        if (!ensureWritable()) return;
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        EditText directory = simpleField(fields, "Directory ID", "my_theme"); EditText name = simpleField(fields, "Theme name", "My Theme"); EditText author = simpleField(fields, "Author", "Author"); EditText style = simpleField(fields, "Default style ID", "light"); EditText keyboard = simpleField(fields, "Default keyboard ID", "default");
        android.widget.Spinner palette = new android.widget.Spinner(this); palette.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Light", "Dark"})); fields.addView(palette);
        android.widget.Spinner layout = new android.widget.Spinner(this); layout.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Rows", "Flex box", "Key maps", "Absolute keys"})); fields.addView(layout);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("New theme project").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Choose target", (dialog, which) -> {
            try {
                pendingCreateSpec = new ThemeProjectCreator.Spec(directory.getText().toString().trim(), name.getText().toString().trim(), author.getText().toString().trim(), style.getText().toString().trim(), keyboard.getText().toString().trim(), palette.getSelectedItemPosition() == 0 ? ThemeProjectCreator.Palette.LIGHT : ThemeProjectCreator.Palette.DARK, ThemeProjectCreator.KeyboardTemplate.values()[layout.getSelectedItemPosition()]).validated();
                createProjectTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
            } catch (Exception error) { pendingCreateSpec = null; workspace.setStatus("New project validation failed: " + error.getMessage()); Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
        }).show();
    }

    private EditText simpleField(LinearLayout parent, String hint, String value) { EditText field = new EditText(this); field.setHint(hint); field.setText(value); field.setSingleLine(true); parent.addView(field, new LinearLayout.LayoutParams(-1, -2)); return field; }

    private void createProjectInTree(Uri treeUri, ThemeProjectCreator.Spec spec) {
        File draft = new File(getCacheDir(), "theme-editor-create-" + System.nanoTime()); DocumentFile created = null;
        try {
            ThemeProject generated = ThemeProjectCreator.create(draft, spec); ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(generated, new ThemeLuaParser());
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry())) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Generated project: " + diagnostic.getMessage());
            DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri); if (tree == null || !tree.canWrite()) throw new IOException("Target directory is not writable");
            if (tree.findFile(spec.getDirectoryName()) != null) throw new IOException("A project with that directory ID already exists");
            created = tree.createDirectory(spec.getDirectoryName()); if (created == null) throw new IOException("Cannot create project directory");
            copyProjectToDocument(draft, created); if (!fileManifest(draft).equals(documentManifest(created))) throw new IOException("Created project verification failed");
            File cache = new File(getCacheDir(), "theme-editor-created-" + System.nanoTime()); copyDirectory(draft, cache);
            importedProjectUri = treeUri; importedProjectTreeUri = treeUri; importedProjectTreePrefix = spec.getDirectoryName(); openedImportedFingerprint = null; rememberRecentProject(treeUri, spec.getDirectoryName(), spec.getDirectoryName()); loadProject(cache, spec.getDirectoryName()); workspace.setStatus("Created and verified project: " + spec.getThemeName());
        } catch (Exception error) { if (created != null) created.delete(); workspace.setStatus("Project creation failed: " + error.getMessage()); Toast.makeText(this, "Unable to create theme project", Toast.LENGTH_LONG).show(); }
        finally { deleteDirectory(draft); }
    }

    private void rememberRecentProject(Uri uri, String name, String prefix) {
        android.content.SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit().putString("recent_uri", uri.toString()).putString("recent_name", name == null ? "Theme project" : name);
        if (prefix == null) edit.remove("recent_prefix"); else edit.putString("recent_prefix", prefix); edit.apply();
    }

    private void showRecentProjects() {
        String uri = getPreferences(MODE_PRIVATE).getString("recent_uri", null), name = getPreferences(MODE_PRIVATE).getString("recent_name", "Theme project"), prefix = getPreferences(MODE_PRIVATE).getString("recent_prefix", null);
        if (uri == null) { new android.app.AlertDialog.Builder(this).setTitle("Recent projects").setMessage("No recent SAF project").setPositiveButton("Close", null).show(); return; }
        new android.app.AlertDialog.Builder(this).setTitle("Recent projects").setItems(new String[]{name}, (dialog, which) -> { try { loadRecentProject(Uri.parse(uri), prefix, name); } catch (Exception error) { workspace.setStatus("Recent project permission expired; open the folder again"); } }).setNegativeButton("Close", null).setNeutralButton("Forget", (dialog, which) -> getPreferences(MODE_PRIVATE).edit().remove("recent_uri").remove("recent_name").remove("recent_prefix").apply()).show();
    }

    private void showThemeSettings() {
        if (project == null) { Toast.makeText(this, "Open a theme project first", Toast.LENGTH_LONG).show(); return; }
        try {
            com.osfans.trime.editor.core.ThemeDocument main = new ThemeLuaParser().parse(readSmallText(project.getMainFile(), 4 * 1024 * 1024)).getDocument();
            LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
            EditText name = simpleField(fields, "Theme name", stringValue(main.get("name"), projectDisplayName)); EditText author = simpleField(fields, "Author", stringValue(main.get("author"), "Author"));
            android.widget.Spinner style = new android.widget.Spinner(this); java.util.ArrayList<String> styles = new java.util.ArrayList<>(); for (ThemeProjectFile file : project.getStyles()) styles.add(file.getName()); style.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, styles)); style.setSelection(Math.max(0, styles.indexOf(stringValue(main.get("style"), "light")))); fields.addView(style);
            android.widget.Spinner keyboard = new android.widget.Spinner(this); java.util.ArrayList<String> keyboards = new java.util.ArrayList<>(); for (ThemeProjectFile file : project.getKeyboards()) keyboards.add(file.getName()); keyboard.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, keyboards)); keyboard.setSelection(Math.max(0, keyboards.indexOf(stringValue(main.get("keyboard"), "qwerty26")))); fields.addView(keyboard);
            android.widget.Button actionLabels = new android.widget.Button(this); actionLabels.setText("Edit action labels"); actionLabels.setOnClickListener(view -> showActionLabelsEditor()); fields.addView(actionLabels);
            android.widget.Button presetEvents = new android.widget.Button(this); presetEvents.setText("Manage preset events"); presetEvents.setOnClickListener(view -> showPresetEventManager()); fields.addView(presetEvents);
            android.widget.Button toolbarKeys = new android.widget.Button(this); toolbarKeys.setText("Manage selected style toolbar keys"); toolbarKeys.setOnClickListener(view -> { ThemeProjectFile target = project.style((String) style.getSelectedItem()); if (target == null) workspace.setStatus("Selected style asset is unavailable"); else showToolbarKeyManager(target); }); fields.addView(toolbarKeys);
            android.widget.Button panelComponents = new android.widget.Button(this); panelComponents.setText("Manage candidate / symbol / clipboard bars"); panelComponents.setOnClickListener(view -> { ThemeProjectFile target = project.style((String) style.getSelectedItem()); if (target == null) workspace.setStatus("Selected style asset is unavailable"); else showPanelComponentManager(target); }); fields.addView(panelComponents);
            TextView note = new TextView(this); note.setText("Dynamic get_keyboard, commands, scripts and callbacks remain code-only and are never executed by the editor."); note.setPadding(0, 16, 0, 0); fields.addView(note);
            new android.app.AlertDialog.Builder(this).setTitle("Theme settings").setView(fields).setNegativeButton("Cancel", null).setNeutralButton("Open advanced Lua", (dialog, which) -> showCodeEditor()).setPositiveButton("Apply", (dialog, which) -> {
                if (!ensureAssetWritable()) return; try { String nextStyle = (String) style.getSelectedItem(), nextKeyboard = (String) keyboard.getSelectedItem(); mutateMainWithMirror(() -> ThemeProjectMutator.updateMetadata(project, name.getText().toString(), author.getText().toString(), nextStyle, nextKeyboard)); projectDisplayName = name.getText().toString().trim(); workspace.setStatus("Theme settings updated"); } catch (Exception error) { workspace.setStatus("Theme settings failed: " + error.getMessage()); }
            }).show();
        } catch (Exception error) { workspace.setStatus("Unable to load theme settings: " + error.getMessage()); }
    }
    private void showActionLabelsEditor() {
        if (!ensureAssetWritable() || project == null) return;
        try {
            String source = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.Map<String, String> current = ThemePresetEvents.actionLabels(source);
            LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); java.util.LinkedHashMap<String, EditText> inputs = new java.util.LinkedHashMap<>();
            String[] actionIds = {"none", "send", "go", "done", "search", "previous", "next"}; java.util.LinkedHashMap<String, android.widget.CheckBox> missing = new java.util.LinkedHashMap<>();
            for (String id : actionIds) { android.widget.CheckBox inherit = new android.widget.CheckBox(this); inherit.setText("Remove " + id + " and use runtime fallback"); inherit.setChecked(!current.containsKey(id)); fields.addView(inherit); EditText input = simpleField(fields, "action_labels." + id + " (explicit empty is preserved)", current.containsKey(id) ? current.get(id) : ""); input.setEnabled(!inherit.isChecked()); inherit.setOnCheckedChangeListener((button, checked) -> input.setEnabled(!checked)); missing.put(id, inherit); inputs.put(id, input); }
            android.widget.Spinner previewAction = new android.widget.Spinner(this); previewAction.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, actionIds)); fields.addView(previewAction);
            new android.app.AlertDialog.Builder(this).setTitle("Editor action labels").setMessage("Preview labels only; no editor action is sent.").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> {
                try { java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(); for (java.util.Map.Entry<String, EditText> input : inputs.entrySet()) values.put(input.getKey(), missing.get(input.getKey()).isChecked() ? null : input.getValue().getText().toString()); mutateMainPreset(latest -> { if (!ThemeSaveCoordinator.Companion.fingerprint(source).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("main.lua changed after opening action labels; reopen editor"); return ThemePresetEvents.updateActionLabels(latest, values); }, "Updated action_labels"); String selected = actionIds[previewAction.getSelectedItemPosition()]; ThemeEditorModel previewModel = workspace.getModel(); previewModel.editorActionLabel = values.get(selected) == null ? "" : values.get(selected); workspace.setModelKeepingHistory(previewModel); workspace.setStatus("Updated action_labels and previewed " + selected + "; no action executed"); }
                catch (Exception error) { workspace.setStatus("Action labels update blocked: " + error.getMessage()); }
            }).show();
        } catch (Exception error) { workspace.setStatus("Action labels are code-only: " + error.getMessage()); }
    }

    private static final class PresetUsage {
        final java.util.LinkedHashMap<File, Integer> references = new java.util.LinkedHashMap<>();
        final java.util.LinkedHashMap<File, String> originals = new java.util.LinkedHashMap<>();
        final java.util.ArrayList<String> uncertain = new java.util.ArrayList<>(); int total;
    }

    private void showPresetEventManager() {
        if (!ensureAssetWritable() || project == null) return;
        try {
            String source = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.List<ThemePresetEvents.Event> events = ThemePresetEvents.list(source); String[] labels = new String[events.size() + 1]; labels[0] = "+ New preset event";
            for (int i = 0; i < events.size(); i++) { ThemePresetEvents.Event event = events.get(i); labels[i + 1] = event.getId() + " — " + presetSummary(event) + (event.getRisky() ? " [code-only execution]" : ""); }
            new android.app.AlertDialog.Builder(this).setTitle("Preset events — static editor").setItems(labels, (dialog, which) -> { if (which == 0) showPresetEventEditor(null); else showPresetEventActions(events.get(which - 1)); }).setNegativeButton("Close", null).show();
        } catch (Exception error) { workspace.setStatus("preset_keys is code-only: " + error.getMessage()); }
    }

    private static String presetSummary(ThemePresetEvents.Event event) {
        if (!event.getLabel().isEmpty()) return event.getLabel(); if (!event.getCommand().isEmpty()) return "command=" + event.getCommand(); if (!event.getSend().isEmpty()) return "send=" + event.getSend(); if (!event.getText().isEmpty()) return "text"; if (!event.getCommit().isEmpty()) return "commit"; return "empty event";
    }

    private void showPresetEventActions(ThemePresetEvents.Event event) {
        try {
            PresetUsage usage = collectPresetUsage(event.getId()); String details = "Static references: " + usage.total + (usage.uncertain.isEmpty() ? "" : "\nUncertain Raw Lua files: " + android.text.TextUtils.join(", ", usage.uncertain)) + "\nExecution risk: " + (event.getRisky() ? "command/script is retained but never executed" : "preview shows summary only");
            String[] actions = {"Edit fields", "Copy", "Rename and replace references", "Delete if unreferenced", "View summary"};
            new android.app.AlertDialog.Builder(this).setTitle(event.getId()).setMessage(details).setItems(actions, (dialog, which) -> { if (which == 0) showPresetEventEditor(event); else if (which == 1) promptCopyPreset(event); else if (which == 2) promptRenamePreset(event, usage); else if (which == 3) confirmDeletePreset(event, usage); }).setNegativeButton("Close", null).show();
        } catch (Exception error) { workspace.setStatus("Preset reference analysis failed: " + error.getMessage()); }
    }

    private static String formatEventStates(java.util.List<String> values) { java.util.ArrayList<String> lines = new java.util.ArrayList<>(); for (String value : values) lines.add(value.isEmpty() ? "\\0" : value.replace("\\", "\\\\").replace("\n", "\\n")); return android.text.TextUtils.join("\n", lines); }
    private static java.util.ArrayList<String> parseEventStates(String source) { java.util.ArrayList<String> result = new java.util.ArrayList<>(); if (source.isEmpty()) return result; for (String line : source.split("\n", -1)) { if (line.equals("\\0")) { result.add(""); continue; } StringBuilder value = new StringBuilder(); for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '\\' && i + 1 < line.length()) { char next = line.charAt(++i); value.append(next == 'n' ? '\n' : next); } else value.append(c); } result.add(value.toString()); } return result; }

    private void showPresetEventEditor(ThemePresetEvents.Event event) {
        if (!ensureAssetWritable()) return; final String openedSource; ThemePresetEvents.Event initial;
        try { openedSource = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (event == null) initial = new ThemePresetEvents.Event("Preset_new", "", "", "", "", "", "", "", "", "", "", java.util.Collections.emptyList(), "", false, false, true, null); else { ThemePresetEvents.Event current = null; for (ThemePresetEvents.Event candidate : ThemePresetEvents.list(openedSource)) if (candidate.getId().equals(event.getId())) { current = candidate; break; } if (current == null) throw new IOException("Preset changed or was deleted; reopen manager"); initial = current; } }
        catch (Exception error) { workspace.setStatus("Preset editor blocked: " + error.getMessage()); return; }
        final ThemePresetEvents.Event openedEvent = initial;
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        EditText id = simpleField(fields, "Preset ID", initial.getId()); id.setEnabled(event == null); EditText label = simpleField(fields, "label", initial.getLabel()); EditText send = simpleField(fields, "send", initial.getSend()); EditText text = simpleField(fields, "text", initial.getText()); EditText commit = simpleField(fields, "commit", initial.getCommit()); EditText command = simpleField(fields, "command (retained, never executed)", initial.getCommand()); EditText option = simpleField(fields, "option", initial.getOption()); EditText select = simpleField(fields, "select", initial.getSelect()); EditText toggle = simpleField(fields, "toggle", initial.getToggle()); EditText preview = simpleField(fields, "preview", initial.getPreview()); EditText description = simpleField(fields, "description", initial.getDescription()); EditText states = simpleField(fields, "states: one per line; \\0 empty, \\n embedded newline", formatEventStates(initial.getStates())); states.setSingleLine(false); states.setMinLines(3); EditText shiftLock = simpleField(fields, "shift_lock: click/double/long", initial.getShiftLock()); EditText index = simpleField(fields, "index (preserved; preset references do not consume it)", initial.getIndex() == null ? "" : trim(initial.getIndex().floatValue())); index.setEnabled(false);
        android.widget.CheckBox repeatable = new android.widget.CheckBox(this); repeatable.setText("repeatable"); repeatable.setChecked(initial.getRepeatable()); fields.addView(repeatable); android.widget.CheckBox sticky = new android.widget.CheckBox(this); sticky.setText("sticky"); sticky.setChecked(initial.getSticky()); fields.addView(sticky); android.widget.CheckBox functional = new android.widget.CheckBox(this); functional.setText("functional"); functional.setChecked(initial.getFunctional()); fields.addView(functional);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle(event == null ? "New preset event" : "Edit preset event").setMessage("Static fields only. Apply never sends keys, commits text, invokes commands, scripts, Intents, or callbacks.").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> {
            try { Double nextIndex = openedEvent.getIndex(); java.util.ArrayList<String> nextStates = parseEventStates(states.getText().toString()); ThemePresetEvents.Event next = new ThemePresetEvents.Event(id.getText().toString().trim(), send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), nextStates, shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); mutateMainPreset(source -> { if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(source))) throw new IOException("main.lua changed after opening preset editor; reopen it"); return ThemePresetEvents.put(source, next, event != null); }, "Updated preset " + next.getId() + "; nothing executed"); }
            catch (Exception error) { workspace.setStatus("Preset update blocked: " + error.getMessage()); }
        }).show();
    }

    private interface MainSourceMutation { String apply(String source) throws Exception; }
    private void mutateMainPreset(MainSourceMutation mutation, String success) throws Exception { String source = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(); java.util.LinkedHashMap<File, String> originals = new java.util.LinkedHashMap<>(); changes.put(project.getMainFile(), mutation.apply(source)); originals.put(project.getMainFile(), source); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success); }

    private void promptCopyPreset(ThemePresetEvents.Event event) { LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "Copy ID", event.getId() + "_copy"); new android.app.AlertDialog.Builder(this).setTitle("Copy preset event").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Copy", (dialog, which) -> { try { mutateMainPreset(source -> ThemePresetEvents.copy(source, event.getId(), id.getText().toString().trim()), "Copied preset event"); } catch (Exception error) { workspace.setStatus("Preset copy blocked: " + error.getMessage()); } }).show(); }

    private void promptRenamePreset(ThemePresetEvents.Event event, PresetUsage usage) { if (!usage.uncertain.isEmpty()) { workspace.setStatus("Rename blocked by Raw Lua references: " + android.text.TextUtils.join(", ", usage.uncertain)); return; } LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "New preset ID", event.getId() + "_renamed"); new android.app.AlertDialog.Builder(this).setTitle("Rename preset and references?").setMessage("Replace " + usage.total + " static references in " + usage.references.size() + " files. No event executes.").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Rename", (dialog, which) -> renamePresetTransaction(event.getId(), id.getText().toString().trim())).show(); }

    private void renamePresetTransaction(String oldId, String newId) {
        try {
            PresetUsage usage = collectPresetUsage(oldId); if (!usage.uncertain.isEmpty()) throw new IOException("Raw Lua references changed after review");
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(usage.originals); String main = originals.get(project.getMainFile()); if (main == null) throw new IOException("main.lua was not included in the project-wide reference snapshot"); changes.put(project.getMainFile(), ThemePresetEvents.renameDefinition(main, oldId, newId)); int count = 0;
            for (File file : usage.references.keySet()) { String original = originals.get(file); if (original == null) throw new IOException("Reference source disappeared from the project snapshot: " + relativeProjectFile(file)); String base = file.equals(project.getMainFile()) ? changes.get(file) : original; ThemePresetEvents.ReferenceUpdate update = ThemePresetEvents.replaceReferences(base, oldId, newId); changes.put(file, update.getSource()); count += update.getCount(); }
            // Include unchanged Lua files and the manifest so a previously clean/new file cannot gain a reference between scan and commit.
            applyProjectSourceTransaction(changes, originals, usage.originals.keySet()); workspace.setStatus("Renamed preset and replaced " + count + " static references");
        } catch (Exception error) { workspace.setStatus("Preset rename blocked: " + error.getMessage()); }
    }

    private void confirmDeletePreset(ThemePresetEvents.Event event, PresetUsage usage) { if (usage.total > 0 || !usage.uncertain.isEmpty()) { workspace.setStatus("Preset delete blocked by " + usage.total + " references or uncertain Raw Lua"); return; } new android.app.AlertDialog.Builder(this).setTitle("Delete unreferenced preset?").setMessage(event.getId()).setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> { try { PresetUsage current = collectPresetUsage(event.getId()); if (current.total > 0 || !current.uncertain.isEmpty()) throw new IOException("References changed after review"); String main = current.originals.get(project.getMainFile()); if (main == null) throw new IOException("main.lua was not included in the project-wide reference snapshot"); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(); changes.put(project.getMainFile(), ThemePresetEvents.deleteDefinition(main, event.getId())); applyProjectSourceTransaction(changes, current.originals, current.originals.keySet()); workspace.setStatus("Deleted unreferenced preset " + event.getId()); } catch (Exception error) { workspace.setStatus("Preset delete blocked: " + error.getMessage()); } }).show(); }

    private PresetUsage collectPresetUsage(String id) throws IOException {
        PresetUsage usage = new PresetUsage(); java.util.ArrayList<File> files = new java.util.ArrayList<>(); collectProjectLuaFiles(project.getRoot(), project.getRoot().getCanonicalPath(), new java.util.HashSet<>(), files);
        for (File file : files) { try { String source = new String(readFileBytes(file, 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); usage.originals.put(file, source); int count = ThemePresetEvents.references(source, id); if (count > 0) { usage.references.put(file, count); usage.total += count; } if (ThemePresetEvents.hasUncertainReference(source, id)) usage.uncertain.add(relativeProjectFile(file)); } catch (Exception error) { usage.uncertain.add(relativeProjectFile(file)); } }
        return usage;
    }

    private void collectProjectLuaFiles(File directory, String root, java.util.Set<String> visited, java.util.List<File> result) throws IOException {
        String canonical = directory.getCanonicalPath(); if (!canonical.equals(root) && !canonical.startsWith(root + File.separator)) return; if (!visited.add(canonical)) return; File[] children = directory.listFiles(); if (children == null) return;
        for (File child : children) { String path = child.getCanonicalPath(); if (!path.startsWith(root + File.separator)) continue; if (child.isDirectory()) collectProjectLuaFiles(child, root, visited, result); else if (child.isFile() && child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lua")) result.add(child); }
    }

    private String relativeProjectFile(File file) { try { return file.getCanonicalPath().substring(project.getRoot().getCanonicalPath().length() + 1).replace(File.separatorChar, '/'); } catch (Exception error) { return file.getName(); } }

    private static String stringValue(ThemeValue value, String fallback) { return value instanceof ThemeValue.LuaString ? ((ThemeValue.LuaString) value).getValue() : fallback; }

    private boolean isCurrentProjectFile(ThemeProjectFile file) { return repository instanceof DirectoryThemeProjectRepository && ((DirectoryThemeProjectRepository) repository).getSelected().getFile().equals(file.getFile()); }

    private void showKeyboardAssets() {
        if (project == null) { Toast.makeText(this, "Open a theme project first", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[project.getKeyboards().size() + 1]; labels[0] = "+ New keyboard"; for (int i = 0; i < project.getKeyboards().size(); i++) labels[i + 1] = project.getKeyboards().get(i).getName();
        new android.app.AlertDialog.Builder(this).setTitle("Keyboard assets").setItems(labels, (dialog, which) -> { if (which == 0) createKeyboardAsset(); else showKeyboardAssetActions(project.getKeyboards().get(which - 1)); }).setNegativeButton("Close", null).show();
    }

    private void createKeyboardAsset() {
        if (!ensureAssetWritable()) return; LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText id = simpleField(fields, "Keyboard ID", "keyboard_new"); android.widget.Spinner layout = new android.widget.Spinner(this); layout.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Rows", "Flex box", "Key maps", "Absolute keys"})); fields.addView(layout);
        new android.app.AlertDialog.Builder(this).setTitle("New keyboard").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Create", (dialog, which) -> {
            try { String value = id.getText().toString().trim(); ThemeProjectCreator.Spec spec = new ThemeProjectCreator.Spec("template", value, "Editor", "light", value, ThemeProjectCreator.Palette.LIGHT, ThemeProjectCreator.KeyboardTemplate.values()[layout.getSelectedItemPosition()]); ThemeProjectFile created = ThemeProjectMutator.createKeyboard(project, value, ThemeProjectCreator.keyboardSource(spec)); try { mirrorCreatedProjectFile(created.getFile()); } catch (Exception error) { created.getFile().delete(); throw error; } refreshProjectAfterAssetMutation(); requestProjectFileSwitch(project.keyboard(value)); workspace.setStatus("Created keyboard " + value); }
            catch (Exception error) { workspace.setStatus("Keyboard creation failed: " + error.getMessage()); }
        }).show();
    }

    private void showKeyboardAssetActions(ThemeProjectFile file) {
        String[] actions = {"Open", "Edit top-level fields", "Copy", "Rename", "Set as default", "Delete"};
        new android.app.AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (dialog, which) -> { if (which == 0) requestProjectFileSwitch(file); else if (which == 1) showKeyboardMetadataEditor(file); else if (which == 2) promptCopyKeyboard(file); else if (which == 3) promptRenameKeyboard(file); else if (which == 4) setDefaultKeyboard(file); else confirmDeleteKeyboard(file); }).setNegativeButton("Close", null).show();
    }

    private void showKeyboardMetadataEditor(ThemeProjectFile file) {
        if (!ensureAssetWritable()) return;
        try {
            ThemeProjectMutator.KeyboardMetadata metadata = ThemeProjectMutator.readKeyboardMetadata(file);
            LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
            EditText name = simpleField(fields, "Keyboard name", metadata.getName()); EditText author = simpleField(fields, "Author", metadata.getAuthor()); EditText style = simpleField(fields, "Style reference (optional)", metadata.getStyle() == null ? "" : metadata.getStyle());
            android.widget.CheckBox lock = new android.widget.CheckBox(this); lock.setText("lock"); lock.setChecked(metadata.getLock()); fields.addView(lock);
            android.widget.CheckBox asciiMode = new android.widget.CheckBox(this); asciiMode.setText("ascii_mode"); asciiMode.setChecked(metadata.getAsciiMode()); fields.addView(asciiMode);
            EditText keyWidth = simpleField(fields, "key_width (empty = inherit)", metadata.getKeyWidth() == null ? "" : trim(metadata.getKeyWidth().floatValue())); EditText keyHeight = simpleField(fields, "key_height (empty = inherit)", metadata.getKeyHeight() == null ? "" : trim(metadata.getKeyHeight().floatValue()));
            TextView note = new TextView(this); note.setText("Dynamic values are code-only. Empty dimensions remove the top-level field and restore runtime fallback."); fields.addView(note);
            android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
            new android.app.AlertDialog.Builder(this).setTitle("Keyboard fields: " + file.getName()).setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> {
                try {
                    ThemeProjectMutator.KeyboardMetadata next = new ThemeProjectMutator.KeyboardMetadata(name.getText().toString().trim(), author.getText().toString().trim(), emptyToNull(style), lock.isChecked(), asciiMode.isChecked(), optionalPositiveDouble(keyWidth, "key_width"), optionalPositiveDouble(keyHeight, "key_height"));
                    byte[] backup = readFileBytes(file.getFile(), 4L * 1024 * 1024);
                    boolean current = isCurrentProjectFile(file);
                    try { ThemeProjectMutator.updateKeyboardMetadata(file, next); mirrorExistingProjectFile(file.getFile()); refreshProjectAfterAssetMutation(); }
                    catch (Exception error) { try (FileOutputStream output = new FileOutputStream(file.getFile(), false)) { output.write(backup); output.getFD().sync(); } if (importedProjectTreeUri != null) try { writeImportedProjectFile(file.getFile(), new String(backup, java.nio.charset.StandardCharsets.UTF_8)); } catch (Exception restoreError) { error.addSuppressed(restoreError); } throw error; }
                    if (current) loadProjectFile(project.keyboard(file.getName()));
                    workspace.setStatus("Updated keyboard fields for " + file.getName());
                } catch (Exception error) { workspace.setStatus("Keyboard fields failed: " + error.getMessage()); }
            }).show();
        } catch (Exception error) { workspace.setStatus("Unable to read keyboard fields: " + error.getMessage()); }
    }

    private static String emptyToNull(EditText field) { String value = field.getText().toString().trim(); return value.isEmpty() ? null : value; }
    private static Double optionalPositiveDouble(EditText field, String name) {
        String value = field.getText().toString().trim(); if (value.isEmpty()) return null;
        double parsed = Double.parseDouble(value); if (!(parsed > 0) || Double.isInfinite(parsed) || Double.isNaN(parsed)) throw new IllegalArgumentException(name + " must be a positive number"); return parsed;
    }

    private void promptCopyKeyboard(ThemeProjectFile file) { promptKeyboardId("Copy keyboard", file.getName() + "_copy", id -> { ThemeProjectFile created = ThemeProjectMutator.copyKeyboard(project, file, id); try { mirrorCreatedProjectFile(created.getFile()); } catch (Exception error) { created.getFile().delete(); throw error; } refreshProjectAfterAssetMutation(); workspace.setStatus("Copied keyboard " + id); }); }
    private void promptRenameKeyboard(ThemeProjectFile file) { if (isCurrentProjectFile(file)) { workspace.setStatus("Open another file before renaming the current keyboard"); return; } promptKeyboardId("Rename keyboard", file.getName(), id -> { File old = file.getFile(); ThemeProjectFile renamed = ThemeProjectMutator.renameKeyboard(project, file, id); try { mirrorRenamedProjectFile(old, renamed.getFile()); } catch (Exception error) { renamed.getFile().renameTo(old); throw error; } refreshProjectAfterAssetMutation(); requestProjectFileSwitch(project.keyboard(id)); workspace.setStatus("Renamed keyboard to " + id); }); }
    private interface KeyboardIdAction { void run(String id) throws Exception; }
    private void promptKeyboardId(String title, String initial, KeyboardIdAction action) { if (!ensureAssetWritable()) return; LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "Keyboard ID", initial); new android.app.AlertDialog.Builder(this).setTitle(title).setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { try { action.run(id.getText().toString().trim()); } catch (Exception error) { workspace.setStatus(title + " failed: " + error.getMessage()); } }).show(); }

    private void setDefaultKeyboard(ThemeProjectFile file) { if (!ensureAssetWritable()) return; try { mutateMainWithMirror(() -> ThemeProjectMutator.setDefaultKeyboard(project, file.getName())); workspace.setStatus("Default keyboard: " + file.getName()); } catch (Exception error) { workspace.setStatus("Default keyboard update failed: " + error.getMessage()); } }
    private void confirmDeleteKeyboard(ThemeProjectFile file) { if (!ensureAssetWritable()) return; if (isCurrentProjectFile(file)) { workspace.setStatus("Open another file before deleting the current keyboard"); return; } new android.app.AlertDialog.Builder(this).setTitle("Delete keyboard?").setMessage(file.getName()).setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> { try { ThemeProjectMutator.validateKeyboardDeletion(project, file); if (importedProjectTreeUri != null) deleteImportedProjectPath(file.getFile()); if (!file.getFile().delete()) throw new IOException("Cannot delete local keyboard cache"); refreshProjectAfterAssetMutation(); workspace.setStatus("Deleted keyboard " + file.getName()); } catch (Exception error) { workspace.setStatus("Keyboard delete blocked: " + error.getMessage()); } }).show(); }

    private interface MainMutation { void run() throws Exception; }
    private void mutateMainWithMirror(MainMutation mutation) throws Exception {
        byte[] backup = readFileBytes(project.getMainFile(), 4L * 1024 * 1024);
        try { mutation.run(); mirrorExistingProjectFile(project.getMainFile()); refreshProjectAfterAssetMutation(); }
        catch (Exception error) { try (FileOutputStream output = new FileOutputStream(project.getMainFile(), false)) { output.write(backup); output.getFD().sync(); } throw error; }
    }

    private void showStyleAssets() {
        if (project == null) { Toast.makeText(this, "Open a theme project first", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[project.getStyles().size()]; for (int i = 0; i < labels.length; i++) labels[i] = project.getStyles().get(i).getName();
        new android.app.AlertDialog.Builder(this).setTitle("Style assets").setItems(labels, (dialog, which) -> showStyleAssetActions(project.getStyles().get(which))).setNegativeButton("Close", null).show();
    }
    private void showStyleAssetActions(ThemeProjectFile file) {
        String[] actions = {"Open", "Manage entities", "Manage toolbar keys", "Manage panel bars", "Copy style asset", "Rename style asset", "Set as default", "Delete style asset"};
        new android.app.AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (dialog, which) -> { if (which == 0) requestProjectFileSwitch(file); else if (which == 1) showStyleEntityManager(file); else if (which == 2) showToolbarKeyManager(file); else if (which == 3) showPanelComponentManager(file); else if (which == 4) promptStyleId("Copy style", file.getName() + "_copy", id -> { ThemeProjectFile created = ThemeProjectMutator.copyStyle(project, file, id); try { mirrorCreatedProjectDirectory(created.getFile().getParentFile()); } catch (Exception error) { deleteDirectory(created.getFile().getParentFile()); throw error; } refreshProjectAfterAssetMutation(); }); else if (which == 5) { if (isCurrentProjectFile(file)) { workspace.setStatus("Open another file before renaming the current style"); return; } promptStyleId("Rename style", file.getName(), id -> { File old = file.getFile().getParentFile(); ThemeProjectFile renamed = ThemeProjectMutator.renameStyle(project, file, id); try { mirrorRenamedProjectDirectory(old, renamed.getFile().getParentFile()); } catch (Exception error) { renamed.getFile().getParentFile().renameTo(old); throw error; } refreshProjectAfterAssetMutation(); requestProjectFileSwitch(project.style(id)); }); } else if (which == 6) { if (!ensureAssetWritable()) return; try { mutateMainWithMirror(() -> ThemeProjectMutator.setDefaultStyle(project, file.getName())); workspace.setStatus("Default style: " + file.getName()); } catch (Exception error) { workspace.setStatus("Default style failed: " + error.getMessage()); } } else confirmDeleteStyle(file); }).setNegativeButton("Close", null).show();
    }
    private void showPanelComponentManager(ThemeProjectFile styleFile) {
        if (!ensureAssetWritable()) return;
        try {
            String source = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            ThemePanelComponents.FilterBar filter = ThemePanelComponents.readCandidateFilter(source); ThemePanelComponents.Toolbar candidate = ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.CANDIDATE_EXPANDED); ThemePanelComponents.Toolbar symbol = ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.SYMBOL); ThemePanelComponents.Toolbar clipboard = ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.CLIPBOARD); ThemePanelComponents.TabBar symbolTab = ThemePanelComponents.readTabBar(source, ThemePanelComponents.Panel.SYMBOL); ThemePanelComponents.TabBar clipboardTab = ThemePanelComponents.readTabBar(source, ThemePanelComponents.Panel.CLIPBOARD);
            String[] labels = {
                    "Candidate filter — show=" + filter.getShow() + ", gravity=" + filter.getGravity(),
                    "Expanded candidate toolbar — " + panelToolbarSummary(candidate),
                    "Symbol toolbar — " + panelToolbarSummary(symbol),
                    "Symbol tab bar — " + panelTabSummary(symbolTab),
                    "Clipboard toolbar — " + panelToolbarSummary(clipboard),
                    "Clipboard tab bar — " + panelTabSummary(clipboardTab)
            };
            new android.app.AlertDialog.Builder(this).setTitle(styleFile.getName() + " panel components").setMessage("Panel toolbar arrays accept strings only. Built-in names are previewed statically and never invoked.").setItems(labels, (dialog, which) -> { if (which == 0) editCandidateFilter(styleFile, source, filter); else if (which == 1) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.CANDIDATE_EXPANDED, candidate); else if (which == 2) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.SYMBOL, symbol); else if (which == 3) editPanelTabBar(styleFile, source, ThemePanelComponents.Panel.SYMBOL, symbolTab); else if (which == 4) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.CLIPBOARD, clipboard); else editPanelTabBar(styleFile, source, ThemePanelComponents.Panel.CLIPBOARD, clipboardTab); }).setNegativeButton("Close", null).setNeutralButton("Open style source", (dialog, which) -> requestProjectFileSwitch(styleFile)).show();
        } catch (Exception error) { workspace.setStatus("Panel component manager blocked: " + error.getMessage()); }
    }

    private static String panelToolbarSummary(ThemePanelComponents.Toolbar value) { return "gravity=" + value.getGravity() + ", keys=" + value.getKeys().size() + (value.getHeight() == null ? "" : ", height=" + value.getHeight()) + (value.getInherited() ? " [literal override after inherited root]" : ""); }
    private static String panelTabSummary(ThemePanelComponents.TabBar value) { return "gravity=" + (value.getGravity() == null ? "runtime default" : value.getGravity()) + ", height=" + (value.getHeight() == null ? "runtime default" : value.getHeight()); }

    private void editCandidateFilter(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.FilterBar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner show = nullableSpinner(fields, "show", current.getShowExplicit() ? Boolean.valueOf(current.getShow()) : null); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"left", "top", "right", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null);
        new android.app.AlertDialog.Builder(this).setTitle("Candidate filter bar").setMessage("inherit removes the literal field and restores show=true / gravity=left runtime defaults.").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateCandidateFilter(source, nullableSpinnerBoolean(show), nullableSpinnerString(gravity)), "Updated candidate filter bar")).show();
    }

    private void editPanelToolbar(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.Panel panel, ThemePanelComponents.Toolbar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"left", "top", "right", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null); EditText height = null; android.widget.CheckBox inheritHeight = null;
        if (panel != ThemePanelComponents.Panel.CANDIDATE_EXPANDED) { inheritHeight = new android.widget.CheckBox(this); inheritHeight.setText("Remove height and use runtime layout"); inheritHeight.setChecked(!current.getHeightExplicit()); fields.addView(inheritHeight); height = simpleField(fields, "height (finite nonnegative)", current.getHeight() == null ? "" : current.getHeight().toString()); height.setEnabled(!inheritHeight.isChecked()); EditText target = height; inheritHeight.setOnCheckedChangeListener((button, checked) -> target.setEnabled(!checked)); }
        android.widget.CheckBox inheritKeys = new android.widget.CheckBox(this); inheritKeys.setText("Remove keys and use panel defaults"); inheritKeys.setChecked(!current.getKeysExplicit()); fields.addView(inheritKeys); EditText keys = simpleField(fields, "keys: one literal string per line", formatEventStates(current.getKeys())); keys.setSingleLine(false); keys.setMinLines(4); keys.setEnabled(!inheritKeys.isChecked()); inheritKeys.setOnCheckedChangeListener((button, checked) -> keys.setEnabled(!checked)); TextView defaults = new TextView(this); defaults.setText("Missing keys fallback: " + android.text.TextUtils.join(", ", current.getKeys()) + ". Tables and events are not accepted here."); fields.addView(defaults); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2)); final EditText heightField = height; final android.widget.CheckBox removeHeight = inheritHeight;
        new android.app.AlertDialog.Builder(this).setTitle(panel + " toolbar").setMessage("hide/page_up/page_down/char_filter/undo/BackSpace are retained as static names; no action runs.").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { try { Double nextHeight = panel == ThemePanelComponents.Panel.CANDIDATE_EXPANDED || removeHeight.isChecked() ? null : Double.valueOf(heightField.getText().toString().trim()); java.util.List<String> nextKeys = inheritKeys.isChecked() ? null : parseEventStates(keys.getText().toString()); mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateToolbar(source, panel, nullableSpinnerString(gravity), nextHeight, nextKeys), "Updated " + panel + " toolbar"); } catch (Exception error) { workspace.setStatus("Panel toolbar update blocked: " + error.getMessage()); } }).show();
    }

    private void editPanelTabBar(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.Panel panel, ThemePanelComponents.TabBar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"top", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null); android.widget.CheckBox inheritHeight = new android.widget.CheckBox(this); inheritHeight.setText("Remove height and use runtime layout"); inheritHeight.setChecked(!current.getHeightExplicit()); fields.addView(inheritHeight); EditText height = simpleField(fields, "height (finite nonnegative)", current.getHeight() == null ? "" : current.getHeight().toString()); height.setEnabled(!inheritHeight.isChecked()); inheritHeight.setOnCheckedChangeListener((button, checked) -> height.setEnabled(!checked));
        new android.app.AlertDialog.Builder(this).setTitle(panel + " tab bar").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { try { Double nextHeight = inheritHeight.isChecked() ? null : Double.valueOf(height.getText().toString().trim()); mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateTabBar(source, panel, nullableSpinnerString(gravity), nextHeight), "Updated " + panel + " tab bar"); } catch (Exception error) { workspace.setStatus("Tab bar update blocked: " + error.getMessage()); } }).show();
    }

    private android.widget.Spinner nullableSpinner(LinearLayout parent, String label, Boolean selected) { TextView text = new TextView(this); text.setText(label); parent.addView(text); android.widget.Spinner spinner = new android.widget.Spinner(this); String[] values = {"inherit", "false", "true"}; spinner.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); spinner.setSelection(selected == null ? 0 : selected ? 2 : 1); parent.addView(spinner); return spinner; }
    private android.widget.Spinner nullableStringSpinner(LinearLayout parent, String label, String[] values, String selected) { TextView text = new TextView(this); text.setText(label); parent.addView(text); String[] choices = new String[values.length + 1]; choices[0] = "inherit"; System.arraycopy(values, 0, choices, 1, values.length); android.widget.Spinner spinner = new android.widget.Spinner(this); spinner.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices)); int index = 0; if (selected != null) for (int i = 0; i < values.length; i++) if (selected.equals(values[i])) index = i + 1; spinner.setSelection(index); parent.addView(spinner); return spinner; }
    private static Boolean nullableSpinnerBoolean(android.widget.Spinner spinner) { return spinner.getSelectedItemPosition() == 0 ? null : spinner.getSelectedItemPosition() == 2; }
    private static String nullableSpinnerString(android.widget.Spinner spinner) { return spinner.getSelectedItemPosition() == 0 ? null : spinner.getSelectedItem().toString(); }
    private interface PanelSourceMutation { String apply(String source) throws Exception; }
    private void mutatePanelSource(ThemeProjectFile styleFile, String openedSource, PanelSourceMutation mutation, String success) { try { if (!ensureAssetWritable()) return; String latest = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("Style source changed after opening panel manager; reopen it"); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), mutation.apply(latest)); originals.put(styleFile.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success); } catch (Exception error) { workspace.setStatus("Panel component update blocked: " + error.getMessage()); } }

    private void showToolbarKeyManager(ThemeProjectFile styleFile) {
        if (!ensureAssetWritable()) return;
        try {
            String source = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            java.util.List<ThemeToolbarKeys.Item> items = ThemeToolbarKeys.list(source);
            String[] labels = new String[items.size() + 1]; labels[0] = "+ New toolbar key";
            for (int i = 0; i < items.size(); i++) labels[i + 1] = (i + 1) + ". " + toolbarItemSummary(items.get(i));
            new android.app.AlertDialog.Builder(this).setTitle(styleFile.getName() + " toolbar.keys — static only").setMessage("Items are inspected only. Commands, options, scripts, callbacks and scheme switches never execute in preview.").setItems(labels, (dialog, which) -> {
                if (which == 0) chooseToolbarItemType(styleFile, source, -1, null, true);
                else showToolbarItemActions(styleFile, source, which - 1, items.get(which - 1), items.size());
            }).setNegativeButton("Close", null).setNeutralButton("Open style source", (dialog, which) -> requestProjectFileSwitch(styleFile)).show();
        } catch (Exception error) { workspace.setStatus("Toolbar key manager blocked: " + error.getMessage()); }
    }

    private static String toolbarItemSummary(ThemeToolbarKeys.Item item) {
        if (item.getSource() == ThemeToolbarKeys.Source.STRING) return "string/preset — " + item.getLiteral();
        if (item.getSource() == ThemeToolbarKeys.Source.INLINE_EVENT) return "direct event — " + presetSummary(item.getEvent()) + (item.getRisky() ? " [execution retained, never run]" : "");
        if (item.getSource() == ThemeToolbarKeys.Source.SCHEMA_SWITCH) { ThemeToolbarKeys.SchemaSwitch value = item.getSchemaSwitch(); return "scheme switch — " + value.getName() + " (" + value.getOptions().size() + " options)" + (item.getCompatibilityWarning() ? " [style ignored by current runtime]" : ""); }
        if (item.getSource() == ThemeToolbarKeys.Source.FULL_KEY) return "complete key table — explicit source replacement required";
        return "Raw Lua — source only";
    }

    private void showToolbarItemActions(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item item, int size) {
        String[] actions = {"Edit", "Move up", "Move down", "Delete", "View compatibility summary"};
        new android.app.AlertDialog.Builder(this).setTitle(toolbarItemSummary(item)).setItems(actions, (dialog, which) -> {
            if (which == 0) {
                if (item.getSource() == ThemeToolbarKeys.Source.FULL_KEY || item.getSource() == ThemeToolbarKeys.Source.RAW_LUA) { workspace.setStatus("Open the style source for complete-key or Raw Lua toolbar items"); requestProjectFileSwitch(styleFile); }
                else chooseToolbarItemType(styleFile, openedSource, index, item, false);
            } else if (which == 1 || which == 2) {
                int target = which == 1 ? index - 1 : index + 1;
                if (target < 0 || target >= size) { workspace.setStatus("Toolbar item is already at that edge"); return; }
                mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.move(source, index, target), "Moved toolbar key");
            } else if (which == 3) {
                new android.app.AlertDialog.Builder(this).setTitle("Delete toolbar key?").setMessage(toolbarItemSummary(item)).setNegativeButton("Cancel", null).setPositiveButton("Delete", (confirm, selected) -> mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.delete(source, index), "Deleted toolbar key")).show();
            } else workspace.setStatus(toolbarItemSummary(item) + "; toolbar scheme-switch style is read but ignored by current ToolbarView construction");
        }).setNegativeButton("Close", null).show();
    }

    private void chooseToolbarItemType(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        String[] types = {"String / preset key reference", "Direct static event table", "Scheme switch table"};
        int selected = current == null ? 0 : current.getSource() == ThemeToolbarKeys.Source.INLINE_EVENT ? 1 : current.getSource() == ThemeToolbarKeys.Source.SCHEMA_SWITCH ? 2 : 0;
        new android.app.AlertDialog.Builder(this).setTitle(append ? "New toolbar key" : "Replace toolbar item type").setSingleChoiceItems(types, selected, (dialog, which) -> { dialog.dismiss(); if (which == 0) editToolbarString(styleFile, openedSource, index, current, append); else if (which == 1) editToolbarEvent(styleFile, openedSource, index, current, append); else editToolbarSchemaSwitch(styleFile, openedSource, index, current, append); }).setNegativeButton("Cancel", null).show();
    }

    private void editToolbarString(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        LinearLayout fields = new LinearLayout(this); EditText value = simpleField(fields, "Preset key ID or literal event", current != null && current.getLiteral() != null ? current.getLiteral() : "");
        new android.app.AlertDialog.Builder(this).setTitle("Toolbar string item").setMessage("The value is retained as a static reference. It is not sent or executed.").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.put(source, index, ThemeToolbarKeys.string(value.getText().toString()), append), "Updated toolbar string item; nothing executed")).show();
    }

    private void editToolbarEvent(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        ThemePresetEvents.Event event = current != null && current.getEvent() != null ? current.getEvent() : new ThemePresetEvents.Event("ToolbarKey", "", "", "", "", "", "", "", "", "", "", java.util.Collections.emptyList(), "", false, false, true, null);
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText label = simpleField(fields, "label", event.getLabel()); EditText send = simpleField(fields, "send", event.getSend()); EditText text = simpleField(fields, "text", event.getText()); EditText commit = simpleField(fields, "commit", event.getCommit()); EditText command = simpleField(fields, "command (retained, never executed)", event.getCommand()); EditText option = simpleField(fields, "option", event.getOption()); EditText select = simpleField(fields, "select", event.getSelect()); EditText toggle = simpleField(fields, "toggle", event.getToggle()); EditText preview = simpleField(fields, "preview", event.getPreview()); EditText description = simpleField(fields, "description", event.getDescription()); EditText states = simpleField(fields, "states: one per line; \\0 empty, \\n embedded newline", formatEventStates(event.getStates())); states.setSingleLine(false); states.setMinLines(3); EditText shiftLock = simpleField(fields, "shift_lock: click/double/long", event.getShiftLock()); EditText eventIndex = simpleField(fields, "index (32-bit integer; no reliable effect)", event.getIndex() == null ? "" : event.getIndex().toString()); android.widget.CheckBox repeatable = new android.widget.CheckBox(this); repeatable.setText("repeatable"); repeatable.setChecked(event.getRepeatable()); fields.addView(repeatable); android.widget.CheckBox sticky = new android.widget.CheckBox(this); sticky.setText("sticky"); sticky.setChecked(event.getSticky()); fields.addView(sticky); android.widget.CheckBox functional = new android.widget.CheckBox(this); functional.setText("functional"); functional.setChecked(event.getFunctional()); fields.addView(functional); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("Toolbar direct event").setMessage("Static fields only; applying never executes the event.").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { try { String indexText = eventIndex.getText().toString().trim(); Double nextIndex = indexText.isEmpty() ? null : Double.valueOf(indexText); ThemePresetEvents.Event next = new ThemePresetEvents.Event("ToolbarKey", send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), parseEventStates(states.getText().toString()), shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.put(source, index, ThemeToolbarKeys.inlineEvent(next), append), "Updated direct toolbar event; nothing executed"); } catch (Exception error) { workspace.setStatus("Toolbar event update blocked: " + error.getMessage()); } }).show();
    }

    private void editToolbarSchemaSwitch(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        ThemeToolbarKeys.SchemaSwitch value = current != null && current.getSchemaSwitch() != null ? current.getSchemaSwitch() : new ThemeToolbarKeys.SchemaSwitch("ascii_mode", java.util.Arrays.asList("ascii_mode"), java.util.Arrays.asList("中", "A"), 0, null);
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText name = simpleField(fields, "name", value.getName()); EditText options = simpleField(fields, "options: one per line", formatEventStates(value.getOptions())); options.setSingleLine(false); options.setMinLines(2); EditText states = simpleField(fields, "states: one per line", formatEventStates(value.getStates())); states.setSingleLine(false); states.setMinLines(2); EditText reset = simpleField(fields, "reset (32-bit integer)", Integer.toString(value.getReset())); EditText style = simpleField(fields, "style (compatibility only; ToolbarView uses toolbar.key)", value.getStyle() == null ? "" : value.getStyle()); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("Toolbar scheme switch").setMessage("Preview is static. Applying does not toggle options, switch scheme/theme/style/keyboard, restart Trime, or invoke callbacks.").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { try { int nextReset = Integer.parseInt(reset.getText().toString().trim()); ThemeToolbarKeys.Item next = ThemeToolbarKeys.schemaSwitch(name.getText().toString().trim(), parseEventStates(options.getText().toString()), parseEventStates(states.getText().toString()), nextReset, style.getText().toString().trim().isEmpty() ? null : style.getText().toString().trim()); mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.put(source, index, next, append), "Updated toolbar scheme switch; nothing executed"); } catch (Exception error) { workspace.setStatus("Scheme switch update blocked: " + error.getMessage()); } }).show();
    }

    private interface ToolbarSourceMutation { String apply(String source) throws Exception; }
    private void mutateToolbarSource(ThemeProjectFile styleFile, String openedSource, ToolbarSourceMutation mutation, String success) {
        try {
            if (!ensureAssetWritable()) return; String latest = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("Style source changed after opening toolbar manager; reopen it");
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), mutation.apply(latest)); originals.put(styleFile.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success);
        } catch (Exception error) { workspace.setStatus("Toolbar update blocked: " + error.getMessage()); }
    }

    private static final class EntityUsage {
        final java.util.LinkedHashMap<ThemeProjectFile, Integer> references = new java.util.LinkedHashMap<>();
        final java.util.ArrayList<String> uncertain = new java.util.ArrayList<>();
        int total;
    }

    private void showStyleEntityManager(ThemeProjectFile styleFile) {
        if (!ensureAssetWritable()) return;
        try {
            String source = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.List<ThemeStyleEntities.Entry> entries = ThemeStyleEntities.list(source);
            String[] labels = new String[entries.size() + 2]; labels[0] = "+ New entity"; labels[1] = "Paste entity from private clipboard";
            for (int i = 0; i < entries.size(); i++) { ThemeStyleEntities.Entry entry = entries.get(i); labels[i + 2] = entry.getId() + (entry.getCloneParent() == null ? "" : " ← " + entry.getCloneParent()) + (entry.getDynamic() ? " [code-only]" : ""); }
            new android.app.AlertDialog.Builder(this).setTitle("Style entities — " + styleFile.getName()).setItems(labels, (dialog, which) -> { if (which == 0) promptCreateStyleEntity(styleFile); else if (which == 1) promptPasteEntityIntoStyle(styleFile); else showStyleEntityActions(styleFile, entries.get(which - 2)); }).setNegativeButton("Close", null).show();
        } catch (Exception error) { workspace.setStatus("Style entity list failed: " + error.getMessage()); }
    }

    private void showStyleEntityActions(ThemeProjectFile styleFile, ThemeStyleEntities.Entry entry) {
        try {
            EntityUsage usage = collectEntityUsage(styleFile, entry.getId()); String details = "Entity: " + entry.getId() + (entry.getCloneParent() == null ? "" : "\nInherits: " + entry.getCloneParent()) + "\nStatic key references: " + usage.total + (usage.uncertain.isEmpty() ? "" : "\nUncertain keyboards: " + android.text.TextUtils.join(", ", usage.uncertain)) + (entry.getDynamic() ? "\nDynamic entity: structural actions are disabled; use Lua source." : "");
            String[] actions = entry.getDynamic() ? new String[]{"Details", "Open style Lua"} : new String[]{"Details", "Copy complete entity", "Duplicate", "Rename and replace references", "Delete if unreferenced", "Open style Lua"};
            new android.app.AlertDialog.Builder(this).setTitle(entry.getId()).setMessage(details).setItems(actions, (dialog, which) -> {
                if (which == 0) return;
                if (entry.getDynamic()) { requestProjectFileSwitch(styleFile); return; }
                if (which == 1) copyEntityFromStyleAsset(styleFile, entry.getId()); else if (which == 2) promptDuplicateStyleEntity(styleFile, entry.getId()); else if (which == 3) promptRenameStyleEntity(styleFile, entry.getId(), usage); else if (which == 4) confirmDeleteStyleEntity(styleFile, entry.getId(), usage); else requestProjectFileSwitch(styleFile);
            }).setNegativeButton("Close", null).show();
        } catch (Exception error) { workspace.setStatus("Style entity analysis blocked: " + error.getMessage()); }
    }

    private void copyEntityFromStyleAsset(ThemeProjectFile styleFile, String id) {
        try { ThemeStyleEntities.Snapshot snapshot = ThemeStyleEntities.extract(new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8), id); workspace.storeStyleEntityClipboard(snapshot); }
        catch (Exception error) { workspace.setStatus("Style entity copy blocked: " + error.getMessage()); }
    }

    private void promptCreateStyleEntity(ThemeProjectFile styleFile) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText id = simpleField(fields, "New entity ID", "style_new"); EditText parent = simpleField(fields, "Clone parent (empty for table)", "key");
        new android.app.AlertDialog.Builder(this).setTitle("New style entity").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Create", (dialog, which) -> mutateSingleStyleEntity(styleFile, source -> ThemeStyleEntities.create(source, id.getText().toString().trim(), emptyToNull(parent)), "Created style entity " + id.getText().toString().trim())).show();
    }

    private void promptDuplicateStyleEntity(ThemeProjectFile styleFile, String sourceId) {
        LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "Duplicate entity ID", sourceId + "_copy");
        new android.app.AlertDialog.Builder(this).setTitle("Duplicate complete entity").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Duplicate", (dialog, which) -> mutateSingleStyleEntity(styleFile, source -> ThemeStyleEntities.paste(source, ThemeStyleEntities.extract(source, sourceId), id.getText().toString().trim()), "Duplicated style entity " + sourceId)).show();
    }

    private void promptPasteEntityIntoStyle(ThemeProjectFile styleFile) {
        ThemeEditorClipboard.Payload payload = workspace.styleEntityClipboard(); if (payload == null || payload.styleEntity == null) { workspace.setStatus("Private clipboard does not contain a complete style entity"); return; }
        try { java.util.ArrayList<String> missing = missingStyleEntityResources(styleFile, payload.styleEntity); if (!missing.isEmpty()) throw new IOException("Target style is missing resources: " + android.text.TextUtils.join(", ", missing)); }
        catch (Exception error) { workspace.setStatus("Entity paste blocked: " + error.getMessage()); return; }
        LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "New entity ID", payload.styleEntity.getId() + "_copy");
        new android.app.AlertDialog.Builder(this).setTitle("Paste complete entity").setMessage(payload.styleEntity.getCloneParent() == null ? "No clone dependency" : "Requires clone parent: " + payload.styleEntity.getCloneParent()).setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Paste", (dialog, which) -> mutateSingleStyleEntity(styleFile, source -> ThemeStyleEntities.paste(source, payload.styleEntity, id.getText().toString().trim()), "Pasted complete style entity")).show();
    }

    private interface StyleSourceMutation { String apply(String source) throws Exception; }
    private void mutateSingleStyleEntity(ThemeProjectFile styleFile, StyleSourceMutation mutation, String success) {
        if (!ensureAssetWritable()) return;
        try {
            String original = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); String updated = mutation.apply(original);
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), updated); originals.put(styleFile.getFile(), original); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success + "; transaction verified");
        } catch (Exception error) { workspace.setStatus("Style entity mutation failed: " + error.getMessage()); }
    }

    private EntityUsage collectEntityUsage(ThemeProjectFile styleFile, String entityId) throws IOException {
        EntityUsage usage = new EntityUsage(); com.osfans.trime.editor.core.ThemeDocument main = new ThemeLuaParser().parse(new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8)).getDocument(); ThemeValue mainStyle = main.get("style"); String defaultStyle = mainStyle instanceof ThemeValue.LuaString ? ((ThemeValue.LuaString) mainStyle).getValue() : "light"; boolean dynamicDefault = mainStyle != null && !(mainStyle instanceof ThemeValue.LuaString);
        for (ThemeProjectFile keyboard : project.getKeyboards()) {
            try {
                String source = new String(readFileBytes(keyboard.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); com.osfans.trime.editor.core.ThemeDocument document = new ThemeLuaParser().parse(source).getDocument(); ThemeValue declared = document.get("style");
                if ((declared != null && !(declared instanceof ThemeValue.LuaString)) || (declared == null && dynamicDefault)) { usage.uncertain.add(keyboard.getName()); continue; }
                String styleId = declared instanceof ThemeValue.LuaString ? ((ThemeValue.LuaString) declared).getValue() : defaultStyle; if (!styleFile.getName().equals(styleId)) continue;
                int count = ThemeStyleEntities.referenceCount(source, entityId); if (count > 0) { usage.references.put(keyboard, count); usage.total += count; }
            } catch (Exception error) { usage.uncertain.add(keyboard.getName()); }
        }
        return usage;
    }

    private void promptRenameStyleEntity(ThemeProjectFile styleFile, String oldId, EntityUsage previewUsage) {
        if (ThemeStyleEntities.isReserved(oldId)) { workspace.setStatus("Reserved component style cannot be renamed: " + oldId); return; }
        if (!previewUsage.uncertain.isEmpty()) { workspace.setStatus("Rename blocked by uncertain keyboard references: " + android.text.TextUtils.join(", ", previewUsage.uncertain)); return; }
        LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "New entity ID", oldId + "_renamed"); String message = "Replace " + previewUsage.total + " static key references across " + previewUsage.references.size() + " keyboards. Style and keyboard files commit as one rollback-safe transaction.";
        new android.app.AlertDialog.Builder(this).setTitle("Rename style entity?").setMessage(message).setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Rename", (dialog, which) -> renameStyleEntityTransaction(styleFile, oldId, id.getText().toString().trim())).show();
    }

    private void renameStyleEntityTransaction(ThemeProjectFile styleFile, String oldId, String newId) {
        if (!ensureAssetWritable()) return;
        try {
            EntityUsage usage = collectEntityUsage(styleFile, oldId); if (!usage.uncertain.isEmpty()) throw new IOException("References became uncertain: " + android.text.TextUtils.join(", ", usage.uncertain));
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); String styleSource = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); originals.put(styleFile.getFile(), styleSource); changes.put(styleFile.getFile(), ThemeStyleEntities.rename(styleSource, oldId, newId));
            int changed = 0; for (ThemeProjectFile keyboard : usage.references.keySet()) { String source = new String(readFileBytes(keyboard.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); originals.put(keyboard.getFile(), source); ThemeStyleEntities.ReferenceUpdate update = ThemeStyleEntities.replaceKeyboardReferences(source, oldId, newId); if (update.getChangedKeys() > 0) { changes.put(keyboard.getFile(), update.getSource()); changed += update.getChangedKeys(); } }
            applyProjectSourceTransaction(changes, originals); workspace.setStatus("Renamed " + oldId + " to " + newId + " and replaced " + changed + " key references");
        } catch (Exception error) { workspace.setStatus("Style entity rename blocked: " + error.getMessage()); }
    }

    private void confirmDeleteStyleEntity(ThemeProjectFile styleFile, String id, EntityUsage previewUsage) {
        if (ThemeStyleEntities.isReserved(id)) { workspace.setStatus("Reserved component style cannot be deleted: " + id); return; }
        if (previewUsage.total > 0 || !previewUsage.uncertain.isEmpty()) { workspace.setStatus("Delete blocked: " + previewUsage.total + " references; uncertain keyboards: " + android.text.TextUtils.join(", ", previewUsage.uncertain)); return; }
        new android.app.AlertDialog.Builder(this).setTitle("Delete unreferenced style entity?").setMessage(id + "\nThis removes only its static style statements. Clone consumers are also checked at commit time.").setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> deleteStyleEntityTransaction(styleFile, id)).show();
    }

    private void deleteStyleEntityTransaction(ThemeProjectFile styleFile, String id) {
        if (!ensureAssetWritable()) return;
        try {
            EntityUsage usage = collectEntityUsage(styleFile, id); if (usage.total > 0 || !usage.uncertain.isEmpty()) throw new IOException("References changed after review; reopen entity manager");
            String source = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), ThemeStyleEntities.delete(source, id)); originals.put(styleFile.getFile(), source); applyProjectSourceTransaction(changes, originals); workspace.setStatus("Deleted unreferenced style entity " + id);
        } catch (Exception error) { workspace.setStatus("Style entity delete blocked: " + error.getMessage()); }
    }

    private void applyProjectSourceTransaction(java.util.LinkedHashMap<File, String> changes, java.util.Map<File, String> expectedOriginals) throws Exception { applyProjectSourceTransaction(changes, expectedOriginals, null); }
    private void applyProjectSourceTransaction(java.util.LinkedHashMap<File, String> changes, java.util.Map<File, String> expectedOriginals, java.util.Collection<File> expectedLuaManifest) throws Exception {
        if (changes.isEmpty()) return;
        final class Backup { final byte[] bytes; final String localHash; final String remoteHash; Backup(byte[] bytes, String localHash, String remoteHash) { this.bytes = bytes; this.localHash = localHash; this.remoteHash = remoteHash; } }
        java.util.LinkedHashMap<File, Backup> backups = new java.util.LinkedHashMap<>(); String root = project.getRoot().getCanonicalPath();
        validateProjectTransactionSnapshot(root, expectedOriginals, expectedLuaManifest);
        for (java.util.Map.Entry<File, String> change : changes.entrySet()) {
            File file = change.getKey(); if (!file.getCanonicalPath().startsWith(root + File.separator)) throw new IOException("Transaction file escapes project root");
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(change.getValue()); for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Updated " + file.getName() + " contains Lua errors");
            byte[] bytes = readFileBytes(file, 4L * 1024 * 1024); String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8); String localHash = ThemeSaveCoordinator.Companion.fingerprint(text), expected = expectedOriginals == null ? null : expectedOriginals.get(file); if (expected != null && !ThemeSaveCoordinator.Companion.fingerprint(expected).equals(localHash)) throw new IOException("Project file changed while preparing transaction: " + file.getName()); String remoteHash = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(file); if (remoteHash != null && !remoteHash.equals(localHash)) throw new IOException("Imported project differs from local cache; reload before transaction: " + file.getName()); backups.put(file, new Backup(bytes, localHash, remoteHash));
        }
        validateProjectTransactionSnapshot(root, expectedOriginals, expectedLuaManifest);
        for (java.util.Map.Entry<File, Backup> entry : backups.entrySet()) {
            String current = new String(readFileBytes(entry.getKey(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (!entry.getValue().localHash.equals(ThemeSaveCoordinator.Companion.fingerprint(current))) throw new IOException("Project file changed before transaction: " + entry.getKey().getName());
            if (importedProjectTreeUri != null && !entry.getValue().remoteHash.equals(fingerprintImportedProjectFile(entry.getKey()))) throw new IOException("Imported project file changed before transaction: " + entry.getKey().getName());
        }
        ThemeProjectFile current = repository instanceof DirectoryThemeProjectRepository ? ((DirectoryThemeProjectRepository) repository).getSelected() : null;
        try {
            for (java.util.Map.Entry<File, String> change : changes.entrySet()) new FileThemeProjectRepository(change.getKey()).write(change.getValue());
            for (File file : changes.keySet()) mirrorExistingProjectFile(file);
            refreshProjectAfterAssetMutation();
        } catch (Exception error) {
            for (java.util.Map.Entry<File, Backup> entry : backups.entrySet()) try { try (FileOutputStream output = new FileOutputStream(entry.getKey(), false)) { output.write(entry.getValue().bytes); output.getFD().sync(); } } catch (Exception restoreError) { error.addSuppressed(restoreError); }
            if (importedProjectTreeUri != null) for (java.util.Map.Entry<File, Backup> entry : backups.entrySet()) try { writeImportedProjectFile(entry.getKey(), new String(entry.getValue().bytes, java.nio.charset.StandardCharsets.UTF_8)); } catch (Exception restoreError) { error.addSuppressed(restoreError); }
            try { refreshProjectAfterAssetMutation(); } catch (Exception refreshError) { error.addSuppressed(refreshError); }
            throw error;
        }
        if (current != null && changes.containsKey(current.getFile())) { ThemeProjectFile reloaded = current.getKind() == ThemeProjectFile.Kind.STYLE ? project.style(current.getName()) : current.getKind() == ThemeProjectFile.Kind.KEYBOARD ? project.keyboard(current.getName()) : new ThemeProjectFile("main", project.getMainFile(), ThemeProjectFile.Kind.MAIN); if (reloaded != null) loadProjectFile(reloaded); }
        else { ThemeEditorModel refreshed = workspace.getModel(); applyPreviewStyles(refreshed); workspace.setModelKeepingHistory(refreshed); }
    }

    private void validateProjectTransactionSnapshot(String root, java.util.Map<File, String> expectedOriginals, java.util.Collection<File> expectedLuaManifest) throws Exception {
        if (expectedOriginals != null) for (java.util.Map.Entry<File, String> expected : expectedOriginals.entrySet()) {
            File file = expected.getKey(); String path = file.getCanonicalPath(); if (!path.startsWith(root + File.separator) || !file.isFile()) throw new IOException("Project snapshot file is unavailable: " + file.getName());
            String current = new String(readFileBytes(file, 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); String expectedHash = ThemeSaveCoordinator.Companion.fingerprint(expected.getValue()); if (!expectedHash.equals(ThemeSaveCoordinator.Companion.fingerprint(current))) throw new IOException("Project file changed after reference scan: " + file.getName());
            if (importedProjectTreeUri != null && !expectedHash.equals(fingerprintImportedProjectFile(file))) throw new IOException("Imported project changed after reference scan: " + file.getName());
        }
        if (expectedLuaManifest != null) {
            java.util.ArrayList<File> current = new java.util.ArrayList<>(); collectProjectLuaFiles(project.getRoot(), root, new java.util.HashSet<>(), current); java.util.HashSet<String> expectedPaths = new java.util.HashSet<>(), currentPaths = new java.util.HashSet<>(); for (File file : expectedLuaManifest) expectedPaths.add(file.getCanonicalPath()); for (File file : current) currentPaths.add(file.getCanonicalPath()); if (!expectedPaths.equals(currentPaths)) throw new IOException("Project Lua file set changed after reference scan; reopen the preset manager");
        }
    }

    private interface StyleIdAction { void run(String id) throws Exception; }
    private void promptStyleId(String title, String initial, StyleIdAction action) { if (!ensureAssetWritable()) return; LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "Style ID", initial); new android.app.AlertDialog.Builder(this).setTitle(title).setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { try { action.run(id.getText().toString().trim()); workspace.setStatus(title + " complete"); } catch (Exception error) { workspace.setStatus(title + " failed: " + error.getMessage()); } }).show(); }
    private void confirmDeleteStyle(ThemeProjectFile file) { if (!ensureAssetWritable()) return; if (isCurrentProjectFile(file)) { workspace.setStatus("Open another file before deleting the current style"); return; } new android.app.AlertDialog.Builder(this).setTitle("Delete style?").setMessage(file.getName()).setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> { try { ThemeProjectMutator.validateStyleDeletion(project, file); File directory = file.getFile().getParentFile(); if (importedProjectTreeUri != null) deleteImportedProjectPath(directory); deleteDirectory(directory); if (directory.exists()) throw new IOException("Cannot delete local style cache"); refreshProjectAfterAssetMutation(); workspace.setStatus("Deleted style " + file.getName()); } catch (Exception error) { workspace.setStatus("Style delete blocked: " + error.getMessage()); } }).show(); }

    private void loadRecentProject(Uri uri, String prefix, String name) throws IOException {
        DocumentFile tree = DocumentFile.fromTreeUri(this, uri); if (tree == null) throw new IOException("Recent tree unavailable"); DocumentFile source = prefix == null ? tree : tree.findFile(prefix); if (source == null || !source.isDirectory()) throw new IOException("Recent project directory unavailable");
        File cache = new File(getCacheDir(), "theme-editor-recent-" + System.nanoTime()); copyDocumentTree(source, cache); importedProjectUri = uri; importedProjectTreeUri = uri; importedProjectTreePrefix = prefix; openedImportedFingerprint = null; loadProject(cache, name);
    }

    private void showStructurePage() {
        ThemeEditorModel model = workspace.getModel(); StringBuilder text = new StringBuilder("Effective priority: rows > flex_box > keys > key_maps\nActive layout: ").append(model.layoutMode).append("\nKeys: ").append(model.keys.size());
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) text.append("\nRows: ").append(model.rows.size()).append("\nUse Row... and drag keys across rows.");
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) text.append("\nContainers: ").append(model.flexContainers.size()).append("\nUse Flex/Flex... to edit and reparent.");
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) text.append("\nPages: ").append(model.keyMapPages.size()).append("\nUse Page... for bulk operations.");
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) text.append("\nUse Keys... for grid, alignment, distribution and locks.");
        else text.append("\nNo literal keyboard layout; use Lua source.");
        String[] actions = model.layoutMode == ThemeEditorModel.LayoutMode.NONE ? new String[]{"Close"} : new String[]{"Migrate layout...", "Close"};
        new android.app.AlertDialog.Builder(this).setTitle("Keyboard structure").setMessage(text.toString()).setItems(actions, (dialog, which) -> { if (model.layoutMode != ThemeEditorModel.LayoutMode.NONE && which == 0) chooseLayoutMigrationTarget(model); }).show();
    }

    private void chooseLayoutMigrationTarget(ThemeEditorModel source) {
        java.util.ArrayList<ThemeEditorModel.LayoutMode> modes = new java.util.ArrayList<>(); java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (ThemeEditorModel.LayoutMode mode : new ThemeEditorModel.LayoutMode[]{ThemeEditorModel.LayoutMode.ROWS, ThemeEditorModel.LayoutMode.FLEX_BOX, ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS, ThemeEditorModel.LayoutMode.KEY_MAPS}) if (mode != source.layoutMode) { modes.add(mode); labels.add(mode.name()); }
        new android.app.AlertDialog.Builder(this).setTitle("Migrate " + source.layoutMode + " to").setItems(labels.toArray(new String[0]), (dialog, which) -> showLayoutMigrationPreview(source, modes.get(which))).setNegativeButton("Cancel", null).show();
    }

    private void showLayoutMigrationPreview(ThemeEditorModel source, ThemeEditorModel.LayoutMode target) {
        try {
            ThemeLayoutMigration.Preview preview = ThemeLayoutMigration.preview(source, target); StringBuilder message = new StringBuilder();
            message.append("Convert ").append(preview.getKeyCount()).append(" keys; containers ").append(preview.getSourceContainerCount()).append(" → ").append(preview.getTargetContainerCount()).append(".\n");
            if (preview.getOmittedKeyMapPages() > 0) message.append("Non-active pages omitted: ").append(preview.getOmittedKeyMapPages()).append(".\n");
            for (String note : preview.getNotes()) message.append("\n• ").append(note);
            String[] actions = {"Copy backup and convert", "Convert", "Hide original data and convert", "Cancel"};
            new android.app.AlertDialog.Builder(this).setTitle("Migration preview: " + source.layoutMode + " → " + target).setMessage(message.toString()).setItems(actions, (dialog, which) -> { if (which < 3) applyLayoutMigration(source, target, which == 0, which == 2); }).show();
        } catch (Exception error) { workspace.setStatus("Migration preview failed: " + error.getMessage()); }
    }

    private static void assertLayoutMigrationSafe(com.osfans.trime.editor.core.ThemeDocument document) throws IOException {
        java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
        for (String root : new String[]{"rows", "flex_box", "keys", "key_maps"}) if (containsRawLua(document.get(root))) throw new IOException("Dynamic value in layout root requires the Lua source page: " + root);
        for (com.osfans.trime.editor.core.ThemeSourceStatement statement : document.getSourceStatements()) { String path = statement.getPath(); if (path == null) continue; String root = path.contains(".") ? path.substring(0, path.indexOf('.')) : path; if (root.equals("rows") || root.equals("flex_box") || root.equals("keys") || root.equals("key_maps")) counts.put(path, counts.containsKey(path) ? counts.get(path) + 1 : 1); }
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) if (entry.getValue() > 1) throw new IOException("Duplicate layout assignment requires the Lua source page: " + entry.getKey());
    }

    private static boolean containsRawLua(ThemeValue value) {
        if (value instanceof ThemeValue.RawLuaNode) return true;
        if (value instanceof ThemeValue.LuaTable) for (ThemeValue child : ((ThemeValue.LuaTable) value).getFields().values()) if (containsRawLua(child)) return true;
        return false;
    }

    private void clearMigrationHistory() { migrationUndoDocument = null; migrationRedoDocument = null; migrationSourceMode = null; migrationTargetMode = null; applyingMigration = false; }

    private void applyLayoutMigration(ThemeEditorModel source, ThemeEditorModel.LayoutMode target, boolean backup, boolean hideOriginal) {
        if (!ensureWritable() || editor == null) return;
        com.osfans.trime.editor.core.ThemeDocument before = null;
        try {
            if (migrationUndoDocument != null) throw new IOException("Save or reload before starting another layout migration");
            if (!syncModel(source)) throw new IOException("Cannot synchronize the current layout before migration");
            before = editor.getDocument();
            assertLayoutMigrationSafe(before);
            File migrationBackup = backup ? createLayoutMigrationBackup(before) : null;
            ThemeLayoutMigration.Result result = ThemeLayoutMigration.migrate(before, source, target, hideOriginal);
            String candidate = com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(result.getDocument(), com.osfans.trime.editor.core.ThemeWriteMode.HYBRID);
            com.osfans.trime.editor.core.ParseResult verified = new ThemeLuaParser().parse(candidate); for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : verified.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Migrated source failed parse verification");
            migrationUndoDocument = before; migrationRedoDocument = verified.getDocument(); migrationSourceMode = source.layoutMode; migrationTargetMode = target;
            editor.replaceDocument(verified.getDocument()); applyingMigration = true;
            try { if (!workspace.replaceModelAsAtomic(toUiModel(verified.getDocument()), "Migrated " + source.layoutMode + " to " + target + " as one undo step")) throw new IOException("Workspace rejected migration"); }
            finally { applyingMigration = false; }
            layoutEditable = true; viewModel.setDirty(true); if (migrationBackup != null) workspace.setStatus("Migrated " + source.layoutMode + " to " + target + "; backup: keyboards/.editor-backups/" + migrationBackup.getName());
        } catch (Exception error) { applyingMigration = false; if (before != null) editor.replaceDocument(before); clearMigrationHistory(); workspace.setStatus("Layout migration failed; original draft retained: " + error.getMessage()); }
    }

    private File createLayoutMigrationBackup(com.osfans.trime.editor.core.ThemeDocument source) throws IOException {
        if (project == null || !(repository instanceof DirectoryThemeProjectRepository)) throw new IOException("A project keyboard must be open to create a migration backup");
        ThemeProjectFile selected = ((DirectoryThemeProjectRepository) repository).getSelected(); if (selected.getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("Open a keyboard asset before creating a migration backup");
        File backup = new File(project.getRoot(), "keyboards/.editor-backups/" + selected.getName() + "-" + System.currentTimeMillis() + ".lua");
        new FileThemeProjectRepository(backup).write(com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(source, com.osfans.trime.editor.core.ThemeWriteMode.HYBRID));
        try { mirrorCreatedProjectFile(backup); } catch (Exception error) { backup.delete(); try { deleteImportedProjectPath(backup); } catch (Exception ignored) { } throw error; }
        return backup;
    }

    private void showExportInstallPage() {
        String[] actions = {"Export verified ZIP", "Share verified ZIP", "Install to authorized directory", "Rollback last installation"};
        new android.app.AlertDialog.Builder(this).setTitle("Export and install").setItems(actions, (dialog, which) -> { if (which == 0) exportZip(false); else if (which == 1) exportZip(true); else if (which == 2) chooseInstallTarget(); else rollbackLastInstall(true); }).setNegativeButton("Close", null).show();
    }

    private void showRecoveryStatus() {
        String text = recoveryDraftFile().isFile() ? "Private draft exists for: " + recoveryIdentity() : "No private recovery draft";
        File journal = new File(getFilesDir(), "theme-editor-install.journal"); if (journal.isFile()) text += "\nInstallation journal is available.";
        new android.app.AlertDialog.Builder(this).setTitle("Recovery status").setMessage(text).setNegativeButton("Close", null).setPositiveButton("Delete private draft", (dialog, which) -> { deleteRecoveryDraft(); workspace.setStatus("Private recovery draft deleted"); }).show();
    }

    private boolean ensureWritable() {
        if (!readOnlySession) return true;
        workspace.setStatus("Read-only: this project is already open in another editor session"); Toast.makeText(this, "Second session is read-only", Toast.LENGTH_LONG).show(); return false;
    }

    private boolean ensureAssetWritable() {
        if (!ensureWritable()) return false;
        if (viewModel.getDirty()) { workspace.setStatus("Save or discard current file changes before modifying project assets"); Toast.makeText(this, "Save current changes first", Toast.LENGTH_LONG).show(); return false; }
        return true;
    }

    private void claimSession(String identity) {
        String next = identity;
        synchronized (ACTIVE_WRITE_SESSIONS) {
            if (java.util.Objects.equals(sessionKey, next)) return;
            String token = viewModel.getSessionToken();
            if (sessionKey != null && token.equals(ACTIVE_WRITE_SESSIONS.get(sessionKey))) ACTIVE_WRITE_SESSIONS.remove(sessionKey);
            sessionKey = next; String owner = next == null ? null : ACTIVE_WRITE_SESSIONS.get(next); readOnlySession = owner != null && !owner.equals(token);
            if (next != null && !readOnlySession) ACTIVE_WRITE_SESSIONS.put(next, token);
        }
        workspace.setClipboardScope(next);
        workspace.setReadOnly(readOnlySession);
        if (readOnlySession) workspace.setStatus("Opened read-only: another editor session owns this project");
    }

    private String sessionIdentity() {
        if (importedProjectUri != null) return "import:" + importedProjectUri.normalizeScheme() + "|" + (importedProjectTreePrefix == null ? "" : importedProjectTreePrefix);
        if (project != null) try { return "project:" + project.getRoot().getCanonicalPath(); } catch (IOException ignored) { return "project:" + project.getRoot().getAbsolutePath(); }
        return currentUri == null ? null : "file:" + currentUri.normalizeScheme();
    }

    private void loadFile(File file) {
        if (file == null || !file.isFile()) return;
        importedProjectUri = null; importedProjectTreeUri = null; importedProjectTreePrefix = null; openedImportedFingerprint = null;
        if (file.getName().equals("main.lua")) {
            loadProject(file.getParentFile(), file.getParentFile().getName());
            return;
        }
        project = null;
        repository = new FileThemeProjectRepository(file);
        currentUri = Uri.fromFile(file);
        viewModel.setCurrentUri(currentUri);
        loadRepository();
    }

    private void requestProjectFileSwitch(ThemeProjectFile file) {
        if (file == null || (currentUri != null && currentUri.equals(Uri.fromFile(file.getFile())))) return;
        if (!viewModel.getDirty()) {
            loadProjectFile(file);
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("Save the current Lua file before switching?")
                .setPositiveButton("Save", (dialog, which) -> {
                    saveModel(workspace.getModel());
                    if (!viewModel.getDirty()) loadProjectFile(file);
                })
                .setNegativeButton("Discard", (dialog, which) -> {
                    viewModel.setDirty(false);
                    deleteRecoveryDraft();
                    loadProjectFile(file);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void loadProjectFile(ThemeProjectFile file) {
        if (file == null || !file.getFile().isFile()) return;
        clearMigrationHistory();
        repository = new DirectoryThemeProjectRepository(project, file);
        recoveryPrompted = false;
        currentUri = Uri.fromFile(file.getFile());
        viewModel.setCurrentUri(currentUri);
        loadRepository();
        invalidateOptionsMenu();
    }

    private void loadProject(File root, String displayName) {
        try {
            clearMigrationHistory();
            project = ThemeProject.Companion.discover(root); projectDisplayName = displayName == null || displayName.trim().isEmpty() ? root.getName() : displayName;
            com.osfans.trime.editor.project.ThemeProjectRepository mainRepository =
                    new FileThemeProjectRepository(project.getMainFile());
            projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
            com.osfans.trime.editor.core.ParseResult main = projectSnapshot.getMain();
            ThemeProjectFile selected = projectSnapshot.getKeyboardSource();
            if (selected == null) selected = new ThemeProjectFile("main", project.getMainFile(), ThemeProjectFile.Kind.MAIN);
            repository = new DirectoryThemeProjectRepository(project, selected);
            currentUri = Uri.fromFile(selected.getFile());
            viewModel.setCurrentUri(currentUri); claimSession(sessionIdentity());
            editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            com.osfans.trime.editor.core.ParseResult parsed = editor.load(repository);
            openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(repository.read());
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            openedFingerprint = ThemeSourceFingerprint.Companion.capture(selected.getFile());
            openedImportedFingerprint = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(selected.getFile());
            viewModel.setDirty(false);
            int diagnosticCount = ThemeProjectDiagnostics.INSTANCE.collect(projectSnapshot, new ThemeFieldRegistry()).size() + parsed.getDiagnostics().size();
            workspace.setStatus("Project " + root.getName() + ": " + project.getStyles().size() + " styles, " + project.getKeyboards().size() + " keyboards, " + diagnosticCount + " diagnostics");
            invalidateOptionsMenu();
            offerRecoveryDraft();
        } catch (Exception error) {
            project = null;
            workspace.setStatus("Project load failed: " + error.getMessage());
            Toast.makeText(this, "Unable to load theme project", Toast.LENGTH_LONG).show();
        }
    }

    private void loadTree(Uri uri) {
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(this, uri);
            if (tree == null) throw new IOException("Cannot open theme folder");
            File root = new File(getCacheDir(), "theme-editor-tree-" + System.nanoTime());
            copyDocumentTree(tree, root);
            importedProjectUri = uri; importedProjectTreeUri = uri; importedProjectTreePrefix = null;
            rememberRecentProject(uri, tree.getName(), null);
            loadProject(root, tree.getName());
            workspace.setStatus("Imported theme folder: " + root.getName());
        } catch (Exception error) {
            workspace.setStatus("Folder import failed: " + error.getMessage());
            Toast.makeText(this, "Unable to import theme folder", Toast.LENGTH_LONG).show();
        }
    }

    private void copyDocumentTree(DocumentFile source, File destination) throws IOException {
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("Cannot create cache directory");
        for (DocumentFile child : source.listFiles()) {
            File target = new File(destination, child.getName() == null ? "unnamed" : child.getName());
            if (child.isDirectory()) {
                copyDocumentTree(child, target);
            } else if (child.isFile()) {
                try (InputStream input = getContentResolver().openInputStream(child.getUri())) {
                    if (input == null) throw new IOException("Cannot read " + child.getName());
                    target.getParentFile().mkdirs();
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    private String documentName(Uri uri) {
        try { DocumentFile file = DocumentFile.fromSingleUri(this, uri); if (file != null && file.getName() != null) return file.getName(); } catch (Exception ignored) { }
        String segment = uri.getLastPathSegment(); return segment == null ? "theme" : segment.substring(segment.lastIndexOf('/') + 1);
    }

    private void loadUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        String name = documentName(uri).toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".zip") || "application/zip".equalsIgnoreCase(getContentResolver().getType(uri))) {
            loadZip(uri);
            return;
        }
        importedProjectUri = null; importedProjectTreeUri = null; importedProjectTreePrefix = null; openedImportedFingerprint = null; recoveryPrompted = false;
        currentUri = uri;
        viewModel.setCurrentUri(uri);
        repository = new UriThemeProjectRepository(getContentResolver(), uri);
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
            importedProjectUri = uri; importedProjectTreeUri = null; importedProjectTreePrefix = null; openedImportedFingerprint = null;
            String archiveName = documentName(uri); if (archiveName.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) archiveName = archiveName.substring(0, archiveName.length() - 4);
            String displayName = main.getParentFile().equals(root) ? archiveName : main.getParentFile().getName();
            loadProject(main.getParentFile(), displayName);
            workspace.setStatus("Imported ZIP: " + main.getParentFile());
        } catch (Exception error) {
            workspace.setStatus("ZIP import failed: " + error.getMessage());
            Toast.makeText(this, "Unable to import ZIP", Toast.LENGTH_LONG).show();
        }
    }

    private static File findMainLua(File root) {
        File direct = new File(root, "main.lua"); if (direct.isFile()) return direct;
        File[] children = root.listFiles(); if (children == null) return null;
        java.util.ArrayList<File> candidates = new java.util.ArrayList<>();
        for (File child : children) if (child.isDirectory() && new File(child, "main.lua").isFile()) {
            if (new File(child, "keyboards").isDirectory() || new File(child, "styles").isDirectory()) candidates.add(new File(child, "main.lua"));
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private void loadRepository() {
        try {
            clearMigrationHistory();
            claimSession(sessionIdentity());
            editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            com.osfans.trime.editor.core.ParseResult parsed = editor.load(repository);
            openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(repository.read());
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            if (project != null) projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            if (repository instanceof DirectoryThemeProjectRepository) {
                openedFingerprint = ThemeSourceFingerprint.Companion.capture(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            } else {
                openedFingerprint = null;
            }
            if (repository instanceof DirectoryThemeProjectRepository && importedProjectTreeUri != null) openedImportedFingerprint = fingerprintImportedProjectFile(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            else if (importedProjectTreeUri == null) openedImportedFingerprint = null;
            viewModel.setDirty(false);
            workspace.setStatus("Loaded " + currentUri + " (" + parsed.getDiagnostics().size() + " diagnostics)" + (layoutEditable ? "" : "; no structured keyboard layout in this file"));
            offerRecoveryDraft();
        } catch (Exception error) {
            workspace.setStatus("Load failed: " + error.getMessage());
            Toast.makeText(this, "Unable to load theme", Toast.LENGTH_LONG).show();
        }
    }

    private static final class ImportedDocumentRef {
        final DocumentFile parent;
        final DocumentFile file;
        final String name;
        ImportedDocumentRef(DocumentFile parent, DocumentFile file, String name) { this.parent = parent; this.file = file; this.name = name; }
    }

    private ImportedDocumentRef importedDocumentRef(File file, boolean create) throws IOException {
        if (importedProjectTreeUri == null || project == null) return null;
        String rootPath = project.getRoot().getCanonicalPath(), filePath = file.getCanonicalPath();
        if (!filePath.startsWith(rootPath + File.separator)) throw new IOException("Imported file escapes project root");
        String[] parts = filePath.substring(rootPath.length() + 1).split(java.util.regex.Pattern.quote(File.separator));
        if (parts.length == 0) throw new IOException("Imported file has no relative path");
        DocumentFile parent = DocumentFile.fromTreeUri(this, importedProjectTreeUri); if (parent == null) throw new IOException("Imported project permission is unavailable");
        if (importedProjectTreePrefix != null && !importedProjectTreePrefix.isEmpty()) { DocumentFile child = parent.findFile(importedProjectTreePrefix); if (child == null || !child.isDirectory()) throw new IOException("Created project directory is unavailable"); parent = child; }
        for (int i = 0; i < parts.length - 1; i++) {
            DocumentFile next = parent.findFile(parts[i]); if (next == null && create) next = parent.createDirectory(parts[i]);
            if (next == null || !next.isDirectory()) return null; parent = next;
        }
        DocumentFile target = parent.findFile(parts[parts.length - 1]);
        if (target == null && create) target = parent.createFile(mimeForName(parts[parts.length - 1]), parts[parts.length - 1]);
        return new ImportedDocumentRef(parent, target, parts[parts.length - 1]);
    }

    private String fingerprintImportedProjectFile(File file) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(file, false); if (ref == null || ref.file == null || !ref.file.isFile()) throw new IOException("Imported source file is missing");
        return fingerprintDocument(ref.file);
    }

    private static String fingerprintStream(InputStream input) throws IOException {
        try { java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff)); return result.toString(); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IOException("SHA-256 unavailable", error); }
    }

    private void writeImportedProjectFile(File cacheFile, String source) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(cacheFile, false); if (ref == null || ref.file == null || !ref.file.isFile()) throw new IOException("Imported target file is missing");
        DocumentFile target = ref.file, parent = ref.parent; String name = ref.name;
        DocumentFile backup = parent.createFile(mimeForName(name), "." + name + ".editor-backup-" + System.nanoTime());
        DocumentFile temporary = parent.createFile(mimeForName(name), "." + name + ".editor-temp-" + System.nanoTime());
        if (backup == null || temporary == null) { if (backup != null) backup.delete(); if (temporary != null) temporary.delete(); throw new IOException("Cannot create SAF save transaction files"); }
        boolean backupReady = false, targetDeleted = false, restored = false;
        try {
            String originalFingerprint = fingerprintDocument(target); copyDocumentFile(target, backup);
            if (!originalFingerprint.equals(fingerprintDocument(backup))) throw new IOException("SAF backup verification failed");
            backupReady = true; writeDocumentText(temporary, source);
            String expected = ThemeSaveCoordinator.Companion.fingerprint(source);
            if (!expected.equals(fingerprintDocument(temporary))) throw new IOException("SAF temporary file verification failed");
            if (!target.delete()) throw new IOException("Cannot replace imported source"); targetDeleted = true;
            DocumentFile replacement = parent.createFile(mimeForName(name), name); if (replacement == null) throw new IOException("Cannot create imported replacement");
            copyDocumentFile(temporary, replacement);
            if (!expected.equals(fingerprintDocument(replacement))) throw new IOException("SAF replacement verification failed");
            backup.delete();
            temporary.delete();
        } catch (Exception error) {
            if (backupReady && targetDeleted) {
                DocumentFile current = parent.findFile(name); if (current != null) current.delete();
                DocumentFile replacement = parent.createFile(mimeForName(name), name);
                if (replacement != null) try { copyDocumentFile(backup, replacement); restored = fingerprintDocument(backup).equals(fingerprintDocument(replacement)); } catch (Exception ignored) { }
            }
            temporary.delete(); if (restored || !backupReady) backup.delete();
            throw new IOException(restored ? "SAF save failed; backup restored" : backupReady ? "SAF save failed; backup retained for recovery" : "SAF save failed before replacing the original", error);
        }
    }

    private void writeDocumentText(DocumentFile file, String source) throws IOException {
        try (java.io.OutputStream output = getContentResolver().openOutputStream(file.getUri(), "wt")) { if (output == null) throw new IOException("Cannot write SAF file"); output.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    private void copyDocumentFile(DocumentFile source, DocumentFile target) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(source.getUri()); java.io.OutputStream output = getContentResolver().openOutputStream(target.getUri(), "wt")) { if (input == null || output == null) throw new IOException("Cannot copy SAF file"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
    }
    private String fingerprintDocument(DocumentFile file) throws IOException { try (InputStream input = getContentResolver().openInputStream(file.getUri())) { if (input == null) throw new IOException("Cannot verify SAF file"); return fingerprintStream(input); } }

    private void refreshImportedCacheFile() throws IOException {
        if (!(repository instanceof DirectoryThemeProjectRepository) || importedProjectTreeUri == null) return;
        File cacheFile = ((DirectoryThemeProjectRepository) repository).getSelected().getFile(); ImportedDocumentRef ref = importedDocumentRef(cacheFile, false); if (ref == null || ref.file == null) throw new IOException("Imported source is missing");
        try (InputStream input = getContentResolver().openInputStream(ref.file.getUri()); FileOutputStream output = new FileOutputStream(cacheFile, false)) { if (input == null) throw new IOException("Cannot read imported source"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); output.getFD().sync(); }
        openedImportedFingerprint = fingerprintDocument(ref.file);
    }

    private void mirrorExistingProjectFile(File file) throws IOException { if (importedProjectTreeUri != null) writeImportedProjectFile(file, new String(readFileBytes(file, 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8)); }
    private void mirrorCreatedProjectFile(File file) throws IOException { if (importedProjectTreeUri != null) { ImportedDocumentRef ref = importedDocumentRef(file, true); if (ref == null || ref.file == null) throw new IOException("Cannot create SAF project file"); try (FileInputStream input = new FileInputStream(file); java.io.OutputStream output = getContentResolver().openOutputStream(ref.file.getUri(), "wt")) { if (output == null) throw new IOException("Cannot mirror project file"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); } } }
    private void mirrorRenamedProjectFile(File old, File renamed) throws IOException { if (importedProjectTreeUri != null) { mirrorCreatedProjectFile(renamed); try { deleteImportedProjectPath(old); } catch (IOException error) { try { deleteImportedProjectPath(renamed); } catch (Exception ignored) { } throw error; } } }
    private void mirrorCreatedProjectDirectory(File directory) throws IOException { if (importedProjectTreeUri == null) return; File[] children = directory.listFiles(); if (children == null) return; for (File child : children) { if (child.isDirectory()) mirrorCreatedProjectDirectory(child); else mirrorCreatedProjectFile(child); } }
    private void mirrorRenamedProjectDirectory(File old, File renamed) throws IOException { if (importedProjectTreeUri != null) { mirrorCreatedProjectDirectory(renamed); try { deleteImportedProjectPath(old); } catch (IOException error) { try { deleteImportedProjectPath(renamed); } catch (Exception ignored) { } throw error; } } }
    private void deleteImportedProjectPath(File path) throws IOException { if (importedProjectTreeUri == null) return; ImportedDocumentRef ref = importedDocumentRef(path.isDirectory() ? new File(path, "main.lua") : path, false); DocumentFile target = path.isDirectory() ? (ref == null ? null : ref.parent) : (ref == null ? null : ref.file); if (target != null && !target.delete()) throw new IOException("Cannot delete SAF project path"); }
    private void refreshProjectAfterAssetMutation() throws IOException { project = ThemeProject.Companion.discover(project.getRoot()); projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser()); invalidateOptionsMenu(); }

    private static String findLayoutRoot(com.osfans.trime.editor.core.ThemeDocument document) {
        if (document.get("rows") instanceof ThemeValue.LuaTable) return "rows";
        if (document.get("flex_box") instanceof ThemeValue.LuaTable) return "flex_box";
        if (document.get("keys") instanceof ThemeValue.LuaTable) return "keys";
        if (document.get("key_maps") instanceof ThemeValue.LuaTable) return "key_maps";
        return null;
    }

    private boolean syncUndoModel(ThemeEditorModel model) {
        if (migrationUndoDocument != null && migrationSourceMode == model.layoutMode && migrationTargetMode == layoutModeForRoot(findLayoutRoot(editor.getDocument()))) { editor.replaceDocument(migrationUndoDocument); return true; }
        return syncModel(model);
    }

    private boolean syncRedoModel(ThemeEditorModel model) {
        if (migrationRedoDocument != null && migrationTargetMode == model.layoutMode && migrationSourceMode == layoutModeForRoot(findLayoutRoot(editor.getDocument()))) { editor.replaceDocument(migrationRedoDocument); return true; }
        return syncModel(model);
    }

    private static ThemeEditorModel.LayoutMode layoutModeForRoot(String root) {
        if ("rows".equals(root)) return ThemeEditorModel.LayoutMode.ROWS; if ("flex_box".equals(root)) return ThemeEditorModel.LayoutMode.FLEX_BOX; if ("keys".equals(root)) return ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS; if ("key_maps".equals(root)) return ThemeEditorModel.LayoutMode.KEY_MAPS; return ThemeEditorModel.LayoutMode.NONE;
    }

    private boolean syncModel(ThemeEditorModel model) {
        if (editor == null || model == null || model.layoutMode == ThemeEditorModel.LayoutMode.NONE) return false;
        String root = findLayoutRoot(editor.getDocument());
        if (root == null || editor.getDocument().get(root) instanceof ThemeValue.RawLuaNode) { workspace.setStatus("Layout uses dynamic Lua; edit it in the Lua source"); return false; }
        int rootAssignments = 0; for (com.osfans.trime.editor.core.ThemeSourceStatement statement : editor.getDocument().getSourceStatements()) if (root.equals(statement.getPath())) rootAssignments++;
        if (rootAssignments > 1) { workspace.setStatus("Duplicate layout assignments require the Lua source editor"); return false; }
        try {
            com.osfans.trime.editor.core.ThemeDocument updated = ThemeLayoutCodec.writeAgainstOriginal(editor.getDocument(), model);
            com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(updated, com.osfans.trime.editor.core.ThemeWriteMode.HYBRID);
            editor.replaceDocument(updated); return true;
        } catch (Exception error) { workspace.setStatus("Structured update blocked: " + error.getMessage()); return false; }
    }

    private void showKeyEventManager(ThemeEditorModel.Key key) {
        if (!ensureAssetWritable()) return;
        try {
            if (key == null || key.sourcePath == null || key.sourcePath.isEmpty()) throw new IOException("This key has no stable source path; save/reload or use Lua source");
            if (!(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD || editor == null) throw new IOException("Open a project keyboard first");
            java.util.List<ThemeKeyEvents.Slot> slots = ThemeKeyEvents.read(editor.getDocument(), key.sourcePath); ThemeKeyEvents.Options options = ThemeKeyEvents.options(editor.getDocument(), key.sourcePath); ThemeKeyEvents.Hints hints = ThemeKeyEvents.hints(editor.getDocument(), key.sourcePath);
            String[] labels = new String[slots.size() + 4]; for (int i = 0; i < slots.size(); i++) { ThemeKeyEvents.Slot slot = slots.get(i); labels[i] = slot.getName() + " — " + slot.getSource() + eventSlotSummary(slot); } labels[slots.size()] = "swipe_repeatable — " + nullableBoolean(options.getSwipeRepeatable()); labels[slots.size() + 1] = "send_bindings — " + nullableBoolean(options.getSendBindings()) + "; effective=" + options.getEffectiveSendBindings() + " (" + options.getSendBindingsSource() + ")"; labels[slots.size() + 2] = "event hints — missing values fall back to event labels"; labels[slots.size() + 3] = "long/repeat click time — inherited from key style entity";
            new android.app.AlertDialog.Builder(this).setTitle("Key events — static only").setMessage("No event, command, script, Intent, commit, or callback will execute.").setItems(labels, (dialog, which) -> { if (which < slots.size()) editKeyEventSlot(key, slots.get(which)); else if (which < slots.size() + 2) editKeyEventOptions(key, options); else if (which == slots.size() + 2) editKeyEventHints(key, hints); else workspace.setStatus("long_click_time and repeat_click_time belong to the resolved key style; edit that style entity, not this key source"); }).setNegativeButton("Close", null).setNeutralButton("View Lua", (dialog, which) -> showCodeEditor()).show();
        } catch (Exception error) { workspace.setStatus("Key event manager blocked: " + error.getMessage()); }
    }

    private static String nullableBoolean(Boolean value) { return value == null ? "inherit" : value ? "true" : "false"; }
    private static String eventSlotSummary(ThemeKeyEvents.Slot slot) { if (slot.getLiteral() != null) return " = " + slot.getLiteral(); if (slot.getEvent() != null) return " = " + presetSummary(slot.getEvent()); return slot.getRisky() ? " [code-only]" : ""; }

    private void editKeyEventSlot(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        if (slot.getSource() == ThemeKeyEvents.Source.RAW_LUA || slot.getSource() == ThemeKeyEvents.Source.FULL_KEY_REPLACEMENT) { workspace.setStatus(slot.getName() + " is " + slot.getSource() + "; use Lua source"); showCodeEditor(); return; }
        boolean stringOnly = java.util.Arrays.asList(ThemeKeyEvents.STRING_ONLY_SLOTS).contains(slot.getName());
        String[] modes = stringOnly ? new String[]{"String/preset reference", "Clear"} : new String[]{"String/preset reference", "Inline event table", "Clear"}; int selected = slot.getSource() == ThemeKeyEvents.Source.INLINE_EVENT ? 1 : slot.getSource() == ThemeKeyEvents.Source.MISSING ? modes.length - 1 : 0;
        new android.app.AlertDialog.Builder(this).setTitle("Edit " + slot.getName()).setMessage(stringOnly ? "The current Trime runtime consumes this state replacement only as a string." : "Static source selection; nothing executes.").setSingleChoiceItems(modes, selected, (dialog, which) -> { dialog.dismiss(); if (which == 0) editKeyEventString(key, slot); else if (!stringOnly && which == 1) editInlineKeyEvent(key, slot); else commitKeyEventChange(key, document -> ThemeKeyEvents.updateString(document, key.sourcePath, slot.getName(), null), "Cleared " + slot.getName()); }).setNegativeButton("Cancel", null).show();
    }

    private void editKeyEventString(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        LinearLayout fields = new LinearLayout(this); EditText value = simpleField(fields, "Literal event or preset ID", slot.getLiteral() == null ? "" : slot.getLiteral());
        new android.app.AlertDialog.Builder(this).setTitle(slot.getName() + " string source").setMessage("A .lua suffix or command preset is retained but never executed in preview.").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> commitKeyEventChange(key, document -> ThemeKeyEvents.updateString(document, key.sourcePath, slot.getName(), value.getText().toString()), "Updated " + slot.getName() + " string event")).show();
    }

    private void editInlineKeyEvent(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        ThemePresetEvents.Event event = slot.getEvent() == null ? new ThemePresetEvents.Event(slot.getName(), "", "", "", "", "", "", "", "", "", "", java.util.Collections.emptyList(), "", false, false, true, null) : slot.getEvent();
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText label = simpleField(fields, "label", event.getLabel()); EditText send = simpleField(fields, "send", event.getSend()); EditText text = simpleField(fields, "text", event.getText()); EditText commit = simpleField(fields, "commit", event.getCommit()); EditText command = simpleField(fields, "command (never executed)", event.getCommand()); EditText option = simpleField(fields, "option", event.getOption()); EditText select = simpleField(fields, "select", event.getSelect()); EditText toggle = simpleField(fields, "toggle", event.getToggle()); EditText preview = simpleField(fields, "preview", event.getPreview()); EditText description = simpleField(fields, "description", event.getDescription()); EditText states = simpleField(fields, "states: one per line; \\0 empty, \\n embedded newline", formatEventStates(event.getStates())); states.setSingleLine(false); states.setMinLines(3); EditText shiftLock = simpleField(fields, "shift_lock", event.getShiftLock()); EditText index = simpleField(fields, "index (preserved; unreliable)", event.getIndex() == null ? "" : event.getIndex().toString()); android.widget.CheckBox repeatable = new android.widget.CheckBox(this); repeatable.setText("repeatable"); repeatable.setChecked(event.getRepeatable()); fields.addView(repeatable); android.widget.CheckBox sticky = new android.widget.CheckBox(this); sticky.setText("sticky"); sticky.setChecked(event.getSticky()); fields.addView(sticky); android.widget.CheckBox functional = new android.widget.CheckBox(this); functional.setText("functional"); functional.setChecked(event.getFunctional()); fields.addView(functional); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle(slot.getName() + " inline event").setMessage("Static table only; no field executes.").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> { try { java.util.ArrayList<String> nextStates = parseEventStates(states.getText().toString()); Double nextIndex = index.getText().toString().trim().isEmpty() ? null : Double.valueOf(index.getText().toString().trim()); ThemePresetEvents.Event next = new ThemePresetEvents.Event(slot.getName(), send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), nextStates, shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); commitKeyEventChange(key, document -> ThemeKeyEvents.updateInline(document, key.sourcePath, slot.getName(), next), "Updated " + slot.getName() + " inline event; nothing executed"); } catch (Exception error) { workspace.setStatus("Inline event blocked: " + error.getMessage()); } }).show();
    }

    private void editKeyEventHints(ThemeEditorModel.Key key, ThemeKeyEvents.Hints hints) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); java.util.LinkedHashMap<String, EditText> inputs = new java.util.LinkedHashMap<>(); java.util.LinkedHashMap<String, android.widget.CheckBox> inherit = new java.util.LinkedHashMap<>();
        for (String name : ThemeKeyEvents.HINTS) { android.widget.CheckBox useFallback = new android.widget.CheckBox(this); useFallback.setText(name + " missing → event label fallback"); useFallback.setChecked(hints.getValues().get(name) == null); fields.addView(useFallback); EditText input = simpleField(fields, name + " (empty remains explicit empty)", hints.getValues().get(name) == null ? "" : hints.getValues().get(name)); input.setEnabled(!useFallback.isChecked()); useFallback.setOnCheckedChangeListener((button, checked) -> input.setEnabled(!checked)); inherit.put(name, useFallback); inputs.put(name, input); }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2)); new android.app.AlertDialog.Builder(this).setTitle("Event hints").setMessage("A missing hint falls back to the corresponding event label; explicit empty does not.").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> commitKeyEventChange(key, document -> { java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(); for (String name : ThemeKeyEvents.HINTS) values.put(name, inherit.get(name).isChecked() ? null : inputs.get(name).getText().toString()); return ThemeKeyEvents.updateHints(document, key.sourcePath, values); }, "Updated event hints with source fallbacks preserved")).show();
    }

    private void editKeyEventOptions(ThemeEditorModel.Key key, ThemeKeyEvents.Options options) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); String[] values = {"inherit", "false", "true"}; android.widget.Spinner swipe = new android.widget.Spinner(this); swipe.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); swipe.setSelection(options.getSwipeRepeatable() == null ? 0 : options.getSwipeRepeatable() ? 2 : 1); fields.addView(swipe); android.widget.Spinner bindings = new android.widget.Spinner(this); bindings.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); bindings.setSelection(options.getSendBindings() == null ? 0 : options.getSendBindings() ? 2 : 1); fields.addView(bindings);
        new android.app.AlertDialog.Builder(this).setTitle("Key event flags").setMessage("swipe_repeatable then send_bindings; inherit preserves missing fields and Trime runtime defaults.").setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> commitKeyEventChange(key, document -> ThemeKeyEvents.updateOptions(document, key.sourcePath, spinnerBoolean(swipe), spinnerBoolean(bindings)), "Updated key event flags")).show();
    }

    private static Boolean spinnerBoolean(android.widget.Spinner value) { return value.getSelectedItemPosition() == 0 ? null : value.getSelectedItemPosition() == 2; }
    private interface KeyDocumentMutation { com.osfans.trime.editor.core.ThemeDocument apply(com.osfans.trime.editor.core.ThemeDocument document) throws Exception; }
    private void commitKeyEventChange(ThemeEditorModel.Key key, KeyDocumentMutation mutation, String success) {
        try {
            if (!ensureAssetWritable() || !(repository instanceof DirectoryThemeProjectRepository)) return; ThemeProjectFile file = ((DirectoryThemeProjectRepository) repository).getSelected(); if (file.getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("Open a keyboard file first"); String latest = new String(readFileBytes(file.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); String loaded = com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(editor.getDocument(), com.osfans.trime.editor.core.ThemeWriteMode.HYBRID); if (!ThemeSaveCoordinator.Companion.fingerprint(latest).equals(ThemeSaveCoordinator.Companion.fingerprint(loaded))) throw new IOException("Keyboard changed outside the loaded editor; reload before editing this key"); com.osfans.trime.editor.core.ThemeDocument document = ThemeKeyEvents.parseDocument(latest); if (document.get(key.sourcePath) == null) throw new IOException("Key source path changed; reload keyboard"); String updated = ThemeKeyEvents.verifiedSource(mutation.apply(document)); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(file.getFile(), updated); originals.put(file.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success);
        } catch (Exception error) { workspace.setStatus("Key event update blocked: " + error.getMessage()); }
    }

    private void copyStyleEntity(ThemeEditorModel.Key key) {
        try {
            if (project == null || editor == null || !(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("Open a project keyboard first");
            ThemeProjectFile styleSource = resolvedStyleSource(editor.getDocument()); if (styleSource == null) throw new IOException("The keyboard style asset cannot be resolved statically");
            String source = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.ArrayList<String> entityIds = new java.util.ArrayList<>(); for (ThemeStyleEntities.Entry entry : ThemeStyleEntities.list(source)) entityIds.add(entry.getId());
            String styleId = ThemeKeyStyleBatch.effectiveStyleId(key, entityIds); ThemeStyleEntities.Snapshot snapshot = ThemeStyleEntities.extract(source, styleId);
            workspace.storeStyleEntityClipboard(snapshot);
        } catch (Exception error) { workspace.setStatus("Style entity copy blocked: " + error.getMessage()); }
    }

    private void promptPasteStyleEntity(java.util.List<ThemeEditorModel.Key> keys) {
        if (!ensureAssetWritable()) return;
        try {
            if (keys == null || keys.isEmpty()) throw new IOException("Select one or more target keys first");
            ThemeEditorClipboard.Payload payload = workspace.styleEntityClipboard();
            if (payload == null || payload.styleEntity == null) throw new IOException("Private clipboard does not contain a complete style entity");
            if (project == null || editor == null || !(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("Open a target project keyboard first");
            ThemeProjectFile styleSource = resolvedStyleSource(editor.getDocument()); if (styleSource == null) throw new IOException("The target keyboard style asset cannot be resolved statically");
            boolean crossProject = workspace.isCrossProjectClipboard(payload); ThemeStyleEntities.Snapshot snapshot = payload.styleEntity;
            java.util.ArrayList<String> missing = missingStyleEntityResources(styleSource, snapshot);
            if (!missing.isEmpty()) throw new IOException("Target project is missing style resources: " + android.text.TextUtils.join(", ", missing));
            String original = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            String expectedLocal = ThemeSaveCoordinator.Companion.fingerprint(original), expectedRemote = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(styleSource.getFile());
            LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "New style entity ID", snapshot.getId() + "_copy");
            String message = "Source entity: " + snapshot.getId() + "\nTarget style asset: " + styleSource.getName() + "\nTarget keys: " + keys.size() + (snapshot.getCloneParent() == null ? "" : "\nClone dependency: " + snapshot.getCloneParent()) + (snapshot.getReferencedResources().isEmpty() ? "" : "\nResources: " + android.text.TextUtils.join(", ", snapshot.getReferencedResources())) + (crossProject ? "\n\nCross-project paste: no URI/path metadata is retained; dependencies were verified by name." : "");
            new android.app.AlertDialog.Builder(this).setTitle("Paste complete style entity").setMessage(message).setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Paste", (dialog, which) -> pasteStyleEntity(styleSource, snapshot, id.getText().toString().trim(), keys, expectedLocal, expectedRemote)).show();
        } catch (Exception error) { workspace.setStatus("Style entity paste blocked: " + error.getMessage()); Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private java.util.ArrayList<String> missingStyleEntityResources(ThemeProjectFile styleSource, ThemeStyleEntities.Snapshot snapshot) throws IOException {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>(); String root = project.getRoot().getCanonicalPath();
        for (String relative : snapshot.getReferencedResources()) {
            boolean found = false; for (File base : new File[]{styleSource.getFile().getParentFile(), project.getRoot(), new File(project.getRoot(), "images"), new File(project.getRoot(), "fonts"), new File(project.getRoot(), "sounds")}) { File candidate = new File(base, relative); if (candidate.isFile() && candidate.getCanonicalPath().startsWith(root + File.separator)) { found = true; break; } }
            if (!found) missing.add(relative);
        }
        return missing;
    }

    private void pasteStyleEntity(ThemeProjectFile styleSource, ThemeStyleEntities.Snapshot snapshot, String targetId, java.util.List<ThemeEditorModel.Key> keys, String expectedLocal, String expectedRemote) {
        byte[] backup = null;
        try {
            backup = readFileBytes(styleSource.getFile(), 4L * 1024 * 1024); String original = new String(backup, java.nio.charset.StandardCharsets.UTF_8);
            if (!expectedLocal.equals(ThemeSaveCoordinator.Companion.fingerprint(original))) throw new IOException("Target style changed after review; reopen Paste");
            if (importedProjectTreeUri != null && (expectedRemote == null || !expectedRemote.equals(fingerprintImportedProjectFile(styleSource.getFile())))) throw new IOException("Imported target style changed after review; reload project");
            String updated = ThemeStyleEntities.paste(original, snapshot, targetId); new FileThemeProjectRepository(styleSource.getFile()).write(updated);
            try { mirrorExistingProjectFile(styleSource.getFile()); refreshProjectAfterAssetMutation(); }
            catch (Exception error) { try (FileOutputStream output = new FileOutputStream(styleSource.getFile(), false)) { output.write(backup); output.getFD().sync(); } if (importedProjectTreeUri != null) try { writeImportedProjectFile(styleSource.getFile(), original); } catch (Exception restoreError) { error.addSuppressed(restoreError); } try { refreshProjectAfterAssetMutation(); } catch (Exception refreshError) { error.addSuppressed(refreshError); } throw error; }
            workspace.applyStyleEntityReference(keys, targetId); workspace.setStatus("Pasted complete style entity " + targetId + " and verified dependencies");
        } catch (Exception error) { workspace.setStatus("Style entity paste failed without overwriting newer data: " + error.getMessage()); Toast.makeText(this, "Unable to paste style entity", Toast.LENGTH_LONG).show(); }
    }

    private void reviewBatchStyleEntities(java.util.List<ThemeEditorModel.Key> keys, String background, String textColor) {
        if (!ensureWritable()) return;
        try {
            if (project == null || editor == null || !(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("Open a project keyboard first");
            ThemeProjectFile styleSource = resolvedStyleSource(editor.getDocument());
            if (styleSource == null) throw new IOException("The keyboard style asset cannot be resolved statically");
            String styleSourceText = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.ArrayList<String> entityIds = new java.util.ArrayList<>(); for (ThemeStyleEntities.Entry entry : ThemeStyleEntities.list(styleSourceText)) entityIds.add(entry.getId());
            java.util.LinkedHashSet<String> styleIds = new java.util.LinkedHashSet<>(); for (ThemeEditorModel.Key key : keys) styleIds.add(ThemeKeyStyleBatch.effectiveStyleId(key, entityIds));
            validateBatchStyleBackground(styleSource, background);
            ThemeKeyStyleBatch.Report report = ThemeKeyStyleBatch.references(project, styleIds, styleSource.getName());
            String localFingerprint = ThemeSaveCoordinator.Companion.fingerprint(styleSourceText);
            String remoteFingerprint = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(styleSource.getFile());
            StringBuilder message = new StringBuilder("Style asset: ").append(styleSource.getName()).append("\nSelected keys: ").append(keys.size()).append("\nStyle entities: ").append(android.text.TextUtils.join(", ", report.getStyleIds())).append("\nSaved project references using this asset: ").append(report.getTotalReferences());
            for (ThemeKeyStyleBatch.Reference reference : report.getReferences()) message.append("\n• ").append(reference.getKeyboardId()).append(": ").append(reference.getCount()).append(" key/container nodes");
            if (!report.getUncertainKeyboardIds().isEmpty()) message.append("\nDynamic/invalid layouts with uncertain references: ").append(android.text.TextUtils.join(", ", report.getUncertainKeyboardIds()));
            message.append("\n\nAll listed keys sharing these entities will inherit the changed colors/background. Static counts may be incomplete for uncertain keyboards. No Lua or callback will be executed.");
            new android.app.AlertDialog.Builder(this).setTitle("Modify shared style entities?").setMessage(message.toString()).setNegativeButton("Cancel", null).setPositiveButton("Apply transaction", (dialog, which) -> applyBatchStyleEntities(styleSource, styleIds, background, textColor, localFingerprint, remoteFingerprint)).show();
        } catch (Exception error) { workspace.setStatus("Style batch blocked: " + error.getMessage()); Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private ThemeProjectFile resolvedStyleSource(com.osfans.trime.editor.core.ThemeDocument keyboardDocument) {
        ThemeValue value = keyboardDocument.get("style");
        if (value instanceof ThemeValue.RawLuaNode) return null;
        String id;
        if (value instanceof ThemeValue.LuaString) id = ((ThemeValue.LuaString) value).getValue();
        else {
            try { ThemeValue mainStyle = new ThemeLuaParser().parse(new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8)).getDocument().get("style"); if (mainStyle != null && !(mainStyle instanceof ThemeValue.LuaString)) return null; id = mainStyle instanceof ThemeValue.LuaString ? ((ThemeValue.LuaString) mainStyle).getValue() : "light"; }
            catch (Exception error) { return null; }
        }
        return project.style(id);
    }

    private void validateBatchStyleBackground(ThemeProjectFile styleSource, String background) throws IOException {
        if (background == null || background.isEmpty() || background.matches("^(?:#|0[xX])?[0-9A-Fa-f]{1,8}$") || background.matches("^[0-9]{1,10}$")) return;
        if (background.startsWith("/") || background.startsWith("\\") || background.contains("..")) throw new IOException("Background resource must be a project-relative path");
        File fromStyle = new File(styleSource.getFile().getParentFile(), background), fromProject = new File(project.getRoot(), background);
        String root = project.getRoot().getCanonicalPath(); File resolved = fromStyle.isFile() ? fromStyle : fromProject;
        if (!resolved.isFile() || !resolved.getCanonicalPath().startsWith(root + File.separator)) throw new IOException("Background resource does not exist inside this project: " + background);
    }

    private void applyBatchStyleEntities(ThemeProjectFile styleSource, java.util.Set<String> styleIds, String background, String textColor, String expectedLocalFingerprint, String expectedRemoteFingerprint) {
        byte[] backup = null;
        try {
            backup = readFileBytes(styleSource.getFile(), 4L * 1024 * 1024); String original = new String(backup, java.nio.charset.StandardCharsets.UTF_8);
            if (!expectedLocalFingerprint.equals(ThemeSaveCoordinator.Companion.fingerprint(original))) throw new IOException("Style file changed after review; reopen the batch editor");
            if (importedProjectTreeUri != null && (expectedRemoteFingerprint == null || !expectedRemoteFingerprint.equals(fingerprintImportedProjectFile(styleSource.getFile())))) throw new IOException("Imported style file changed after review; reload the project");
            String updated = ThemeKeyStyleBatch.update(original, styleIds, new ThemeKeyStyleBatch.Change(background, textColor));
            new FileThemeProjectRepository(styleSource.getFile()).write(updated);
            try { mirrorExistingProjectFile(styleSource.getFile()); refreshProjectAfterAssetMutation(); }
            catch (Exception error) {
                try (FileOutputStream output = new FileOutputStream(styleSource.getFile(), false)) { output.write(backup); output.getFD().sync(); }
                if (importedProjectTreeUri != null) try { writeImportedProjectFile(styleSource.getFile(), original); } catch (Exception restoreError) { error.addSuppressed(restoreError); }
                try { refreshProjectAfterAssetMutation(); } catch (Exception refreshError) { error.addSuppressed(refreshError); }
                throw error;
            }
            if (repository instanceof DirectoryThemeProjectRepository && ((DirectoryThemeProjectRepository) repository).getSelected().getFile().equals(styleSource.getFile())) loadProjectFile(project.style(styleSource.getName()));
            else { ThemeEditorModel refreshed = workspace.getModel(); applyPreviewStyles(refreshed); workspace.setModelKeepingHistory(refreshed); }
            workspace.setStatus("Updated " + styleIds.size() + " shared style entities in " + styleSource.getName() + "; transaction verified");
        } catch (Exception error) { workspace.setStatus("Style batch failed without overwriting newer data: " + error.getMessage()); Toast.makeText(this, "Unable to update style entities", Toast.LENGTH_LONG).show(); }
    }

    private boolean isCurrentStyleFile() {
        return repository instanceof DirectoryThemeProjectRepository
                && ((DirectoryThemeProjectRepository) repository).getSelected().getKind() == ThemeProjectFile.Kind.STYLE;
    }

    private ThemeEditorModel stylePreviewModel(com.osfans.trime.editor.core.ThemeDocument style) {
        ThemeEditorModel model = ThemeEditorModel.sample();
        model.layoutMode = ThemeEditorModel.LayoutMode.NONE;
        if (isCurrentStyleFile() && editor != null) applyPreviewStyles(model);
        else applyStyleDocument(model, style);
        return model;
    }

    private void applyStyleDocument(ThemeEditorModel model, com.osfans.trime.editor.core.ThemeDocument style) {
        model.backgroundColor = colorValue(style.get("keyboard.background"), colorValue(style.get("background"), model.backgroundColor));
        model.candidateBackgroundColor = colorValue(style.get("candidate.background"), model.candidateBackgroundColor);
        model.candidateTextColor = colorValue(style.get("candidate.text_color"), model.candidateTextColor);
        model.toolbarBackgroundColor = colorValue(style.get("toolbar.background"), model.candidateBackgroundColor);
        model.toolbarTextColor = model.candidateTextColor;
        model.preeditBackgroundColor = colorValue(style.get("preedit.background"), model.preeditBackgroundColor);
        model.preeditTextColor = colorValue(style.get("preedit.text_color"), model.preeditTextColor);
        model.compositionBackgroundColor = colorValue(style.get("composition.background"), model.compositionBackgroundColor);
        model.compositionTextColor = colorValue(style.get("composition.text_color"), model.compositionTextColor);
        model.symbolBackgroundColor = colorValue(style.get("symbol.background"), model.symbolBackgroundColor);
        model.symbolTabTextColor = colorValue(style.get("symbol.key.text_color"), model.symbolTabTextColor);
        model.symbolIndicatorColor = colorValue(style.get("symbol.tab_bar.indicator_color"), colorValue(style.get("symbol.indicator_color"), model.symbolIndicatorColor));
        model.pressedKeyBackgroundColor = colorValue(style.get("key.pressed.background"), model.pressedKeyBackgroundColor);
        model.pressedKeyTextColor = colorValue(style.get("key.pressed.text_color"), model.pressedKeyTextColor);
        model.pressedCandidateBackgroundColor = colorValue(style.get("candidate.pressed.background"), model.pressedCandidateBackgroundColor);
        model.pressedCandidateTextColor = colorValue(style.get("candidate.pressed.text_color"), model.pressedCandidateTextColor);
        model.candidateHeight = numberValue(style.get("candidate.height"), 48f) / 5.3f;
        model.toolbarHeight = model.candidateHeight;
        model.keyTextSize = Math.max(2f, numberValue(style.get("key.text_size"), 22f) / 4f);
        model.keyCornerRadius = Math.max(0f, numberValue(style.get("key.corner_radius"), 8f) / 5f);
        int fill = colorValue(style.get("key.background"), 0xfff5f5f5);
        int text = colorValue(style.get("key.text_color"), 0xff1e1e1e);
        for (ThemeEditorModel.Key key : model.keys) { key.fillColor = fill; key.textColor = text; }
    }

    private static final class StyleInput {
        final String path;
        final boolean color;
        final EditText field;
        StyleInput(String path, boolean color, EditText field) { this.path = path; this.color = color; this.field = field; }
    }

    private static final class ComponentScalarInput {
        static final int NUMBER = 0, COLOR = 1, BOOLEAN = 2, ENUM = 3, INLINE = 4, FLOAT = 5, COLOR_OR_RESOURCE = 6;
        final String path;
        final int kind;
        final ThemeComponentStyles.Value original;
        final android.widget.CheckBox inherit;
        final EditText text;
        final android.widget.Spinner spinner;
        final String initialText;
        final String unknownChoice;

        ComponentScalarInput(String path, int kind, ThemeComponentStyles.Value original,
                             android.widget.CheckBox inherit, EditText text,
                             android.widget.Spinner spinner, String initialText, String unknownChoice) {
            this.path = path; this.kind = kind; this.original = original; this.inherit = inherit;
            this.text = text; this.spinner = spinner; this.initialText = initialText; this.unknownChoice = unknownChoice;
        }
    }

    private void showVisualComponentStyleEditor() {
        if (!ensureWritable()) return;
        if (!isCurrentStyleFile() || editor == null) {
            Toast.makeText(this, "Open a style file from the Style menu first", Toast.LENGTH_LONG).show();
            return;
        }
        String source = editor.source();
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        java.util.ArrayList<ComponentScalarInput> inputs = new java.util.ArrayList<>();
        componentSection(fields, "Candidate and expanded candidate");
        addVisualStyleGroup(fields, inputs, source, "candidate", true, true);
        addResourceBackgroundScalar(fields, inputs, source, "candidate.expanded.background", "Expanded candidate container background");
        TextView expandedHeightNote = new TextView(this); expandedHeightNote.setText("candidate.expanded.height is not consumed by the current ExpandedCandidateView; edit it only in Lua source for compatibility."); fields.addView(expandedHeightNote);
        addVisualStyleGroup(fields, inputs, source, "candidate.key", false, false);
        addVisualStyleGroup(fields, inputs, source, "candidate.expanded.key", false, false);

        componentSection(fields, "Toolbar");
        addResourceBackgroundScalar(fields, inputs, source, "toolbar.background", "Toolbar background");
        TextView toolbarHeightNote = new TextView(this); toolbarHeightNote.setText("ToolbarView fills candidate.height; toolbar.height is not consumed by the current runtime."); fields.addView(toolbarHeightNote);
        addComponentScalar(fields, inputs, source, "toolbar.schema_switches", "Show runtime schema switches", ComponentScalarInput.BOOLEAN, null);
        addVisualStyleGroup(fields, inputs, source, "toolbar.hide", false, false);
        addVisualStyleGroup(fields, inputs, source, "toolbar.key", false, false);

        componentSection(fields, "Symbol panel");
        addResourceBackgroundScalar(fields, inputs, source, "symbol.background", "Symbol panel background");
        addColorScalar(fields, inputs, source, "symbol.indicator_color", "Symbol fallback indicator color");
        addVisualStyleGroup(fields, inputs, source, "symbol.text", false, false);
        addVisualStyleGroup(fields, inputs, source, "symbol.key", false, false);
        addColorScalar(fields, inputs, source, "symbol.tab_bar.indicator_color", "Symbol selected-tab indicator color");
        TextView symbolToolNote = new TextView(this); symbolToolNote.setText("symbol.tool_bar visual key fields are not consumed by the current runtime; gravity, height and keys remain in the panel-bar manager / Lua source."); fields.addView(symbolToolNote);

        componentSection(fields, "Clipboard panel");
        addResourceBackgroundScalar(fields, inputs, source, "clipboard.background", "Clipboard panel background");
        addColorScalar(fields, inputs, source, "clipboard.indicator_color", "Clipboard fallback indicator color");
        addVisualStyleGroup(fields, inputs, source, "clipboard.key", false, false);
        addVisualStyleGroup(fields, inputs, source, "clipboard.item", false, false);
        TextView compatibility = new TextView(this);
        compatibility.setText("ClipboardKeyboardView consumes clipboard.key for tabs and tool buttons; WaterfallAdapter consumes clipboard.item for clipboard and phrase rows.");
        fields.addView(compatibility);
        addColorScalar(fields, inputs, source, "clipboard.tab_bar.indicator_color", "Clipboard selected-tab indicator color");
        TextView clipboardToolNote = new TextView(this); clipboardToolNote.setText("clipboard.tool_bar visual key fields are not consumed by the current runtime; gravity, height and keys remain in the panel-bar manager / Lua source."); fields.addView(clipboardToolNote);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        showComponentScalarDialog("Candidate / toolbar / panels — static fields", source, inputs, scroll,
                "Candidate/toolbar/panel styles applied; save to commit");
    }

    private void addVisualStyleGroup(LinearLayout fields, java.util.List<ComponentScalarInput> inputs,
                                     String source, String path, boolean includeHeight, boolean includeComment) {
        if (includeHeight) addIntegerScalar(fields, inputs, source, path + ".height", path + " height");
        addResourceBackgroundScalar(fields, inputs, source, path + ".background", path + " background");
        addColorScalar(fields, inputs, source, path + ".text_color", path + " text color");
        addIntegerScalar(fields, inputs, source, path + ".text_size", path + " text size");
        addResourceBackgroundScalar(fields, inputs, source, path + ".pressed.background", path + " pressed background");
        addColorScalar(fields, inputs, source, path + ".pressed.text_color", path + " pressed text color");
        if (includeComment) {
            addColorScalar(fields, inputs, source, path + ".comment.text_color", path + " comment color");
            addIntegerScalar(fields, inputs, source, path + ".comment.text_size", path + " comment size");
            addColorScalar(fields, inputs, source, path + ".comment.pressed.text_color", path + " pressed comment color");
            addIntegerScalar(fields, inputs, source, path + ".comment.pressed.text_size", path + " pressed comment size");
        }
    }

    private void addColorScalar(LinearLayout fields, java.util.List<ComponentScalarInput> inputs, String source, String path, String label) {
        addComponentScalar(fields, inputs, source, path, label, ComponentScalarInput.COLOR, null);
    }

    private void addResourceBackgroundScalar(LinearLayout fields, java.util.List<ComponentScalarInput> inputs, String source, String path, String label) {
        addComponentScalar(fields, inputs, source, path, label + " (color or safe project-relative resource)", ComponentScalarInput.COLOR_OR_RESOURCE, null);
    }

    private void addIntegerScalar(LinearLayout fields, java.util.List<ComponentScalarInput> inputs, String source, String path, String label) {
        addComponentScalar(fields, inputs, source, path, label + " (Trime2 integer size)", ComponentScalarInput.NUMBER, null);
    }

    private void showComponentScalarDialog(String title, String source, java.util.List<ComponentScalarInput> inputs,
                                           android.widget.ScrollView scroll, String success) {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(title).setView(scroll).setNegativeButton("Cancel", null).setNeutralButton("Lua source", null)
                .setPositiveButton("Apply", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> { dialog.dismiss(); showCodeEditor(); });
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    String updated = source; boolean changed = false;
                    for (ComponentScalarInput input : inputs) {
                        String next = applyComponentScalar(updated, input);
                        if (!next.equals(updated)) { changed = true; updated = next; }
                    }
                    if (!changed) { workspace.setStatus("No literal component fields changed"); dialog.dismiss(); return; }
                    com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(updated);
                    for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics())
                        if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR)
                            throw new IllegalArgumentException("Updated source failed parse verification: " + diagnostic.getMessage());
                    editor.replaceDocument(parsed.getDocument()); workspace.setModel(stylePreviewModel(editor.getDocument()));
                    viewModel.setDirty(true); workspace.setStatus(success); dialog.dismiss();
                } catch (Exception error) {
                    workspace.setStatus("Component style update blocked: " + error.getMessage());
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.show();
    }

    private void showCompositionStyleEditor() {
        if (!ensureWritable()) return;
        if (!isCurrentStyleFile() || editor == null) {
            Toast.makeText(this, "Open a style file from the Style menu first", Toast.LENGTH_LONG).show();
            return;
        }
        String source = editor.source();
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        java.util.ArrayList<ComponentScalarInput> inputs = new java.util.ArrayList<>();
        componentSection(fields, "Preedit");
        addComponentScalar(fields, inputs, source, "preedit.show", "Legacy show fallback used when composition.show is missing", ComponentScalarInput.BOOLEAN, null);
        addComponentScalar(fields, inputs, source, "preedit.background", "Background color/resource", ComponentScalarInput.COLOR_OR_RESOURCE, null);
        addComponentScalar(fields, inputs, source, "preedit.text_color", "Text color", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "preedit.text_size", "Text size (Trime2 size/SP semantics)", ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "preedit.inline", "Inline mode (source spelling preserved)", ComponentScalarInput.INLINE,
                new String[]{"none", "input", "preedit", "composition", "preview", "true (string)", "true (boolean source; current runtime none)"});

        componentSection(fields, "Composition window");
        addComponentScalar(fields, inputs, source, "composition.show", "Show (runtime default true; falls back through preedit style)", ComponentScalarInput.BOOLEAN, null);
        addComponentScalar(fields, inputs, source, "composition.background", "Background color/resource", ComponentScalarInput.COLOR_OR_RESOURCE, null);
        addComponentScalar(fields, inputs, source, "composition.text_color", "Text color", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.text_size", "Text size (Trime2 size/SP semantics)", ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "composition.position", "Position (unknown source is preserved; preview uses fixed)", ComponentScalarInput.ENUM,
                new String[]{"left", "right", "left_up", "right_up", "drag", "fixed", "bottom_left", "bottom_right", "top_left", "top_right"});
        addComponentScalar(fields, inputs, source, "composition.movable", "Movable string enum", ComponentScalarInput.ENUM,
                new String[]{"false", "true", "once"});

        componentSection(fields, "Composition filtering and entries");
        String[][] numbers = {
                {"composition.min_length", "Minimum input length"}, {"composition.max_length", "Maximum line length"},
                {"composition.sticky_lines", "Sticky lines"}, {"composition.max_entries", "Maximum entries (-1 means all)"},
                {"composition.cloud_max_entries", "Maximum cloud entries (0 uses runtime behavior)"},
                {"composition.min_width", "Minimum width"}, {"composition.min_height", "Minimum height"},
                {"composition.max_width", "Maximum width"}, {"composition.max_height", "Maximum height"},
                {"composition.padding.left", "Padding left"}, {"composition.padding.top", "Padding top"},
                {"composition.padding.right", "Padding right"}, {"composition.padding.bottom", "Padding bottom"}
        };
        for (String[] item : numbers) addComponentScalar(fields, inputs, source, item[0], item[1], ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "composition.line_spacing", "Line spacing (runtime float)", ComponentScalarInput.FLOAT, null);
        addComponentScalar(fields, inputs, source, "composition.line_spacing_multiplier", "Line-spacing multiplier (runtime float; 0 previews as 1)", ComponentScalarInput.FLOAT, null);
        addComponentScalar(fields, inputs, source, "composition.all_phrases", "Include all phrases", ComponentScalarInput.BOOLEAN, null);
        addComponentScalar(fields, inputs, source, "composition.use_cursor", "Use highlighted candidate cursor (runtime default true)", ComponentScalarInput.BOOLEAN, null);

        componentSection(fields, "Composition pressed state");
        addComponentScalar(fields, inputs, source, "composition.pressed.background", "Pressed background color", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.pressed.text_color", "Pressed text color", ComponentScalarInput.COLOR, null);
        componentSection(fields, "Internal composition.window key style");
        addComponentScalar(fields, inputs, source, "composition.key.background", "Internal key background", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.text_color", "Internal key text color", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.text_size", "Internal key text size (integer)", ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "composition.key.pressed.background", "Internal pressed key background", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.pressed.text_color", "Internal pressed key text color", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.hint.text_color", "Internal label text color", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.hint.text_size", "Internal label text size (integer)", ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "composition.key.pressed.hint.text_color", "Internal pressed label text color", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.pressed.hint.text_size", "Internal pressed label text size (integer)", ComponentScalarInput.NUMBER, null);
        TextView fontNote = new TextView(this);
        fontNote.setText("preedit/composition/key font values may be a string or a fallback array. They remain source-only so arrays are not flattened.");
        fields.addView(fontNote);
        TextView sourceOnly = new TextView(this);
        sourceOnly.setText("composition.window remains source-only: component order, conditions, alignment, letter spacing and click events are never evaluated or generically rewritten.");
        sourceOnly.setPadding(0, 16, 0, 16); fields.addView(sourceOnly);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Preedit / composition — literal static fields")
                .setView(scroll).setNegativeButton("Cancel", null).setNeutralButton("Lua source", null)
                .setPositiveButton("Apply", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> { dialog.dismiss(); showCodeEditor(); });
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    String updated = source;
                    boolean changed = false;
                    for (ComponentScalarInput input : inputs) {
                        String next = applyComponentScalar(updated, input);
                        if (!next.equals(updated)) { changed = true; updated = next; }
                    }
                    if (!changed) { workspace.setStatus("No preedit/composition literal fields changed"); dialog.dismiss(); return; }
                    com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(updated);
                    for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) {
                        if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR)
                            throw new IllegalArgumentException("Updated source failed parse verification: " + diagnostic.getMessage());
                    }
                    editor.replaceDocument(parsed.getDocument());
                    workspace.setModel(stylePreviewModel(editor.getDocument()));
                    viewModel.setDirty(true);
                    workspace.setStatus("Preedit/composition fields applied; save to commit");
                    dialog.dismiss();
                } catch (Exception error) {
                    workspace.setStatus("Preedit/composition update blocked: " + error.getMessage());
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.show();
    }

    private void componentSection(LinearLayout parent, String title) {
        TextView label = new TextView(this); label.setText(title); label.setTextSize(18); label.setPadding(0, 20, 0, 4); parent.addView(label);
    }

    private void addComponentScalar(LinearLayout parent, java.util.List<ComponentScalarInput> inputs, String source,
                                    String path, String label, int kind, String[] choices) {
        TextView title = new TextView(this); title.setText(label + "\n" + path); title.setPadding(0, 10, 0, 0); parent.addView(title);
        try {
            ThemeComponentStyles.Value value = ThemeComponentStyles.read(source, path);
            if (value.getDynamic()) {
                TextView blocked = new TextView(this); blocked.setText("Source-only: " + value.getDiagnostic()); blocked.setEnabled(false); parent.addView(blocked);
                return;
            }
            String trace = value.getInheritedFrom() == null ? null : "Inherited from " + value.getInheritedFrom();
            if (value.getCompatibilityDiagnostic() != null) trace = (trace == null ? "" : trace + ". ") + value.getCompatibilityDiagnostic();
            if (trace != null) { TextView note = new TextView(this); note.setText(trace); parent.addView(note); }
            if (kind == ComponentScalarInput.BOOLEAN || kind == ComponentScalarInput.ENUM || kind == ComponentScalarInput.INLINE) {
                java.util.ArrayList<String> values = new java.util.ArrayList<>(); values.add("inherit");
                if (choices == null) { values.add("false"); values.add("true"); } else java.util.Collections.addAll(values, choices);
                String current = componentScalarSelection(value, kind); String unknown = null;
                int selection = values.indexOf(current);
                if (selection < 0 && current != null) { unknown = "keep original: " + current; values.add(unknown); selection = values.size() - 1; }
                if (selection < 0) selection = 0;
                android.widget.Spinner spinner = new android.widget.Spinner(this);
                spinner.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
                spinner.setSelection(value.getExplicit() ? selection : 0); parent.addView(spinner);
                inputs.add(new ComponentScalarInput(path, kind, value, null, null, spinner, current, unknown));
            } else {
                android.widget.CheckBox inherit = new android.widget.CheckBox(this); inherit.setText("inherit / remove explicit field"); inherit.setChecked(!value.getExplicit()); parent.addView(inherit);
                String initial = componentScalarText(value, kind); EditText field = simpleField(parent, label, initial); field.setEnabled(!inherit.isChecked());
                inherit.setOnCheckedChangeListener((button, checked) -> field.setEnabled(!checked));
                inputs.add(new ComponentScalarInput(path, kind, value, inherit, field, null, initial, null));
            }
        } catch (Exception error) {
            TextView blocked = new TextView(this); blocked.setText("Source-only: " + error.getMessage()); blocked.setEnabled(false); parent.addView(blocked);
        }
    }

    private static String componentScalarSelection(ThemeComponentStyles.Value value, int kind) {
        if (value.getLiteral() == null) return null;
        if (kind == ComponentScalarInput.BOOLEAN) return value.getBooleanValue() == null ? null : value.getBooleanValue() ? "true" : "false";
        if (kind == ComponentScalarInput.INLINE && value.getLiteral() instanceof ThemeValue.LuaBoolean)
            return Boolean.TRUE.equals(value.getBooleanValue()) ? "true (boolean source; current runtime none)" : "false (boolean source)";
        String text = value.getStringValue();
        return kind == ComponentScalarInput.INLINE && "true".equals(text) ? "true (string)" : text;
    }

    private static String componentScalarText(ThemeComponentStyles.Value value, int kind) {
        if (kind == ComponentScalarInput.COLOR || kind == ComponentScalarInput.COLOR_OR_RESOURCE) {
            if (value.getColorValue() != null) return String.format(java.util.Locale.ROOT, "#%08X", value.getColorValue());
            return value.getResourceValue() == null ? "" : value.getResourceValue();
        }
        if (value.getNumberValue() == null) return "";
        double number = value.getNumberValue(); return number == (long) number ? Long.toString((long) number) : Double.toString(number);
    }

    private static long parseUnsignedColor(String text, String path) {
        try {
            long value = text.startsWith("#") ? Long.parseLong(text.substring(1), 16)
                    : text.startsWith("0x") || text.startsWith("0X") ? Long.parseLong(text.substring(2), 16)
                    : Long.parseLong(text);
            if (value < 0 || value > 0xffffffffL) throw new NumberFormatException("outside unsigned 32-bit range");
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(path + " must be an unsigned #AARRGGBB, 0xAARRGGBB, or decimal color");
        }
    }

    private static String applyComponentScalar(String source, ComponentScalarInput input) {
        if (input.spinner != null) {
            String selected = input.spinner.getSelectedItem().toString();
            if ("inherit".equals(selected)) return input.original.getExplicit() ? ThemeComponentStyles.remove(source, input.path) : source;
            if (input.unknownChoice != null && input.unknownChoice.equals(selected)) return source;
            if (input.original.getExplicit() && selected.equals(input.initialText)) return source;
            if (input.kind == ComponentScalarInput.BOOLEAN) return ThemeComponentStyles.updateBoolean(source, input.path, "true".equals(selected));
            if (input.kind == ComponentScalarInput.INLINE) {
                if ("true (boolean source; current runtime none)".equals(selected)) return ThemeComponentStyles.updatePreeditInline(source, null, true);
                return ThemeComponentStyles.updatePreeditInline(source, "true (string)".equals(selected) ? "true" : selected, false);
            }
            return ThemeComponentStyles.updateString(source, input.path, selected);
        }
        if (input.inherit.isChecked()) return input.original.getExplicit() ? ThemeComponentStyles.remove(source, input.path) : source;
        String text = input.text.getText().toString().trim();
        if (text.isEmpty()) throw new IllegalArgumentException(input.path + " cannot be empty unless inherit is selected");
        if (input.original.getExplicit() && text.equals(input.initialText)) return source;
        if (input.kind == ComponentScalarInput.COLOR_OR_RESOURCE) return ThemeComponentStyles.updateColorOrResource(source, input.path, text);
        if (input.kind == ComponentScalarInput.COLOR) {
            long color = parseUnsignedColor(text, input.path);
            return ThemeComponentStyles.updateColorOrResource(source, input.path, color);
        }
        double number = Double.parseDouble(text);
        if (input.kind == ComponentScalarInput.NUMBER && number != Math.rint(number))
            throw new IllegalArgumentException(input.path + " must be an integer for the Trime2 runtime");
        return ThemeComponentStyles.updateNumber(source, input.path, number);
    }

    private void showStyleEditor() {
        if (!ensureWritable()) return;
        if (!isCurrentStyleFile() || editor == null) { Toast.makeText(this, "Open a style file from the Style menu first", Toast.LENGTH_LONG).show(); return; }
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        java.util.ArrayList<StyleInput> inputs = new java.util.ArrayList<>();
        addStyleInput(fields, inputs, "keyboard.background", "Keyboard background", true); addStyleInput(fields, inputs, "keyboard.height", "Keyboard height dp", false);
        addStyleInput(fields, inputs, "key.background", "Key background", true); addStyleInput(fields, inputs, "key.text_color", "Key text color", true); addStyleInput(fields, inputs, "key.text_size", "Key text size dp", false); addStyleInput(fields, inputs, "key.corner_radius", "Key corner radius dp", false); addStyleInput(fields, inputs, "key.elevation", "Key elevation dp", false); addStyleInput(fields, inputs, "key.stroke_width", "Key stroke width dp", false);
        addStyleInput(fields, inputs, "key.pressed.background", "Pressed key background", true); addStyleInput(fields, inputs, "key.pressed.text_color", "Pressed key text color", true); addStyleInput(fields, inputs, "key.hint.text_color", "Key hint text color", true); addStyleInput(fields, inputs, "key.hint.text_size", "Key hint text size dp", false); addStyleInput(fields, inputs, "key.long_click.text_color", "Long-click hint color", true); addStyleInput(fields, inputs, "key.long_click.text_size", "Long-click hint size dp", false);
        addStyleInput(fields, inputs, "popup.background", "Popup background", true); addStyleInput(fields, inputs, "popup.corner_radius", "Popup corner radius dp", false); addStyleInput(fields, inputs, "popup.column_count", "Popup column count", false);
        TextView compositionLink = new TextView(this); compositionLink.setText("Preedit and composition fields use the dedicated Preedit / composition page so inherited and source-only Lua remain lossless."); fields.addView(compositionLink);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("Style properties (literal fields only)").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Apply", (dialog, which) -> {
            boolean changed = false; for (StyleInput input : inputs) changed |= input.color ? setStyleColor(input.path, input.field) : setStyleNumber(input.path, input.field);
            workspace.setModel(stylePreviewModel(editor.getDocument()));
            if (changed) { viewModel.setDirty(true); workspace.setStatus("Style properties applied; save to commit"); }
            else workspace.setStatus("No literal style values changed; inherited raw paths remain code-only");
        }).show();
    }

    private void addStyleInput(LinearLayout parent, java.util.List<StyleInput> inputs, String path, String label, boolean color) {
        EditText field = styleField(parent, label + (color ? " (#AARRGGBB)" : ""), editor.getDocument().get(path), color); inputs.add(new StyleInput(path, color, field));
    }

    private EditText styleField(LinearLayout parent, String hint, ThemeValue value, boolean color) {
        EditText field = new EditText(this); field.setHint(hint);
        if (value instanceof ThemeValue.LuaNumber) {
            long number = (long) ((ThemeValue.LuaNumber) value).getValue();
            field.setText(color ? String.format(java.util.Locale.ROOT, "#%08X", number) : Long.toString(number));
        }
        parent.addView(field, new LinearLayout.LayoutParams(-1, -2)); return field;
    }

    private boolean setStyleColor(String path, EditText field) {
        String value = field.getText().toString().trim(); if (value.isEmpty()) return false;
        try {
            long parsed = value.startsWith("#") ? Long.parseLong(value.substring(1), 16) : value.startsWith("0x") || value.startsWith("0X") ? Long.parseLong(value.substring(2), 16) : Long.parseLong(value);
            ThemeValue next = new ThemeValue.LuaNumber((double) parsed); if (next.equals(editor.getDocument().get(path))) return false;
            return applyStyleValue(path, next);
        } catch (NumberFormatException ignored) { Toast.makeText(this, "Invalid color for " + path, Toast.LENGTH_LONG).show(); return false; }
    }

    private boolean setStyleNumber(String path, EditText field) {
        String value = field.getText().toString().trim(); if (value.isEmpty()) return false;
        try { ThemeValue next = new ThemeValue.LuaNumber(Double.parseDouble(value)); if (next.equals(editor.getDocument().get(path))) return false; return applyStyleValue(path, next); }
        catch (NumberFormatException ignored) { Toast.makeText(this, "Invalid number for " + path, Toast.LENGTH_LONG).show(); return false; }
    }

    private boolean applyStyleValue(String path, ThemeValue value) {
        java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics = editor.set(path, value);
        for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : diagnostics) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) {
            Toast.makeText(this, diagnostic.getMessage(), Toast.LENGTH_LONG).show(); return false;
        }
        return true;
    }

    private void showCodeEditor() {
        if (!ensureWritable()) return;
        if (repository == null || editor == null) {
            Toast.makeText(this, "Open a Lua file before editing source", Toast.LENGTH_LONG).show();
            return;
        }
        final EditText source = new EditText(this);
        source.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        source.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        source.setSingleLine(false);
        source.setText(editor.source());
        source.setSelection(source.length());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        source.setPadding(padding, padding, padding, padding);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Lua source")
                .setView(source)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(source.getText().toString());
            boolean hasErrors = false;
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) {
                if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) { hasErrors = true; break; }
            }
            if (hasErrors) {
                Toast.makeText(this, "Lua source has errors; changes were not applied", Toast.LENGTH_LONG).show();
                return;
            }
            editor.replaceDocument(parsed.getDocument());
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            viewModel.setDirty(true);
            workspace.setStatus("Lua source applied; save to commit");
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void chooseInstallTarget() {
        if (!ensureWritable()) return;
        if (project == null) { Toast.makeText(this, "Open a theme directory before installing", Toast.LENGTH_LONG).show(); return; }
        if (viewModel.getDirty()) {
            new android.app.AlertDialog.Builder(this).setTitle("Save before installation").setMessage("Installation uses a verified saved project snapshot.").setNegativeButton("Cancel", null).setPositiveButton("Save and continue", (dialog, which) -> {
                saveModel(workspace.getModel()); if (!viewModel.getDirty()) openInstallTargetPicker();
            }).show(); return;
        }
        openInstallTargetPicker();
    }

    private void openInstallTargetPicker() {
        installTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
    }

    private void installToTree(Uri treeUri) {
        DocumentFile target = null, backup = null;
        boolean targetExisted = false;
        java.util.Map<String, Long> backupManifest = null;
        try {
            validateProjectForInstall();
            DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri);
            if (tree == null || !tree.canWrite()) throw new IOException("Install target is not writable");
            String themeName = projectDisplayName == null || projectDisplayName.trim().isEmpty() ? project.getRoot().getName() : projectDisplayName;
            target = tree.findFile(themeName);
            targetExisted = target != null;
            if (target != null && !target.isDirectory()) throw new IOException("Install target name is occupied by a file");
            if (target != null) {
                backup = tree.createDirectory(themeName + ".backup-" + System.currentTimeMillis());
                if (backup == null) throw new IOException("Cannot create installation backup");
                copyDocumentToDocument(target, backup);
                backupManifest = documentManifest(backup);
                if (!backupManifest.equals(documentManifest(target))) throw new IOException("Backup verification failed");
            } else {
                target = tree.createDirectory(themeName);
                if (target == null) throw new IOException("Cannot create target theme directory");
            }
            writeInstallJournal("BACKUP_READY", target, backup, null);
            clearDocumentDirectory(target);
            copyProjectToDocument(project.getRoot(), target);
            java.util.Map<String, Long> expected = fileManifest(project.getRoot());
            java.util.Map<String, Long> installed = documentManifest(target);
            if (!expected.equals(installed)) throw new IOException("Installed file verification failed");
            writeInstallJournal("COMPLETED", target, backup, null);
            lastInstallTarget = target; lastInstallBackup = backup; lastBackupManifest = backupManifest;
            invalidateOptionsMenu();
            workspace.setStatus("Theme installed and verified: " + themeName + (backup == null ? "" : "; backup " + backup.getName()));
        } catch (Exception error) {
            boolean rolledBack = false;
            if (target != null && backup != null) rolledBack = rollbackInstall(target, backup, backupManifest);
            else if (target != null && !targetExisted) { try { rolledBack = target.delete(); } catch (Exception ignored) { } }
            writeInstallJournal(rolledBack ? "ROLLED_BACK" : "FAILED", target, backup, error.getMessage());
            workspace.setStatus("Install failed: " + error.getMessage() + (rolledBack ? "; backup restored" : ""));
            Toast.makeText(this, rolledBack ? "Theme install failed and was rolled back" : "Theme install failed", Toast.LENGTH_LONG).show();
        }
    }

    private void validateProjectForInstall() throws IOException {
        if (project == null || !project.getMainFile().isFile()) throw new IOException("Theme project has no main.lua");
        ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
        for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry())) {
            if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException(diagnostic.getMessage());
        }
        if (snapshot.getStyleSource() == null) throw new IOException("Default style file is missing");
        if (snapshot.getKeyboardSource() == null) throw new IOException("Selected default keyboard file is missing or dynamic selection cannot be verified");
        for (ThemeResource resource : ThemeResourceIndex.INSTANCE.scan(project.getRoot(), allProjectLuaSource())) if (resource.getReferenced() && resource.getSize() == 0) throw new IOException("Referenced resource is empty: " + resource.getRelativePath());
    }

    private void copyProjectToDocument(File source, DocumentFile destination) throws IOException {
        File[] children = source.listFiles(); if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                DocumentFile dir = destination.findFile(child.getName()); if (dir == null) dir = destination.createDirectory(child.getName());
                if (dir == null || !dir.isDirectory()) throw new IOException("Cannot create " + child.getName()); copyProjectToDocument(child, dir);
            } else if (child.isFile()) {
                DocumentFile outputFile = destination.createFile(mimeForName(child.getName()), child.getName());
                if (outputFile == null) throw new IOException("Cannot create " + child.getName()); copyFileToDocument(child, outputFile);
            }
        }
    }

    private void copyFileToDocument(File source, DocumentFile destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source); java.io.OutputStream output = getContentResolver().openOutputStream(destination.getUri(), "wt")) {
            if (output == null) throw new IOException("Cannot write " + source.getName()); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private void copyDocumentToDocument(DocumentFile source, DocumentFile destination) throws IOException {
        for (DocumentFile child : source.listFiles()) {
            String name = child.getName() == null ? "unnamed" : child.getName();
            if (child.isDirectory()) { DocumentFile dir = destination.createDirectory(name); if (dir == null) throw new IOException("Cannot back up " + name); copyDocumentToDocument(child, dir); }
            else if (child.isFile()) { DocumentFile file = destination.createFile(child.getType() == null ? mimeForName(name) : child.getType(), name); if (file == null) throw new IOException("Cannot back up " + name); try (InputStream input = getContentResolver().openInputStream(child.getUri()); java.io.OutputStream output = getContentResolver().openOutputStream(file.getUri(), "wt")) { if (input == null || output == null) throw new IOException("Cannot copy backup file " + name); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); } }
        }
    }

    private static String mimeForName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT); if (lower.endsWith(".lua")) return "text/x-lua"; if (lower.endsWith(".png")) return "image/png"; if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg"; if (lower.endsWith(".ogg")) return "audio/ogg"; if (lower.endsWith(".mp3")) return "audio/mpeg"; if (lower.endsWith(".ttf")) return "font/ttf"; if (lower.endsWith(".otf")) return "font/otf"; return "application/octet-stream";
    }

    private void clearDocumentDirectory(DocumentFile directory) throws IOException { for (DocumentFile child : directory.listFiles()) if (!child.delete()) throw new IOException("Cannot clear " + child.getName()); }

    private java.util.Map<String, Long> fileManifest(File root) { java.util.LinkedHashMap<String, Long> result = new java.util.LinkedHashMap<>(); collectFileManifest(root, root, result); return result; }
    private void collectFileManifest(File root, File current, java.util.Map<String, Long> out) { File[] files = current.listFiles(); if (files == null) return; for (File file : files) { if (file.isDirectory()) collectFileManifest(root, file, out); else if (file.isFile()) try { out.put(file.getCanonicalPath().substring(root.getCanonicalPath().length() + 1).replace(File.separatorChar, '/'), file.length()); } catch (IOException ignored) { } } }
    private java.util.Map<String, Long> documentManifest(DocumentFile root) { java.util.LinkedHashMap<String, Long> result = new java.util.LinkedHashMap<>(); collectDocumentManifest(root, "", result); return result; }
    private void collectDocumentManifest(DocumentFile directory, String prefix, java.util.Map<String, Long> out) { for (DocumentFile file : directory.listFiles()) { String name = file.getName() == null ? "unnamed" : file.getName(); String path = prefix.isEmpty() ? name : prefix + "/" + name; if (file.isDirectory()) collectDocumentManifest(file, path, out); else if (file.isFile()) out.put(path, file.length()); } }

    private boolean rollbackInstall(DocumentFile target, DocumentFile backup, java.util.Map<String, Long> expected) {
        try { clearDocumentDirectory(target); copyDocumentToDocument(backup, target); return expected == null || expected.equals(documentManifest(target)); } catch (Exception ignored) { return false; }
    }

    private void rollbackLastInstall(boolean confirm) {
        if (lastInstallTarget == null || lastInstallBackup == null) { Toast.makeText(this, "No installation backup is available", Toast.LENGTH_LONG).show(); return; }
        if (confirm) { new android.app.AlertDialog.Builder(this).setTitle("Rollback last installation?").setMessage(lastInstallBackup.getName()).setNegativeButton("Cancel", null).setPositiveButton("Rollback", (dialog, which) -> rollbackLastInstall(false)).show(); return; }
        boolean success = rollbackInstall(lastInstallTarget, lastInstallBackup, lastBackupManifest); writeInstallJournal(success ? "ROLLED_BACK" : "ROLLBACK_FAILED", lastInstallTarget, lastInstallBackup, null); workspace.setStatus(success ? "Installation backup restored and verified" : "Rollback verification failed"); if (success) { lastInstallBackup = null; lastInstallTarget = null; lastBackupManifest = null; invalidateOptionsMenu(); }
    }

    private void writeInstallJournal(String state, DocumentFile target, DocumentFile backup, String error) {
        File journal = new File(getFilesDir(), "theme-editor-install.journal");
        try (FileOutputStream output = new FileOutputStream(journal, false)) { String value = "state=" + state + "\ntarget=" + (target == null ? "" : target.getUri()) + "\nbackup=" + (backup == null ? "" : backup.getUri()) + "\nerror=" + (error == null ? "" : error.replace('\n', ' ')) + "\ntime=" + System.currentTimeMillis() + "\n"; output.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (IOException ignored) { }
    }

    private DocumentFile documentFromPersistedUri(Uri uri) {
        try { DocumentFile tree = DocumentFile.fromTreeUri(this, uri); if (tree != null && tree.exists()) return tree; } catch (Exception ignored) { }
        try { return DocumentFile.fromSingleUri(this, uri); } catch (Exception ignored) { return null; }
    }

    private void restoreInstallJournal() {
        File journal = new File(getFilesDir(), "theme-editor-install.journal"); if (!journal.isFile()) return;
        try {
            String text = readSmallText(journal, 32 * 1024); java.util.HashMap<String, String> values = new java.util.HashMap<>();
            for (String line : text.split("\n")) { int equals = line.indexOf('='); if (equals > 0) values.put(line.substring(0, equals), line.substring(equals + 1)); }
            String state = values.get("state"), targetValue = values.get("target"), backupValue = values.get("backup");
            if (targetValue == null || targetValue.isEmpty() || backupValue == null || backupValue.isEmpty()) return;
            DocumentFile target = documentFromPersistedUri(Uri.parse(targetValue)); DocumentFile backup = documentFromPersistedUri(Uri.parse(backupValue));
            if (target == null || backup == null || !target.isDirectory() || !backup.isDirectory()) return;
            lastInstallTarget = target; lastInstallBackup = backup; lastBackupManifest = documentManifest(backup); invalidateOptionsMenu();
            if ("BACKUP_READY".equals(state) || "FAILED".equals(state) || "ROLLBACK_FAILED".equals(state)) {
                new android.app.AlertDialog.Builder(this).setTitle("Incomplete theme installation").setMessage("A verified backup is available. Restore it now?").setNegativeButton("Later", null).setPositiveButton("Restore", (dialog, which) -> rollbackLastInstall(false)).show();
            }
        } catch (Exception ignored) { }
    }

    private void showDiagnostics() {
        StringBuilder text = new StringBuilder();
        if (projectSnapshot != null) {
            java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics =
                    ThemeProjectDiagnostics.INSTANCE.collect(projectSnapshot, new ThemeFieldRegistry());
            if (diagnostics.isEmpty()) text.append("No diagnostics");
            for (com.osfans.trime.editor.core.ThemeDiagnostic item : diagnostics) {
                text.append(item.getSeverity()).append("  ").append(item.getPath() == null ? "" : item.getPath() + ": ").append(item.getMessage()).append('\n');
            }
        } else if (editor != null) {
            for (com.osfans.trime.editor.core.ThemeDiagnostic item : editor.diagnostics()) text.append(item.getSeverity()).append("  ").append(item.getMessage()).append('\n');
        } else {
            text.append("No theme loaded");
        }
        new android.app.AlertDialog.Builder(this).setTitle("Diagnostics").setMessage(text.toString()).setPositiveButton("Close", null).show();
    }

    private void showResources() {
        if (project == null) { Toast.makeText(this, "Open a theme directory first", Toast.LENGTH_LONG).show(); return; }
        java.util.List<ThemeResource> resources = ThemeResourceIndex.INSTANCE.scan(project.getRoot(), allProjectLuaSource());
        String[] labels = new String[resources.size() + 1]; labels[0] = "+ Import resource";
        for (int i = 0; i < resources.size(); i++) { ThemeResource resource = resources.get(i); labels[i + 1] = (resource.getReferenced() ? "Referenced  " : resource.getReferenceUncertain() ? "Review reference  " : "Unused  ") + resource.getKind() + "  " + resource.getRelativePath() + "  " + resource.getSize() + " bytes"; }
        new android.app.AlertDialog.Builder(this).setTitle("Theme resources").setItems(labels, (dialog, which) -> {
            if (which == 0) chooseResourceType(); else showResourceActions(resources.get(which - 1));
        }).setNegativeButton("Close", null).show();
    }

    private String allProjectLuaSource() {
        if (project == null) return editor == null ? "" : editor.source();
        StringBuilder source = new StringBuilder(); File[] files = project.getRoot().listFiles();
        appendLuaSource(project.getRoot(), source);
        if (editor != null && repository instanceof DirectoryThemeProjectRepository) source.append('\n').append(editor.source());
        return source.toString();
    }

    private static void appendLuaSource(File directory, StringBuilder source) {
        File[] children = directory.listFiles(); if (children == null) return;
        for (File child : children) { if (child.isDirectory()) appendLuaSource(child, source); else if (child.isFile() && child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lua")) {
            try (FileInputStream input = new FileInputStream(child); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); source.append('\n').append(output.toString("UTF-8")); } catch (Exception ignored) { }
        } }
    }

    private void chooseResourceType() {
        if (!ensureWritable()) return;
        String[] types = {"Image", "Font", "Sound", "Script"}; String[] folders = {"images", "fonts", "sounds", "scripts"}; String[] mime = {"image/*", "font/*", "audio/*", "text/*"};
        new android.app.AlertDialog.Builder(this).setTitle("Import resource type").setItems(types, (dialog, which) -> {
            pendingResourceFolder = folders[which]; importResourceLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime[which]).addCategory(Intent.CATEGORY_OPENABLE));
        }).setNegativeButton("Cancel", null).show();
    }

    private void importResource(Uri uri) {
        if (!ensureWritable()) return;
        if (project == null || pendingResourceFolder == null) return;
        File target = null;
        try {
            DocumentFile document = DocumentFile.fromSingleUri(this, uri); String name = document == null ? null : document.getName();
            if (name == null || name.trim().isEmpty()) name = "resource";
            name = name.replaceAll("[^A-Za-z0-9._ -]", "_").replace("..", "_");
            File folder = new File(project.getRoot(), pendingResourceFolder); if (!folder.exists() && !folder.mkdirs()) throw new IOException("Cannot create resource folder");
            target = new File(folder, name); String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name; String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ""; int suffix = 2;
            while (target.exists()) target = new File(folder, base + "-" + suffix++ + extension);
            if (!target.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator)) throw new IOException("Invalid resource name");
            try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(target)) { if (input == null) throw new IOException("Cannot read resource"); byte[] buffer = new byte[8192]; int count; long total = 0; while ((count = input.read(buffer)) != -1) { total += count; if (total > 64L * 1024 * 1024) throw new IOException("Resource exceeds 64 MiB limit"); output.write(buffer, 0, count); } output.getFD().sync(); }
            if (importedProjectTreeUri != null) mirrorNewResourceToImportedTree(target);
            project = ThemeProject.Companion.discover(project.getRoot()); projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser()); workspace.setStatus("Imported " + target.getName());
        } catch (Exception error) { if (target != null && target.exists()) target.delete(); workspace.setStatus("Resource import failed: " + error.getMessage()); Toast.makeText(this, "Unable to import resource", Toast.LENGTH_LONG).show(); }
        finally { pendingResourceFolder = null; }
    }

    private void mirrorNewResourceToImportedTree(File local) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(local, true); if (ref == null || ref.file == null) throw new IOException("Cannot create imported resource");
        try (FileInputStream input = new FileInputStream(local); java.io.OutputStream output = getContentResolver().openOutputStream(ref.file.getUri(), "wt")) { if (output == null) throw new IOException("Cannot write imported resource"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
        try (FileInputStream input = new FileInputStream(local)) { if (!fingerprintStream(input).equals(fingerprintDocument(ref.file))) { ref.file.delete(); throw new IOException("Imported resource verification failed"); } }
    }

    private void deleteImportedResource(File local) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(local, false); if (ref == null || ref.file == null) return;
        File backup = new File(getCacheDir(), "theme-editor-resource-delete-" + System.nanoTime());
        try (InputStream input = getContentResolver().openInputStream(ref.file.getUri()); FileOutputStream output = new FileOutputStream(backup)) { if (input == null) throw new IOException("Cannot back up imported resource"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); output.getFD().sync(); }
        if (!ref.file.delete() || ref.parent.findFile(ref.name) != null) {
            DocumentFile restore = ref.parent.findFile(ref.name); if (restore == null) restore = ref.parent.createFile(mimeForName(ref.name), ref.name);
            if (restore != null) try (FileInputStream input = new FileInputStream(backup); java.io.OutputStream output = getContentResolver().openOutputStream(restore.getUri(), "wt")) { if (output != null) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); } }
            backup.delete(); throw new IOException("Imported resource deletion failed");
        }
        backup.delete();
    }

    private static byte[] readFileBytes(File file, long limit) throws IOException {
        if (file.length() > limit) throw new IOException("File exceeds backup limit");
        try (FileInputStream input = new FileInputStream(file); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("File exceeds backup limit"); output.write(buffer, 0, count); } return output.toByteArray(); }
    }

    private void showResourceActions(ThemeResource resource) {
        String[] actions = {"Copy relative path", "Delete"};
        new android.app.AlertDialog.Builder(this).setTitle(resource.getRelativePath()).setMessage(resource.getKind() + " • " + resource.getSize() + " bytes • " + (resource.getReferenced() ? "referenced" : resource.getReferenceUncertain() ? "dynamic reference possible" : "unused")).setItems(actions, (dialog, which) -> {
            if (which == 0) { android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE); clipboard.setPrimaryClip(android.content.ClipData.newPlainText("theme resource", resource.getRelativePath())); workspace.setStatus("Resource path copied"); }
            else confirmResourceDelete(resource);
        }).setNegativeButton("Close", null).show();
    }

    private void confirmResourceDelete(ThemeResource resource) {
        if (!ensureWritable()) return;
        if (resource.getReferenced() || resource.getReferenceUncertain()) { Toast.makeText(this, "Referenced or dynamically resolved resources cannot be deleted", Toast.LENGTH_LONG).show(); return; }
        new android.app.AlertDialog.Builder(this).setTitle("Delete unused resource?").setMessage(resource.getRelativePath()).setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
            File local = new File(project.getRoot(), resource.getRelativePath()); byte[] backup = null; try { if (local.isFile() && local.length() <= 64L * 1024 * 1024) backup = readFileBytes(local, 64L * 1024 * 1024); } catch (IOException ignored) { }
            ResourceDeleteResult result = new ThemeResourceManager(project.getRoot(), allProjectLuaSource()).delete(resource.getRelativePath());
            if (result instanceof ResourceDeleteResult.Deleted) {
                try { if (importedProjectTreeUri != null) deleteImportedResource(local); project = ThemeProject.Companion.discover(project.getRoot()); workspace.setStatus("Deleted " + resource.getRelativePath()); }
                catch (Exception error) { if (backup != null) try { File restore = new File(project.getRoot(), resource.getRelativePath()); restore.getParentFile().mkdirs(); try (FileOutputStream output = new FileOutputStream(restore)) { output.write(backup); output.getFD().sync(); } } catch (Exception ignored) { } try { project = ThemeProject.Companion.discover(project.getRoot()); } catch (Exception ignored) { } workspace.setStatus("Resource delete rolled back: " + error.getMessage()); }
            } else if (result instanceof ResourceDeleteResult.Referenced) workspace.setStatus("Delete blocked: resource is referenced"); else workspace.setStatus("Resource delete failed");
        }).show();
    }

    private void exportZip(boolean share) {
        if (editor == null) { Toast.makeText(this, "Open a theme before exporting", Toast.LENGTH_LONG).show(); return; }
        File sourceRoot = null;
        try {
            if (workspace.getModel().layoutMode != ThemeEditorModel.LayoutMode.NONE && !syncModel(workspace.getModel())) return;
            com.osfans.trime.editor.core.ParseResult check = new ThemeLuaParser().parse(editor.source());
            boolean hasErrors = false;
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : check.getDiagnostics()) {
                if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) { hasErrors = true; break; }
            }
            if (hasErrors) {
                workspace.setStatus("Export blocked by Lua errors");
                return;
            }
            sourceRoot = new File(getCacheDir(), "theme-editor-export-source-" + System.nanoTime());
            sourceRoot.mkdirs();
            if (project == null) {
                File main = new File(sourceRoot, "main.lua");
                try (FileOutputStream output = new FileOutputStream(main)) {
                    output.write(editor.source().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            } else {
                copyDirectory(project.getRoot(), sourceRoot);
                if (repository instanceof DirectoryThemeProjectRepository) {
                    File selected = ((DirectoryThemeProjectRepository) repository).getSelected().getFile();
                    String selectedPath = selected.getCanonicalPath();
                    String projectPath = project.getRoot().getCanonicalPath();
                    if (!selectedPath.startsWith(projectPath + File.separator)) {
                        throw new IOException("Selected theme file escapes project root");
                    }
                    String relative = selectedPath.substring(projectPath.length() + 1);
                    File draftTarget = new File(sourceRoot, relative);
                    if (!draftTarget.getCanonicalPath().startsWith(sourceRoot.getCanonicalPath() + File.separator)) {
                        throw new IOException("Selected theme file escapes project root");
                    }
                    draftTarget.getParentFile().mkdirs();
                    try (FileOutputStream output = new FileOutputStream(draftTarget)) {
                        output.write(editor.source().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                syncProjectMainFileIfNeeded();
            }
            File zip = new File(getCacheDir(), "theme-editor-share/theme-" + System.currentTimeMillis() + ".zip");
            com.osfans.trime.editor.project.ThemeProjectArchive.exportDirectory(sourceRoot, zip);
            verifyExportArchive(zip);
            if (!share) {
                pendingExport = zip;
                exportZipLauncher.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").putExtra(Intent.EXTRA_TITLE, zip.getName()));
                return;
            }
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", zip);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, contentUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).setClipData(android.content.ClipData.newRawUri("theme", contentUri));
            startActivity(Intent.createChooser(intent, "Share theme ZIP"));
            workspace.setStatus("Ready to share " + zip.getName());
        } catch (Exception error) {
            workspace.setStatus("Export failed: " + error.getMessage()); Toast.makeText(this, "Unable to export theme", Toast.LENGTH_LONG).show();
        } finally { deleteDirectory(sourceRoot); }
    }

    private void verifyExportArchive(File zip) throws IOException {
        File verifyRoot = new File(getCacheDir(), "theme-editor-export-verify-" + System.nanoTime());
        try (FileInputStream input = new FileInputStream(zip)) { com.osfans.trime.editor.project.ThemeProjectArchive.extractZip(input, verifyRoot); }
        try {
            File main = findMainLua(verifyRoot); if (main == null) throw new IOException("Export verification found no unambiguous main.lua");
            ThemeProject verified = ThemeProject.Companion.discover(main.getParentFile()); ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(verified, new ThemeLuaParser());
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry())) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Export verification: " + diagnostic.getMessage());
        } finally { deleteDirectory(verifyRoot); }
    }

    private static void deleteDirectory(File file) { if (file == null || !file.exists()) return; File[] children = file.listFiles(); if (children != null) for (File child : children) deleteDirectory(child); file.delete(); }

    private static void copyDirectory(File source, File destination) throws IOException {
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("Cannot create export directory");
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(destination, child.getName());
            if (child.isDirectory()) copyDirectory(child, target);
            else if (child.isFile()) {
                try (FileInputStream input = new FileInputStream(child); FileOutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
            }
        }
    }

    private void syncProjectMainFileIfNeeded() throws IOException {
        if (project == null) return;
        com.osfans.trime.editor.project.FileThemeProjectRepository main =
                new com.osfans.trime.editor.project.FileThemeProjectRepository(project.getMainFile());
        com.osfans.trime.editor.core.ParseResult parsed = main.load(new ThemeLuaParser());
        if (parsed.getDocument().get("keyboard") == null && project.getKeyboards().isEmpty()) {
            throw new IOException("Theme project has no keyboard entry");
        }
    }

    private void savePendingSource(Uri uri) {
        String source = pendingSaveSource; pendingSaveSource = null; if (source == null) { workspace.setStatus("No pending Lua source to save"); return; }
        try {
            UriThemeProjectRepository target = new UriThemeProjectRepository(getContentResolver(), uri); target.write(source);
            String verified = target.read(); if (!source.equals(verified)) throw new IOException("Saved source verification mismatch");
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(verified); for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Saved source failed parse verification");
            repository = target; project = null; projectSnapshot = null; currentUri = uri; importedProjectUri = null; importedProjectTreeUri = null; importedProjectTreePrefix = null; openedImportedFingerprint = null; viewModel.setCurrentUri(uri); claimSession(sessionIdentity()); editor.replaceDocument(parsed.getDocument()); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(verified); openedFingerprint = null; layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            workspace.setModel(layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); viewModel.setDirty(false); deleteRecoveryDraft(); workspace.setStatus("Saved and verified: " + uri); invalidateOptionsMenu();
        } catch (Exception error) { pendingSaveSource = source; workspace.setStatus("Save failed: " + error.getMessage()); Toast.makeText(this, "Unable to save Lua source", Toast.LENGTH_LONG).show(); }
    }

    private void saveModel(ThemeEditorModel model) {
        if (!ensureWritable()) return;
        if (repository == null) {
            try {
                pendingSaveSource = editor == null ? "" : editor.source();
                com.osfans.trime.editor.core.ParseResult check = new ThemeLuaParser().parse(pendingSaveSource);
                for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : check.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Lua source contains errors");
                saveLuaLauncher.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/x-lua").putExtra(Intent.EXTRA_TITLE, "main.lua"));
            } catch (Exception error) { pendingSaveSource = null; workspace.setStatus("Save blocked: " + error.getMessage()); }
            return;
        }
        String previousSource = null;
        try {
            if (editor == null) editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            if (!isCurrentStyleFile()) {
                if (!layoutEditable || model.layoutMode == ThemeEditorModel.LayoutMode.NONE) {
                    workspace.setStatus("This Lua file has no structured keyboard layout");
                    Toast.makeText(this, "Open a keyboard Lua file before editing", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!syncModel(model)) return;
            }
            previousSource = repository.read();
            if (importedProjectTreeUri != null && repository instanceof DirectoryThemeProjectRepository) {
                String remoteFingerprint = fingerprintImportedProjectFile(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
                if (openedImportedFingerprint != null && !openedImportedFingerprint.equals(remoteFingerprint)) { showSaveConflict(); return; }
            }
            SaveResult result = saveCoordinator.save(
                    sessionIdentity() == null ? "draft" : sessionIdentity(),
                    repository,
                    editor.getDocument(),
                    openedSourceFingerprint
            );
            if (result instanceof SaveResult.ExternalConflict) {
                showSaveConflict();
                return;
            }
            if (result instanceof SaveResult.Succeeded) openedSourceFingerprint = ((SaveResult.Succeeded) result).getFingerprint();
            if (importedProjectTreeUri != null && repository instanceof DirectoryThemeProjectRepository) { File selectedFile = ((DirectoryThemeProjectRepository) repository).getSelected().getFile(); writeImportedProjectFile(selectedFile, editor.source()); openedImportedFingerprint = fingerprintImportedProjectFile(selectedFile); }
            com.osfans.trime.editor.core.ParseResult saved = new ThemeLuaParser().parse(repository.read());
            boolean savedErrors = false; for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : saved.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) { savedErrors = true; break; }
            if (savedErrors) throw new IOException("Saved source failed verification parse");
            editor.replaceDocument(saved.getDocument());
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            if (repository instanceof DirectoryThemeProjectRepository) openedFingerprint = ThemeSourceFingerprint.Companion.capture(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            if (project != null) projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
            clearMigrationHistory();
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            viewModel.setDirty(false);
            deleteRecoveryDraft();
            workspace.setStatus("Saved and verified");
        } catch (Exception error) {
            if (previousSource != null && importedProjectTreeUri != null) try { repository.write(previousSource); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(previousSource); } catch (Exception ignored) { }
            workspace.setStatus("Save failed: " + error.getMessage());
            Toast.makeText(this, "Unable to save theme", Toast.LENGTH_LONG).show();
        }
    }

    private void showSaveConflict() {
        workspace.setStatus("External file changed; unsaved editor draft retained");
        new android.app.AlertDialog.Builder(this).setTitle("Theme changed outside editor").setMessage("Reloading discards the current in-memory draft. Cancel keeps it available for source copy or ZIP export.").setNegativeButton("Keep draft", null).setPositiveButton("Reload disk", (dialog, which) -> reloadRepositoryAfterConflict()).show();
    }

    private void reloadRepositoryAfterConflict() {
        try {
            refreshImportedCacheFile();
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(repository.read());
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("External source contains Lua errors");
            editor.replaceDocument(parsed.getDocument()); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(repository.read()); layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            if (repository instanceof DirectoryThemeProjectRepository) openedFingerprint = ThemeSourceFingerprint.Companion.capture(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            if (project != null) projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); viewModel.setDirty(false); deleteRecoveryDraft(); workspace.setStatus("Reloaded external theme source");
        } catch (Exception error) { workspace.setStatus("Reload failed: " + error.getMessage()); Toast.makeText(this, "Unable to reload external theme", Toast.LENGTH_LONG).show(); }
    }

    private String recoveryIdentity() {
        String identity = sessionIdentity(); String file = currentUri == null ? "" : currentUri.toString();
        if (project != null && repository instanceof DirectoryThemeProjectRepository) try { file = ((DirectoryThemeProjectRepository) repository).getSelected().getFile().getCanonicalPath().substring(project.getRoot().getCanonicalPath().length()).replace(File.separatorChar, '/'); } catch (IOException ignored) { }
        return (identity == null ? "" : identity) + "|" + file;
    }

    private void offerRecoveryDraft() {
        if (recoveryPrompted || currentUri == null) return;
        File draft = recoveryDraftFile(), meta = recoveryMetaFile(); if (!draft.isFile() || !meta.isFile()) return;
        try {
            String uri = readSmallText(meta, 8192).trim(); if (!recoveryIdentity().equals(uri)) return;
            String source = readSmallText(draft, 4 * 1024 * 1024); com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(source);
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) { deleteRecoveryDraft(); return; }
            recoveryPrompted = true;
            new android.app.AlertDialog.Builder(this).setTitle("Recover unsaved theme draft?").setMessage("A valid private draft exists for this Lua file.").setNegativeButton("Discard draft", (dialog, which) -> deleteRecoveryDraft()).setPositiveButton("Recover", (dialog, which) -> {
                editor.replaceDocument(parsed.getDocument()); layoutEditable = findLayoutRoot(editor.getDocument()) != null; workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); viewModel.setDirty(true); workspace.setStatus("Recovered private draft; save to commit");
            }).show();
        } catch (Exception error) { deleteRecoveryDraft(); }
    }

    private void persistRecoveryDraft() {
        if (!viewModel.getDirty() || editor == null || currentUri == null) return;
        try { writePrivateText(recoveryDraftFile(), editor.source()); writePrivateText(recoveryMetaFile(), recoveryIdentity()); }
        catch (Exception ignored) { }
    }

    private static String readSmallText(File file, int limit) throws IOException {
        if (file.length() > limit) throw new IOException("Recovery draft exceeds limit");
        try (FileInputStream input = new FileInputStream(file); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("Recovery draft exceeds limit"); output.write(buffer, 0, count); } return output.toString("UTF-8"); }
    }

    private static void writePrivateText(File destination, String source) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) { output.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.getFD().sync(); }
        if (destination.exists() && !destination.delete()) throw new IOException("Cannot replace recovery draft");
        if (!temporary.renameTo(destination)) { temporary.delete(); throw new IOException("Cannot commit recovery draft"); }
    }

    private void deleteRecoveryDraft() { File draft = recoveryDraftFile(), meta = recoveryMetaFile(); if (draft.exists()) draft.delete(); if (meta.exists()) meta.delete(); }
    private File recoveryDraftFile() { return new File(getFilesDir(), "theme-editor-recovery.lua"); }
    private File recoveryMetaFile() { return new File(getFilesDir(), "theme-editor-recovery.uri"); }

    private ThemeEditorModel toUiModel(com.osfans.trime.editor.core.ThemeDocument document) {
        ThemeEditorModel model = ThemeLayoutCodec.fromDocument(document);
        applyPreviewStyles(model);
        return model;
    }

    private void applyPreviewStyles(ThemeEditorModel model) {
        com.osfans.trime.editor.core.ThemeDocument style; String source;
        workspace.setPanelPreviewSource(null);
        try {
            if (isCurrentStyleFile() && editor != null) { style = editor.getDocument(); source = editor.source(); }
            else {
                ThemeProjectFile styleSource = project == null || editor == null ? null : resolvedStyleSource(editor.getDocument());
                if (styleSource != null) { source = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); style = new ThemeLuaParser().parse(source).getDocument(); }
                else if (projectSnapshot != null && projectSnapshot.getStyle() != null && editor != null && !(editor.getDocument().get("style") instanceof ThemeValue.RawLuaNode)) { style = projectSnapshot.getStyle().getDocument(); source = projectSnapshot.getStyleSource() == null ? "" : new String(readFileBytes(projectSnapshot.getStyleSource().getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); }
                else return;
            }
            workspace.setPanelPreviewSource(source);
            applyStyleDocument(model, style);
            java.util.ArrayList<String> entityIds = new java.util.ArrayList<>(); for (ThemeStyleEntities.Entry entry : ThemeStyleEntities.list(source)) entityIds.add(entry.getId());
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>(); for (ThemeEditorModel.Key key : model.keys) ids.add(ThemeKeyStyleBatch.effectiveStyleId(key, entityIds));
            ThemeKeyStyleBatch.PreviewColors colors = ThemeKeyStyleBatch.previewColors(source, ids);
            for (ThemeEditorModel.Key key : model.keys) { String id = ThemeKeyStyleBatch.effectiveStyleId(key, entityIds); Integer fill = colors.getBackgrounds().get(id), text = colors.getTextColors().get(id); if (fill != null) key.fillColor = fill; if (text != null) key.textColor = text; }
        } catch (Exception ignored) { }
    }

    private static float numberValue(ThemeValue value, float fallback) {
        return value instanceof ThemeValue.LuaNumber ? (float) ((ThemeValue.LuaNumber) value).getValue() : fallback;
    }

    private static int colorValue(ThemeValue value, int fallback) {
        return value instanceof ThemeValue.LuaNumber ? (int) ((ThemeValue.LuaNumber) value).getValue() : fallback;
    }

    private static String trim(float value) {
        return value == (long) value ? Long.toString((long) value) : Float.toString(value);
    }

    private static String luaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Override protected void onDestroy() {
        if (isFinishing()) synchronized (ACTIVE_WRITE_SESSIONS) { if (sessionKey != null && viewModel.getSessionToken().equals(ACTIVE_WRITE_SESSIONS.get(sessionKey))) ACTIVE_WRITE_SESSIONS.remove(sessionKey); }
        super.onDestroy();
    }

    @Override protected void onStop() { persistRecoveryDraft(); super.onStop(); }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (currentUri != null) viewModel.setCurrentUri(currentUri);
        super.onSaveInstanceState(outState);
    }

    public ThemeEditorWorkspace getWorkspace() { return workspace; }
    public void setCallbacks(ThemeEditorCallbacks callbacks) { workspace.setCallbacks(callbacks); }
    public void setThemeModel(ThemeEditorModel model) { workspace.setModel(model); }
}
