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
    private static final int MENU_OPEN_LUA = 10;
    private static final int MENU_OPEN_FOLDER = 11;
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
                if (output == null) throw new IOException("无法打开导出目标"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); workspace.setStatus("ZIP 已导出");
            } catch (Exception error) { workspace.setStatus("导出失败:" + error.getMessage()); }
        }
        if (pendingExport != null) pendingExport.delete(); pendingExport = null;
    });

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
            @Override public void onOpenStyleProperties(ThemeEditorModel.Key key) { showStyleEditor(); }
            @Override public void onOpenKeyEvents(ThemeEditorModel.Key key) { showKeyEventManager(key); }
            @Override public void onOpenResources(ThemeEditorModel.Key key) { showResources(); }
            @Override public void onOpenLuaSource() { showCodeEditor(); }
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
                    .setMessage("主题有未保存的更改")
                    .setPositiveButton("保存", (dialog, which) -> saveModel(workspace.getModel()))
                    .setNegativeButton("放弃", (dialog, which) -> { deleteRecoveryDraft(); finish(); })
                    .setNeutralButton("取消", null)
                    .show();
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, MENU_PAGES, 1, "编辑器页面").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, MENU_OPEN_LUA, 2, "打开 Lua 文件").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_OPEN_FOLDER, 3, "打开主题文件夹").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_EXPORT, 20, "导出 ZIP").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_SHARE, 21, "分享 ZIP").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_DIAGNOSTICS, 22, "诊断信息").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_RESOURCES, 23, "资源").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 24, 24, "安装主题").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_CODE, 25, "Lua 源代码").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_STYLE_EDITOR, 26, "样式属性").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_COMPONENT_EDITOR, 27, "候选栏 / 工具栏 / 面板").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_COMPOSITION_EDITOR, 28, "预编辑 / 编码窗口").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_ROLLBACK_INSTALL, 29, "回滚上次安装").setEnabled(lastInstallBackup != null).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        if (project != null) {
            android.view.Menu styles = menu.addSubMenu("样式");
            for (int i = 0; i < project.getStyles().size(); i++) {
                styles.add(0, MENU_STYLE_BASE + i, i, project.getStyles().get(i).getName());
            }
            android.view.Menu keyboards = menu.addSubMenu("键盘");
            for (int i = 0; i < project.getKeyboards().size(); i++) {
                keyboards.add(0, MENU_KEYBOARD_BASE + i, i, project.getKeyboards().get(i).getName());
            }
        }
        return true;
    }

    @Override public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == MENU_PAGES) { showEditorPages(); return true; }
        if (item.getItemId() == MENU_OPEN_LUA) {
            openLuaLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE));
            return true;
        }
        if (item.getItemId() == MENU_OPEN_FOLDER) {
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
        String[] pages = {"项目主页", "新建项目", "最近项目", "键盘资源", "样式资源", "主题设置", "键盘结构", "样式属性", "候选栏 / 工具栏 / 面板", "预编辑 / 编码窗口", "预览工作区", "资源", "诊断信息", "Lua 源代码", "导出与安装", "恢复状态"};
        new android.app.AlertDialog.Builder(this).setTitle("主题编辑器页面").setItems(pages, (dialog, which) -> {
            if (which == 0) showProjectHome(); else if (which == 1) showNewProjectWizard(); else if (which == 2) showRecentProjects(); else if (which == 3) showKeyboardAssets(); else if (which == 4) showStyleAssets(); else if (which == 5) showThemeSettings(); else if (which == 6) showStructurePage(); else if (which == 7) showStyleEditor(); else if (which == 8) showVisualComponentStyleEditor(); else if (which == 9) showCompositionStyleEditor(); else if (which == 10) workspace.setStatus("预览工作区已启用;请使用“预览...”控制设备"); else if (which == 11) showResources(); else if (which == 12) showDiagnostics(); else if (which == 13) showCodeEditor(); else if (which == 14) showExportInstallPage(); else showRecoveryStatus();
        }).setNegativeButton("关闭", null).show();
    }

    private void showProjectHome() {
        StringBuilder text = new StringBuilder();
        if (project == null) text.append("单个 Lua 文件或未保存草稿"); else text.append("项目:").append(projectDisplayName == null ? project.getRoot().getName() : projectDisplayName).append("\n样式数: ").append(project.getStyles().size()).append("\n键盘数: ").append(project.getKeyboards().size()).append("\n资源数: ").append(project.getResources().size());
        text.append("\n当前文件: ").append(currentUri == null ? "未保存" : currentUri).append("\n模式: ").append(readOnlySession ? "第二会话只读" : "可写").append("\n有未保存更改: ").append(viewModel.getDirty());
        new android.app.AlertDialog.Builder(this).setTitle("项目主页").setMessage(text.toString()).setPositiveButton("关闭", null).show();
    }

    private void showNewProjectWizard() {
        if (!ensureWritable()) return;
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        EditText directory = simpleField(fields, "目录标识", "my_theme"); EditText name = simpleField(fields, "主题名称", "我的主题"); EditText author = simpleField(fields, "作者", "作者"); EditText style = simpleField(fields, "默认样式标识", "light"); EditText keyboard = simpleField(fields, "默认键盘标识", "default");
        android.widget.Spinner palette = new android.widget.Spinner(this); palette.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"浅色", "深色"})); fields.addView(palette);
        android.widget.Spinner layout = new android.widget.Spinner(this); layout.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"行布局(rows)", "弹性盒布局(flex_box)", "分页键映射(key_maps)", "绝对键布局(keys)"})); fields.addView(layout);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("新建主题项目").setView(scroll).setNegativeButton("取消", null).setPositiveButton("选择目标", (dialog, which) -> {
            try {
                pendingCreateSpec = new ThemeProjectCreator.Spec(directory.getText().toString().trim(), name.getText().toString().trim(), author.getText().toString().trim(), style.getText().toString().trim(), keyboard.getText().toString().trim(), palette.getSelectedItemPosition() == 0 ? ThemeProjectCreator.Palette.LIGHT : ThemeProjectCreator.Palette.DARK, ThemeProjectCreator.KeyboardTemplate.values()[layout.getSelectedItemPosition()]).validated();
                createProjectTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
            } catch (Exception error) { pendingCreateSpec = null; workspace.setStatus("新项目校验失败:" + error.getMessage()); Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
        }).show();
    }

    private EditText simpleField(LinearLayout parent, String hint, String value) { EditText field = new EditText(this); field.setHint(hint); field.setText(value); field.setSingleLine(true); parent.addView(field, new LinearLayout.LayoutParams(-1, -2)); return field; }

    private void createProjectInTree(Uri treeUri, ThemeProjectCreator.Spec spec) {
        File draft = new File(getCacheDir(), "theme-editor-create-" + System.nanoTime()); DocumentFile created = null;
        try {
            ThemeProject generated = ThemeProjectCreator.create(draft, spec); ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(generated, new ThemeLuaParser());
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry())) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("生成的项目:" + diagnostic.getMessage());
            DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri); if (tree == null || !tree.canWrite()) throw new IOException("目标目录不可写");
            if (tree.findFile(spec.getDirectoryName()) != null) throw new IOException("该目录标识对应的项目已存在");
            created = tree.createDirectory(spec.getDirectoryName()); if (created == null) throw new IOException("无法创建项目目录");
            copyProjectToDocument(draft, created); if (!fileManifest(draft).equals(documentManifest(created))) throw new IOException("新建项目校验失败");
            File cache = new File(getCacheDir(), "theme-editor-created-" + System.nanoTime()); copyDirectory(draft, cache);
            importedProjectUri = treeUri; importedProjectTreeUri = treeUri; importedProjectTreePrefix = spec.getDirectoryName(); openedImportedFingerprint = null; rememberRecentProject(treeUri, spec.getDirectoryName(), spec.getDirectoryName()); loadProject(cache, spec.getDirectoryName()); workspace.setStatus("项目已创建并校验:" + spec.getThemeName());
        } catch (Exception error) { if (created != null) created.delete(); workspace.setStatus("项目创建失败:" + error.getMessage()); Toast.makeText(this, "无法创建主题项目", Toast.LENGTH_LONG).show(); }
        finally { deleteDirectory(draft); }
    }

    private void rememberRecentProject(Uri uri, String name, String prefix) {
        android.content.SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit().putString("recent_uri", uri.toString()).putString("recent_name", name == null ? "主题项目" : name);
        if (prefix == null) edit.remove("recent_prefix"); else edit.putString("recent_prefix", prefix); edit.apply();
    }

    private void showRecentProjects() {
        String uri = getPreferences(MODE_PRIVATE).getString("recent_uri", null), name = getPreferences(MODE_PRIVATE).getString("recent_name", "主题项目"), prefix = getPreferences(MODE_PRIVATE).getString("recent_prefix", null);
        if (uri == null) { new android.app.AlertDialog.Builder(this).setTitle("最近项目").setMessage("没有最近打开的 SAF 项目").setPositiveButton("关闭", null).show(); return; }
        new android.app.AlertDialog.Builder(this).setTitle("最近项目").setItems(new String[]{name}, (dialog, which) -> { try { loadRecentProject(Uri.parse(uri), prefix, name); } catch (Exception error) { workspace.setStatus("最近项目的访问权限已失效,请重新打开文件夹"); } }).setNegativeButton("关闭", null).setNeutralButton("移除记录", (dialog, which) -> getPreferences(MODE_PRIVATE).edit().remove("recent_uri").remove("recent_name").remove("recent_prefix").apply()).show();
    }

    private void showThemeSettings() {
        if (project == null) { Toast.makeText(this, "请先打开主题项目", Toast.LENGTH_LONG).show(); return; }
        try {
            com.osfans.trime.editor.core.ThemeDocument main = new ThemeLuaParser().parse(readSmallText(project.getMainFile(), 4 * 1024 * 1024)).getDocument();
            LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
            EditText name = simpleField(fields, "主题名称", stringValue(main.get("name"), projectDisplayName)); EditText author = simpleField(fields, "作者", stringValue(main.get("author"), "作者"));
            android.widget.Spinner style = new android.widget.Spinner(this); java.util.ArrayList<String> styles = new java.util.ArrayList<>(); for (ThemeProjectFile file : project.getStyles()) styles.add(file.getName()); style.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, styles)); style.setSelection(Math.max(0, styles.indexOf(stringValue(main.get("style"), "light")))); fields.addView(style);
            android.widget.Spinner keyboard = new android.widget.Spinner(this); java.util.ArrayList<String> keyboards = new java.util.ArrayList<>(); for (ThemeProjectFile file : project.getKeyboards()) keyboards.add(file.getName()); keyboard.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, keyboards)); keyboard.setSelection(Math.max(0, keyboards.indexOf(stringValue(main.get("keyboard"), "qwerty26")))); fields.addView(keyboard);
            android.widget.Button actionLabels = new android.widget.Button(this); actionLabels.setText("编辑操作标签(action_labels)"); actionLabels.setOnClickListener(view -> showActionLabelsEditor()); fields.addView(actionLabels);
            android.widget.Button presetEvents = new android.widget.Button(this); presetEvents.setText("管理预设事件(preset_keys)"); presetEvents.setOnClickListener(view -> showPresetEventManager()); fields.addView(presetEvents);
            android.widget.Button toolbarKeys = new android.widget.Button(this); toolbarKeys.setText("管理所选样式的工具栏按键(toolbar.keys)"); toolbarKeys.setOnClickListener(view -> { ThemeProjectFile target = project.style((String) style.getSelectedItem()); if (target == null) workspace.setStatus("所选样式资源不可用"); else showToolbarKeyManager(target); }); fields.addView(toolbarKeys);
            android.widget.Button panelComponents = new android.widget.Button(this); panelComponents.setText("管理候选栏、符号栏和剪贴板栏"); panelComponents.setOnClickListener(view -> { ThemeProjectFile target = project.style((String) style.getSelectedItem()); if (target == null) workspace.setStatus("所选样式资源不可用"); else showPanelComponentManager(target); }); fields.addView(panelComponents);
            TextView note = new TextView(this); note.setText("动态取键盘(get_keyboard)、命令(command)、脚本(script)和回调(callback)仅保留为代码,编辑器绝不会执行它们。"); note.setPadding(0, 16, 0, 0); fields.addView(note);
            new android.app.AlertDialog.Builder(this).setTitle("主题设置").setView(fields).setNegativeButton("取消", null).setNeutralButton("打开高级 Lua", (dialog, which) -> showCodeEditor()).setPositiveButton("应用", (dialog, which) -> {
                if (!ensureAssetWritable()) return; try { String nextStyle = (String) style.getSelectedItem(), nextKeyboard = (String) keyboard.getSelectedItem(); mutateMainWithMirror(() -> ThemeProjectMutator.updateMetadata(project, name.getText().toString(), author.getText().toString(), nextStyle, nextKeyboard)); projectDisplayName = name.getText().toString().trim(); workspace.setStatus("主题设置已更新"); } catch (Exception error) { workspace.setStatus("主题设置更新失败:" + error.getMessage()); }
            }).show();
        } catch (Exception error) { workspace.setStatus("无法加载主题设置:" + error.getMessage()); }
    }
    private static String actionLabel(String id) {
        switch (id) {
            case "none": return "无操作(none)";
            case "send": return "发送(send)";
            case "go": return "前往(go)";
            case "done": return "完成(done)";
            case "search": return "搜索(search)";
            case "previous": return "上一个(previous)";
            case "next": return "下一个(next)";
            default: return id;
        }
    }

    private static String[] actionDisplayLabels(String[] ids) {
        String[] labels = new String[ids.length];
        for (int i = 0; i < ids.length; i++) labels[i] = actionLabel(ids[i]);
        return labels;
    }

    private void showActionLabelsEditor() {
        if (!ensureAssetWritable() || project == null) return;
        try {
            String source = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.Map<String, String> current = ThemePresetEvents.actionLabels(source);
            LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); java.util.LinkedHashMap<String, EditText> inputs = new java.util.LinkedHashMap<>();
            String[] actionIds = {"none", "send", "go", "done", "search", "previous", "next"}; java.util.LinkedHashMap<String, android.widget.CheckBox> missing = new java.util.LinkedHashMap<>();
            for (String id : actionIds) { android.widget.CheckBox inherit = new android.widget.CheckBox(this); inherit.setText("移除 " + actionLabel(id) + " 并使用运行时回退值"); inherit.setChecked(!current.containsKey(id)); fields.addView(inherit); EditText input = simpleField(fields, "操作标签(action_labels)." + id + "(显式空值会保留)", current.containsKey(id) ? current.get(id) : ""); input.setEnabled(!inherit.isChecked()); inherit.setOnCheckedChangeListener((button, checked) -> input.setEnabled(!checked)); missing.put(id, inherit); inputs.put(id, input); }
            android.widget.Spinner previewAction = new android.widget.Spinner(this); previewAction.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, actionDisplayLabels(actionIds))); fields.addView(previewAction);
            new android.app.AlertDialog.Builder(this).setTitle("编辑器操作标签(action_labels)").setMessage("仅预览标签,不会发送任何编辑器操作。").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> {
                try { java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(); for (java.util.Map.Entry<String, EditText> input : inputs.entrySet()) values.put(input.getKey(), missing.get(input.getKey()).isChecked() ? null : input.getValue().getText().toString()); mutateMainPreset(latest -> { if (!ThemeSaveCoordinator.Companion.fingerprint(source).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("main.lua changed after opening action labels; reopen editor"); return ThemePresetEvents.updateActionLabels(latest, values); }, "已更新操作标签(action_labels)"); String selected = actionIds[previewAction.getSelectedItemPosition()]; ThemeEditorModel previewModel = workspace.getModel(); previewModel.editorActionLabel = values.get(selected) == null ? "" : values.get(selected); workspace.setModelKeepingHistory(previewModel); workspace.setStatus("已更新操作标签(action_labels)并预览 " + selected + ";未执行任何操作"); }
                catch (Exception error) { workspace.setStatus("操作标签更新被阻止:" + error.getMessage()); }
            }).show();
        } catch (Exception error) { workspace.setStatus("操作标签只能通过代码编辑:" + error.getMessage()); }
    }

    private static final class PresetUsage {
        final java.util.LinkedHashMap<File, Integer> references = new java.util.LinkedHashMap<>();
        final java.util.LinkedHashMap<File, String> originals = new java.util.LinkedHashMap<>();
        final java.util.ArrayList<String> uncertain = new java.util.ArrayList<>(); int total;
    }

    private void showPresetEventManager() {
        if (!ensureAssetWritable() || project == null) return;
        try {
            String source = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.List<ThemePresetEvents.Event> events = ThemePresetEvents.list(source); String[] labels = new String[events.size() + 1]; labels[0] = "+ 新建预设事件";
            for (int i = 0; i < events.size(); i++) { ThemePresetEvents.Event event = events.get(i); labels[i + 1] = event.getId() + " — " + presetSummary(event) + (event.getRisky() ? " [仅保留代码,绝不执行]" : ""); }
            new android.app.AlertDialog.Builder(this).setTitle("预设事件(preset_keys)——静态编辑器").setItems(labels, (dialog, which) -> { if (which == 0) showPresetEventEditor(null); else showPresetEventActions(events.get(which - 1)); }).setNegativeButton("关闭", null).show();
        } catch (Exception error) { workspace.setStatus("预设按键(preset_keys)只能通过代码编辑:" + error.getMessage()); }
    }

    private static String presetSummary(ThemePresetEvents.Event event) {
        if (!event.getLabel().isEmpty()) return event.getLabel(); if (!event.getCommand().isEmpty()) return "command=" + event.getCommand(); if (!event.getSend().isEmpty()) return "send=" + event.getSend(); if (!event.getText().isEmpty()) return "文本(text)"; if (!event.getCommit().isEmpty()) return "上屏文本(commit)"; return "空事件";
    }

    private void showPresetEventActions(ThemePresetEvents.Event event) {
        try {
            PresetUsage usage = collectPresetUsage(event.getId()); String details = "静态引用:" + usage.total + (usage.uncertain.isEmpty() ? "" : "\n存在不确定引用的原始 Lua 文件: " + android.text.TextUtils.join(", ", usage.uncertain)) + "\n执行风险: " + (event.getRisky() ? "命令(command)/脚本(script)会保留但绝不执行" : "预览仅显示摘要");
            String[] actions = {"编辑字段", "复制", "重命名并替换引用", "无引用时删除", "查看摘要"};
            new android.app.AlertDialog.Builder(this).setTitle(event.getId()).setMessage(details).setItems(actions, (dialog, which) -> { if (which == 0) showPresetEventEditor(event); else if (which == 1) promptCopyPreset(event); else if (which == 2) promptRenamePreset(event, usage); else if (which == 3) confirmDeletePreset(event, usage); }).setNegativeButton("关闭", null).show();
        } catch (Exception error) { workspace.setStatus("预设引用分析失败:" + error.getMessage()); }
    }

    private static String formatEventStates(java.util.List<String> values) { java.util.ArrayList<String> lines = new java.util.ArrayList<>(); for (String value : values) lines.add(value.isEmpty() ? "\\0" : value.replace("\\", "\\\\").replace("\n", "\\n")); return android.text.TextUtils.join("\n", lines); }
    private static java.util.ArrayList<String> parseEventStates(String source) { java.util.ArrayList<String> result = new java.util.ArrayList<>(); if (source.isEmpty()) return result; for (String line : source.split("\n", -1)) { if (line.equals("\\0")) { result.add(""); continue; } StringBuilder value = new StringBuilder(); for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '\\' && i + 1 < line.length()) { char next = line.charAt(++i); value.append(next == 'n' ? '\n' : next); } else value.append(c); } result.add(value.toString()); } return result; }

    private void showPresetEventEditor(ThemePresetEvents.Event event) {
        if (!ensureAssetWritable()) return; final String openedSource; ThemePresetEvents.Event initial;
        try { openedSource = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (event == null) initial = new ThemePresetEvents.Event("Preset_new", "", "", "", "", "", "", "", "", "", "", java.util.Collections.emptyList(), "", false, false, true, null); else { ThemePresetEvents.Event current = null; for (ThemePresetEvents.Event candidate : ThemePresetEvents.list(openedSource)) if (candidate.getId().equals(event.getId())) { current = candidate; break; } if (current == null) throw new IOException("Preset changed or was deleted; reopen manager"); initial = current; } }
        catch (Exception error) { workspace.setStatus("预设编辑被阻止:" + error.getMessage()); return; }
        final ThemePresetEvents.Event openedEvent = initial;
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        EditText id = simpleField(fields, "预设标识", initial.getId()); id.setEnabled(event == null); EditText label = simpleField(fields, "标签(label)", initial.getLabel()); EditText send = simpleField(fields, "发送按键(send)", initial.getSend()); EditText text = simpleField(fields, "文本(text)", initial.getText()); EditText commit = simpleField(fields, "上屏文本(commit)", initial.getCommit()); EditText command = simpleField(fields, "命令(command,保留但绝不执行)", initial.getCommand()); EditText option = simpleField(fields, "选项(option)", initial.getOption()); EditText select = simpleField(fields, "选择(select)", initial.getSelect()); EditText toggle = simpleField(fields, "切换(toggle)", initial.getToggle()); EditText preview = simpleField(fields, "预览(preview)", initial.getPreview()); EditText description = simpleField(fields, "说明(description)", initial.getDescription()); EditText states = simpleField(fields, "状态(states):每行一个;\\0 表示空值,\\n 表示内嵌换行", formatEventStates(initial.getStates())); states.setSingleLine(false); states.setMinLines(3); EditText shiftLock = simpleField(fields, "Shift 锁定(shift_lock):click/double/long", initial.getShiftLock()); EditText index = simpleField(fields, "索引(index,保留;预设引用不使用)", initial.getIndex() == null ? "" : trim(initial.getIndex().floatValue())); index.setEnabled(false);
        android.widget.CheckBox repeatable = new android.widget.CheckBox(this); repeatable.setText("可重复(repeatable)"); repeatable.setChecked(initial.getRepeatable()); fields.addView(repeatable); android.widget.CheckBox sticky = new android.widget.CheckBox(this); sticky.setText("保持(sticky)"); sticky.setChecked(initial.getSticky()); fields.addView(sticky); android.widget.CheckBox functional = new android.widget.CheckBox(this); functional.setText("功能键(functional)"); functional.setChecked(initial.getFunctional()); fields.addView(functional);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle(event == null ? "新建预设事件" : "编辑预设事件").setMessage("仅编辑静态字段。“应用”绝不会发送按键、上屏文本,也不会调用命令、脚本、Intent 或回调。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> {
            try { Double nextIndex = openedEvent.getIndex(); java.util.ArrayList<String> nextStates = parseEventStates(states.getText().toString()); ThemePresetEvents.Event next = new ThemePresetEvents.Event(id.getText().toString().trim(), send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), nextStates, shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); mutateMainPreset(source -> { if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(source))) throw new IOException("main.lua changed after opening preset editor; reopen it"); return ThemePresetEvents.put(source, next, event != null); }, "已更新预设 " + next.getId() + ";未执行任何操作"); }
            catch (Exception error) { workspace.setStatus("预设更新被阻止:" + error.getMessage()); }
        }).show();
    }

    private interface MainSourceMutation { String apply(String source) throws Exception; }
    private void mutateMainPreset(MainSourceMutation mutation, String success) throws Exception { String source = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(); java.util.LinkedHashMap<File, String> originals = new java.util.LinkedHashMap<>(); changes.put(project.getMainFile(), mutation.apply(source)); originals.put(project.getMainFile(), source); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success); }

    private void promptCopyPreset(ThemePresetEvents.Event event) { LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "副本标识", event.getId() + "_copy"); new android.app.AlertDialog.Builder(this).setTitle("复制预设事件").setView(fields).setNegativeButton("取消", null).setPositiveButton("复制", (dialog, which) -> { try { mutateMainPreset(source -> ThemePresetEvents.copy(source, event.getId(), id.getText().toString().trim()), "已复制预设事件"); } catch (Exception error) { workspace.setStatus("预设复制被阻止:" + error.getMessage()); } }).show(); }

    private void promptRenamePreset(ThemePresetEvents.Event event, PresetUsage usage) { if (!usage.uncertain.isEmpty()) { workspace.setStatus("重命名被原始 Lua 引用阻止:" + android.text.TextUtils.join(", ", usage.uncertain)); return; } LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "新预设标识", event.getId() + "_renamed"); new android.app.AlertDialog.Builder(this).setTitle("重命名预设并替换引用?").setMessage("将替换 " + usage.total + " 个静态引用,涉及 " + usage.references.size() + " 个文件。不会执行任何事件。").setView(fields).setNegativeButton("取消", null).setPositiveButton("重命名", (dialog, which) -> renamePresetTransaction(event.getId(), id.getText().toString().trim())).show(); }

    private void renamePresetTransaction(String oldId, String newId) {
        try {
            PresetUsage usage = collectPresetUsage(oldId); if (!usage.uncertain.isEmpty()) throw new IOException("Raw Lua references changed after review");
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(usage.originals); String main = originals.get(project.getMainFile()); if (main == null) throw new IOException("main.lua was not included in the project-wide reference snapshot"); changes.put(project.getMainFile(), ThemePresetEvents.renameDefinition(main, oldId, newId)); int count = 0;
            for (File file : usage.references.keySet()) { String original = originals.get(file); if (original == null) throw new IOException("Reference source disappeared from the project snapshot: " + relativeProjectFile(file)); String base = file.equals(project.getMainFile()) ? changes.get(file) : original; ThemePresetEvents.ReferenceUpdate update = ThemePresetEvents.replaceReferences(base, oldId, newId); changes.put(file, update.getSource()); count += update.getCount(); }
            // Include unchanged Lua files and the manifest so a previously clean/new file cannot gain a reference between scan and commit.
            applyProjectSourceTransaction(changes, originals, usage.originals.keySet()); workspace.setStatus("已重命名预设并替换 " + count + " 个静态引用");
        } catch (Exception error) { workspace.setStatus("预设重命名被阻止:" + error.getMessage()); }
    }

    private void confirmDeletePreset(ThemePresetEvents.Event event, PresetUsage usage) { if (usage.total > 0 || !usage.uncertain.isEmpty()) { workspace.setStatus("预设删除被阻止:" + usage.total + " 个引用或无法确定的原始 Lua"); return; } new android.app.AlertDialog.Builder(this).setTitle("删除无引用的预设?").setMessage(event.getId()).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> { try { PresetUsage current = collectPresetUsage(event.getId()); if (current.total > 0 || !current.uncertain.isEmpty()) throw new IOException("References changed after review"); String main = current.originals.get(project.getMainFile()); if (main == null) throw new IOException("main.lua was not included in the project-wide reference snapshot"); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(); changes.put(project.getMainFile(), ThemePresetEvents.deleteDefinition(main, event.getId())); applyProjectSourceTransaction(changes, current.originals, current.originals.keySet()); workspace.setStatus("已删除无引用的预设 " + event.getId()); } catch (Exception error) { workspace.setStatus("预设删除被阻止:" + error.getMessage()); } }).show(); }

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
        if (project == null) { Toast.makeText(this, "请先打开主题项目", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[project.getKeyboards().size() + 1]; labels[0] = "+ 新建键盘"; for (int i = 0; i < project.getKeyboards().size(); i++) labels[i + 1] = project.getKeyboards().get(i).getName();
        new android.app.AlertDialog.Builder(this).setTitle("键盘资源").setItems(labels, (dialog, which) -> { if (which == 0) createKeyboardAsset(); else showKeyboardAssetActions(project.getKeyboards().get(which - 1)); }).setNegativeButton("关闭", null).show();
    }

    private void createKeyboardAsset() {
        if (!ensureAssetWritable()) return; LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText id = simpleField(fields, "键盘标识", "keyboard_new"); android.widget.Spinner layout = new android.widget.Spinner(this); layout.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"行布局(rows)", "弹性盒布局(flex_box)", "分页键映射(key_maps)", "绝对键布局(keys)"})); fields.addView(layout);
        new android.app.AlertDialog.Builder(this).setTitle("新建键盘").setView(fields).setNegativeButton("取消", null).setPositiveButton("创建", (dialog, which) -> {
            try { String value = id.getText().toString().trim(); ThemeProjectCreator.Spec spec = new ThemeProjectCreator.Spec("template", value, "Editor", "light", value, ThemeProjectCreator.Palette.LIGHT, ThemeProjectCreator.KeyboardTemplate.values()[layout.getSelectedItemPosition()]); ThemeProjectFile created = ThemeProjectMutator.createKeyboard(project, value, ThemeProjectCreator.keyboardSource(spec)); try { mirrorCreatedProjectFile(created.getFile()); } catch (Exception error) { created.getFile().delete(); throw error; } refreshProjectAfterAssetMutation(); requestProjectFileSwitch(project.keyboard(value)); workspace.setStatus("已创建键盘 " + value); }
            catch (Exception error) { workspace.setStatus("键盘创建失败:" + error.getMessage()); }
        }).show();
    }

    private void showKeyboardAssetActions(ThemeProjectFile file) {
        String[] actions = {"打开", "编辑顶层字段", "复制", "重命名", "设为默认", "删除"};
        new android.app.AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (dialog, which) -> { if (which == 0) requestProjectFileSwitch(file); else if (which == 1) showKeyboardMetadataEditor(file); else if (which == 2) promptCopyKeyboard(file); else if (which == 3) promptRenameKeyboard(file); else if (which == 4) setDefaultKeyboard(file); else confirmDeleteKeyboard(file); }).setNegativeButton("关闭", null).show();
    }

    private void showKeyboardMetadataEditor(ThemeProjectFile file) {
        if (!ensureAssetWritable()) return;
        try {
            ThemeProjectMutator.KeyboardMetadata metadata = ThemeProjectMutator.readKeyboardMetadata(file);
            LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
            EditText name = simpleField(fields, "键盘名称", metadata.getName()); EditText author = simpleField(fields, "作者", metadata.getAuthor()); EditText style = simpleField(fields, "样式引用(style,可选)", metadata.getStyle() == null ? "" : metadata.getStyle());
            android.widget.CheckBox lock = new android.widget.CheckBox(this); lock.setText("锁定(lock)"); lock.setChecked(metadata.getLock()); fields.addView(lock);
            android.widget.CheckBox asciiMode = new android.widget.CheckBox(this); asciiMode.setText("ASCII 模式(ascii_mode)"); asciiMode.setChecked(metadata.getAsciiMode()); fields.addView(asciiMode);
            EditText keyWidth = simpleField(fields, "按键宽度(key_width,留空继承)", metadata.getKeyWidth() == null ? "" : trim(metadata.getKeyWidth().floatValue())); EditText keyHeight = simpleField(fields, "按键高度(key_height,留空继承)", metadata.getKeyHeight() == null ? "" : trim(metadata.getKeyHeight().floatValue()));
            TextView note = new TextView(this); note.setText("动态值只能通过代码编辑。尺寸留空会移除顶层字段并恢复运行时回退值。"); fields.addView(note);
            android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
            new android.app.AlertDialog.Builder(this).setTitle("键盘字段:" + file.getName()).setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> {
                try {
                    ThemeProjectMutator.KeyboardMetadata next = new ThemeProjectMutator.KeyboardMetadata(name.getText().toString().trim(), author.getText().toString().trim(), emptyToNull(style), lock.isChecked(), asciiMode.isChecked(), optionalPositiveDouble(keyWidth, "key_width"), optionalPositiveDouble(keyHeight, "key_height"));
                    byte[] backup = readFileBytes(file.getFile(), 4L * 1024 * 1024);
                    boolean current = isCurrentProjectFile(file);
                    try { ThemeProjectMutator.updateKeyboardMetadata(file, next); mirrorExistingProjectFile(file.getFile()); refreshProjectAfterAssetMutation(); }
                    catch (Exception error) { try (FileOutputStream output = new FileOutputStream(file.getFile(), false)) { output.write(backup); output.getFD().sync(); } if (importedProjectTreeUri != null) try { writeImportedProjectFile(file.getFile(), new String(backup, java.nio.charset.StandardCharsets.UTF_8)); } catch (Exception restoreError) { error.addSuppressed(restoreError); } throw error; }
                    if (current) loadProjectFile(project.keyboard(file.getName()));
                    workspace.setStatus("已更新键盘字段:" + file.getName());
                } catch (Exception error) { workspace.setStatus("键盘字段更新失败:" + error.getMessage()); }
            }).show();
        } catch (Exception error) { workspace.setStatus("无法读取键盘字段:" + error.getMessage()); }
    }

    private static String emptyToNull(EditText field) { String value = field.getText().toString().trim(); return value.isEmpty() ? null : value; }
    private static Double optionalPositiveDouble(EditText field, String name) {
        String value = field.getText().toString().trim(); if (value.isEmpty()) return null;
        double parsed = Double.parseDouble(value); if (!(parsed > 0) || Double.isInfinite(parsed) || Double.isNaN(parsed)) throw new IllegalArgumentException(name + " must be a positive number"); return parsed;
    }

    private void promptCopyKeyboard(ThemeProjectFile file) { promptKeyboardId("复制键盘", file.getName() + "_copy", id -> { ThemeProjectFile created = ThemeProjectMutator.copyKeyboard(project, file, id); try { mirrorCreatedProjectFile(created.getFile()); } catch (Exception error) { created.getFile().delete(); throw error; } refreshProjectAfterAssetMutation(); workspace.setStatus("已复制键盘 " + id); }); }
    private void promptRenameKeyboard(ThemeProjectFile file) { if (isCurrentProjectFile(file)) { workspace.setStatus("重命名当前键盘前请先打开另一个文件"); return; } promptKeyboardId("重命名键盘", file.getName(), id -> { File old = file.getFile(); ThemeProjectFile renamed = ThemeProjectMutator.renameKeyboard(project, file, id); try { mirrorRenamedProjectFile(old, renamed.getFile()); } catch (Exception error) { renamed.getFile().renameTo(old); throw error; } refreshProjectAfterAssetMutation(); requestProjectFileSwitch(project.keyboard(id)); workspace.setStatus("键盘已重命名为 " + id); }); }
    private interface KeyboardIdAction { void run(String id) throws Exception; }
    private void promptKeyboardId(String title, String initial, KeyboardIdAction action) { if (!ensureAssetWritable()) return; LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "键盘标识", initial); new android.app.AlertDialog.Builder(this).setTitle(title).setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { action.run(id.getText().toString().trim()); } catch (Exception error) { workspace.setStatus(title + " 失败:" + error.getMessage()); } }).show(); }

    private void setDefaultKeyboard(ThemeProjectFile file) { if (!ensureAssetWritable()) return; try { mutateMainWithMirror(() -> ThemeProjectMutator.setDefaultKeyboard(project, file.getName())); workspace.setStatus("默认键盘:" + file.getName()); } catch (Exception error) { workspace.setStatus("默认键盘更新失败:" + error.getMessage()); } }
    private void confirmDeleteKeyboard(ThemeProjectFile file) { if (!ensureAssetWritable()) return; if (isCurrentProjectFile(file)) { workspace.setStatus("删除当前键盘前请先打开另一个文件"); return; } new android.app.AlertDialog.Builder(this).setTitle("删除键盘?").setMessage(file.getName()).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> { try { ThemeProjectMutator.validateKeyboardDeletion(project, file); if (importedProjectTreeUri != null) deleteImportedProjectPath(file.getFile()); if (!file.getFile().delete()) throw new IOException("无法删除本地键盘缓存"); refreshProjectAfterAssetMutation(); workspace.setStatus("已删除键盘 " + file.getName()); } catch (Exception error) { workspace.setStatus("键盘删除被阻止:" + error.getMessage()); } }).show(); }

    private interface MainMutation { void run() throws Exception; }
    private void mutateMainWithMirror(MainMutation mutation) throws Exception {
        byte[] backup = readFileBytes(project.getMainFile(), 4L * 1024 * 1024);
        try { mutation.run(); mirrorExistingProjectFile(project.getMainFile()); refreshProjectAfterAssetMutation(); }
        catch (Exception error) { try (FileOutputStream output = new FileOutputStream(project.getMainFile(), false)) { output.write(backup); output.getFD().sync(); } throw error; }
    }

    private void showStyleAssets() {
        if (project == null) { Toast.makeText(this, "请先打开主题项目", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[project.getStyles().size()]; for (int i = 0; i < labels.length; i++) labels[i] = project.getStyles().get(i).getName();
        new android.app.AlertDialog.Builder(this).setTitle("样式资源").setItems(labels, (dialog, which) -> showStyleAssetActions(project.getStyles().get(which))).setNegativeButton("关闭", null).show();
    }
    private void showStyleAssetActions(ThemeProjectFile file) {
        String[] actions = {"打开", "管理样式实体", "管理工具栏按键", "管理面板栏", "复制样式资源", "重命名样式资源", "设为默认", "删除样式资源"};
        new android.app.AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (dialog, which) -> { if (which == 0) requestProjectFileSwitch(file); else if (which == 1) showStyleEntityManager(file); else if (which == 2) showToolbarKeyManager(file); else if (which == 3) showPanelComponentManager(file); else if (which == 4) promptStyleId("复制样式", file.getName() + "_copy", id -> { ThemeProjectFile created = ThemeProjectMutator.copyStyle(project, file, id); try { mirrorCreatedProjectDirectory(created.getFile().getParentFile()); } catch (Exception error) { deleteDirectory(created.getFile().getParentFile()); throw error; } refreshProjectAfterAssetMutation(); }); else if (which == 5) { if (isCurrentProjectFile(file)) { workspace.setStatus("重命名当前样式前请先打开另一个文件"); return; } promptStyleId("重命名样式", file.getName(), id -> { File old = file.getFile().getParentFile(); ThemeProjectFile renamed = ThemeProjectMutator.renameStyle(project, file, id); try { mirrorRenamedProjectDirectory(old, renamed.getFile().getParentFile()); } catch (Exception error) { renamed.getFile().getParentFile().renameTo(old); throw error; } refreshProjectAfterAssetMutation(); requestProjectFileSwitch(project.style(id)); }); } else if (which == 6) { if (!ensureAssetWritable()) return; try { mutateMainWithMirror(() -> ThemeProjectMutator.setDefaultStyle(project, file.getName())); workspace.setStatus("默认样式:" + file.getName()); } catch (Exception error) { workspace.setStatus("默认样式设置失败:" + error.getMessage()); } } else confirmDeleteStyle(file); }).setNegativeButton("关闭", null).show();
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
            new android.app.AlertDialog.Builder(this).setTitle(styleFile.getName() + " 的面板组件").setMessage("面板工具栏数组只接受字符串。内置名称仅作静态预览,绝不会调用。").setItems(labels, (dialog, which) -> { if (which == 0) editCandidateFilter(styleFile, source, filter); else if (which == 1) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.CANDIDATE_EXPANDED, candidate); else if (which == 2) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.SYMBOL, symbol); else if (which == 3) editPanelTabBar(styleFile, source, ThemePanelComponents.Panel.SYMBOL, symbolTab); else if (which == 4) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.CLIPBOARD, clipboard); else editPanelTabBar(styleFile, source, ThemePanelComponents.Panel.CLIPBOARD, clipboardTab); }).setNegativeButton("关闭", null).setNeutralButton("打开样式源代码", (dialog, which) -> requestProjectFileSwitch(styleFile)).show();
        } catch (Exception error) { workspace.setStatus("面板组件管理被阻止:" + error.getMessage()); }
    }

    private static String panelToolbarSummary(ThemePanelComponents.Toolbar value) { return "gravity=" + value.getGravity() + ", keys=" + value.getKeys().size() + (value.getHeight() == null ? "" : ", height=" + value.getHeight()) + (value.getInherited() ? " [literal override after inherited root]" : ""); }
    private static String panelTabSummary(ThemePanelComponents.TabBar value) { return "gravity=" + (value.getGravity() == null ? "runtime default" : value.getGravity()) + ", height=" + (value.getHeight() == null ? "runtime default" : value.getHeight()); }

    private void editCandidateFilter(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.FilterBar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner show = nullableSpinner(fields, "show", current.getShowExplicit() ? Boolean.valueOf(current.getShow()) : null); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"left", "top", "right", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null);
        new android.app.AlertDialog.Builder(this).setTitle("候选过滤栏").setMessage("继承会移除字面字段,并恢复显示(show)=true、重力方向(gravity)=left 的运行时默认值。").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateCandidateFilter(source, nullableSpinnerBoolean(show), nullableSpinnerString(gravity)), "已更新候选过滤栏")).show();
    }

    private void editPanelToolbar(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.Panel panel, ThemePanelComponents.Toolbar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"left", "top", "right", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null); EditText height = null; android.widget.CheckBox inheritHeight = null;
        if (panel != ThemePanelComponents.Panel.CANDIDATE_EXPANDED) { inheritHeight = new android.widget.CheckBox(this); inheritHeight.setText("移除高度并使用运行时布局"); inheritHeight.setChecked(!current.getHeightExplicit()); fields.addView(inheritHeight); height = simpleField(fields, "高度(height,有限非负数)", current.getHeight() == null ? "" : current.getHeight().toString()); height.setEnabled(!inheritHeight.isChecked()); EditText target = height; inheritHeight.setOnCheckedChangeListener((button, checked) -> target.setEnabled(!checked)); }
        android.widget.CheckBox inheritKeys = new android.widget.CheckBox(this); inheritKeys.setText("移除按键并使用面板默认值"); inheritKeys.setChecked(!current.getKeysExplicit()); fields.addView(inheritKeys); EditText keys = simpleField(fields, "按键(keys):每行一个字面字符串", formatEventStates(current.getKeys())); keys.setSingleLine(false); keys.setMinLines(4); keys.setEnabled(!inheritKeys.isChecked()); inheritKeys.setOnCheckedChangeListener((button, checked) -> keys.setEnabled(!checked)); TextView defaults = new TextView(this); defaults.setText("缺少按键(keys)时回退为:" + android.text.TextUtils.join(", ", current.getKeys()) + "。此处不接受表或事件。"); fields.addView(defaults); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2)); final EditText heightField = height; final android.widget.CheckBox removeHeight = inheritHeight;
        new android.app.AlertDialog.Builder(this).setTitle(panel + " 工具栏(tool_bar)").setMessage("隐藏(hide)/上翻页(page_up)/下翻页(page_down)/字符过滤(char_filter)/撤销(undo)/退格(BackSpace)仅保留为静态名称,不会执行操作。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { Double nextHeight = panel == ThemePanelComponents.Panel.CANDIDATE_EXPANDED || removeHeight.isChecked() ? null : Double.valueOf(heightField.getText().toString().trim()); java.util.List<String> nextKeys = inheritKeys.isChecked() ? null : parseEventStates(keys.getText().toString()); mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateToolbar(source, panel, nullableSpinnerString(gravity), nextHeight, nextKeys), "已更新 " + panel + " 工具栏(tool_bar)"); } catch (Exception error) { workspace.setStatus("面板工具栏更新被阻止:" + error.getMessage()); } }).show();
    }

    private void editPanelTabBar(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.Panel panel, ThemePanelComponents.TabBar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"top", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null); android.widget.CheckBox inheritHeight = new android.widget.CheckBox(this); inheritHeight.setText("移除高度并使用运行时布局"); inheritHeight.setChecked(!current.getHeightExplicit()); fields.addView(inheritHeight); EditText height = simpleField(fields, "高度(height,有限非负数)", current.getHeight() == null ? "" : current.getHeight().toString()); height.setEnabled(!inheritHeight.isChecked()); inheritHeight.setOnCheckedChangeListener((button, checked) -> height.setEnabled(!checked));
        new android.app.AlertDialog.Builder(this).setTitle(panel + " 标签栏(tab_bar)").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { Double nextHeight = inheritHeight.isChecked() ? null : Double.valueOf(height.getText().toString().trim()); mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateTabBar(source, panel, nullableSpinnerString(gravity), nextHeight), "已更新 " + panel + " 标签栏(tab_bar)"); } catch (Exception error) { workspace.setStatus("标签栏更新被阻止:" + error.getMessage()); } }).show();
    }

    private static final class SpinnerChoice {
        final String value;
        final String label;
        SpinnerChoice(String value, String label) { this.value = value; this.label = label; }
        @Override public String toString() { return label; }
    }

    private static String spinnerChoiceLabel(String value) {
        switch (value) {
            case "inherit": return "继承";
            case "false": return "否(false)";
            case "true": return "是(true)";
            case "left": return "左(left)";
            case "top": return "上(top)";
            case "right": return "右(right)";
            case "bottom": return "下(bottom)";
            case "left_up": return "左上(left_up)";
            case "right_up": return "右上(right_up)";
            case "bottom_left": return "左下(bottom_left)";
            case "bottom_right": return "右下(bottom_right)";
            case "top_left": return "左上(top_left)";
            case "top_right": return "右上(top_right)";
            case "drag": return "拖动(drag)";
            case "fixed": return "固定(fixed)";
            case "once": return "一次(once)";
            case "none": return "无(none)";
            case "input": return "输入(input)";
            case "preedit": return "预编辑(preedit)";
            case "composition": return "编码窗口(composition)";
            case "preview": return "预览(preview)";
            case "true (string)": return "是(字符串 true)";
            case "true (boolean source; current runtime none)": return "是(布尔源值;当前运行时为 none)";
            case "false (boolean source)": return "否(布尔源值)";
            default: return value;
        }
    }

    private static java.util.ArrayList<SpinnerChoice> spinnerChoices(java.util.List<String> values) {
        java.util.ArrayList<SpinnerChoice> choices = new java.util.ArrayList<>();
        for (String value : values) choices.add(new SpinnerChoice(value, spinnerChoiceLabel(value)));
        return choices;
    }

    private static String spinnerInternalValue(android.widget.Spinner spinner) {
        Object selected = spinner.getSelectedItem();
        return selected instanceof SpinnerChoice ? ((SpinnerChoice) selected).value : selected == null ? null : selected.toString();
    }

    private android.widget.Spinner nullableSpinner(LinearLayout parent, String label, Boolean selected) { TextView text = new TextView(this); text.setText(label); parent.addView(text); android.widget.Spinner spinner = new android.widget.Spinner(this); java.util.List<String> values = java.util.Arrays.asList("inherit", "false", "true"); spinner.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerChoices(values))); spinner.setSelection(selected == null ? 0 : selected ? 2 : 1); parent.addView(spinner); return spinner; }
    private android.widget.Spinner nullableStringSpinner(LinearLayout parent, String label, String[] values, String selected) { TextView text = new TextView(this); text.setText(label); parent.addView(text); java.util.ArrayList<String> choices = new java.util.ArrayList<>(); choices.add("inherit"); java.util.Collections.addAll(choices, values); android.widget.Spinner spinner = new android.widget.Spinner(this); spinner.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerChoices(choices))); int index = 0; if (selected != null) for (int i = 0; i < values.length; i++) if (selected.equals(values[i])) index = i + 1; spinner.setSelection(index); parent.addView(spinner); return spinner; }
    private static Boolean nullableSpinnerBoolean(android.widget.Spinner spinner) { return spinner.getSelectedItemPosition() == 0 ? null : spinner.getSelectedItemPosition() == 2; }
    private static String nullableSpinnerString(android.widget.Spinner spinner) { return spinner.getSelectedItemPosition() == 0 ? null : spinnerInternalValue(spinner); }
    private interface PanelSourceMutation { String apply(String source) throws Exception; }
    private void mutatePanelSource(ThemeProjectFile styleFile, String openedSource, PanelSourceMutation mutation, String success) { try { if (!ensureAssetWritable()) return; String latest = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("打开面板管理器后样式源代码已更改,请重新打开"); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), mutation.apply(latest)); originals.put(styleFile.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success); } catch (Exception error) { workspace.setStatus("面板组件更新被阻止:" + error.getMessage()); } }

    private void showToolbarKeyManager(ThemeProjectFile styleFile) {
        if (!ensureAssetWritable()) return;
        try {
            String source = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            java.util.List<ThemeToolbarKeys.Item> items = ThemeToolbarKeys.list(source);
            String[] labels = new String[items.size() + 1]; labels[0] = "+ 新建工具栏按键";
            for (int i = 0; i < items.size(); i++) labels[i + 1] = (i + 1) + ". " + toolbarItemSummary(items.get(i));
            new android.app.AlertDialog.Builder(this).setTitle(styleFile.getName() + " 工具栏按键(toolbar.keys)——仅静态").setMessage("仅检查项目。命令、选项、脚本、回调和方案切换绝不会在预览中执行。").setItems(labels, (dialog, which) -> {
                if (which == 0) chooseToolbarItemType(styleFile, source, -1, null, true);
                else showToolbarItemActions(styleFile, source, which - 1, items.get(which - 1), items.size());
            }).setNegativeButton("关闭", null).setNeutralButton("打开样式源代码", (dialog, which) -> requestProjectFileSwitch(styleFile)).show();
        } catch (Exception error) { workspace.setStatus("工具栏按键管理被阻止:" + error.getMessage()); }
    }

    private static String toolbarItemSummary(ThemeToolbarKeys.Item item) {
        if (item.getSource() == ThemeToolbarKeys.Source.STRING) return "string/preset — " + item.getLiteral();
        if (item.getSource() == ThemeToolbarKeys.Source.INLINE_EVENT) return "direct event — " + presetSummary(item.getEvent()) + (item.getRisky() ? " [execution retained, never run]" : "");
        if (item.getSource() == ThemeToolbarKeys.Source.SCHEMA_SWITCH) { ThemeToolbarKeys.SchemaSwitch value = item.getSchemaSwitch(); return "scheme switch — " + value.getName() + " (" + value.getOptions().size() + " options)" + (item.getCompatibilityWarning() ? " [style ignored by current runtime]" : ""); }
        if (item.getSource() == ThemeToolbarKeys.Source.FULL_KEY) return "complete key table — explicit source replacement required";
        return "Raw Lua — source only";
    }

    private void showToolbarItemActions(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item item, int size) {
        String[] actions = {"编辑", "上移", "下移", "删除", "查看兼容性摘要"};
        new android.app.AlertDialog.Builder(this).setTitle(toolbarItemSummary(item)).setItems(actions, (dialog, which) -> {
            if (which == 0) {
                if (item.getSource() == ThemeToolbarKeys.Source.FULL_KEY || item.getSource() == ThemeToolbarKeys.Source.RAW_LUA) { workspace.setStatus("完整按键或原始 Lua 工具栏项目请在样式源代码中编辑"); requestProjectFileSwitch(styleFile); }
                else chooseToolbarItemType(styleFile, openedSource, index, item, false);
            } else if (which == 1 || which == 2) {
                int target = which == 1 ? index - 1 : index + 1;
                if (target < 0 || target >= size) { workspace.setStatus("工具栏项目已位于该边缘"); return; }
                mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.move(source, index, target), "已移动工具栏按键");
            } else if (which == 3) {
                new android.app.AlertDialog.Builder(this).setTitle("删除工具栏按键?").setMessage(toolbarItemSummary(item)).setNegativeButton("取消", null).setPositiveButton("删除", (confirm, selected) -> mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.delete(source, index), "已删除工具栏按键")).show();
            } else workspace.setStatus(toolbarItemSummary(item) + "; toolbar scheme-switch style is read but ignored by current ToolbarView construction");
        }).setNegativeButton("关闭", null).show();
    }

    private void chooseToolbarItemType(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        String[] types = {"字符串 / 预设按键引用", "直接静态事件表", "方案切换表"};
        int selected = current == null ? 0 : current.getSource() == ThemeToolbarKeys.Source.INLINE_EVENT ? 1 : current.getSource() == ThemeToolbarKeys.Source.SCHEMA_SWITCH ? 2 : 0;
        new android.app.AlertDialog.Builder(this).setTitle(append ? "新建工具栏按键" : "替换工具栏项目类型").setSingleChoiceItems(types, selected, (dialog, which) -> { dialog.dismiss(); if (which == 0) editToolbarString(styleFile, openedSource, index, current, append); else if (which == 1) editToolbarEvent(styleFile, openedSource, index, current, append); else editToolbarSchemaSwitch(styleFile, openedSource, index, current, append); }).setNegativeButton("取消", null).show();
    }

    private void editToolbarString(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        LinearLayout fields = new LinearLayout(this); EditText value = simpleField(fields, "预设按键标识或字面事件", current != null && current.getLiteral() != null ? current.getLiteral() : "");
        new android.app.AlertDialog.Builder(this).setTitle("工具栏字符串项目").setMessage("该值作为静态引用保留,不会发送或执行。").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.put(source, index, ThemeToolbarKeys.string(value.getText().toString()), append), "已更新工具栏字符串项目;未执行任何操作")).show();
    }

    private void editToolbarEvent(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        ThemePresetEvents.Event event = current != null && current.getEvent() != null ? current.getEvent() : new ThemePresetEvents.Event("ToolbarKey", "", "", "", "", "", "", "", "", "", "", java.util.Collections.emptyList(), "", false, false, true, null);
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText label = simpleField(fields, "标签(label)", event.getLabel()); EditText send = simpleField(fields, "发送按键(send)", event.getSend()); EditText text = simpleField(fields, "文本(text)", event.getText()); EditText commit = simpleField(fields, "上屏文本(commit)", event.getCommit()); EditText command = simpleField(fields, "命令(command,保留但绝不执行)", event.getCommand()); EditText option = simpleField(fields, "选项(option)", event.getOption()); EditText select = simpleField(fields, "选择(select)", event.getSelect()); EditText toggle = simpleField(fields, "切换(toggle)", event.getToggle()); EditText preview = simpleField(fields, "预览(preview)", event.getPreview()); EditText description = simpleField(fields, "说明(description)", event.getDescription()); EditText states = simpleField(fields, "状态(states):每行一个;\\0 表示空值,\\n 表示内嵌换行", formatEventStates(event.getStates())); states.setSingleLine(false); states.setMinLines(3); EditText shiftLock = simpleField(fields, "Shift 锁定(shift_lock):click/double/long", event.getShiftLock()); EditText eventIndex = simpleField(fields, "索引(index,32 位整数;无可靠效果)", event.getIndex() == null ? "" : event.getIndex().toString()); android.widget.CheckBox repeatable = new android.widget.CheckBox(this); repeatable.setText("可重复(repeatable)"); repeatable.setChecked(event.getRepeatable()); fields.addView(repeatable); android.widget.CheckBox sticky = new android.widget.CheckBox(this); sticky.setText("保持(sticky)"); sticky.setChecked(event.getSticky()); fields.addView(sticky); android.widget.CheckBox functional = new android.widget.CheckBox(this); functional.setText("功能键(functional)"); functional.setChecked(event.getFunctional()); fields.addView(functional); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("工具栏直接事件").setMessage("仅编辑静态字段;应用时绝不会执行事件。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { String indexText = eventIndex.getText().toString().trim(); Double nextIndex = indexText.isEmpty() ? null : Double.valueOf(indexText); ThemePresetEvents.Event next = new ThemePresetEvents.Event("ToolbarKey", send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), parseEventStates(states.getText().toString()), shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.put(source, index, ThemeToolbarKeys.inlineEvent(next), append), "已更新工具栏直接事件;未执行任何操作"); } catch (Exception error) { workspace.setStatus("工具栏事件更新被阻止:" + error.getMessage()); } }).show();
    }

    private void editToolbarSchemaSwitch(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        ThemeToolbarKeys.SchemaSwitch value = current != null && current.getSchemaSwitch() != null ? current.getSchemaSwitch() : new ThemeToolbarKeys.SchemaSwitch("ASCII 模式(ascii_mode)", java.util.Arrays.asList("ASCII 模式(ascii_mode)"), java.util.Arrays.asList("中", "A"), 0, null);
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText name = simpleField(fields, "名称(name)", value.getName()); EditText options = simpleField(fields, "选项(options):每行一个", formatEventStates(value.getOptions())); options.setSingleLine(false); options.setMinLines(2); EditText states = simpleField(fields, "状态(states):每行一个", formatEventStates(value.getStates())); states.setSingleLine(false); states.setMinLines(2); EditText reset = simpleField(fields, "重置值(reset,32 位整数)", Integer.toString(value.getReset())); EditText style = simpleField(fields, "样式(style,仅兼容;ToolbarView 使用 toolbar.key)", value.getStyle() == null ? "" : value.getStyle()); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("工具栏方案切换").setMessage("仅静态预览。应用时不会切换选项、方案、主题、样式或键盘,不会重启 Trime,也不会调用回调。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { int nextReset = Integer.parseInt(reset.getText().toString().trim()); ThemeToolbarKeys.Item next = ThemeToolbarKeys.schemaSwitch(name.getText().toString().trim(), parseEventStates(options.getText().toString()), parseEventStates(states.getText().toString()), nextReset, style.getText().toString().trim().isEmpty() ? null : style.getText().toString().trim()); mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.put(source, index, next, append), "已更新工具栏方案切换;未执行任何操作"); } catch (Exception error) { workspace.setStatus("方案切换更新被阻止:" + error.getMessage()); } }).show();
    }

    private interface ToolbarSourceMutation { String apply(String source) throws Exception; }
    private void mutateToolbarSource(ThemeProjectFile styleFile, String openedSource, ToolbarSourceMutation mutation, String success) {
        try {
            if (!ensureAssetWritable()) return; String latest = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("Style source changed after opening toolbar manager; reopen it");
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), mutation.apply(latest)); originals.put(styleFile.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success);
        } catch (Exception error) { workspace.setStatus("工具栏更新被阻止:" + error.getMessage()); }
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
            String[] labels = new String[entries.size() + 2]; labels[0] = "+ 新建实体"; labels[1] = "从私有剪贴板粘贴实体";
            for (int i = 0; i < entries.size(); i++) { ThemeStyleEntities.Entry entry = entries.get(i); labels[i + 2] = entry.getId() + (entry.getCloneParent() == null ? "" : " ← " + entry.getCloneParent()) + (entry.getDynamic() ? " [code-only]" : ""); }
            new android.app.AlertDialog.Builder(this).setTitle("样式实体——" + styleFile.getName()).setItems(labels, (dialog, which) -> { if (which == 0) promptCreateStyleEntity(styleFile); else if (which == 1) promptPasteEntityIntoStyle(styleFile); else showStyleEntityActions(styleFile, entries.get(which - 2)); }).setNegativeButton("关闭", null).show();
        } catch (Exception error) { workspace.setStatus("样式实体列表加载失败:" + error.getMessage()); }
    }

    private void showStyleEntityActions(ThemeProjectFile styleFile, ThemeStyleEntities.Entry entry) {
        try {
            EntityUsage usage = collectEntityUsage(styleFile, entry.getId()); String details = "Entity: " + entry.getId() + (entry.getCloneParent() == null ? "" : "\nInherits: " + entry.getCloneParent()) + "\nStatic key references: " + usage.total + (usage.uncertain.isEmpty() ? "" : "\nUncertain keyboards: " + android.text.TextUtils.join(", ", usage.uncertain)) + (entry.getDynamic() ? "\nDynamic entity: structural actions are disabled; use Lua source." : "");
            String[] actions = entry.getDynamic() ? new String[]{"详情", "打开样式 Lua"} : new String[]{"详情", "复制完整实体", "创建副本", "重命名并替换引用", "无引用时删除", "打开样式 Lua"};
            new android.app.AlertDialog.Builder(this).setTitle(entry.getId()).setMessage(details).setItems(actions, (dialog, which) -> {
                if (which == 0) return;
                if (entry.getDynamic()) { requestProjectFileSwitch(styleFile); return; }
                if (which == 1) copyEntityFromStyleAsset(styleFile, entry.getId()); else if (which == 2) promptDuplicateStyleEntity(styleFile, entry.getId()); else if (which == 3) promptRenameStyleEntity(styleFile, entry.getId(), usage); else if (which == 4) confirmDeleteStyleEntity(styleFile, entry.getId(), usage); else requestProjectFileSwitch(styleFile);
            }).setNegativeButton("关闭", null).show();
        } catch (Exception error) { workspace.setStatus("样式实体分析被阻止:" + error.getMessage()); }
    }

    private void copyEntityFromStyleAsset(ThemeProjectFile styleFile, String id) {
        try { ThemeStyleEntities.Snapshot snapshot = ThemeStyleEntities.extract(new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8), id); workspace.storeStyleEntityClipboard(snapshot); }
        catch (Exception error) { workspace.setStatus("样式实体复制被阻止:" + error.getMessage()); }
    }

    private void promptCreateStyleEntity(ThemeProjectFile styleFile) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText id = simpleField(fields, "新实体标识", "style_new"); EditText parent = simpleField(fields, "克隆父项(clone,留空则为表)", "key");
        new android.app.AlertDialog.Builder(this).setTitle("新建样式实体").setView(fields).setNegativeButton("取消", null).setPositiveButton("创建", (dialog, which) -> mutateSingleStyleEntity(styleFile, source -> ThemeStyleEntities.create(source, id.getText().toString().trim(), emptyToNull(parent)), "已创建样式实体 " + id.getText().toString().trim())).show();
    }

    private void promptDuplicateStyleEntity(ThemeProjectFile styleFile, String sourceId) {
        LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "副本实体标识", sourceId + "_copy");
        new android.app.AlertDialog.Builder(this).setTitle("复制完整实体").setView(fields).setNegativeButton("取消", null).setPositiveButton("创建副本", (dialog, which) -> mutateSingleStyleEntity(styleFile, source -> ThemeStyleEntities.paste(source, ThemeStyleEntities.extract(source, sourceId), id.getText().toString().trim()), "已复制样式实体 " + sourceId)).show();
    }

    private void promptPasteEntityIntoStyle(ThemeProjectFile styleFile) {
        ThemeEditorClipboard.Payload payload = workspace.styleEntityClipboard(); if (payload == null || payload.styleEntity == null) { workspace.setStatus("私有剪贴板中没有完整的样式实体"); return; }
        try { java.util.ArrayList<String> missing = missingStyleEntityResources(styleFile, payload.styleEntity); if (!missing.isEmpty()) throw new IOException("目标样式缺少资源:" + android.text.TextUtils.join(", ", missing)); }
        catch (Exception error) { workspace.setStatus("实体粘贴被阻止:" + error.getMessage()); return; }
        LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "新实体标识", payload.styleEntity.getId() + "_copy");
        new android.app.AlertDialog.Builder(this).setTitle("粘贴完整实体").setMessage(payload.styleEntity.getCloneParent() == null ? "无克隆依赖" : "需要克隆父项:" + payload.styleEntity.getCloneParent()).setView(fields).setNegativeButton("取消", null).setPositiveButton("粘贴", (dialog, which) -> mutateSingleStyleEntity(styleFile, source -> ThemeStyleEntities.paste(source, payload.styleEntity, id.getText().toString().trim()), "已粘贴完整样式实体")).show();
    }

    private interface StyleSourceMutation { String apply(String source) throws Exception; }
    private void mutateSingleStyleEntity(ThemeProjectFile styleFile, StyleSourceMutation mutation, String success) {
        if (!ensureAssetWritable()) return;
        try {
            String original = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); String updated = mutation.apply(original);
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), updated); originals.put(styleFile.getFile(), original); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success + ";事务已校验");
        } catch (Exception error) { workspace.setStatus("样式实体修改失败:" + error.getMessage()); }
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
        if (ThemeStyleEntities.isReserved(oldId)) { workspace.setStatus("保留组件样式不能重命名:" + oldId); return; }
        if (!previewUsage.uncertain.isEmpty()) { workspace.setStatus("重命名被无法确定的键盘引用阻止:" + android.text.TextUtils.join(", ", previewUsage.uncertain)); return; }
        LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "新实体标识", oldId + "_renamed"); String message = "将替换 " + previewUsage.total + " 个静态按键引用,涉及 " + previewUsage.references.size() + " 个键盘。样式和键盘文件将作为一个可安全回滚的事务提交。";
        new android.app.AlertDialog.Builder(this).setTitle("重命名样式实体?").setMessage(message).setView(fields).setNegativeButton("取消", null).setPositiveButton("重命名", (dialog, which) -> renameStyleEntityTransaction(styleFile, oldId, id.getText().toString().trim())).show();
    }

    private void renameStyleEntityTransaction(ThemeProjectFile styleFile, String oldId, String newId) {
        if (!ensureAssetWritable()) return;
        try {
            EntityUsage usage = collectEntityUsage(styleFile, oldId); if (!usage.uncertain.isEmpty()) throw new IOException("References became uncertain: " + android.text.TextUtils.join(", ", usage.uncertain));
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); String styleSource = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); originals.put(styleFile.getFile(), styleSource); changes.put(styleFile.getFile(), ThemeStyleEntities.rename(styleSource, oldId, newId));
            int changed = 0; for (ThemeProjectFile keyboard : usage.references.keySet()) { String source = new String(readFileBytes(keyboard.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); originals.put(keyboard.getFile(), source); ThemeStyleEntities.ReferenceUpdate update = ThemeStyleEntities.replaceKeyboardReferences(source, oldId, newId); if (update.getChangedKeys() > 0) { changes.put(keyboard.getFile(), update.getSource()); changed += update.getChangedKeys(); } }
            applyProjectSourceTransaction(changes, originals); workspace.setStatus("已将 " + oldId + " 重命名为 " + newId + " 并替换 " + changed + " 个按键引用");
        } catch (Exception error) { workspace.setStatus("样式实体重命名被阻止:" + error.getMessage()); }
    }

    private void confirmDeleteStyleEntity(ThemeProjectFile styleFile, String id, EntityUsage previewUsage) {
        if (ThemeStyleEntities.isReserved(id)) { workspace.setStatus("保留组件样式不能删除:" + id); return; }
        if (previewUsage.total > 0 || !previewUsage.uncertain.isEmpty()) { workspace.setStatus("删除被阻止:" + previewUsage.total + " 个引用;无法确定的键盘:" + android.text.TextUtils.join(", ", previewUsage.uncertain)); return; }
        new android.app.AlertDialog.Builder(this).setTitle("删除无引用的样式实体?").setMessage(id + "\nThis removes only its static style statements. Clone consumers are also checked at commit time.").setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> deleteStyleEntityTransaction(styleFile, id)).show();
    }

    private void deleteStyleEntityTransaction(ThemeProjectFile styleFile, String id) {
        if (!ensureAssetWritable()) return;
        try {
            EntityUsage usage = collectEntityUsage(styleFile, id); if (usage.total > 0 || !usage.uncertain.isEmpty()) throw new IOException("References changed after review; reopen entity manager");
            String source = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), ThemeStyleEntities.delete(source, id)); originals.put(styleFile.getFile(), source); applyProjectSourceTransaction(changes, originals); workspace.setStatus("已删除无引用的样式实体 " + id);
        } catch (Exception error) { workspace.setStatus("样式实体删除被阻止:" + error.getMessage()); }
    }

    private void applyProjectSourceTransaction(java.util.LinkedHashMap<File, String> changes, java.util.Map<File, String> expectedOriginals) throws Exception { applyProjectSourceTransaction(changes, expectedOriginals, null); }
    private void applyProjectSourceTransaction(java.util.LinkedHashMap<File, String> changes, java.util.Map<File, String> expectedOriginals, java.util.Collection<File> expectedLuaManifest) throws Exception {
        if (changes.isEmpty()) return;
        final class Backup { final byte[] bytes; final String localHash; final String remoteHash; Backup(byte[] bytes, String localHash, String remoteHash) { this.bytes = bytes; this.localHash = localHash; this.remoteHash = remoteHash; } }
        java.util.LinkedHashMap<File, Backup> backups = new java.util.LinkedHashMap<>(); String root = project.getRoot().getCanonicalPath();
        validateProjectTransactionSnapshot(root, expectedOriginals, expectedLuaManifest);
        for (java.util.Map.Entry<File, String> change : changes.entrySet()) {
            File file = change.getKey(); if (!file.getCanonicalPath().startsWith(root + File.separator)) throw new IOException("Transaction file escapes project root");
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(change.getValue()); for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("已更新 " + file.getName() + " contains Lua errors");
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
    private void promptStyleId(String title, String initial, StyleIdAction action) { if (!ensureAssetWritable()) return; LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "样式标识", initial); new android.app.AlertDialog.Builder(this).setTitle(title).setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { action.run(id.getText().toString().trim()); workspace.setStatus(title + " 已完成"); } catch (Exception error) { workspace.setStatus(title + " 失败:" + error.getMessage()); } }).show(); }
    private void confirmDeleteStyle(ThemeProjectFile file) { if (!ensureAssetWritable()) return; if (isCurrentProjectFile(file)) { workspace.setStatus("删除当前样式前请先打开另一个文件"); return; } new android.app.AlertDialog.Builder(this).setTitle("删除样式?").setMessage(file.getName()).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> { try { ThemeProjectMutator.validateStyleDeletion(project, file); File directory = file.getFile().getParentFile(); if (importedProjectTreeUri != null) deleteImportedProjectPath(directory); deleteDirectory(directory); if (directory.exists()) throw new IOException("无法删除本地样式缓存"); refreshProjectAfterAssetMutation(); workspace.setStatus("已删除样式 " + file.getName()); } catch (Exception error) { workspace.setStatus("样式删除被阻止:" + error.getMessage()); } }).show(); }

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
        String[] actions = model.layoutMode == ThemeEditorModel.LayoutMode.NONE ? new String[]{"关闭"} : new String[]{"迁移布局...", "关闭"};
        new android.app.AlertDialog.Builder(this).setTitle("键盘结构").setMessage(text.toString()).setItems(actions, (dialog, which) -> { if (model.layoutMode != ThemeEditorModel.LayoutMode.NONE && which == 0) chooseLayoutMigrationTarget(model); }).show();
    }

    private void chooseLayoutMigrationTarget(ThemeEditorModel source) {
        java.util.ArrayList<ThemeEditorModel.LayoutMode> modes = new java.util.ArrayList<>(); java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (ThemeEditorModel.LayoutMode mode : new ThemeEditorModel.LayoutMode[]{ThemeEditorModel.LayoutMode.ROWS, ThemeEditorModel.LayoutMode.FLEX_BOX, ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS, ThemeEditorModel.LayoutMode.KEY_MAPS}) if (mode != source.layoutMode) { modes.add(mode); labels.add(mode.name()); }
        new android.app.AlertDialog.Builder(this).setTitle("迁移 " + source.layoutMode + " 到").setItems(labels.toArray(new String[0]), (dialog, which) -> showLayoutMigrationPreview(source, modes.get(which))).setNegativeButton("取消", null).show();
    }

    private void showLayoutMigrationPreview(ThemeEditorModel source, ThemeEditorModel.LayoutMode target) {
        try {
            ThemeLayoutMigration.Preview preview = ThemeLayoutMigration.preview(source, target); StringBuilder message = new StringBuilder();
            message.append("Convert ").append(preview.getKeyCount()).append(" keys; containers ").append(preview.getSourceContainerCount()).append(" → ").append(preview.getTargetContainerCount()).append(".\n");
            if (preview.getOmittedKeyMapPages() > 0) message.append("Non-active pages omitted: ").append(preview.getOmittedKeyMapPages()).append(".\n");
            for (String note : preview.getNotes()) message.append("\n• ").append(note);
            String[] actions = {"复制备份并转换", "转换", "隐藏原数据并转换", "取消"};
            new android.app.AlertDialog.Builder(this).setTitle("迁移预览:" + source.layoutMode + " → " + target).setMessage(message.toString()).setItems(actions, (dialog, which) -> { if (which < 3) applyLayoutMigration(source, target, which == 0, which == 2); }).show();
        } catch (Exception error) { workspace.setStatus("迁移预览失败:" + error.getMessage()); }
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
            try { if (!workspace.replaceModelAsAtomic(toUiModel(verified.getDocument()), "已将 " + source.layoutMode + " 重命名为 " + target + ",作为一个撤销步骤")) throw new IOException("工作区拒绝迁移"); }
            finally { applyingMigration = false; }
            layoutEditable = true; viewModel.setDirty(true); if (migrationBackup != null) workspace.setStatus("已将 " + source.layoutMode + " 重命名为 " + target + ";备份:keyboards/.editor-backups/" + migrationBackup.getName());
        } catch (Exception error) { applyingMigration = false; if (before != null) editor.replaceDocument(before); clearMigrationHistory(); workspace.setStatus("布局迁移失败,已保留原草稿:" + error.getMessage()); }
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
        String[] actions = {"导出已校验的 ZIP", "分享已校验的 ZIP", "安装到已授权目录", "回滚上次安装"};
        new android.app.AlertDialog.Builder(this).setTitle("导出与安装").setItems(actions, (dialog, which) -> { if (which == 0) exportZip(false); else if (which == 1) exportZip(true); else if (which == 2) chooseInstallTarget(); else rollbackLastInstall(true); }).setNegativeButton("关闭", null).show();
    }

    private void showRecoveryStatus() {
        String text = recoveryDraftFile().isFile() ? "存在私有草稿:" + recoveryIdentity() : "没有私有恢复草稿";
        File journal = new File(getFilesDir(), "theme-editor-install.journal"); if (journal.isFile()) text += "\nInstallation journal is available.";
        new android.app.AlertDialog.Builder(this).setTitle("恢复状态").setMessage(text).setNegativeButton("关闭", null).setPositiveButton("删除私有草稿", (dialog, which) -> { deleteRecoveryDraft(); workspace.setStatus("私有恢复草稿已删除"); }).show();
    }

    private boolean ensureWritable() {
        if (!readOnlySession) return true;
        workspace.setStatus("只读:此项目已在另一个编辑器会话中打开"); Toast.makeText(this, "第二个会话为只读", Toast.LENGTH_LONG).show(); return false;
    }

    private boolean ensureAssetWritable() {
        if (!ensureWritable()) return false;
        if (viewModel.getDirty()) { workspace.setStatus("修改项目资源前,请先保存或放弃当前文件的更改"); Toast.makeText(this, "请先保存当前更改", Toast.LENGTH_LONG).show(); return false; }
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
        if (readOnlySession) workspace.setStatus("已以只读方式打开:另一个编辑器会话正在使用此项目");
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
                .setTitle("有未保存的更改")
                .setMessage("切换前是否保存当前 Lua 文件?")
                .setPositiveButton("保存", (dialog, which) -> {
                    saveModel(workspace.getModel());
                    if (!viewModel.getDirty()) loadProjectFile(file);
                })
                .setNegativeButton("放弃", (dialog, which) -> {
                    viewModel.setDirty(false);
                    deleteRecoveryDraft();
                    loadProjectFile(file);
                })
                .setNeutralButton("取消", null)
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
            workspace.setStatus("项目 " + root.getName() + ": " + project.getStyles().size() + " 个样式," + project.getKeyboards().size() + " 个键盘," + diagnosticCount + " 条诊断");
            invalidateOptionsMenu();
            offerRecoveryDraft();
        } catch (Exception error) {
            project = null;
            workspace.setStatus("项目加载失败:" + error.getMessage());
            Toast.makeText(this, "无法加载主题项目", Toast.LENGTH_LONG).show();
        }
    }

    private void loadTree(Uri uri) {
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(this, uri);
            if (tree == null) throw new IOException("无法打开主题文件夹");
            File root = new File(getCacheDir(), "theme-editor-tree-" + System.nanoTime());
            copyDocumentTree(tree, root);
            importedProjectUri = uri; importedProjectTreeUri = uri; importedProjectTreePrefix = null;
            rememberRecentProject(uri, tree.getName(), null);
            loadProject(root, tree.getName());
            workspace.setStatus("已导入主题文件夹:" + root.getName());
        } catch (Exception error) {
            workspace.setStatus("文件夹导入失败:" + error.getMessage());
            Toast.makeText(this, "无法导入主题文件夹", Toast.LENGTH_LONG).show();
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
                if (input == null) throw new IOException("无法打开 ZIP");
                com.osfans.trime.editor.project.ThemeProjectArchive.extractZip(input, root);
            }
            File main = findMainLua(root);
            if (main == null) throw new IOException("ZIP 中不包含 main.lua");
            importedProjectUri = uri; importedProjectTreeUri = null; importedProjectTreePrefix = null; openedImportedFingerprint = null;
            String archiveName = documentName(uri); if (archiveName.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) archiveName = archiveName.substring(0, archiveName.length() - 4);
            String displayName = main.getParentFile().equals(root) ? archiveName : main.getParentFile().getName();
            loadProject(main.getParentFile(), displayName);
            workspace.setStatus("已导入 ZIP:" + main.getParentFile());
        } catch (Exception error) {
            workspace.setStatus("ZIP 导入失败:" + error.getMessage());
            Toast.makeText(this, "无法导入 ZIP", Toast.LENGTH_LONG).show();
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
            workspace.setStatus("已加载 " + currentUri + " (" + parsed.getDiagnostics().size() + " 条诊断)" + (layoutEditable ? "" : ";此文件中没有结构化键盘布局"));
            offerRecoveryDraft();
        } catch (Exception error) {
            workspace.setStatus("加载失败:" + error.getMessage());
            Toast.makeText(this, "无法加载主题", Toast.LENGTH_LONG).show();
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
        if (root == null || editor.getDocument().get(root) instanceof ThemeValue.RawLuaNode) { workspace.setStatus("布局使用动态 Lua,请在 Lua 源代码中编辑"); return false; }
        int rootAssignments = 0; for (com.osfans.trime.editor.core.ThemeSourceStatement statement : editor.getDocument().getSourceStatements()) if (root.equals(statement.getPath())) rootAssignments++;
        if (rootAssignments > 1) { workspace.setStatus("重复的布局赋值需要使用 Lua 源代码编辑器"); return false; }
        try {
            com.osfans.trime.editor.core.ThemeDocument updated = ThemeLayoutCodec.writeAgainstOriginal(editor.getDocument(), model);
            com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(updated, com.osfans.trime.editor.core.ThemeWriteMode.HYBRID);
            editor.replaceDocument(updated); return true;
        } catch (Exception error) { workspace.setStatus("结构化更新被阻止:" + error.getMessage()); return false; }
    }

    private void showKeyEventManager(ThemeEditorModel.Key key) {
        if (!ensureAssetWritable()) return;
        try {
            if (key == null || key.sourcePath == null || key.sourcePath.isEmpty()) throw new IOException("This key has no stable source path; save/reload or use Lua source");
            if (!(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD || editor == null) throw new IOException("Open a project keyboard first");
            java.util.List<ThemeKeyEvents.Slot> slots = ThemeKeyEvents.read(editor.getDocument(), key.sourcePath); ThemeKeyEvents.Options options = ThemeKeyEvents.options(editor.getDocument(), key.sourcePath); ThemeKeyEvents.Hints hints = ThemeKeyEvents.hints(editor.getDocument(), key.sourcePath);
            String[] labels = new String[slots.size() + 4]; for (int i = 0; i < slots.size(); i++) { ThemeKeyEvents.Slot slot = slots.get(i); labels[i] = slot.getName() + " — " + slot.getSource() + eventSlotSummary(slot); } labels[slots.size()] = "swipe_repeatable — " + nullableBoolean(options.getSwipeRepeatable()); labels[slots.size() + 1] = "send_bindings — " + nullableBoolean(options.getSendBindings()) + "; effective=" + options.getEffectiveSendBindings() + " (" + options.getSendBindingsSource() + ")"; labels[slots.size() + 2] = "event hints — missing values fall back to event labels"; labels[slots.size() + 3] = "long/repeat click time — inherited from key style entity";
            new android.app.AlertDialog.Builder(this).setTitle("按键事件——仅静态").setMessage("不会执行任何事件、命令、脚本、Intent、上屏或回调。").setItems(labels, (dialog, which) -> { if (which < slots.size()) editKeyEventSlot(key, slots.get(which)); else if (which < slots.size() + 2) editKeyEventOptions(key, options); else if (which == slots.size() + 2) editKeyEventHints(key, hints); else workspace.setStatus("长按时间(long_click_time)和重复点击时间(repeat_click_time)属于解析后的按键样式;请编辑该样式实体,而不是此按键源"); }).setNegativeButton("关闭", null).setNeutralButton("查看 Lua", (dialog, which) -> showCodeEditor()).show();
        } catch (Exception error) { workspace.setStatus("按键事件管理被阻止:" + error.getMessage()); }
    }

    private static String nullableBoolean(Boolean value) { return value == null ? "inherit" : value ? "true" : "false"; }
    private static String eventSlotSummary(ThemeKeyEvents.Slot slot) { if (slot.getLiteral() != null) return " = " + slot.getLiteral(); if (slot.getEvent() != null) return " = " + presetSummary(slot.getEvent()); return slot.getRisky() ? " [code-only]" : ""; }

    private void editKeyEventSlot(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        if (slot.getSource() == ThemeKeyEvents.Source.RAW_LUA || slot.getSource() == ThemeKeyEvents.Source.FULL_KEY_REPLACEMENT) { workspace.setStatus(slot.getName() + " is " + slot.getSource() + ";请使用 Lua 源代码"); showCodeEditor(); return; }
        boolean stringOnly = java.util.Arrays.asList(ThemeKeyEvents.STRING_ONLY_SLOTS).contains(slot.getName());
        String[] modes = stringOnly ? new String[]{"String/preset reference", "Clear"} : new String[]{"String/preset reference", "Inline event table", "Clear"}; int selected = slot.getSource() == ThemeKeyEvents.Source.INLINE_EVENT ? 1 : slot.getSource() == ThemeKeyEvents.Source.MISSING ? modes.length - 1 : 0;
        new android.app.AlertDialog.Builder(this).setTitle("编辑 " + slot.getName()).setMessage(stringOnly ? "当前 Trime 运行时仅以字符串形式使用此状态替换值。" : "仅选择静态源;不会执行任何操作。").setSingleChoiceItems(modes, selected, (dialog, which) -> { dialog.dismiss(); if (which == 0) editKeyEventString(key, slot); else if (!stringOnly && which == 1) editInlineKeyEvent(key, slot); else commitKeyEventChange(key, document -> ThemeKeyEvents.updateString(document, key.sourcePath, slot.getName(), null), "已清除 " + slot.getName()); }).setNegativeButton("取消", null).show();
    }

    private void editKeyEventString(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        LinearLayout fields = new LinearLayout(this); EditText value = simpleField(fields, "字面事件或预设标识", slot.getLiteral() == null ? "" : slot.getLiteral());
        new android.app.AlertDialog.Builder(this).setTitle(slot.getName() + " 字符串源").setMessage(".lua 后缀或命令预设会保留,但绝不会在预览中执行。").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> commitKeyEventChange(key, document -> ThemeKeyEvents.updateString(document, key.sourcePath, slot.getName(), value.getText().toString()), "已更新 " + slot.getName() + " 字符串事件")).show();
    }

    private void editInlineKeyEvent(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        ThemePresetEvents.Event event = slot.getEvent() == null ? new ThemePresetEvents.Event(slot.getName(), "", "", "", "", "", "", "", "", "", "", java.util.Collections.emptyList(), "", false, false, true, null) : slot.getEvent();
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText label = simpleField(fields, "标签(label)", event.getLabel()); EditText send = simpleField(fields, "发送按键(send)", event.getSend()); EditText text = simpleField(fields, "文本(text)", event.getText()); EditText commit = simpleField(fields, "上屏文本(commit)", event.getCommit()); EditText command = simpleField(fields, "命令(command,绝不执行)", event.getCommand()); EditText option = simpleField(fields, "选项(option)", event.getOption()); EditText select = simpleField(fields, "选择(select)", event.getSelect()); EditText toggle = simpleField(fields, "切换(toggle)", event.getToggle()); EditText preview = simpleField(fields, "预览(preview)", event.getPreview()); EditText description = simpleField(fields, "说明(description)", event.getDescription()); EditText states = simpleField(fields, "状态(states):每行一个;\\0 表示空值,\\n 表示内嵌换行", formatEventStates(event.getStates())); states.setSingleLine(false); states.setMinLines(3); EditText shiftLock = simpleField(fields, "Shift 锁定(shift_lock)", event.getShiftLock()); EditText index = simpleField(fields, "索引(index,保留;效果不可靠)", event.getIndex() == null ? "" : event.getIndex().toString()); android.widget.CheckBox repeatable = new android.widget.CheckBox(this); repeatable.setText("可重复(repeatable)"); repeatable.setChecked(event.getRepeatable()); fields.addView(repeatable); android.widget.CheckBox sticky = new android.widget.CheckBox(this); sticky.setText("保持(sticky)"); sticky.setChecked(event.getSticky()); fields.addView(sticky); android.widget.CheckBox functional = new android.widget.CheckBox(this); functional.setText("功能键(functional)"); functional.setChecked(event.getFunctional()); fields.addView(functional); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle(slot.getName() + " 内联事件").setMessage("仅静态表;任何字段都不会执行。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { java.util.ArrayList<String> nextStates = parseEventStates(states.getText().toString()); Double nextIndex = index.getText().toString().trim().isEmpty() ? null : Double.valueOf(index.getText().toString().trim()); ThemePresetEvents.Event next = new ThemePresetEvents.Event(slot.getName(), send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), nextStates, shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); commitKeyEventChange(key, document -> ThemeKeyEvents.updateInline(document, key.sourcePath, slot.getName(), next), "已更新 " + slot.getName() + " 内联事件;未执行任何操作"); } catch (Exception error) { workspace.setStatus("内联事件被阻止:" + error.getMessage()); } }).show();
    }

    private void editKeyEventHints(ThemeEditorModel.Key key, ThemeKeyEvents.Hints hints) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); java.util.LinkedHashMap<String, EditText> inputs = new java.util.LinkedHashMap<>(); java.util.LinkedHashMap<String, android.widget.CheckBox> inherit = new java.util.LinkedHashMap<>();
        for (String name : ThemeKeyEvents.HINTS) { android.widget.CheckBox useFallback = new android.widget.CheckBox(this); useFallback.setText(name + " 缺失 → 回退到事件标签"); useFallback.setChecked(hints.getValues().get(name) == null); fields.addView(useFallback); EditText input = simpleField(fields, name + "(空值仍为显式空值)", hints.getValues().get(name) == null ? "" : hints.getValues().get(name)); input.setEnabled(!useFallback.isChecked()); useFallback.setOnCheckedChangeListener((button, checked) -> input.setEnabled(!checked)); inherit.put(name, useFallback); inputs.put(name, input); }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2)); new android.app.AlertDialog.Builder(this).setTitle("事件提示(event hints)").setMessage("缺失的提示会回退到对应事件标签;显式空值不会回退。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> commitKeyEventChange(key, document -> { java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(); for (String name : ThemeKeyEvents.HINTS) values.put(name, inherit.get(name).isChecked() ? null : inputs.get(name).getText().toString()); return ThemeKeyEvents.updateHints(document, key.sourcePath, values); }, "已更新事件提示,并保留源回退行为")).show();
    }

    private void editKeyEventOptions(ThemeEditorModel.Key key, ThemeKeyEvents.Options options) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); java.util.List<String> values = java.util.Arrays.asList("inherit", "false", "true"); android.widget.Spinner swipe = new android.widget.Spinner(this); swipe.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerChoices(values))); swipe.setSelection(options.getSwipeRepeatable() == null ? 0 : options.getSwipeRepeatable() ? 2 : 1); fields.addView(swipe); android.widget.Spinner bindings = new android.widget.Spinner(this); bindings.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerChoices(values))); bindings.setSelection(options.getSendBindings() == null ? 0 : options.getSendBindings() ? 2 : 1); fields.addView(bindings);
        new android.app.AlertDialog.Builder(this).setTitle("按键事件标志").setMessage("依次为滑动重复(swipe_repeatable)与发送绑定(send_bindings);继承会保留缺失字段和 Trime 运行时默认值。").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> commitKeyEventChange(key, document -> ThemeKeyEvents.updateOptions(document, key.sourcePath, spinnerBoolean(swipe), spinnerBoolean(bindings)), "已更新按键事件标志")).show();
    }

    private static Boolean spinnerBoolean(android.widget.Spinner value) { return value.getSelectedItemPosition() == 0 ? null : value.getSelectedItemPosition() == 2; }
    private interface KeyDocumentMutation { com.osfans.trime.editor.core.ThemeDocument apply(com.osfans.trime.editor.core.ThemeDocument document) throws Exception; }
    private void commitKeyEventChange(ThemeEditorModel.Key key, KeyDocumentMutation mutation, String success) {
        try {
            if (!ensureAssetWritable() || !(repository instanceof DirectoryThemeProjectRepository)) return; ThemeProjectFile file = ((DirectoryThemeProjectRepository) repository).getSelected(); if (file.getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("Open a keyboard file first"); String latest = new String(readFileBytes(file.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); String loaded = com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(editor.getDocument(), com.osfans.trime.editor.core.ThemeWriteMode.HYBRID); if (!ThemeSaveCoordinator.Companion.fingerprint(latest).equals(ThemeSaveCoordinator.Companion.fingerprint(loaded))) throw new IOException("Keyboard changed outside the loaded editor; reload before editing this key"); com.osfans.trime.editor.core.ThemeDocument document = ThemeKeyEvents.parseDocument(latest); if (document.get(key.sourcePath) == null) throw new IOException("Key source path changed; reload keyboard"); String updated = ThemeKeyEvents.verifiedSource(mutation.apply(document)); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(file.getFile(), updated); originals.put(file.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success);
        } catch (Exception error) { workspace.setStatus("按键事件更新被阻止:" + error.getMessage()); }
    }

    private void copyStyleEntity(ThemeEditorModel.Key key) {
        try {
            if (project == null || editor == null || !(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("Open a project keyboard first");
            ThemeProjectFile styleSource = resolvedStyleSource(editor.getDocument()); if (styleSource == null) throw new IOException("The keyboard style asset cannot be resolved statically");
            String source = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.ArrayList<String> entityIds = new java.util.ArrayList<>(); for (ThemeStyleEntities.Entry entry : ThemeStyleEntities.list(source)) entityIds.add(entry.getId());
            String styleId = ThemeKeyStyleBatch.effectiveStyleId(key, entityIds); ThemeStyleEntities.Snapshot snapshot = ThemeStyleEntities.extract(source, styleId);
            workspace.storeStyleEntityClipboard(snapshot);
        } catch (Exception error) { workspace.setStatus("样式实体复制被阻止:" + error.getMessage()); }
    }

    private void promptPasteStyleEntity(java.util.List<ThemeEditorModel.Key> keys) {
        if (!ensureAssetWritable()) return;
        try {
            if (keys == null || keys.isEmpty()) throw new IOException("Select one or more target keys first");
            ThemeEditorClipboard.Payload payload = workspace.styleEntityClipboard();
            if (payload == null || payload.styleEntity == null) throw new IOException("私有剪贴板中没有完整的样式实体");
            if (project == null || editor == null || !(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("Open a target project keyboard first");
            ThemeProjectFile styleSource = resolvedStyleSource(editor.getDocument()); if (styleSource == null) throw new IOException("The target keyboard style asset cannot be resolved statically");
            boolean crossProject = workspace.isCrossProjectClipboard(payload); ThemeStyleEntities.Snapshot snapshot = payload.styleEntity;
            java.util.ArrayList<String> missing = missingStyleEntityResources(styleSource, snapshot);
            if (!missing.isEmpty()) throw new IOException("Target project is missing style resources: " + android.text.TextUtils.join(", ", missing));
            String original = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            String expectedLocal = ThemeSaveCoordinator.Companion.fingerprint(original), expectedRemote = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(styleSource.getFile());
            LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "新样式实体标识", snapshot.getId() + "_copy");
            String message = "Source entity: " + snapshot.getId() + "\nTarget style asset: " + styleSource.getName() + "\nTarget keys: " + keys.size() + (snapshot.getCloneParent() == null ? "" : "\nClone dependency: " + snapshot.getCloneParent()) + (snapshot.getReferencedResources().isEmpty() ? "" : "\n资源数: " + android.text.TextUtils.join(", ", snapshot.getReferencedResources())) + (crossProject ? "\n\nCross-project paste: no URI/path metadata is retained; dependencies were verified by name." : "");
            new android.app.AlertDialog.Builder(this).setTitle("粘贴完整样式实体").setMessage(message).setView(fields).setNegativeButton("取消", null).setPositiveButton("粘贴", (dialog, which) -> pasteStyleEntity(styleSource, snapshot, id.getText().toString().trim(), keys, expectedLocal, expectedRemote)).show();
        } catch (Exception error) { workspace.setStatus("样式实体粘贴被阻止:" + error.getMessage()); Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
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
            workspace.applyStyleEntityReference(keys, targetId); workspace.setStatus("已粘贴完整样式实体 " + targetId + " 并校验了依赖");
        } catch (Exception error) { workspace.setStatus("样式实体粘贴失败,未覆盖较新数据:" + error.getMessage()); Toast.makeText(this, "无法粘贴样式实体", Toast.LENGTH_LONG).show(); }
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
            new android.app.AlertDialog.Builder(this).setTitle("修改共享样式实体?").setMessage(message.toString()).setNegativeButton("取消", null).setPositiveButton("应用事务", (dialog, which) -> applyBatchStyleEntities(styleSource, styleIds, background, textColor, localFingerprint, remoteFingerprint)).show();
        } catch (Exception error) { workspace.setStatus("样式批量更新被阻止:" + error.getMessage()); Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
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
            workspace.setStatus("已更新 " + styleIds.size() + " 个共享样式实体,位于 " + styleSource.getName() + ";事务已校验");
        } catch (Exception error) { workspace.setStatus("样式批量更新失败,未覆盖较新数据:" + error.getMessage()); Toast.makeText(this, "无法更新样式实体", Toast.LENGTH_LONG).show(); }
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
            Toast.makeText(this, "请先从“样式”菜单打开一个样式文件", Toast.LENGTH_LONG).show();
            return;
        }
        String source = editor.source();
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        java.util.ArrayList<ComponentScalarInput> inputs = new java.util.ArrayList<>();
        componentSection(fields, "候选栏与展开候选栏");
        addVisualStyleGroup(fields, inputs, source, "candidate", true, true);
        addResourceBackgroundScalar(fields, inputs, source, "candidate.expanded.background", "展开候选容器背景(candidate.expanded.background)");
        TextView expandedHeightNote = new TextView(this); expandedHeightNote.setText("当前 ExpandedCandidateView 不使用展开候选高度(candidate.expanded.height);仅为兼容性时在 Lua 源代码中编辑。"); fields.addView(expandedHeightNote);
        addVisualStyleGroup(fields, inputs, source, "candidate.key", false, false);
        addVisualStyleGroup(fields, inputs, source, "candidate.expanded.key", false, false);

        componentSection(fields, "工具栏");
        addResourceBackgroundScalar(fields, inputs, source, "toolbar.background", "工具栏背景(toolbar.background)");
        TextView toolbarHeightNote = new TextView(this); toolbarHeightNote.setText("ToolbarView 使用候选高度(candidate.height)填充;当前运行时不使用工具栏高度(toolbar.height)。"); fields.addView(toolbarHeightNote);
        addComponentScalar(fields, inputs, source, "toolbar.schema_switches", "显示运行时方案切换(toolbar.schema_switches)", ComponentScalarInput.BOOLEAN, null);
        addVisualStyleGroup(fields, inputs, source, "toolbar.hide", false, false);
        addVisualStyleGroup(fields, inputs, source, "toolbar.key", false, false);

        componentSection(fields, "符号面板");
        addResourceBackgroundScalar(fields, inputs, source, "symbol.background", "符号面板背景(symbol.background)");
        addColorScalar(fields, inputs, source, "symbol.indicator_color", "符号回退指示器颜色(symbol.indicator_color)");
        addVisualStyleGroup(fields, inputs, source, "symbol.text", false, false);
        addVisualStyleGroup(fields, inputs, source, "symbol.key", false, false);
        addColorScalar(fields, inputs, source, "symbol.tab_bar.indicator_color", "符号选中标签指示器颜色(symbol.tab_bar.indicator_color)");
        TextView symbolToolNote = new TextView(this); symbolToolNote.setText("当前运行时不使用符号工具栏(symbol.tool_bar)的可视按键字段;重力方向(gravity)、高度(height)和按键(keys)仍在面板栏管理器 / Lua 源代码中编辑。"); fields.addView(symbolToolNote);

        componentSection(fields, "剪贴板面板");
        addResourceBackgroundScalar(fields, inputs, source, "clipboard.background", "剪贴板面板背景(clipboard.background)");
        addColorScalar(fields, inputs, source, "clipboard.indicator_color", "剪贴板回退指示器颜色(clipboard.indicator_color)");
        addVisualStyleGroup(fields, inputs, source, "clipboard.key", false, false);
        addVisualStyleGroup(fields, inputs, source, "clipboard.item", false, false);
        TextView compatibility = new TextView(this);
        compatibility.setText("ClipboardKeyboardView 使用剪贴板按键(clipboard.key)渲染标签和工具按钮;WaterfallAdapter 使用剪贴板项目(clipboard.item)渲染剪贴板及短语行。");
        fields.addView(compatibility);
        addColorScalar(fields, inputs, source, "clipboard.tab_bar.indicator_color", "剪贴板选中标签指示器颜色(clipboard.tab_bar.indicator_color)");
        TextView clipboardToolNote = new TextView(this); clipboardToolNote.setText("当前运行时不使用剪贴板工具栏(clipboard.tool_bar)的可视按键字段;重力方向(gravity)、高度(height)和按键(keys)仍在面板栏管理器 / Lua 源代码中编辑。"); fields.addView(clipboardToolNote);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        showComponentScalarDialog("候选栏 / 工具栏 / 面板——静态字段", source, inputs, scroll,
                "候选栏、工具栏和面板样式已应用;保存后生效");
    }

    private void addVisualStyleGroup(LinearLayout fields, java.util.List<ComponentScalarInput> inputs,
                                     String source, String path, boolean includeHeight, boolean includeComment) {
        if (includeHeight) addIntegerScalar(fields, inputs, source, path + ".height", path + " 高度(height)");
        addResourceBackgroundScalar(fields, inputs, source, path + ".background", path + " 背景(background)");
        addColorScalar(fields, inputs, source, path + ".text_color", path + " 文本颜色(text_color)");
        addIntegerScalar(fields, inputs, source, path + ".text_size", path + " 文本大小(text_size)");
        addResourceBackgroundScalar(fields, inputs, source, path + ".pressed.background", path + " 按下背景(pressed.background)");
        addColorScalar(fields, inputs, source, path + ".pressed.text_color", path + " 按下文本颜色(pressed.text_color)");
        if (includeComment) {
            addColorScalar(fields, inputs, source, path + ".comment.text_color", path + " 注释颜色(comment.text_color)");
            addIntegerScalar(fields, inputs, source, path + ".comment.text_size", path + " 注释大小(comment.text_size)");
            addColorScalar(fields, inputs, source, path + ".comment.pressed.text_color", path + " 按下注释颜色(comment.pressed.text_color)");
            addIntegerScalar(fields, inputs, source, path + ".comment.pressed.text_size", path + " 按下注释大小(comment.pressed.text_size)");
        }
    }

    private void addColorScalar(LinearLayout fields, java.util.List<ComponentScalarInput> inputs, String source, String path, String label) {
        addComponentScalar(fields, inputs, source, path, label, ComponentScalarInput.COLOR, null);
    }

    private void addResourceBackgroundScalar(LinearLayout fields, java.util.List<ComponentScalarInput> inputs, String source, String path, String label) {
        addComponentScalar(fields, inputs, source, path, label + "(颜色或安全的项目相对资源)", ComponentScalarInput.COLOR_OR_RESOURCE, null);
    }

    private void addIntegerScalar(LinearLayout fields, java.util.List<ComponentScalarInput> inputs, String source, String path, String label) {
        addComponentScalar(fields, inputs, source, path, label + "(Trime2 整数大小)", ComponentScalarInput.NUMBER, null);
    }

    private void showComponentScalarDialog(String title, String source, java.util.List<ComponentScalarInput> inputs,
                                           android.widget.ScrollView scroll, String success) {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(title).setView(scroll).setNegativeButton("取消", null).setNeutralButton("Lua 源代码", null)
                .setPositiveButton("应用", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> { dialog.dismiss(); showCodeEditor(); });
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                try {
                    String updated = source; boolean changed = false;
                    for (ComponentScalarInput input : inputs) {
                        String next = applyComponentScalar(updated, input);
                        if (!next.equals(updated)) { changed = true; updated = next; }
                    }
                    if (!changed) { workspace.setStatus("没有字面组件字段发生变化"); dialog.dismiss(); return; }
                    com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(updated);
                    for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics())
                        if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR)
                            throw new IllegalArgumentException("Updated source failed parse verification: " + diagnostic.getMessage());
                    editor.replaceDocument(parsed.getDocument()); workspace.setModel(stylePreviewModel(editor.getDocument()));
                    viewModel.setDirty(true); workspace.setStatus(success); dialog.dismiss();
                } catch (Exception error) {
                    workspace.setStatus("组件样式更新被阻止:" + error.getMessage());
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.show();
    }

    private void showCompositionStyleEditor() {
        if (!ensureWritable()) return;
        if (!isCurrentStyleFile() || editor == null) {
            Toast.makeText(this, "请先从“样式”菜单打开一个样式文件", Toast.LENGTH_LONG).show();
            return;
        }
        String source = editor.source();
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        java.util.ArrayList<ComponentScalarInput> inputs = new java.util.ArrayList<>();
        componentSection(fields, "预编辑(preedit)");
        addComponentScalar(fields, inputs, source, "preedit.show", "编码窗口显示(composition.show)缺失时使用旧版显示回退(preedit.show)", ComponentScalarInput.BOOLEAN, null);
        addComponentScalar(fields, inputs, source, "preedit.background", "背景颜色/资源", ComponentScalarInput.COLOR_OR_RESOURCE, null);
        addComponentScalar(fields, inputs, source, "preedit.text_color", "文本颜色", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "preedit.text_size", "文本大小(Trime2 大小/SP 语义)", ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "preedit.inline", "内联模式(preedit.inline,保留源拼写)", ComponentScalarInput.INLINE,
                new String[]{"none", "input", "preedit", "composition", "preview", "true (string)", "true (boolean source; current runtime none)"});

        componentSection(fields, "编码窗口(composition)");
        addComponentScalar(fields, inputs, source, "composition.show", "显示(composition.show,运行时默认 true;经 preedit 样式回退)", ComponentScalarInput.BOOLEAN, null);
        addComponentScalar(fields, inputs, source, "composition.background", "背景颜色/资源", ComponentScalarInput.COLOR_OR_RESOURCE, null);
        addComponentScalar(fields, inputs, source, "composition.text_color", "文本颜色", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.text_size", "文本大小(Trime2 大小/SP 语义)", ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "composition.position", "位置(composition.position,保留未知源值;预览使用 fixed)", ComponentScalarInput.ENUM,
                new String[]{"left", "right", "left_up", "right_up", "drag", "fixed", "bottom_left", "bottom_right", "top_left", "top_right"});
        addComponentScalar(fields, inputs, source, "composition.movable", "可移动枚举(composition.movable)", ComponentScalarInput.ENUM,
                new String[]{"false", "true", "once"});

        componentSection(fields, "编码窗口过滤与条目");
        String[][] numbers = {
                {"composition.min_length", "最小输入长度(min_length)"}, {"composition.max_length", "最大行长度(max_length)"},
                {"composition.sticky_lines", "固定行数(sticky_lines)"}, {"composition.max_entries", "最大条目数(max_entries,-1 表示全部)"},
                {"composition.cloud_max_entries", "最大云端条目数(cloud_max_entries,0 使用运行时行为)"},
                {"composition.min_width", "最小宽度(min_width)"}, {"composition.min_height", "最小高度(min_height)"},
                {"composition.max_width", "最大宽度(max_width)"}, {"composition.max_height", "最大高度(max_height)"},
                {"composition.padding.left", "左内边距(padding.left)"}, {"composition.padding.top", "上内边距(padding.top)"},
                {"composition.padding.right", "右内边距(padding.right)"}, {"composition.padding.bottom", "下内边距(padding.bottom)"}
        };
        for (String[] item : numbers) addComponentScalar(fields, inputs, source, item[0], item[1], ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "composition.line_spacing", "行间距(composition.line_spacing,运行时浮点数)", ComponentScalarInput.FLOAT, null);
        addComponentScalar(fields, inputs, source, "composition.line_spacing_multiplier", "行间距倍数(composition.line_spacing_multiplier,运行时浮点数;0 按 1 预览)", ComponentScalarInput.FLOAT, null);
        addComponentScalar(fields, inputs, source, "composition.all_phrases", "包含所有短语(composition.all_phrases)", ComponentScalarInput.BOOLEAN, null);
        addComponentScalar(fields, inputs, source, "composition.use_cursor", "使用高亮候选光标(composition.use_cursor,运行时默认 true)", ComponentScalarInput.BOOLEAN, null);

        componentSection(fields, "编码窗口按下状态");
        addComponentScalar(fields, inputs, source, "composition.pressed.background", "按下背景颜色", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.pressed.text_color", "按下文本颜色", ComponentScalarInput.COLOR, null);
        componentSection(fields, "内部编码窗口按键样式(composition.window)");
        addComponentScalar(fields, inputs, source, "composition.key.background", "内部按键背景", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.text_color", "内部按键文本颜色", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.text_size", "内部按键文本大小(整数)", ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "composition.key.pressed.background", "内部按下按键背景", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.pressed.text_color", "内部按下按键文本颜色", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.hint.text_color", "内部标签文本颜色", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.hint.text_size", "内部标签文本大小(整数)", ComponentScalarInput.NUMBER, null);
        addComponentScalar(fields, inputs, source, "composition.key.pressed.hint.text_color", "内部按下标签文本颜色", ComponentScalarInput.COLOR, null);
        addComponentScalar(fields, inputs, source, "composition.key.pressed.hint.text_size", "内部按下标签文本大小(整数)", ComponentScalarInput.NUMBER, null);
        TextView fontNote = new TextView(this);
        fontNote.setText("预编辑(preedit)、编码窗口(composition)和按键(key)字体值可以是字符串或回退数组。它们仅在源代码中编辑,避免数组被展平。");
        fields.addView(fontNote);
        TextView sourceOnly = new TextView(this);
        sourceOnly.setText("编码窗口(composition.window)仅在源代码中编辑:组件顺序、条件、对齐、字间距和点击事件绝不会被求值或通用改写。");
        sourceOnly.setPadding(0, 16, 0, 16); fields.addView(sourceOnly);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("预编辑 / 编码窗口——字面静态字段")
                .setView(scroll).setNegativeButton("取消", null).setNeutralButton("Lua 源代码", null)
                .setPositiveButton("应用", null).create();
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
                    if (!changed) { workspace.setStatus("预编辑/编码窗口没有字面字段发生变化"); dialog.dismiss(); return; }
                    com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(updated);
                    for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) {
                        if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR)
                            throw new IllegalArgumentException("Updated source failed parse verification: " + diagnostic.getMessage());
                    }
                    editor.replaceDocument(parsed.getDocument());
                    workspace.setModel(stylePreviewModel(editor.getDocument()));
                    viewModel.setDirty(true);
                    workspace.setStatus("预编辑/编码窗口字段已应用;保存后生效");
                    dialog.dismiss();
                } catch (Exception error) {
                    workspace.setStatus("预编辑/编码窗口更新被阻止:" + error.getMessage());
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
                TextView blocked = new TextView(this); blocked.setText("仅源代码可编辑:" + value.getDiagnostic()); blocked.setEnabled(false); parent.addView(blocked);
                return;
            }
            String trace = value.getInheritedFrom() == null ? null : "继承自 " + value.getInheritedFrom();
            if (value.getCompatibilityDiagnostic() != null) trace = (trace == null ? "" : trace + ". ") + value.getCompatibilityDiagnostic();
            if (trace != null) { TextView note = new TextView(this); note.setText(trace); parent.addView(note); }
            if (kind == ComponentScalarInput.BOOLEAN || kind == ComponentScalarInput.ENUM || kind == ComponentScalarInput.INLINE) {
                java.util.ArrayList<String> values = new java.util.ArrayList<>(); values.add("inherit");
                if (choices == null) { values.add("false"); values.add("true"); } else java.util.Collections.addAll(values, choices);
                String current = componentScalarSelection(value, kind); String unknown = null;
                int selection = values.indexOf(current);
                if (selection < 0 && current != null) { unknown = "保留原值:" + current; values.add(unknown); selection = values.size() - 1; }
                if (selection < 0) selection = 0;
                android.widget.Spinner spinner = new android.widget.Spinner(this);
                spinner.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, spinnerChoices(values)));
                spinner.setSelection(value.getExplicit() ? selection : 0); parent.addView(spinner);
                inputs.add(new ComponentScalarInput(path, kind, value, null, null, spinner, current, unknown));
            } else {
                android.widget.CheckBox inherit = new android.widget.CheckBox(this); inherit.setText("继承 / 移除显式字段"); inherit.setChecked(!value.getExplicit()); parent.addView(inherit);
                String initial = componentScalarText(value, kind); EditText field = simpleField(parent, label, initial); field.setEnabled(!inherit.isChecked());
                inherit.setOnCheckedChangeListener((button, checked) -> field.setEnabled(!checked));
                inputs.add(new ComponentScalarInput(path, kind, value, inherit, field, null, initial, null));
            }
        } catch (Exception error) {
            TextView blocked = new TextView(this); blocked.setText("仅源代码可编辑:" + error.getMessage()); blocked.setEnabled(false); parent.addView(blocked);
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
            String selected = spinnerInternalValue(input.spinner);
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
        if (!isCurrentStyleFile() || editor == null) { Toast.makeText(this, "请先从“样式”菜单打开一个样式文件", Toast.LENGTH_LONG).show(); return; }
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        java.util.ArrayList<StyleInput> inputs = new java.util.ArrayList<>();
        addStyleInput(fields, inputs, "keyboard.background", "键盘背景(keyboard.background)", true); addStyleInput(fields, inputs, "keyboard.height", "键盘高度(keyboard.height,dp)", false);
        addStyleInput(fields, inputs, "key.background", "按键背景(key.background)", true); addStyleInput(fields, inputs, "key.text_color", "按键文本颜色(key.text_color)", true); addStyleInput(fields, inputs, "key.text_size", "按键文本大小(key.text_size,dp)", false); addStyleInput(fields, inputs, "key.corner_radius", "按键圆角(key.corner_radius,dp)", false); addStyleInput(fields, inputs, "key.elevation", "按键海拔(key.elevation,dp)", false); addStyleInput(fields, inputs, "key.stroke_width", "按键描边宽度(key.stroke_width,dp)", false);
        addStyleInput(fields, inputs, "key.pressed.background", "按下按键背景(key.pressed.background)", true); addStyleInput(fields, inputs, "key.pressed.text_color", "按下按键文本颜色(key.pressed.text_color)", true); addStyleInput(fields, inputs, "key.hint.text_color", "按键提示文本颜色(key.hint.text_color)", true); addStyleInput(fields, inputs, "key.hint.text_size", "按键提示文本大小(key.hint.text_size,dp)", false); addStyleInput(fields, inputs, "key.long_click.text_color", "长按提示颜色(key.long_click.text_color)", true); addStyleInput(fields, inputs, "key.long_click.text_size", "长按提示大小(key.long_click.text_size,dp)", false);
        addStyleInput(fields, inputs, "popup.background", "弹窗背景(popup.background)", true); addStyleInput(fields, inputs, "popup.corner_radius", "弹窗圆角(popup.corner_radius,dp)", false); addStyleInput(fields, inputs, "popup.column_count", "弹窗列数(popup.column_count)", false);
        TextView compositionLink = new TextView(this); compositionLink.setText("预编辑与编码窗口字段请使用专用页面,以确保继承值和仅源代码 Lua 无损保留。"); fields.addView(compositionLink);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("样式属性(仅字面字段)").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> {
            boolean changed = false; for (StyleInput input : inputs) changed |= input.color ? setStyleColor(input.path, input.field) : setStyleNumber(input.path, input.field);
            workspace.setModel(stylePreviewModel(editor.getDocument()));
            if (changed) { viewModel.setDirty(true); workspace.setStatus("样式属性已应用;保存后生效"); }
            else workspace.setStatus("没有字面样式值发生变化;继承的原始路径仍只能通过代码编辑");
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
        } catch (NumberFormatException ignored) { Toast.makeText(this, "颜色无效:" + path, Toast.LENGTH_LONG).show(); return false; }
    }

    private boolean setStyleNumber(String path, EditText field) {
        String value = field.getText().toString().trim(); if (value.isEmpty()) return false;
        try { ThemeValue next = new ThemeValue.LuaNumber(Double.parseDouble(value)); if (next.equals(editor.getDocument().get(path))) return false; return applyStyleValue(path, next); }
        catch (NumberFormatException ignored) { Toast.makeText(this, "数值无效:" + path, Toast.LENGTH_LONG).show(); return false; }
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
            Toast.makeText(this, "编辑源代码前请先打开 Lua 文件", Toast.LENGTH_LONG).show();
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
                .setTitle("Lua 源代码")
                .setView(source)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(source.getText().toString());
            boolean hasErrors = false;
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) {
                if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) { hasErrors = true; break; }
            }
            if (hasErrors) {
                Toast.makeText(this, "Lua 源代码有错误,未应用更改", Toast.LENGTH_LONG).show();
                return;
            }
            editor.replaceDocument(parsed.getDocument());
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            viewModel.setDirty(true);
            workspace.setStatus("Lua 源代码已应用;保存后生效");
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void chooseInstallTarget() {
        if (!ensureWritable()) return;
        if (project == null) { Toast.makeText(this, "安装前请先打开主题目录", Toast.LENGTH_LONG).show(); return; }
        if (viewModel.getDirty()) {
            new android.app.AlertDialog.Builder(this).setTitle("安装前保存").setMessage("安装将使用已校验的项目保存快照。").setNegativeButton("取消", null).setPositiveButton("保存并继续", (dialog, which) -> {
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
            if (tree == null || !tree.canWrite()) throw new IOException("安装目标不可写");
            String themeName = projectDisplayName == null || projectDisplayName.trim().isEmpty() ? project.getRoot().getName() : projectDisplayName;
            target = tree.findFile(themeName);
            targetExisted = target != null;
            if (target != null && !target.isDirectory()) throw new IOException("安装目标名称已被文件占用");
            if (target != null) {
                backup = tree.createDirectory(themeName + ".backup-" + System.currentTimeMillis());
                if (backup == null) throw new IOException("无法创建安装备份");
                copyDocumentToDocument(target, backup);
                backupManifest = documentManifest(backup);
                if (!backupManifest.equals(documentManifest(target))) throw new IOException("备份校验失败");
            } else {
                target = tree.createDirectory(themeName);
                if (target == null) throw new IOException("无法创建目标主题目录");
            }
            writeInstallJournal("BACKUP_READY", target, backup, null);
            clearDocumentDirectory(target);
            copyProjectToDocument(project.getRoot(), target);
            java.util.Map<String, Long> expected = fileManifest(project.getRoot());
            java.util.Map<String, Long> installed = documentManifest(target);
            if (!expected.equals(installed)) throw new IOException("已安装文件校验失败");
            writeInstallJournal("COMPLETED", target, backup, null);
            lastInstallTarget = target; lastInstallBackup = backup; lastBackupManifest = backupManifest;
            invalidateOptionsMenu();
            workspace.setStatus("主题已安装并校验:" + themeName + (backup == null ? "" : ";备份 " + backup.getName()));
        } catch (Exception error) {
            boolean rolledBack = false;
            if (target != null && backup != null) rolledBack = rollbackInstall(target, backup, backupManifest);
            else if (target != null && !targetExisted) { try { rolledBack = target.delete(); } catch (Exception ignored) { } }
            writeInstallJournal(rolledBack ? "ROLLED_BACK" : "FAILED", target, backup, error.getMessage());
            workspace.setStatus("安装失败:" + error.getMessage() + (rolledBack ? ";备份已恢复" : ""));
            Toast.makeText(this, rolledBack ? "主题安装失败,已回滚" : "主题安装失败", Toast.LENGTH_LONG).show();
        }
    }

    private void validateProjectForInstall() throws IOException {
        if (project == null || !project.getMainFile().isFile()) throw new IOException("主题项目没有 main.lua");
        ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
        for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry())) {
            if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException(diagnostic.getMessage());
        }
        if (snapshot.getStyleSource() == null) throw new IOException("默认样式文件缺失");
        if (snapshot.getKeyboardSource() == null) throw new IOException("所选默认键盘文件缺失,或无法校验动态选择");
        for (ThemeResource resource : ThemeResourceIndex.INSTANCE.scan(project.getRoot(), allProjectLuaSource())) if (resource.getReferenced() && resource.getSize() == 0) throw new IOException("引用的资源为空:" + resource.getRelativePath());
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
        if (lastInstallTarget == null || lastInstallBackup == null) { Toast.makeText(this, "没有可用的安装备份", Toast.LENGTH_LONG).show(); return; }
        if (confirm) { new android.app.AlertDialog.Builder(this).setTitle("回滚上次安装?").setMessage(lastInstallBackup.getName()).setNegativeButton("取消", null).setPositiveButton("回滚", (dialog, which) -> rollbackLastInstall(false)).show(); return; }
        boolean success = rollbackInstall(lastInstallTarget, lastInstallBackup, lastBackupManifest); writeInstallJournal(success ? "ROLLED_BACK" : "ROLLBACK_FAILED", lastInstallTarget, lastInstallBackup, null); workspace.setStatus(success ? "安装备份已恢复并校验" : "回滚校验失败"); if (success) { lastInstallBackup = null; lastInstallTarget = null; lastBackupManifest = null; invalidateOptionsMenu(); }
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
                new android.app.AlertDialog.Builder(this).setTitle("主题安装未完成").setMessage("存在已校验的备份,是否立即恢复?").setNegativeButton("稍后", null).setPositiveButton("恢复备份", (dialog, which) -> rollbackLastInstall(false)).show();
            }
        } catch (Exception ignored) { }
    }

    private void showDiagnostics() {
        StringBuilder text = new StringBuilder();
        if (projectSnapshot != null) {
            java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics =
                    ThemeProjectDiagnostics.INSTANCE.collect(projectSnapshot, new ThemeFieldRegistry());
            if (diagnostics.isEmpty()) text.append("没有诊断信息");
            for (com.osfans.trime.editor.core.ThemeDiagnostic item : diagnostics) {
                text.append(item.getSeverity()).append("  ").append(item.getPath() == null ? "" : item.getPath() + ": ").append(item.getMessage()).append('\n');
            }
        } else if (editor != null) {
            for (com.osfans.trime.editor.core.ThemeDiagnostic item : editor.diagnostics()) text.append(item.getSeverity()).append("  ").append(item.getMessage()).append('\n');
        } else {
            text.append("尚未加载主题");
        }
        new android.app.AlertDialog.Builder(this).setTitle("诊断信息").setMessage(text.toString()).setPositiveButton("关闭", null).show();
    }

    private void showResources() {
        if (project == null) { Toast.makeText(this, "请先打开主题目录", Toast.LENGTH_LONG).show(); return; }
        java.util.List<ThemeResource> resources = ThemeResourceIndex.INSTANCE.scan(project.getRoot(), allProjectLuaSource());
        String[] labels = new String[resources.size() + 1]; labels[0] = "+ 导入资源";
        for (int i = 0; i < resources.size(); i++) { ThemeResource resource = resources.get(i); labels[i + 1] = (resource.getReferenced() ? "已引用  " : resource.getReferenceUncertain() ? "需检查引用  " : "未使用  ") + resource.getKind() + "  " + resource.getRelativePath() + "  " + resource.getSize() + " 字节"; }
        new android.app.AlertDialog.Builder(this).setTitle("主题资源").setItems(labels, (dialog, which) -> {
            if (which == 0) chooseResourceType(); else showResourceActions(resources.get(which - 1));
        }).setNegativeButton("关闭", null).show();
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
        String[] types = {"图片", "字体", "声音", "脚本"}; String[] folders = {"images", "fonts", "sounds", "scripts"}; String[] mime = {"image/*", "font/*", "audio/*", "text/*"};
        new android.app.AlertDialog.Builder(this).setTitle("导入资源类型").setItems(types, (dialog, which) -> {
            pendingResourceFolder = folders[which]; importResourceLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime[which]).addCategory(Intent.CATEGORY_OPENABLE));
        }).setNegativeButton("取消", null).show();
    }

    private void importResource(Uri uri) {
        if (!ensureWritable()) return;
        if (project == null || pendingResourceFolder == null) return;
        File target = null;
        try {
            DocumentFile document = DocumentFile.fromSingleUri(this, uri); String name = document == null ? null : document.getName();
            if (name == null || name.trim().isEmpty()) name = "resource";
            name = name.replaceAll("[^A-Za-z0-9._ -]", "_").replace("..", "_");
            File folder = new File(project.getRoot(), pendingResourceFolder); if (!folder.exists() && !folder.mkdirs()) throw new IOException("无法创建资源文件夹");
            target = new File(folder, name); String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name; String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ""; int suffix = 2;
            while (target.exists()) target = new File(folder, base + "-" + suffix++ + extension);
            if (!target.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator)) throw new IOException("资源名称无效");
            try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(target)) { if (input == null) throw new IOException("无法读取资源"); byte[] buffer = new byte[8192]; int count; long total = 0; while ((count = input.read(buffer)) != -1) { total += count; if (total > 64L * 1024 * 1024) throw new IOException("资源超过 64 MiB 限制"); output.write(buffer, 0, count); } output.getFD().sync(); }
            if (importedProjectTreeUri != null) mirrorNewResourceToImportedTree(target);
            project = ThemeProject.Companion.discover(project.getRoot()); projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser()); workspace.setStatus("已导入 " + target.getName());
        } catch (Exception error) { if (target != null && target.exists()) target.delete(); workspace.setStatus("资源导入失败:" + error.getMessage()); Toast.makeText(this, "无法导入资源", Toast.LENGTH_LONG).show(); }
        finally { pendingResourceFolder = null; }
    }

    private void mirrorNewResourceToImportedTree(File local) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(local, true); if (ref == null || ref.file == null) throw new IOException("无法创建导入资源");
        try (FileInputStream input = new FileInputStream(local); java.io.OutputStream output = getContentResolver().openOutputStream(ref.file.getUri(), "wt")) { if (output == null) throw new IOException("无法写入导入资源"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
        try (FileInputStream input = new FileInputStream(local)) { if (!fingerprintStream(input).equals(fingerprintDocument(ref.file))) { ref.file.delete(); throw new IOException("导入资源校验失败"); } }
    }

    private void deleteImportedResource(File local) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(local, false); if (ref == null || ref.file == null) return;
        File backup = new File(getCacheDir(), "theme-editor-resource-delete-" + System.nanoTime());
        try (InputStream input = getContentResolver().openInputStream(ref.file.getUri()); FileOutputStream output = new FileOutputStream(backup)) { if (input == null) throw new IOException("无法备份导入资源"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); output.getFD().sync(); }
        if (!ref.file.delete() || ref.parent.findFile(ref.name) != null) {
            DocumentFile restore = ref.parent.findFile(ref.name); if (restore == null) restore = ref.parent.createFile(mimeForName(ref.name), ref.name);
            if (restore != null) try (FileInputStream input = new FileInputStream(backup); java.io.OutputStream output = getContentResolver().openOutputStream(restore.getUri(), "wt")) { if (output != null) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); } }
            backup.delete(); throw new IOException("导入资源删除失败");
        }
        backup.delete();
    }

    private static byte[] readFileBytes(File file, long limit) throws IOException {
        if (file.length() > limit) throw new IOException("文件超过备份限制");
        try (FileInputStream input = new FileInputStream(file); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("文件超过备份限制"); output.write(buffer, 0, count); } return output.toByteArray(); }
    }

    private void showResourceActions(ThemeResource resource) {
        String[] actions = {"复制相对路径", "删除"};
        new android.app.AlertDialog.Builder(this).setTitle(resource.getRelativePath()).setMessage(resource.getKind() + " • " + resource.getSize() + " 字节 • " + (resource.getReferenced() ? "已引用" : resource.getReferenceUncertain() ? "可能动态引用" : "未使用")).setItems(actions, (dialog, which) -> {
            if (which == 0) { android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE); clipboard.setPrimaryClip(android.content.ClipData.newPlainText("theme resource", resource.getRelativePath())); workspace.setStatus("资源路径已复制"); }
            else confirmResourceDelete(resource);
        }).setNegativeButton("关闭", null).show();
    }

    private void confirmResourceDelete(ThemeResource resource) {
        if (!ensureWritable()) return;
        if (resource.getReferenced() || resource.getReferenceUncertain()) { Toast.makeText(this, "被引用或动态解析的资源不能删除", Toast.LENGTH_LONG).show(); return; }
        new android.app.AlertDialog.Builder(this).setTitle("删除未使用的资源?").setMessage(resource.getRelativePath()).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> {
            File local = new File(project.getRoot(), resource.getRelativePath()); byte[] backup = null; try { if (local.isFile() && local.length() <= 64L * 1024 * 1024) backup = readFileBytes(local, 64L * 1024 * 1024); } catch (IOException ignored) { }
            ResourceDeleteResult result = new ThemeResourceManager(project.getRoot(), allProjectLuaSource()).delete(resource.getRelativePath());
            if (result instanceof ResourceDeleteResult.Deleted) {
                try { if (importedProjectTreeUri != null) deleteImportedResource(local); project = ThemeProject.Companion.discover(project.getRoot()); workspace.setStatus("已删除 " + resource.getRelativePath()); }
                catch (Exception error) { if (backup != null) try { File restore = new File(project.getRoot(), resource.getRelativePath()); restore.getParentFile().mkdirs(); try (FileOutputStream output = new FileOutputStream(restore)) { output.write(backup); output.getFD().sync(); } } catch (Exception ignored) { } try { project = ThemeProject.Companion.discover(project.getRoot()); } catch (Exception ignored) { } workspace.setStatus("资源删除已回滚:" + error.getMessage()); }
            } else if (result instanceof ResourceDeleteResult.Referenced) workspace.setStatus("删除被阻止:资源已被引用"); else workspace.setStatus("资源删除失败");
        }).show();
    }

    private void exportZip(boolean share) {
        if (editor == null) { Toast.makeText(this, "导出前请先打开主题", Toast.LENGTH_LONG).show(); return; }
        File sourceRoot = null;
        try {
            if (workspace.getModel().layoutMode != ThemeEditorModel.LayoutMode.NONE && !syncModel(workspace.getModel())) return;
            com.osfans.trime.editor.core.ParseResult check = new ThemeLuaParser().parse(editor.source());
            boolean hasErrors = false;
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : check.getDiagnostics()) {
                if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) { hasErrors = true; break; }
            }
            if (hasErrors) {
                workspace.setStatus("导出被阻止:Lua 存在错误");
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
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(android.content.ClipData.newRawUri("theme", contentUri));
            startActivity(Intent.createChooser(intent, "分享主题 ZIP"));
            workspace.setStatus("已准备分享 " + zip.getName());
        } catch (Exception error) {
            workspace.setStatus("导出失败:" + error.getMessage()); Toast.makeText(this, "无法导出主题", Toast.LENGTH_LONG).show();
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
        String source = pendingSaveSource; pendingSaveSource = null; if (source == null) { workspace.setStatus("没有待保存的 Lua 源代码"); return; }
        try {
            UriThemeProjectRepository target = new UriThemeProjectRepository(getContentResolver(), uri); target.write(source);
            String verified = target.read(); if (!source.equals(verified)) throw new IOException("Saved source verification mismatch");
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(verified); for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Saved source failed parse verification");
            repository = target; project = null; projectSnapshot = null; currentUri = uri; importedProjectUri = null; importedProjectTreeUri = null; importedProjectTreePrefix = null; openedImportedFingerprint = null; viewModel.setCurrentUri(uri); claimSession(sessionIdentity()); editor.replaceDocument(parsed.getDocument()); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(verified); openedFingerprint = null; layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            workspace.setModel(layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); viewModel.setDirty(false); deleteRecoveryDraft(); workspace.setStatus("已保存并校验:" + uri); invalidateOptionsMenu();
        } catch (Exception error) { pendingSaveSource = source; workspace.setStatus("保存失败:" + error.getMessage()); Toast.makeText(this, "无法保存 Lua 源代码", Toast.LENGTH_LONG).show(); }
    }

    private void saveModel(ThemeEditorModel model) {
        if (!ensureWritable()) return;
        if (repository == null) {
            try {
                pendingSaveSource = editor == null ? "" : editor.source();
                com.osfans.trime.editor.core.ParseResult check = new ThemeLuaParser().parse(pendingSaveSource);
                for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : check.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Lua source contains errors");
                saveLuaLauncher.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/x-lua").putExtra(Intent.EXTRA_TITLE, "main.lua"));
            } catch (Exception error) { pendingSaveSource = null; workspace.setStatus("保存被阻止:" + error.getMessage()); }
            return;
        }
        String previousSource = null;
        try {
            if (editor == null) editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            if (!isCurrentStyleFile()) {
                if (!layoutEditable || model.layoutMode == ThemeEditorModel.LayoutMode.NONE) {
                    workspace.setStatus("此 Lua 文件没有结构化键盘布局");
                    Toast.makeText(this, "编辑前请先打开键盘 Lua 文件", Toast.LENGTH_LONG).show();
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
            workspace.setStatus("已保存并校验");
        } catch (Exception error) {
            if (previousSource != null && importedProjectTreeUri != null) try { repository.write(previousSource); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(previousSource); } catch (Exception ignored) { }
            workspace.setStatus("保存失败:" + error.getMessage());
            Toast.makeText(this, "无法保存主题", Toast.LENGTH_LONG).show();
        }
    }

    private void showSaveConflict() {
        workspace.setStatus("外部文件已更改;已保留编辑器中的未保存草稿");
        new android.app.AlertDialog.Builder(this).setTitle("主题已在编辑器外部更改").setMessage("重新加载会丢弃当前内存草稿;保留草稿后仍可复制源代码或导出 ZIP。").setNegativeButton("保留草稿", null).setPositiveButton("重新加载磁盘文件", (dialog, which) -> reloadRepositoryAfterConflict()).show();
    }

    private void reloadRepositoryAfterConflict() {
        try {
            refreshImportedCacheFile();
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(repository.read());
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("External source contains Lua errors");
            editor.replaceDocument(parsed.getDocument()); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(repository.read()); layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            if (repository instanceof DirectoryThemeProjectRepository) openedFingerprint = ThemeSourceFingerprint.Companion.capture(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            if (project != null) projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); viewModel.setDirty(false); deleteRecoveryDraft(); workspace.setStatus("已重新加载外部主题源代码");
        } catch (Exception error) { workspace.setStatus("重新加载失败:" + error.getMessage()); Toast.makeText(this, "无法重新加载外部主题", Toast.LENGTH_LONG).show(); }
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
            new android.app.AlertDialog.Builder(this).setTitle("恢复未保存的主题草稿?").setMessage("此 Lua 文件存在有效的私有草稿。").setNegativeButton("丢弃草稿", (dialog, which) -> deleteRecoveryDraft()).setPositiveButton("恢复", (dialog, which) -> {
                editor.replaceDocument(parsed.getDocument()); layoutEditable = findLayoutRoot(editor.getDocument()) != null; workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); viewModel.setDirty(true); workspace.setStatus("已恢复私有草稿;请保存以提交更改");
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
