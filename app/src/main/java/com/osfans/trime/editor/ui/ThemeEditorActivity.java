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

import com.osfans.trime.Config;
import com.osfans.trime.core.Rime;
import com.osfans.trime.editor.core.ThemeEditor;
import com.osfans.trime.editor.core.ThemeFieldRegistry;
import com.osfans.trime.editor.core.ThemeFieldCoverage;
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
import com.osfans.trime.editor.project.ThemeResourceStats;
import com.osfans.trime.editor.project.ThemeProjectCreator;
import com.osfans.trime.editor.project.ThemeProjectMutator;
import com.osfans.trime.editor.project.ResourceDeleteResult;
import com.osfans.trime.util.Function;

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
    private static final int MENU_EDITOR_PAGES = 23;
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
    private File pendingDirectoryExport;
    private String pendingDirectoryExportName;
    private File pendingBuiltInTemplate;
    private String pendingBuiltInTemplateName;
    private String pendingTextExport;
    private File lastExportArtifact;
    private Uri lastExportUri;
    private String lastExportReport;
    private com.osfans.trime.editor.project.ThemeExportKind lastExportKind;
    private String pendingSaveSource;
    private ThemeProjectCreator.Spec pendingCreateSpec;
    private String pendingResourceFolder;
    private boolean restoringDirtySession;
    private String restoringProjectFile;
    private DocumentFile lastInstallTarget;
    private DocumentFile lastInstallBackup;
    private java.util.Map<String, Long> lastBackupManifest;
    private java.util.Map<String, String> lastBackupHashManifest;
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
    private int editsAfterSourceTransaction;
    private boolean sourceTransactionUndone;
    private boolean applyingMigration;
    private Runnable pendingWorkspaceReplacement;

    private final ActivityResultLauncher<Intent> openLuaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) loadUri(data.getData());
    });
    private final ActivityResultLauncher<Intent> openTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) return; Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
        loadTree(uri);
    });
    private final ActivityResultLauncher<Intent> createProjectTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); ThemeProjectCreator.Spec spec = pendingCreateSpec; setPendingCreateSpec(null);
        if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null || spec == null) return; Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
        createProjectInTree(uri, spec);
    });
    private final ActivityResultLauncher<Intent> saveLuaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData();
        if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) savePendingSource(data.getData());
        else { setPendingSaveSource(null); pendingWorkspaceReplacement = null; }
    });
    private final ActivityResultLauncher<Intent> installTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) return; Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
        installToTree(uri);
    });
    private final ActivityResultLauncher<Intent> importResourceLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) importResource(data.getData()); else setPendingResourceFolder(null);
    });
    private final ActivityResultLauncher<Intent> defaultProjectTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
        editorPreferences().edit().putString("default_project_uri", uri.toString()).apply();
        if (workspace != null) workspace.setStatus("默认项目目录已更新");
    });
    private final ActivityResultLauncher<Intent> exportZipLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); File artifact = pendingExport; setPendingExport(null);
        if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null && artifact != null) {
            try {
                try (FileInputStream input = new FileInputStream(artifact); java.io.OutputStream output = getContentResolver().openOutputStream(data.getData())) { if (output == null) throw new IOException("无法打开导出目标"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
                try (FileInputStream expected = new FileInputStream(artifact); InputStream actual = getContentResolver().openInputStream(data.getData())) { if (actual == null || !fingerprintStream(expected).equals(fingerprintStream(actual))) throw new IOException("导出目标回读校验不一致"); }
                rememberExportResult(artifact, data.getData()); workspace.setStatus("导出文件已写入并回读校验"); showExportResult(); artifact = null;
            } catch (Exception error) { workspace.setStatus("导出失败:" + safeErrorMessage(error)); }
        }
        if (artifact != null && artifact.exists()) artifact.delete();
    });
    private final ActivityResultLauncher<Intent> exportDirectoryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); File source = pendingDirectoryExport; String name = pendingDirectoryExportName; setPendingDirectoryExport(null, null);
        if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null || source == null) { deleteDirectory(source); return; }
        DocumentFile created = null;
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(this, data.getData()); if (tree == null || !tree.canWrite()) throw new IOException("导出目标目录不可写");
            String base = name == null || name.isEmpty() ? "theme-export" : name; String unique = base; int suffix = 2; while (tree.findFile(unique) != null) unique = base + "-" + suffix++;
            created = tree.createDirectory(unique); if (created == null) throw new IOException("无法创建导出主题目录");
            copyProjectToDocument(source, created); if (!fileManifest(source).equals(documentManifest(created))) throw new IOException("导出目录回读清单不一致");
            rememberExportResult(source, created.getUri()); workspace.setStatus("完整主题目录已导出并校验:" + unique); showExportResult(); source = null;
        } catch (Exception error) { if (created != null) created.delete(); workspace.setStatus("目录导出失败:" + safeErrorMessage(error)); }
        finally { deleteDirectory(source); }
    });
    private final ActivityResultLauncher<Intent> builtInTemplateTreeLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); File source = pendingBuiltInTemplate; String name = pendingBuiltInTemplateName; setPendingBuiltInTemplate(null, null);
        if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null || source == null) { deleteDirectory(source); return; }
        DocumentFile created = null;
        try {
            Uri treeUri = data.getData(); try { getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (SecurityException ignored) { }
            DocumentFile parent = DocumentFile.fromTreeUri(this, treeUri); if (parent == null || !parent.canWrite()) throw new IOException("模板目标目录不可写"); if (parent.findFile(name) != null) throw new IOException("同名模板项目已存在");
            created = parent.createDirectory(name); if (created == null) throw new IOException("无法创建模板项目目录"); copyProjectToDocument(source, created);
            if (!fileManifest(source).equals(documentManifest(created)) || !fileHashManifest(source).equals(documentHashManifest(created))) throw new IOException("内置模板副本回读校验不一致");
            File cache = new File(getCacheDir(), "theme-editor-built-in-open-" + System.nanoTime()); copyDirectory(source, cache); importedProjectUri = treeUri; importedProjectTreeUri = treeUri; importedProjectTreePrefix = name; openedImportedFingerprint = null; rememberRecentProject(treeUri, name, name); loadProject(cache, name); workspace.setStatus("内置默认主题已复制为可编辑副本:" + name + ";内置 assets 未修改"); source = null;
        } catch (Exception error) { if (created != null) created.delete(); workspace.setStatus("内置模板复制失败:" + safeErrorMessage(error)); }
        finally { deleteDirectory(source); }
    });
    private final ActivityResultLauncher<Intent> exportTextLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Intent data = result.getData(); String source = pendingTextExport; setPendingTextExport(null);
        if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null || source == null) return;
        try {
            try (java.io.OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) { if (output == null) throw new IOException("无法打开文本导出目标"); output.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            try (InputStream actual = getContentResolver().openInputStream(data.getData())) { if (actual == null || !ThemeSaveCoordinator.Companion.fingerprint(source).equals(fingerprintStream(actual))) throw new IOException("文本导出目标回读校验不一致"); }
            if (lastExportArtifact != null) deleteDirectory(lastExportArtifact); lastExportUri = data.getData(); lastExportArtifact = null; workspace.setStatus("文本导出已完成并回读校验"); showExportResult();
        } catch (Exception error) { workspace.setStatus("文本导出失败:" + safeErrorMessage(error)); }
    });

    private void restorePendingOperations() {
        setPendingResourceFolder(viewModel.getPendingResourceFolder());
        pendingExport = restoredPendingCacheFile(viewModel.getPendingExportPath(), false);
        if (pendingExport == null) viewModel.setPendingExportPath(null);
        pendingDirectoryExport = restoredPendingCacheFile(viewModel.getPendingDirectoryExportPath(), true); pendingDirectoryExportName = viewModel.getPendingDirectoryExportName();
        if (pendingDirectoryExport == null) { viewModel.setPendingDirectoryExportPath(null); viewModel.setPendingDirectoryExportName(null); pendingDirectoryExportName = null; }
        pendingCreateSpec = decodePendingCreateSpec(viewModel.getPendingCreateSpec()); if (pendingCreateSpec == null) viewModel.setPendingCreateSpec(null);
        pendingBuiltInTemplate = restoredPendingCacheFile(viewModel.getPendingBuiltInTemplatePath(), true); pendingBuiltInTemplateName = viewModel.getPendingBuiltInTemplateName(); if (pendingBuiltInTemplate == null) setPendingBuiltInTemplate(null, null);
        pendingTextExport = restorePendingPrivateText(pendingTextExportFile(), viewModel.getPendingTextExport()); if (pendingTextExport == null) viewModel.setPendingTextExport(false);
        pendingSaveSource = restorePendingPrivateText(pendingSaveSourceFile(), viewModel.getPendingSaveSource()); if (pendingSaveSource == null) viewModel.setPendingSaveSource(false);
    }

    private File restoredPendingCacheFile(String path, boolean directory) {
        if (path == null || path.isEmpty()) return null;
        try { File root = new File(getCacheDir(), "theme-editor-share").getCanonicalFile(), file = new File(path).getCanonicalFile(); String prefix = root.getCanonicalPath() + File.separator; return file.getCanonicalPath().startsWith(prefix) && (directory ? file.isDirectory() : file.isFile()) ? file : null; }
        catch (IOException ignored) { return null; }
    }

    private String restorePendingPrivateText(File file, boolean expected) {
        if (!expected || !file.isFile()) return null; try { return readSmallText(file, 4 * 1024 * 1024); } catch (Exception error) { file.delete(); return null; }
    }

    private void setPendingResourceFolder(String value) { pendingResourceFolder = value; if (viewModel != null) viewModel.setPendingResourceFolder(value); }
    private void setPendingExport(File value) { pendingExport = value; if (viewModel != null) viewModel.setPendingExportPath(value == null ? null : value.getAbsolutePath()); }
    private void setPendingDirectoryExport(File value, String name) { pendingDirectoryExport = value; pendingDirectoryExportName = name; if (viewModel != null) { viewModel.setPendingDirectoryExportPath(value == null ? null : value.getAbsolutePath()); viewModel.setPendingDirectoryExportName(name); } }
    private void setPendingBuiltInTemplate(File value, String name) { pendingBuiltInTemplate = value; pendingBuiltInTemplateName = name; if (viewModel != null) { viewModel.setPendingBuiltInTemplatePath(value == null ? null : value.getAbsolutePath()); viewModel.setPendingBuiltInTemplateName(name); } }
    private void setPendingTextExport(String value) { pendingTextExport = value; if (viewModel == null) return; try { if (value == null) pendingTextExportFile().delete(); else writePrivateText(pendingTextExportFile(), value); viewModel.setPendingTextExport(value != null); } catch (Exception error) { pendingTextExport = null; pendingTextExportFile().delete(); viewModel.setPendingTextExport(false); } }
    private void setPendingSaveSource(String value) { pendingSaveSource = value; if (viewModel == null) return; try { if (value == null) pendingSaveSourceFile().delete(); else writePrivateText(pendingSaveSourceFile(), value); viewModel.setPendingSaveSource(value != null); } catch (Exception error) { pendingSaveSource = null; pendingSaveSourceFile().delete(); viewModel.setPendingSaveSource(false); } }
    private File pendingTextExportFile() { return new File(getFilesDir(), "theme-editor-pending-export.txt"); }
    private File pendingSaveSourceFile() { return new File(getFilesDir(), "theme-editor-pending-save.lua"); }

    private void setPendingCreateSpec(ThemeProjectCreator.Spec spec) { pendingCreateSpec = spec; if (viewModel != null) viewModel.setPendingCreateSpec(encodePendingCreateSpec(spec)); }
    private static String encodePendingCreateSpec(ThemeProjectCreator.Spec spec) {
        if (spec == null) return null; String[] values = {spec.getDirectoryName(), spec.getThemeName(), spec.getAuthor(), spec.getStyleName(), spec.getKeyboardName(), spec.getPalette().name(), spec.getKeyboardTemplate().name()}; StringBuilder result = new StringBuilder();
        for (String value : values) { if (result.length() > 0) result.append('.'); result.append(android.util.Base64.encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP)); } return result.toString();
    }
    private static ThemeProjectCreator.Spec decodePendingCreateSpec(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null; try { String[] parts = encoded.split("\\.", -1); if (parts.length != 7) return null; String[] values = new String[7]; for (int i = 0; i < parts.length; i++) values[i] = new String(android.util.Base64.decode(parts[i], android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP), java.nio.charset.StandardCharsets.UTF_8); return new ThemeProjectCreator.Spec(values[0], values[1], values[2], values[3], values[4], ThemeProjectCreator.Palette.valueOf(values[5]), ThemeProjectCreator.KeyboardTemplate.valueOf(values[6])).validated(); } catch (Exception ignored) { return null; }
    }

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        viewModel = new ViewModelProvider(
                this,
                new SavedStateViewModelFactory(getApplication(), this, state)
        ).get(ThemeEditorViewModel.class);
        workspace = new ThemeEditorWorkspace(this);
        setContentView(workspace);
        restorePendingOperations();
        restoreInstallJournal();
        workspace.setCallbacks(new ThemeEditorCallbacks() {
            @Override public void onSave(ThemeEditorModel model) { if (ensureWritable()) saveModel(model); }
            @Override public void onModelChanged(ThemeEditorModel model) {
                if (!ensureWritable()) return;
                if (!applyingMigration && sourceTransactionUndone) clearMigrationHistory();
                if (applyingMigration || syncModel(model)) {
                    if (!applyingMigration && migrationUndoDocument != null) editsAfterSourceTransaction++;
                    applyPreviewStyles(model); workspace.updatePreviewColors(model); viewModel.recordEdit();
                }
            }
            @Override public void onUndo(ThemeEditorModel model) { if (ensureWritable() && syncUndoModel(model)) { applyPreviewStyles(model); workspace.updatePreviewColors(model); viewModel.recordEdit(); } }
            @Override public void onRedo(ThemeEditorModel model) { if (ensureWritable() && syncRedoModel(model)) { applyPreviewStyles(model); workspace.updatePreviewColors(model); viewModel.recordEdit(); } }
            @Override public void onSelectionChanged(ThemeEditorModel.Key key) { viewModel.setSelectedKeyId(key == null ? null : key.id); }
            @Override public void onBatchStyleEntities(java.util.List<ThemeEditorModel.Key> keys, String background, String textColor) { reviewBatchStyleEntities(keys, background, textColor); }
            @Override public void onCopyStyleEntity(ThemeEditorModel.Key key) { copyStyleEntity(key); }
            @Override public void onPasteStyleEntity(java.util.List<ThemeEditorModel.Key> keys) { promptPasteStyleEntity(keys); }
            @Override public void onManageKeyEvents(ThemeEditorModel.Key key) { showKeyEventManager(key); }
            @Override public void onOpenStyleProperties(ThemeEditorModel.Key key) { viewModel.setInspectorTab("basic"); showStyleEditor(); }
            @Override public void onOpenKeyEvents(ThemeEditorModel.Key key) { viewModel.setInspectorTab("events"); showKeyEventManager(key); }
            @Override public void onOpenResources(ThemeEditorModel.Key key) { viewModel.setInspectorTab("resources"); showResources(); }
            @Override public void onOpenLuaSource() { viewModel.setInspectorTab("states"); showCodeEditor(); }
            @Override public void onInspectorPageChanged(String pageId) { viewModel.setInspectorTab(pageId); }
            @Override public void onPreviewStateChanged() { viewModel.recordPreviewChange(); }
        });
        restoringDirtySession = state != null && viewModel.getDirty();
        restoringProjectFile = state == null ? null : viewModel.getProjectFile();
        Uri data = getIntent().getData();
        boolean restoredProject = state != null && restoreProjectState();
        if (!restoredProject && data != null) loadUri(data);
        else if (!restoredProject && viewModel.getCurrentUri() != null) loadUri(viewModel.getCurrentUri());
        else if (!restoredProject && getIntent().hasExtra(EXTRA_THEME)) loadFile(new File(getIntent().getStringExtra(EXTRA_THEME)));
        else if (!restoredProject) {
            if (openInputThemeProject()) {
                restoreWorkspaceState();
            } else {
                editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
                workspace.setModel(toUiModel(editor.getDocument()));
                restoreWorkspaceState();
            }
            offerRecoveryDraft();
        }
        if (state != null) restoreCurrentPageAfterRecreation();
        restoringDirtySession = false; restoringProjectFile = null;
    }

    private void restoreCurrentPageAfterRecreation() {
        String page = viewModel.getCurrentPage();
        if ("code_editor".equals(page) && repository != null && editor != null) showCodeEditor();
        else if ("project_home".equals(page)) showProjectHome();
        else if ("theme_settings".equals(page) && project != null) showThemeSettings();
        else if ("structure".equals(page) && editor != null) showStructurePage();
        else if ("resources".equals(page) && project != null) showResources();
        else if ("diagnostics".equals(page)) showDiagnostics();
        else if ("export_install".equals(page)) showExportInstallPage();
        else if ("editor_settings".equals(page)) showEditorSettings();
        else if ("recovery".equals(page)) showRecoveryStatus();
    }

    @Override public void onBackPressed() {
        if (workspace != null && workspace.closePropertiesDrawer()) return;
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
        menu.add(0, MENU_PAGES, 1, "属性").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, MENU_EDITOR_PAGES, 2, "编辑器页面").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_OPEN_LUA, 3, "打开 Lua 文件").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
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
        if (item.getItemId() == MENU_PAGES) { workspace.togglePropertiesDrawer(); return true; }
        if (item.getItemId() == MENU_EDITOR_PAGES) { showEditorPages(); return true; }
        if (item.getItemId() == MENU_OPEN_LUA) {
            requestWorkspaceReplacement("打开其他 Lua 文件", () -> openLuaLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)));
            return true;
        }
        if (item.getItemId() == MENU_OPEN_FOLDER) {
            requestWorkspaceReplacement("打开其他主题文件夹", () -> openTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)));
            return true;
        }
        if (item.getItemId() == MENU_EXPORT) { showExportOptions(false); return true; }
        if (item.getItemId() == MENU_SHARE) { showExportOptions(true); return true; }
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

    private void requestWorkspaceReplacement(String action, Runnable replacement) {
        if (replacement == null) return;
        if (!viewModel.getDirty()) { replacement.run(); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("有未保存的更改")
                .setMessage(action + "会替换当前工作区。请先保存、放弃或取消。")
                .setPositiveButton("保存后继续", (dialog, which) -> {
                    pendingWorkspaceReplacement = replacement;
                    saveModel(workspace.getModel());
                    if (!viewModel.getDirty()) {
                        pendingWorkspaceReplacement = null;
                        replacement.run();
                    } else if (pendingSaveSource == null) {
                        pendingWorkspaceReplacement = null;
                        workspace.setStatus("当前更改尚未保存,已取消替换工作区");
                    }
                })
                .setNegativeButton("放弃并继续", (dialog, which) -> {
                    pendingWorkspaceReplacement = null;
                    if (discardCurrentDraft()) replacement.run();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private boolean discardCurrentDraft() {
        try {
            com.osfans.trime.editor.core.ParseResult parsed;
            if (repository == null) parsed = new ThemeLuaParser().parse(com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document(), com.osfans.trime.editor.core.ThemeWriteMode.HYBRID));
            else parsed = new ThemeLuaParser().parse(repository.read());
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("已保存源代码无法重新加载:" + diagnostic.getMessage());
            if (editor == null) editor = new ThemeEditor(parsed.getDocument()); else editor.replaceDocument(parsed.getDocument());
            openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(parsed.getDocument(), com.osfans.trime.editor.core.ThemeWriteMode.HYBRID));
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            restoreWorkspaceState();
            viewModel.markSaved(openedSourceFingerprint); deleteRecoveryDraft(); deleteCodeBuffer(); clearMigrationHistory();
            workspace.markSaved("已放弃未保存更改并恢复已保存版本");
            return true;
        } catch (Exception error) {
            workspace.setStatus("无法安全放弃当前更改:" + safeErrorMessage(error));
            return false;
        }
    }

    private void showEditorPages() {
        String[] pages = {"项目主页", "新建项目", "最近项目", "键盘资源", "样式资源", "主题设置", "键盘结构", "样式属性", "候选栏 / 工具栏 / 面板", "预编辑 / 编码窗口", "预览工作区", "资源", "诊断信息", "Lua 源代码", "导出与安装", "编辑器设置", "恢复状态", "帮助与已知限制"};
        new android.app.AlertDialog.Builder(this).setTitle("主题编辑器页面").setItems(pages, (dialog, which) -> {
            if (which == 0) showProjectHome(); else if (which == 1) requestWorkspaceReplacement("新建并打开主题项目", this::showNewProjectWizard); else if (which == 2) showRecentProjects(); else if (which == 3) showKeyboardAssets(); else if (which == 4) showStyleAssets(); else if (which == 5) showThemeSettings(); else if (which == 6) showStructurePage(); else if (which == 7) showStyleEditor(); else if (which == 8) showVisualComponentStyleEditor(); else if (which == 9) showCompositionStyleEditor(); else if (which == 10) workspace.setStatus("预览工作区已启用;请使用“预览...”控制设备"); else if (which == 11) showResources(); else if (which == 12) showDiagnostics(); else if (which == 13) showCodeEditor(); else if (which == 14) showExportInstallPage(); else if (which == 15) showEditorSettings(); else if (which == 16) showRecoveryStatus(); else showEditorHelp();
        }).setNegativeButton("关闭", null).show();
    }

    private void showProjectHomeState() { viewModel.setCurrentPage("project_home"); }
    private void showProjectHome() {
        showProjectHomeState();
        StringBuilder text = new StringBuilder();
        if (project == null) text.append("单个 Lua 文件或未保存草稿"); else text.append("项目:").append(projectDisplayName == null ? project.getRoot().getName() : projectDisplayName).append("\n样式数: ").append(project.getStyles().size()).append("\n键盘数: ").append(project.getKeyboards().size()).append("\n资源数: ").append(project.getResources().size());
        text.append("\n当前文件: ").append(currentFileDisplayName()).append("\n模式: ").append(readOnlySession ? "第二会话只读" : "可写").append("\n有未保存更改: ").append(viewModel.getDirty() ? "是" : "否");
        String[] actions = project == null ? new String[]{"打开 Lua/ZIP", "打开主题目录", "最近项目", "新建项目", "示例模板", "恢复草稿", "帮助"} : new String[]{"项目操作", "打开 Lua/ZIP", "打开主题目录", "最近项目", "新建项目", "示例模板", "恢复草稿", "帮助"};
        new android.app.AlertDialog.Builder(this).setTitle("项目主页").setMessage(text.toString()).setItems(actions, (dialog, which) -> {
            int action = project == null ? which : which - 1;
            if (project != null && which == 0) showProjectActions();
            else if (action == 0) requestWorkspaceReplacement("打开其他 Lua 文件或 ZIP", () -> openLuaLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)));
            else if (action == 1) requestWorkspaceReplacement("打开其他主题文件夹", () -> openTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)));
            else if (action == 2) showRecentProjects();
            else if (action == 3) requestWorkspaceReplacement("新建并打开主题项目", this::showNewProjectWizard);
            else if (action == 4) requestWorkspaceReplacement("从示例模板新建项目", this::showExampleTemplates);
            else if (action == 5) showRecoveryStatus();
            else showEditorHelp();
        }).setNegativeButton("关闭", null).show();
    }

    private void showNewProjectWizard() { showNewProjectWizard(null); }

    private void showNewProjectWizard(ThemeProjectCreator.Spec template) {
        if (!ensureWritable()) return;
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        EditText directory = simpleField(fields, "目录标识", template == null ? "my_theme" : template.getDirectoryName());
        EditText name = simpleField(fields, "主题名称", template == null ? "我的主题" : template.getThemeName());
        EditText author = simpleField(fields, "作者", template == null ? "作者" : template.getAuthor());
        EditText style = simpleField(fields, "默认样式标识", template == null ? "light" : template.getStyleName());
        EditText keyboard = simpleField(fields, "默认键盘标识", template == null ? "default" : template.getKeyboardName());
        TextView paletteLabel = new TextView(this); paletteLabel.setText("初始样式模板"); fields.addView(paletteLabel);
        android.widget.Spinner palette = new android.widget.Spinner(this); palette.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"浅色", "深色"})); palette.setSelection(template != null && template.getPalette() == ThemeProjectCreator.Palette.DARK ? 1 : 0); fields.addView(palette);
        TextView layoutLabel = new TextView(this); layoutLabel.setText("默认键盘模板"); fields.addView(layoutLabel);
        android.widget.Spinner layout = new android.widget.Spinner(this); layout.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"行布局(rows)", "弹性盒布局(flex_box)", "分页键映射(key_maps)", "绝对键布局(keys)"})); layout.setSelection(template == null ? 0 : template.getKeyboardTemplate().ordinal()); fields.addView(layout);
        TextView note = new TextView(this); note.setText("模板会复制到用户选择的新目录;不会修改应用内置文件或已有主题。"); note.setPadding(0, 12, 0, 0); fields.addView(note);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle(template == null ? "新建主题项目" : "从示例模板新建").setView(scroll).setNegativeButton("取消", null).setPositiveButton("创建预览", (dialog, which) -> {
            try {
                ThemeProjectCreator.Spec spec = new ThemeProjectCreator.Spec(directory.getText().toString().trim(), name.getText().toString().trim(), author.getText().toString().trim(), style.getText().toString().trim(), keyboard.getText().toString().trim(), palette.getSelectedItemPosition() == 0 ? ThemeProjectCreator.Palette.LIGHT : ThemeProjectCreator.Palette.DARK, ThemeProjectCreator.KeyboardTemplate.values()[layout.getSelectedItemPosition()]).validated();
                showCreateProjectPreview(spec);
            } catch (Exception error) { setPendingCreateSpec(null); workspace.setStatus("新项目校验失败:" + safeErrorMessage(error)); Toast.makeText(this, safeErrorMessage(error), Toast.LENGTH_LONG).show(); }
        }).show();
    }

    private void showExampleTemplates() {
        String[] templates = {"复制内置默认主题(完整只读副本)", "浅色 · 行布局键盘", "深色 · 行布局键盘", "深色 · 弹性盒数字键盘", "浅色 · 分页符号键盘"};
        new android.app.AlertDialog.Builder(this).setTitle("示例模板").setMessage("选择后仍可修改主题元数据、样式标识和键盘标识。所有模板仅包含静态 Lua,不会执行脚本或回调。").setItems(templates, (dialog, which) -> {
            if (which == 0) { prepareBuiltInDefaultTemplate(); return; }
            int templateIndex = which - 1; ThemeProjectCreator.Palette palette = templateIndex == 0 || templateIndex == 3 ? ThemeProjectCreator.Palette.LIGHT : ThemeProjectCreator.Palette.DARK;
            ThemeProjectCreator.KeyboardTemplate keyboard = templateIndex == 2 ? ThemeProjectCreator.KeyboardTemplate.FLEX_BOX : templateIndex == 3 ? ThemeProjectCreator.KeyboardTemplate.KEY_MAPS : ThemeProjectCreator.KeyboardTemplate.ROWS;
            String suffix = templateIndex == 0 ? "light_rows" : templateIndex == 1 ? "dark_rows" : templateIndex == 2 ? "dark_flex" : "light_symbols";
            showNewProjectWizard(new ThemeProjectCreator.Spec("example_" + suffix, templates[which], "Trime2 主题编辑器", palette == ThemeProjectCreator.Palette.LIGHT ? "light" : "dark", keyboard == ThemeProjectCreator.KeyboardTemplate.KEY_MAPS ? "symbols" : "default", palette, keyboard));
        }).setNegativeButton("取消", null).show();
    }

    private void prepareBuiltInDefaultTemplate() {
        File root = new File(getCacheDir(), "theme-editor-share/built-in-template-" + System.nanoTime());
        try {
            copyAssetDirectory("themes/default", root, 0, new DocumentCopyBudget()); ThemeProject candidate = ThemeProject.Companion.discover(root); ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(candidate, new ThemeLuaParser());
            java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics = ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry()); int errors = 0; for (com.osfans.trime.editor.core.ThemeDiagnostic item : diagnostics) if (item.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) errors++;
            java.util.Map<String, Long> manifest = fileManifest(root); long bytes = 0; for (long size : manifest.values()) bytes += size; String name = "default_theme_copy";
            String message = "内置来源:assets/themes/default(只读)\n目标子目录:" + name + "\n文件:" + manifest.size() + " 个 / " + bytes + " 字节\n样式:" + candidate.getStyles().size() + " 个 · 键盘:" + candidate.getKeyboards().size() + " 个 · 资源:" + candidate.getResources().size() + " 个\n静态错误:" + errors + "\n脚本仅复制,编辑器绝不执行。复制后按 SHA-256 回读校验。";
            new android.app.AlertDialog.Builder(this).setTitle("复制内置默认主题").setMessage(message).setNegativeButton("取消", (dialog, which) -> deleteDirectory(root)).setPositiveButton("选择目标目录", (dialog, which) -> { setPendingBuiltInTemplate(root, name); builtInTemplateTreeLauncher.launch(projectTreeIntent()); }).setOnCancelListener(dialog -> deleteDirectory(root)).show();
        } catch (Exception error) { deleteDirectory(root); workspace.setStatus("无法读取内置默认主题:" + safeErrorMessage(error)); }
    }

    private void copyAssetDirectory(String assetPath, File destination, int depth, DocumentCopyBudget budget) throws IOException {
        if (depth > 12) throw new IOException("内置模板目录层级超过限制"); String[] children = getAssets().list(assetPath); if (children == null) throw new IOException("内置模板目录不可读");
        if (children.length == 0) {
            if (++budget.files > 500) throw new IOException("内置模板文件数量超过限制"); if (!destination.getParentFile().exists() && !destination.getParentFile().mkdirs()) throw new IOException("无法创建模板缓存目录");
            try (InputStream input = getAssets().open(assetPath); FileOutputStream output = new FileOutputStream(destination)) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) { budget.bytes += count; if (budget.bytes > 64L * 1024 * 1024) throw new IOException("内置模板超过 64 MiB 限制"); output.write(buffer, 0, count); } output.getFD().sync(); } return;
        }
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("无法创建模板缓存目录");
        for (String child : children) { if (child == null || child.isEmpty() || child.contains("/") || child.contains("\\") || child.equals(".") || child.equals("..")) throw new IOException("内置模板包含非法路径"); copyAssetDirectory(assetPath + "/" + child, new File(destination, child), depth + 1, budget); }
    }

    private void showCreateProjectPreview(ThemeProjectCreator.Spec spec) {
        String layout = spec.getKeyboardTemplate() == ThemeProjectCreator.KeyboardTemplate.ROWS ? "行布局(rows)" : spec.getKeyboardTemplate() == ThemeProjectCreator.KeyboardTemplate.FLEX_BOX ? "弹性盒(flex_box)" : spec.getKeyboardTemplate() == ThemeProjectCreator.KeyboardTemplate.KEY_MAPS ? "按键映射(key_maps)" : "绝对定位(keys)";
        String message = "目标子目录:" + spec.getDirectoryName() + "\n主题:" + spec.getThemeName() + "\n作者:" + spec.getAuthor() + "\n默认样式:" + spec.getStyleName() + " · " + (spec.getPalette() == ThemeProjectCreator.Palette.LIGHT ? "浅色" : "深色") + "\n默认键盘:" + spec.getKeyboardName() + " · " + layout + "\n\n将创建 main.lua、样式、键盘及 images/fonts/sounds/scripts 目录。创建后会重新读取并静态校验。";
        new android.app.AlertDialog.Builder(this).setTitle("创建预览").setMessage(message).setNegativeButton("返回修改", (dialog, which) -> showNewProjectWizard(spec)).setPositiveButton("选择目标目录", (dialog, which) -> { setPendingCreateSpec(spec); createProjectTreeLauncher.launch(projectTreeIntent()); }).show();
    }

    private Intent projectTreeIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        String value = editorPreferences().getString("default_project_uri", null);
        if (value != null && android.os.Build.VERSION.SDK_INT >= 26) intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(value));
        return intent;
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
        } catch (Exception error) { if (created != null) created.delete(); workspace.setStatus("项目创建失败:" + safeErrorMessage(error)); Toast.makeText(this, "无法创建主题项目", Toast.LENGTH_LONG).show(); }
        finally { deleteDirectory(draft); }
    }

    private static final class RecentProjectEntry {
        final String uri; final String name; final String prefix;
        RecentProjectEntry(String uri, String name, String prefix) { this.uri = uri; this.name = name; this.prefix = prefix; }
    }

    private java.util.ArrayList<RecentProjectEntry> recentProjects() {
        android.content.SharedPreferences preferences = getPreferences(MODE_PRIVATE); java.util.ArrayList<RecentProjectEntry> result = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) { String uri = preferences.getString("recent_" + i + "_uri", null); if (uri == null) continue; result.add(new RecentProjectEntry(uri, preferences.getString("recent_" + i + "_name", "主题项目"), preferences.getString("recent_" + i + "_prefix", null))); }
        if (result.isEmpty()) { String uri = preferences.getString("recent_uri", null); if (uri != null) result.add(new RecentProjectEntry(uri, preferences.getString("recent_name", "主题项目"), preferences.getString("recent_prefix", null))); }
        return result;
    }

    private void rememberRecentProject(Uri uri, String name, String prefix) {
        java.util.ArrayList<RecentProjectEntry> entries = recentProjects(); String value = uri.toString();
        for (int i = entries.size() - 1; i >= 0; i--) { RecentProjectEntry entry = entries.get(i); if (entry.uri.equals(value) && java.util.Objects.equals(entry.prefix, prefix)) entries.remove(i); }
        entries.add(0, new RecentProjectEntry(value, name == null ? "主题项目" : name, prefix));
        android.content.SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit().remove("recent_uri").remove("recent_name").remove("recent_prefix");
        for (int i = 0; i < 8; i++) { edit.remove("recent_" + i + "_uri").remove("recent_" + i + "_name").remove("recent_" + i + "_prefix"); if (i < entries.size()) { RecentProjectEntry entry = entries.get(i); edit.putString("recent_" + i + "_uri", entry.uri).putString("recent_" + i + "_name", entry.name); if (entry.prefix != null) edit.putString("recent_" + i + "_prefix", entry.prefix); } }
        edit.apply();
    }

    private void clearRecentProjects() {
        android.content.SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit().remove("recent_uri").remove("recent_name").remove("recent_prefix");
        for (int i = 0; i < 8; i++) edit.remove("recent_" + i + "_uri").remove("recent_" + i + "_name").remove("recent_" + i + "_prefix"); edit.apply();
        workspace.setStatus("最近项目记录已清空;不会删除任何主题文件");
    }

    private void showRecentProjects() {
        java.util.ArrayList<RecentProjectEntry> entries = recentProjects();
        if (entries.isEmpty()) { new android.app.AlertDialog.Builder(this).setTitle("最近项目").setMessage("没有最近打开的 SAF 项目").setPositiveButton("关闭", null).show(); return; }
        String[] labels = new String[entries.size()]; for (int i = 0; i < entries.size(); i++) labels[i] = entries.get(i).name + (entries.get(i).prefix == null ? "" : " / " + entries.get(i).prefix);
        new android.app.AlertDialog.Builder(this).setTitle("最近项目").setItems(labels, (dialog, which) -> showRecentProjectActions(entries.get(which))).setNegativeButton("关闭", null).setNeutralButton("清空记录", (dialog, which) -> clearRecentProjects()).show();
    }

    private void showRecentProjectActions(RecentProjectEntry entry) {
        String[] actions = {"打开项目", "从最近记录移除", "重新选择目录并授权"};
        new android.app.AlertDialog.Builder(this).setTitle(entry.name).setItems(actions, (dialog, which) -> {
            if (which == 0) requestWorkspaceReplacement("打开最近项目", () -> { try { loadRecentProject(Uri.parse(entry.uri), entry.prefix, entry.name); } catch (Exception error) { workspace.setStatus("最近项目的访问权限已失效,请重新选择目录授权"); } });
            else if (which == 1) { removeRecentProject(entry.uri, entry.prefix); workspace.setStatus("已移除最近项目记录;主题文件未删除"); }
            else requestWorkspaceReplacement("重新授权并打开主题目录", () -> openTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)));
        }).setNegativeButton("关闭", null).show();
    }

    private void removeRecentProject(String uri, String prefix) {
        java.util.ArrayList<RecentProjectEntry> entries = recentProjects(); for (int i = entries.size() - 1; i >= 0; i--) { RecentProjectEntry entry = entries.get(i); if (entry.uri.equals(uri) && java.util.Objects.equals(entry.prefix, prefix)) entries.remove(i); }
        android.content.SharedPreferences.Editor edit = getPreferences(MODE_PRIVATE).edit(); for (int i = 0; i < 8; i++) edit.remove("recent_" + i + "_uri").remove("recent_" + i + "_name").remove("recent_" + i + "_prefix");
        for (int i = 0; i < Math.min(8, entries.size()); i++) { RecentProjectEntry entry = entries.get(i); edit.putString("recent_" + i + "_uri", entry.uri).putString("recent_" + i + "_name", entry.name); if (entry.prefix != null) edit.putString("recent_" + i + "_prefix", entry.prefix); } edit.apply();
    }

    private void showProjectActions() {
        if (project == null) return;
        boolean parentAuthorized = importedProjectTreeUri != null && importedProjectTreePrefix != null && !importedProjectTreePrefix.isEmpty();
        String[] actions = {"复制项目", "重命名项目目录", "关闭项目", "删除项目"};
        String message = "来源:" + (parentAuthorized ? "已授权父目录,可进行目录级事务" : importedProjectTreeUri != null ? "仅授权当前项目根目录,不能安全重命名/复制/删除" : "ZIP、单文件或内部目录副本,目录级管理只读") + "\n关闭前会检查未保存更改。删除仅在明确确认项目名称后执行。";
        new android.app.AlertDialog.Builder(this).setTitle("项目操作").setMessage(message).setItems(actions, (dialog, which) -> {
            if (which == 0) { if (parentAuthorized) promptDuplicateProject(); else workspace.setStatus("复制项目需要已授权父目录;可使用完整主题目录导出代替"); }
            else if (which == 1) { if (parentAuthorized) promptRenameProjectDirectory(); else workspace.setStatus("重命名项目需要已授权父目录;当前来源保持只读"); }
            else if (which == 2) requestWorkspaceReplacement("关闭当前项目", this::closeCurrentProject);
            else if (parentAuthorized) confirmDeleteProject(); else workspace.setStatus("删除项目需要已授权父目录;编辑器不会删除来源不明确的文件");
        }).setNegativeButton("关闭", null).show();
    }

    private DocumentFile authorizedProjectParent() throws IOException {
        if (importedProjectTreeUri == null || importedProjectTreePrefix == null || importedProjectTreePrefix.isEmpty()) throw new IOException("没有项目父目录授权");
        DocumentFile parent = DocumentFile.fromTreeUri(this, importedProjectTreeUri); if (parent == null || !parent.isDirectory() || !parent.canWrite()) throw new IOException("项目父目录授权不可用"); return parent;
    }

    private void promptDuplicateProject() {
        if (!ensureAssetWritable()) return; EditText name = new EditText(this); name.setText(importedProjectTreePrefix + "_copy"); name.setSingleLine(true);
        new android.app.AlertDialog.Builder(this).setTitle("复制主题项目").setMessage("复制到同一已授权父目录。复制后按文件数、大小和 SHA-256 校验,不会修改源项目。").setView(name).setNegativeButton("取消", null).setPositiveButton("复制", (dialog, which) -> duplicateProject(name.getText().toString().trim())).show();
    }

    private void duplicateProject(String name) {
        DocumentFile created = null;
        try {
            validateProjectDirectoryName(name); DocumentFile parent = authorizedProjectParent(), source = parent.findFile(importedProjectTreePrefix); if (source == null || !source.isDirectory()) throw new IOException("源项目目录不可用"); if (parent.findFile(name) != null) throw new IOException("同名项目目录已存在");
            created = parent.createDirectory(name); if (created == null) throw new IOException("无法创建项目副本目录"); copyDocumentToDocument(source, created);
            if (!documentManifest(source).equals(documentManifest(created)) || !documentHashManifest(source).equals(documentHashManifest(created))) throw new IOException("项目副本回读校验不一致");
            rememberRecentProject(importedProjectTreeUri, name, name); workspace.setStatus("项目已复制并校验:" + name + ";源项目未修改");
        } catch (Exception error) { if (created != null) created.delete(); workspace.setStatus("项目复制失败:" + safeErrorMessage(error)); }
    }

    private void promptRenameProjectDirectory() {
        if (!ensureAssetWritable()) return; EditText name = new EditText(this); name.setText(importedProjectTreePrefix); name.setSingleLine(true);
        new android.app.AlertDialog.Builder(this).setTitle("重命名项目目录").setMessage("重命名后会更新当前会话和最近项目锁标识;主题内部 name 字段不会自动更改。").setView(name).setNegativeButton("取消", null).setPositiveButton("重命名", (dialog, which) -> renameProjectDirectory(name.getText().toString().trim())).show();
    }

    private void renameProjectDirectory(String name) {
        DocumentFile parent = null, renamed = null; String old = importedProjectTreePrefix;
        try {
            validateProjectDirectoryName(name); if (name.equals(old)) return; parent = authorizedProjectParent(); if (parent.findFile(name) != null) throw new IOException("同名项目目录已存在");
            DocumentFile source = parent.findFile(old); if (source == null || !source.isDirectory() || !source.renameTo(name)) throw new IOException("SAF 提供方拒绝重命名项目目录"); renamed = parent.findFile(name); if (renamed == null || !renamed.isDirectory()) throw new IOException("重命名后项目目录不可见");
            removeRecentProject(importedProjectTreeUri.toString(), old); importedProjectTreePrefix = name; importedProjectUri = importedProjectTreeUri; projectDisplayName = name; rememberRecentProject(importedProjectTreeUri, name, name); claimSession(sessionIdentity()); captureProjectState(); workspace.setStatus("项目目录已重命名为 " + name + ";编辑缓存与锁标识已更新");
        } catch (Exception error) {
            if (renamed != null) try { if (renamed.renameTo(old)) { importedProjectTreePrefix = old; projectDisplayName = old; claimSession(sessionIdentity()); } else error.addSuppressed(new IOException("项目目录重命名回滚失败")); } catch (Exception rollback) { error.addSuppressed(rollback); }
            workspace.setStatus("项目重命名失败:" + safeErrorMessage(error));
        }
    }

    private static void validateProjectDirectoryName(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_ -]{1,64}") || name.trim().isEmpty() || name.equals(".") || name.equals("..")) throw new IOException("项目目录名只能包含英文字母、数字、空格、下划线和连字符");
    }

    private void confirmDeleteProject() {
        if (!ensureAssetWritable()) return; EditText confirmation = new EditText(this); confirmation.setHint("输入项目目录名确认"); confirmation.setSingleLine(true); String expected = importedProjectTreePrefix;
        new android.app.AlertDialog.Builder(this).setTitle("永久删除主题项目?").setMessage("将删除已授权父目录中的 “" + expected + "”。建议先导出完整主题 ZIP。此操作不删除编辑器外的其他目录,但删除后无法由编辑器撤销。").setView(confirmation).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> {
            if (!expected.equals(confirmation.getText().toString())) { workspace.setStatus("项目名确认不匹配,删除已取消"); return; }
            try { DocumentFile parent = authorizedProjectParent(), target = parent.findFile(expected); if (target == null || !target.isDirectory() || !target.delete() || parent.findFile(expected) != null) throw new IOException("SAF 项目目录删除失败"); removeRecentProject(importedProjectTreeUri.toString(), expected); closeCurrentProject(); workspace.setStatus("项目目录已删除:" + expected); }
            catch (Exception error) { workspace.setStatus("项目删除失败:" + safeErrorMessage(error)); }
        }).show();
    }

    private void closeCurrentProject() {
        File previousRoot = project == null ? null : project.getRoot(); boolean cacheProject = previousRoot != null && isEditorCachePath(previousRoot);
        releaseSession(); project = null; projectSnapshot = null; repository = null; importedProjectUri = null; importedProjectTreeUri = null; importedProjectTreePrefix = null; openedImportedFingerprint = null; openedFingerprint = null; openedSourceFingerprint = null; currentUri = null; projectDisplayName = null; recoveryPrompted = false; clearMigrationHistory(); deleteRecoveryDraft(); deleteCodeBuffer();
        editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document()); layoutEditable = false; viewModel.setCurrentUri(null); viewModel.markLoaded(null, null); captureProjectState(); workspace.setModel(new ThemeEditorModel()); restoreWorkspaceState(); workspace.markSaved("项目已关闭;请选择最近项目、新建、目录或 ZIP"); invalidateOptionsMenu(); if (cacheProject) deleteDirectory(previousRoot);
    }

    private boolean isEditorCachePath(File file) {
        try { File cache = getCacheDir().getCanonicalFile(), target = file.getCanonicalFile(); return target.getCanonicalPath().startsWith(cache.getCanonicalPath() + File.separator); } catch (IOException ignored) { return false; }
    }

    private void showThemeSettingsState() { viewModel.setCurrentPage("theme_settings"); }
    private void showThemeSettings() {
        showThemeSettingsState();
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
                if (!ensureAssetWritable()) return; try { String nextStyle = (String) style.getSelectedItem(), nextKeyboard = (String) keyboard.getSelectedItem(); mutateMainWithMirror(() -> ThemeProjectMutator.updateMetadata(project, name.getText().toString(), author.getText().toString(), nextStyle, nextKeyboard)); projectDisplayName = name.getText().toString().trim(); workspace.setStatus("主题设置已更新"); } catch (Exception error) { workspace.setStatus("主题设置更新失败:" + safeErrorMessage(error)); }
            }).show();
        } catch (Exception error) { workspace.setStatus("无法加载主题设置:" + safeErrorMessage(error)); }
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
                try { java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>(); for (java.util.Map.Entry<String, EditText> input : inputs.entrySet()) values.put(input.getKey(), missing.get(input.getKey()).isChecked() ? null : input.getValue().getText().toString()); mutateMainPreset(latest -> { if (!ThemeSaveCoordinator.Companion.fingerprint(source).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("打开操作标签后 main.lua 已变化,请重新打开编辑器"); return ThemePresetEvents.updateActionLabels(latest, values); }, "已更新操作标签(action_labels)"); String selected = actionIds[previewAction.getSelectedItemPosition()]; ThemeEditorModel previewModel = workspace.getModel(); previewModel.editorActionLabel = values.get(selected) == null ? "" : values.get(selected); workspace.setModelKeepingHistory(previewModel); workspace.setStatus("已更新操作标签(action_labels)并预览 " + selected + ";未执行任何操作"); }
                catch (Exception error) { workspace.setStatus("操作标签更新被阻止:" + safeErrorMessage(error)); }
            }).show();
        } catch (Exception error) { workspace.setStatus("操作标签只能通过代码编辑:" + safeErrorMessage(error)); }
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
        } catch (Exception error) { workspace.setStatus("预设按键(preset_keys)只能通过代码编辑:" + safeErrorMessage(error)); }
    }

    private static String presetSummary(ThemePresetEvents.Event event) {
        if (!event.getLabel().isEmpty()) return event.getLabel(); if (!event.getCommand().isEmpty()) return "command=" + event.getCommand(); if (!event.getSend().isEmpty()) return "send=" + event.getSend(); if (!event.getText().isEmpty()) return "文本(text)"; if (!event.getCommit().isEmpty()) return "上屏文本(commit)"; return "空事件";
    }

    private void showPresetEventActions(ThemePresetEvents.Event event) {
        try {
            PresetUsage usage = collectPresetUsage(event.getId()); String details = "静态引用:" + usage.total + (usage.uncertain.isEmpty() ? "" : "\n存在不确定引用的原始 Lua 文件: " + android.text.TextUtils.join(", ", usage.uncertain)) + "\n执行风险: " + (event.getRisky() ? "命令(command)/脚本(script)会保留但绝不执行" : "预览仅显示摘要");
            String[] actions = {"编辑字段", "复制", "重命名并替换引用", "无引用时删除", "查看摘要"};
            new android.app.AlertDialog.Builder(this).setTitle(event.getId()).setMessage(details).setItems(actions, (dialog, which) -> { if (which == 0) showPresetEventEditor(event); else if (which == 1) promptCopyPreset(event); else if (which == 2) promptRenamePreset(event, usage); else if (which == 3) confirmDeletePreset(event, usage); }).setNegativeButton("关闭", null).show();
        } catch (Exception error) { workspace.setStatus("预设引用分析失败:" + safeErrorMessage(error)); }
    }

    private static String formatEventStates(java.util.List<String> values) { java.util.ArrayList<String> lines = new java.util.ArrayList<>(); for (String value : values) lines.add(value.isEmpty() ? "\\0" : value.replace("\\", "\\\\").replace("\n", "\\n")); return android.text.TextUtils.join("\n", lines); }
    private static java.util.ArrayList<String> parseEventStates(String source) { java.util.ArrayList<String> result = new java.util.ArrayList<>(); if (source.isEmpty()) return result; for (String line : source.split("\n", -1)) { if (line.equals("\\0")) { result.add(""); continue; } StringBuilder value = new StringBuilder(); for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '\\' && i + 1 < line.length()) { char next = line.charAt(++i); value.append(next == 'n' ? '\n' : next); } else value.append(c); } result.add(value.toString()); } return result; }

    private void showPresetEventEditor(ThemePresetEvents.Event event) {
        if (!ensureAssetWritable()) return; final String openedSource; ThemePresetEvents.Event initial;
        try { openedSource = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (event == null) initial = new ThemePresetEvents.Event("Preset_new", "", "", "", "", "", "", "", "", "", "", java.util.Collections.emptyList(), "", false, false, true, null); else { ThemePresetEvents.Event current = null; for (ThemePresetEvents.Event candidate : ThemePresetEvents.list(openedSource)) if (candidate.getId().equals(event.getId())) { current = candidate; break; } if (current == null) throw new IOException("预设已变化或已删除,请重新打开管理器"); initial = current; } }
        catch (Exception error) { workspace.setStatus("预设编辑被阻止:" + safeErrorMessage(error)); return; }
        final ThemePresetEvents.Event openedEvent = initial;
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        EditText id = simpleField(fields, "预设标识", initial.getId()); id.setEnabled(event == null); EditText label = simpleField(fields, "标签(label)", initial.getLabel()); EditText send = simpleField(fields, "发送按键(send)", initial.getSend()); EditText text = simpleField(fields, "文本(text)", initial.getText()); EditText commit = simpleField(fields, "上屏文本(commit)", initial.getCommit()); EditText command = simpleField(fields, "命令(command,保留但绝不执行)", initial.getCommand()); EditText option = simpleField(fields, "选项(option)", initial.getOption()); EditText select = simpleField(fields, "选择(select)", initial.getSelect()); EditText toggle = simpleField(fields, "切换(toggle)", initial.getToggle()); EditText preview = simpleField(fields, "预览(preview)", initial.getPreview()); EditText description = simpleField(fields, "说明(description)", initial.getDescription()); EditText states = simpleField(fields, "状态(states):每行一个;\\0 表示空值,\\n 表示内嵌换行", formatEventStates(initial.getStates())); states.setSingleLine(false); states.setMinLines(3); EditText shiftLock = simpleField(fields, "Shift 锁定(shift_lock):click/double/long", initial.getShiftLock()); EditText index = simpleField(fields, "索引(index,保留;预设引用不使用)", initial.getIndex() == null ? "" : trim(initial.getIndex().floatValue())); index.setEnabled(false);
        android.widget.CheckBox repeatable = new android.widget.CheckBox(this); repeatable.setText("可重复(repeatable)"); repeatable.setChecked(initial.getRepeatable()); fields.addView(repeatable); android.widget.CheckBox sticky = new android.widget.CheckBox(this); sticky.setText("保持(sticky)"); sticky.setChecked(initial.getSticky()); fields.addView(sticky); android.widget.CheckBox functional = new android.widget.CheckBox(this); functional.setText("功能键(functional)"); functional.setChecked(initial.getFunctional()); fields.addView(functional);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle(event == null ? "新建预设事件" : "编辑预设事件").setMessage("仅编辑静态字段。“应用”绝不会发送按键、上屏文本,也不会调用命令、脚本、Intent 或回调。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> {
            try { Double nextIndex = openedEvent.getIndex(); java.util.ArrayList<String> nextStates = parseEventStates(states.getText().toString()); ThemePresetEvents.Event next = new ThemePresetEvents.Event(id.getText().toString().trim(), send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), nextStates, shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); mutateMainPreset(source -> { if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(source))) throw new IOException("打开预设编辑器后 main.lua 已变化,请重新打开"); return ThemePresetEvents.put(source, next, event != null); }, "已更新预设 " + next.getId() + ";未执行任何操作"); }
            catch (Exception error) { workspace.setStatus("预设更新被阻止:" + safeErrorMessage(error)); }
        }).show();
    }

    private interface MainSourceMutation { String apply(String source) throws Exception; }
    private void mutateMainPreset(MainSourceMutation mutation, String success) throws Exception { String source = new String(readFileBytes(project.getMainFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(); java.util.LinkedHashMap<File, String> originals = new java.util.LinkedHashMap<>(); changes.put(project.getMainFile(), mutation.apply(source)); originals.put(project.getMainFile(), source); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success); }

    private void promptCopyPreset(ThemePresetEvents.Event event) { LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "副本标识", event.getId() + "_copy"); new android.app.AlertDialog.Builder(this).setTitle("复制预设事件").setView(fields).setNegativeButton("取消", null).setPositiveButton("复制", (dialog, which) -> { try { mutateMainPreset(source -> ThemePresetEvents.copy(source, event.getId(), id.getText().toString().trim()), "已复制预设事件"); } catch (Exception error) { workspace.setStatus("预设复制被阻止:" + safeErrorMessage(error)); } }).show(); }

    private void promptRenamePreset(ThemePresetEvents.Event event, PresetUsage usage) { if (!usage.uncertain.isEmpty()) { workspace.setStatus("重命名被原始 Lua 引用阻止:" + android.text.TextUtils.join(", ", usage.uncertain)); return; } LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "新预设标识", event.getId() + "_renamed"); new android.app.AlertDialog.Builder(this).setTitle("重命名预设并替换引用?").setMessage("将替换 " + usage.total + " 个静态引用,涉及 " + usage.references.size() + " 个文件。不会执行任何事件。").setView(fields).setNegativeButton("取消", null).setPositiveButton("重命名", (dialog, which) -> renamePresetTransaction(event.getId(), id.getText().toString().trim())).show(); }

    private void renamePresetTransaction(String oldId, String newId) {
        try {
            PresetUsage usage = collectPresetUsage(oldId); if (!usage.uncertain.isEmpty()) throw new IOException("复核后原始 Lua 引用已变化");
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(usage.originals); String main = originals.get(project.getMainFile()); if (main == null) throw new IOException("项目引用快照中缺少 main.lua"); changes.put(project.getMainFile(), ThemePresetEvents.renameDefinition(main, oldId, newId)); int count = 0;
            for (File file : usage.references.keySet()) { String original = originals.get(file); if (original == null) throw new IOException("项目快照中的引用源已消失:" + relativeProjectFile(file)); String base = file.equals(project.getMainFile()) ? changes.get(file) : original; ThemePresetEvents.ReferenceUpdate update = ThemePresetEvents.replaceReferences(base, oldId, newId); changes.put(file, update.getSource()); count += update.getCount(); }
            // Include unchanged Lua files and the manifest so a previously clean/new file cannot gain a reference between scan and commit.
            applyProjectSourceTransaction(changes, originals, usage.originals.keySet()); workspace.setStatus("已重命名预设并替换 " + count + " 个静态引用");
        } catch (Exception error) { workspace.setStatus("预设重命名被阻止:" + safeErrorMessage(error)); }
    }

    private void confirmDeletePreset(ThemePresetEvents.Event event, PresetUsage usage) { if (usage.total > 0 || !usage.uncertain.isEmpty()) { workspace.setStatus("预设删除被阻止:" + usage.total + " 个引用或无法确定的原始 Lua"); return; } new android.app.AlertDialog.Builder(this).setTitle("删除无引用的预设?").setMessage(event.getId()).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> { try { PresetUsage current = collectPresetUsage(event.getId()); if (current.total > 0 || !current.uncertain.isEmpty()) throw new IOException("复核后引用已变化"); String main = current.originals.get(project.getMainFile()); if (main == null) throw new IOException("项目引用快照中缺少 main.lua"); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(); changes.put(project.getMainFile(), ThemePresetEvents.deleteDefinition(main, event.getId())); applyProjectSourceTransaction(changes, current.originals, current.originals.keySet()); workspace.setStatus("已删除无引用的预设 " + event.getId()); } catch (Exception error) { workspace.setStatus("预设删除被阻止:" + safeErrorMessage(error)); } }).show(); }

    private PresetUsage collectPresetUsage(String id) throws IOException {
        PresetUsage usage = new PresetUsage(); java.util.ArrayList<File> files = new java.util.ArrayList<>(); collectProjectLuaFiles(project.getRoot(), project.getRoot().getCanonicalPath(), new java.util.HashSet<>(), files);
        for (File file : files) { try { String source = new String(readFileBytes(file, 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); usage.originals.put(file, source); int count = ThemePresetEvents.references(source, id); if (count > 0) { usage.references.put(file, count); usage.total += count; } if (ThemePresetEvents.hasUncertainReference(source, id)) usage.uncertain.add(relativeProjectFile(file)); } catch (Exception error) { usage.uncertain.add(relativeProjectFile(file)); } }
        return usage;
    }

    private void collectProjectLuaFiles(File directory, String root, java.util.Set<String> visited, java.util.List<File> result) throws IOException {
        String canonical = directory.getCanonicalPath(); if (!canonical.equals(root) && !canonical.startsWith(root + File.separator)) return; if (!visited.add(canonical)) return; File[] children = directory.listFiles(); if (children == null) return;
        for (File child : children) { String path = child.getCanonicalPath(); if (!child.getAbsolutePath().equals(path) || !path.startsWith(root + File.separator)) continue; if (child.isDirectory()) collectProjectLuaFiles(child, root, visited, result); else if (child.isFile() && child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lua")) result.add(child); }
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
            catch (Exception error) { workspace.setStatus("键盘创建失败:" + safeErrorMessage(error)); }
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
                } catch (Exception error) { workspace.setStatus("键盘字段更新失败:" + safeErrorMessage(error)); }
            }).show();
        } catch (Exception error) { workspace.setStatus("无法读取键盘字段:" + safeErrorMessage(error)); }
    }

    private static String emptyToNull(EditText field) { String value = field.getText().toString().trim(); return value.isEmpty() ? null : value; }
    private static Double optionalPositiveDouble(EditText field, String name) {
        String value = field.getText().toString().trim(); if (value.isEmpty()) return null;
        double parsed = Double.parseDouble(value); if (!(parsed > 0) || Double.isInfinite(parsed) || Double.isNaN(parsed)) throw new IllegalArgumentException(name + " 必须为正数"); return parsed;
    }

    private void promptCopyKeyboard(ThemeProjectFile file) { promptKeyboardId("复制键盘", file.getName() + "_copy", id -> { ThemeProjectFile created = ThemeProjectMutator.copyKeyboard(project, file, id); try { mirrorCreatedProjectFile(created.getFile()); } catch (Exception error) { created.getFile().delete(); throw error; } refreshProjectAfterAssetMutation(); workspace.setStatus("已复制键盘 " + id); }); }
    private void promptRenameKeyboard(ThemeProjectFile file) { if (isCurrentProjectFile(file)) { workspace.setStatus("重命名当前键盘前请先打开另一个文件"); return; } promptKeyboardId("重命名键盘", file.getName(), id -> { File old = file.getFile(); ThemeProjectFile renamed = ThemeProjectMutator.renameKeyboard(project, file, id); try { mirrorRenamedProjectFile(old, renamed.getFile()); } catch (Exception error) { renamed.getFile().renameTo(old); throw error; } refreshProjectAfterAssetMutation(); requestProjectFileSwitch(project.keyboard(id)); workspace.setStatus("键盘已重命名为 " + id); }); }
    private interface KeyboardIdAction { void run(String id) throws Exception; }
    private void promptKeyboardId(String title, String initial, KeyboardIdAction action) { if (!ensureAssetWritable()) return; LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "键盘标识", initial); new android.app.AlertDialog.Builder(this).setTitle(title).setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { action.run(id.getText().toString().trim()); } catch (Exception error) { workspace.setStatus(title + " 失败:" + safeErrorMessage(error)); } }).show(); }

    private void setDefaultKeyboard(ThemeProjectFile file) { if (!ensureAssetWritable()) return; try { mutateMainWithMirror(() -> ThemeProjectMutator.setDefaultKeyboard(project, file.getName())); workspace.setStatus("默认键盘:" + file.getName()); } catch (Exception error) { workspace.setStatus("默认键盘更新失败:" + safeErrorMessage(error)); } }
    private void confirmDeleteKeyboard(ThemeProjectFile file) { if (!ensureAssetWritable()) return; if (isCurrentProjectFile(file)) { workspace.setStatus("删除当前键盘前请先打开另一个文件"); return; } new android.app.AlertDialog.Builder(this).setTitle("删除键盘?").setMessage(file.getName()).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> { try { ThemeProjectMutator.validateKeyboardDeletion(project, file); if (importedProjectTreeUri != null) deleteImportedProjectPath(file.getFile()); if (!file.getFile().delete()) throw new IOException("无法删除本地键盘缓存"); refreshProjectAfterAssetMutation(); workspace.setStatus("已删除键盘 " + file.getName()); } catch (Exception error) { workspace.setStatus("键盘删除被阻止:" + safeErrorMessage(error)); } }).show(); }

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
        new android.app.AlertDialog.Builder(this).setTitle(file.getName()).setItems(actions, (dialog, which) -> { if (which == 0) requestProjectFileSwitch(file); else if (which == 1) showStyleEntityManager(file); else if (which == 2) showToolbarKeyManager(file); else if (which == 3) showPanelComponentManager(file); else if (which == 4) promptStyleId("复制样式", file.getName() + "_copy", id -> { ThemeProjectFile created = ThemeProjectMutator.copyStyle(project, file, id); try { mirrorCreatedProjectDirectory(created.getFile().getParentFile()); } catch (Exception error) { deleteDirectory(created.getFile().getParentFile()); throw error; } refreshProjectAfterAssetMutation(); }); else if (which == 5) { if (isCurrentProjectFile(file)) { workspace.setStatus("重命名当前样式前请先打开另一个文件"); return; } promptStyleId("重命名样式", file.getName(), id -> { File old = file.getFile().getParentFile(); ThemeProjectFile renamed = ThemeProjectMutator.renameStyle(project, file, id); try { mirrorRenamedProjectDirectory(old, renamed.getFile().getParentFile()); } catch (Exception error) { renamed.getFile().getParentFile().renameTo(old); throw error; } refreshProjectAfterAssetMutation(); requestProjectFileSwitch(project.style(id)); }); } else if (which == 6) { if (!ensureAssetWritable()) return; try { mutateMainWithMirror(() -> ThemeProjectMutator.setDefaultStyle(project, file.getName())); workspace.setStatus("默认样式:" + file.getName()); } catch (Exception error) { workspace.setStatus("默认样式设置失败:" + safeErrorMessage(error)); } } else confirmDeleteStyle(file); }).setNegativeButton("关闭", null).show();
    }
    private void showPanelComponentManager(ThemeProjectFile styleFile) {
        if (!ensureAssetWritable()) return;
        try {
            String source = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            ThemePanelComponents.FilterBar filter = ThemePanelComponents.readCandidateFilter(source); ThemePanelComponents.Toolbar candidate = ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.CANDIDATE_EXPANDED); ThemePanelComponents.Toolbar symbol = ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.SYMBOL); ThemePanelComponents.Toolbar clipboard = ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.CLIPBOARD); ThemePanelComponents.TabBar symbolTab = ThemePanelComponents.readTabBar(source, ThemePanelComponents.Panel.SYMBOL); ThemePanelComponents.TabBar clipboardTab = ThemePanelComponents.readTabBar(source, ThemePanelComponents.Panel.CLIPBOARD);
            String[] labels = {
                    "候选过滤栏 — 显示(show)=" + filter.getShow() + ", 重力方向(gravity)=" + filter.getGravity(),
                    "展开候选工具栏 — " + panelToolbarSummary(candidate),
                    "符号工具栏 — " + panelToolbarSummary(symbol),
                    "符号标签栏 — " + panelTabSummary(symbolTab),
                    "剪贴板工具栏 — " + panelToolbarSummary(clipboard),
                    "剪贴板标签栏 — " + panelTabSummary(clipboardTab)
            };
            new android.app.AlertDialog.Builder(this).setTitle(styleFile.getName() + " 的面板组件").setMessage("面板工具栏数组只接受字符串。内置名称仅作静态预览,绝不会调用。").setItems(labels, (dialog, which) -> { if (which == 0) editCandidateFilter(styleFile, source, filter); else if (which == 1) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.CANDIDATE_EXPANDED, candidate); else if (which == 2) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.SYMBOL, symbol); else if (which == 3) editPanelTabBar(styleFile, source, ThemePanelComponents.Panel.SYMBOL, symbolTab); else if (which == 4) editPanelToolbar(styleFile, source, ThemePanelComponents.Panel.CLIPBOARD, clipboard); else editPanelTabBar(styleFile, source, ThemePanelComponents.Panel.CLIPBOARD, clipboardTab); }).setNegativeButton("关闭", null).setNeutralButton("打开样式源代码", (dialog, which) -> requestProjectFileSwitch(styleFile)).show();
        } catch (Exception error) { workspace.setStatus("面板组件管理被阻止:" + safeErrorMessage(error)); }
    }

    private static String panelToolbarSummary(ThemePanelComponents.Toolbar value) { return "重力方向(gravity)=" + value.getGravity() + ", 按键数(keys)=" + value.getKeys().size() + (value.getHeight() == null ? "" : ", 高度(height)=" + value.getHeight()) + (value.getInherited() ? " [继承根之后的字面覆盖]" : ""); }
    private static String panelTabSummary(ThemePanelComponents.TabBar value) { return "重力方向(gravity)=" + (value.getGravity() == null ? "运行时默认值" : value.getGravity()) + ", 高度(height)=" + (value.getHeight() == null ? "运行时默认值" : value.getHeight()); }

    private void editCandidateFilter(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.FilterBar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner show = nullableSpinner(fields, "show", current.getShowExplicit() ? Boolean.valueOf(current.getShow()) : null); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"left", "top", "right", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null);
        new android.app.AlertDialog.Builder(this).setTitle("候选过滤栏").setMessage("继承会移除字面字段,并恢复显示(show)=true、重力方向(gravity)=left 的运行时默认值。").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateCandidateFilter(source, nullableSpinnerBoolean(show), nullableSpinnerString(gravity)), "已更新候选过滤栏")).show();
    }

    private void editPanelToolbar(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.Panel panel, ThemePanelComponents.Toolbar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"left", "top", "right", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null); EditText height = null; android.widget.CheckBox inheritHeight = null;
        if (panel != ThemePanelComponents.Panel.CANDIDATE_EXPANDED) { inheritHeight = new android.widget.CheckBox(this); inheritHeight.setText("移除高度并使用运行时布局"); inheritHeight.setChecked(!current.getHeightExplicit()); fields.addView(inheritHeight); height = simpleField(fields, "高度(height,有限非负数)", current.getHeight() == null ? "" : current.getHeight().toString()); height.setEnabled(!inheritHeight.isChecked()); EditText target = height; inheritHeight.setOnCheckedChangeListener((button, checked) -> target.setEnabled(!checked)); }
        android.widget.CheckBox inheritKeys = new android.widget.CheckBox(this); inheritKeys.setText("移除按键并使用面板默认值"); inheritKeys.setChecked(!current.getKeysExplicit()); fields.addView(inheritKeys); EditText keys = simpleField(fields, "按键(keys):每行一个字面字符串", formatEventStates(current.getKeys())); keys.setSingleLine(false); keys.setMinLines(4); keys.setEnabled(!inheritKeys.isChecked()); inheritKeys.setOnCheckedChangeListener((button, checked) -> keys.setEnabled(!checked)); TextView defaults = new TextView(this); defaults.setText("缺少按键(keys)时回退为:" + android.text.TextUtils.join(", ", current.getKeys()) + "。此处不接受表或事件。"); fields.addView(defaults); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2)); final EditText heightField = height; final android.widget.CheckBox removeHeight = inheritHeight;
        new android.app.AlertDialog.Builder(this).setTitle(panel + " 工具栏(tool_bar)").setMessage("隐藏(hide)/上翻页(page_up)/下翻页(page_down)/字符过滤(char_filter)/撤销(undo)/退格(BackSpace)仅保留为静态名称,不会执行操作。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { Double nextHeight = panel == ThemePanelComponents.Panel.CANDIDATE_EXPANDED || removeHeight.isChecked() ? null : Double.valueOf(heightField.getText().toString().trim()); java.util.List<String> nextKeys = inheritKeys.isChecked() ? null : parseEventStates(keys.getText().toString()); mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateToolbar(source, panel, nullableSpinnerString(gravity), nextHeight, nextKeys), "已更新 " + panel + " 工具栏(tool_bar)"); } catch (Exception error) { workspace.setStatus("面板工具栏更新被阻止:" + safeErrorMessage(error)); } }).show();
    }

    private void editPanelTabBar(ThemeProjectFile styleFile, String openedSource, ThemePanelComponents.Panel panel, ThemePanelComponents.TabBar current) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); android.widget.Spinner gravity = nullableStringSpinner(fields, "gravity", new String[]{"top", "bottom"}, current.getGravityExplicit() ? current.getGravity() : null); android.widget.CheckBox inheritHeight = new android.widget.CheckBox(this); inheritHeight.setText("移除高度并使用运行时布局"); inheritHeight.setChecked(!current.getHeightExplicit()); fields.addView(inheritHeight); EditText height = simpleField(fields, "高度(height,有限非负数)", current.getHeight() == null ? "" : current.getHeight().toString()); height.setEnabled(!inheritHeight.isChecked()); inheritHeight.setOnCheckedChangeListener((button, checked) -> height.setEnabled(!checked));
        new android.app.AlertDialog.Builder(this).setTitle(panel + " 标签栏(tab_bar)").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { Double nextHeight = inheritHeight.isChecked() ? null : Double.valueOf(height.getText().toString().trim()); mutatePanelSource(styleFile, openedSource, source -> ThemePanelComponents.updateTabBar(source, panel, nullableSpinnerString(gravity), nextHeight), "已更新 " + panel + " 标签栏(tab_bar)"); } catch (Exception error) { workspace.setStatus("标签栏更新被阻止:" + safeErrorMessage(error)); } }).show();
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
    private void mutatePanelSource(ThemeProjectFile styleFile, String openedSource, PanelSourceMutation mutation, String success) { try { if (!ensureAssetWritable()) return; String latest = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("打开面板管理器后样式源代码已更改,请重新打开"); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), mutation.apply(latest)); originals.put(styleFile.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success); } catch (Exception error) { workspace.setStatus("面板组件更新被阻止:" + safeErrorMessage(error)); } }

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
        } catch (Exception error) { workspace.setStatus("工具栏按键管理被阻止:" + safeErrorMessage(error)); }
    }

    private static String toolbarItemSummary(ThemeToolbarKeys.Item item) {
        if (item.getSource() == ThemeToolbarKeys.Source.STRING) return "字符串/预设引用 — " + item.getLiteral();
        if (item.getSource() == ThemeToolbarKeys.Source.INLINE_EVENT) return "直接事件 — " + presetSummary(item.getEvent()) + (item.getRisky() ? " [保留执行源,编辑器绝不执行]" : "");
        if (item.getSource() == ThemeToolbarKeys.Source.SCHEMA_SWITCH) { ThemeToolbarKeys.SchemaSwitch value = item.getSchemaSwitch(); return "方案切换 — " + value.getName() + " (" + value.getOptions().size() + " 个选项)" + (item.getCompatibilityWarning() ? " [当前运行时忽略独立样式(style)]" : ""); }
        if (item.getSource() == ThemeToolbarKeys.Source.FULL_KEY) return "完整按键表 — 需要显式替换源码";
        return "原始 Lua — 仅源码编辑";
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
            } else workspace.setStatus(toolbarItemSummary(item) + ";方案切换样式已读取,但当前工具栏视图(ToolbarView)构造不会使用该独立样式");
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
        new android.app.AlertDialog.Builder(this).setTitle("工具栏直接事件").setMessage("仅编辑静态字段;应用时绝不会执行事件。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { String indexText = eventIndex.getText().toString().trim(); Double nextIndex = indexText.isEmpty() ? null : Double.valueOf(indexText); ThemePresetEvents.Event next = new ThemePresetEvents.Event("ToolbarKey", send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), parseEventStates(states.getText().toString()), shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.put(source, index, ThemeToolbarKeys.inlineEvent(next), append), "已更新工具栏直接事件;未执行任何操作"); } catch (Exception error) { workspace.setStatus("工具栏事件更新被阻止:" + safeErrorMessage(error)); } }).show();
    }

    private void editToolbarSchemaSwitch(ThemeProjectFile styleFile, String openedSource, int index, ThemeToolbarKeys.Item current, boolean append) {
        ThemeToolbarKeys.SchemaSwitch value = current != null && current.getSchemaSwitch() != null ? current.getSchemaSwitch() : new ThemeToolbarKeys.SchemaSwitch("ASCII 模式(ascii_mode)", java.util.Arrays.asList("ASCII 模式(ascii_mode)"), java.util.Arrays.asList("中", "A"), 0, null);
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText name = simpleField(fields, "名称(name)", value.getName()); EditText options = simpleField(fields, "选项(options):每行一个", formatEventStates(value.getOptions())); options.setSingleLine(false); options.setMinLines(2); EditText states = simpleField(fields, "状态(states):每行一个", formatEventStates(value.getStates())); states.setSingleLine(false); states.setMinLines(2); EditText reset = simpleField(fields, "重置值(reset,32 位整数)", Integer.toString(value.getReset())); EditText style = simpleField(fields, "样式(style,仅兼容;ToolbarView 使用 toolbar.key)", value.getStyle() == null ? "" : value.getStyle()); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("工具栏方案切换").setMessage("仅静态预览。应用时不会切换选项、方案、主题、样式或键盘,不会重启 Trime,也不会调用回调。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { int nextReset = Integer.parseInt(reset.getText().toString().trim()); ThemeToolbarKeys.Item next = ThemeToolbarKeys.schemaSwitch(name.getText().toString().trim(), parseEventStates(options.getText().toString()), parseEventStates(states.getText().toString()), nextReset, style.getText().toString().trim().isEmpty() ? null : style.getText().toString().trim()); mutateToolbarSource(styleFile, openedSource, source -> ThemeToolbarKeys.put(source, index, next, append), "已更新工具栏方案切换;未执行任何操作"); } catch (Exception error) { workspace.setStatus("方案切换更新被阻止:" + safeErrorMessage(error)); } }).show();
    }

    private interface ToolbarSourceMutation { String apply(String source) throws Exception; }
    private void mutateToolbarSource(ThemeProjectFile styleFile, String openedSource, ToolbarSourceMutation mutation, String success) {
        try {
            if (!ensureAssetWritable()) return; String latest = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (!ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(latest))) throw new IOException("打开工具栏管理器后样式源码已变化,请重新打开");
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), mutation.apply(latest)); originals.put(styleFile.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success);
        } catch (Exception error) { workspace.setStatus("工具栏更新被阻止:" + safeErrorMessage(error)); }
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
            for (int i = 0; i < entries.size(); i++) { ThemeStyleEntities.Entry entry = entries.get(i); labels[i + 2] = entry.getId() + (entry.getCloneParent() == null ? "" : " ← " + entry.getCloneParent()) + (entry.getDynamic() ? " [仅代码]" : ""); }
            new android.app.AlertDialog.Builder(this).setTitle("样式实体——" + styleFile.getName()).setItems(labels, (dialog, which) -> { if (which == 0) promptCreateStyleEntity(styleFile); else if (which == 1) promptPasteEntityIntoStyle(styleFile); else showStyleEntityActions(styleFile, entries.get(which - 2)); }).setNegativeButton("关闭", null).show();
        } catch (Exception error) { workspace.setStatus("样式实体列表加载失败:" + safeErrorMessage(error)); }
    }

    private void showStyleEntityActions(ThemeProjectFile styleFile, ThemeStyleEntities.Entry entry) {
        try {
            EntityUsage usage = collectEntityUsage(styleFile, entry.getId()); String details = "实体:" + entry.getId() + (entry.getCloneParent() == null ? "" : "\n继承自:" + entry.getCloneParent()) + "\n静态按键引用:" + usage.total + (usage.uncertain.isEmpty() ? "" : "\n无法确定的键盘:" + android.text.TextUtils.join(", ", usage.uncertain)) + (entry.getDynamic() ? "\n动态实体已禁用结构化操作;请使用 Lua 源代码页。" : "");
            String[] actions = entry.getDynamic() ? new String[]{"详情", "打开样式 Lua"} : new String[]{"详情", "复制完整实体", "创建副本", "重命名并替换引用", "无引用时删除", "打开样式 Lua"};
            new android.app.AlertDialog.Builder(this).setTitle(entry.getId()).setMessage(details).setItems(actions, (dialog, which) -> {
                if (which == 0) return;
                if (entry.getDynamic()) { requestProjectFileSwitch(styleFile); return; }
                if (which == 1) copyEntityFromStyleAsset(styleFile, entry.getId()); else if (which == 2) promptDuplicateStyleEntity(styleFile, entry.getId()); else if (which == 3) promptRenameStyleEntity(styleFile, entry.getId(), usage); else if (which == 4) confirmDeleteStyleEntity(styleFile, entry.getId(), usage); else requestProjectFileSwitch(styleFile);
            }).setNegativeButton("关闭", null).show();
        } catch (Exception error) { workspace.setStatus("样式实体分析被阻止:" + safeErrorMessage(error)); }
    }

    private void copyEntityFromStyleAsset(ThemeProjectFile styleFile, String id) {
        try { ThemeStyleEntities.Snapshot snapshot = ThemeStyleEntities.extract(new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8), id); workspace.storeStyleEntityClipboard(snapshot); }
        catch (Exception error) { workspace.setStatus("样式实体复制被阻止:" + safeErrorMessage(error)); }
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
        catch (Exception error) { workspace.setStatus("实体粘贴被阻止:" + safeErrorMessage(error)); return; }
        LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "新实体标识", payload.styleEntity.getId() + "_copy");
        new android.app.AlertDialog.Builder(this).setTitle("粘贴完整实体").setMessage(payload.styleEntity.getCloneParent() == null ? "无克隆依赖" : "需要克隆父项:" + payload.styleEntity.getCloneParent()).setView(fields).setNegativeButton("取消", null).setPositiveButton("粘贴", (dialog, which) -> mutateSingleStyleEntity(styleFile, source -> ThemeStyleEntities.paste(source, payload.styleEntity, id.getText().toString().trim()), "已粘贴完整样式实体")).show();
    }

    private interface StyleSourceMutation { String apply(String source) throws Exception; }
    private void mutateSingleStyleEntity(ThemeProjectFile styleFile, StyleSourceMutation mutation, String success) {
        if (!ensureAssetWritable()) return;
        try {
            String original = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); String updated = mutation.apply(original);
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), updated); originals.put(styleFile.getFile(), original); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success + ";事务已校验");
        } catch (Exception error) { workspace.setStatus("样式实体修改失败:" + safeErrorMessage(error)); }
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
            EntityUsage usage = collectEntityUsage(styleFile, oldId); if (!usage.uncertain.isEmpty()) throw new IOException("引用变为无法确定:" + android.text.TextUtils.join(", ", usage.uncertain));
            java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); String styleSource = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); originals.put(styleFile.getFile(), styleSource); changes.put(styleFile.getFile(), ThemeStyleEntities.rename(styleSource, oldId, newId));
            int changed = 0; for (ThemeProjectFile keyboard : usage.references.keySet()) { String source = new String(readFileBytes(keyboard.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); originals.put(keyboard.getFile(), source); ThemeStyleEntities.ReferenceUpdate update = ThemeStyleEntities.replaceKeyboardReferences(source, oldId, newId); if (update.getChangedKeys() > 0) { changes.put(keyboard.getFile(), update.getSource()); changed += update.getChangedKeys(); } }
            applyProjectSourceTransaction(changes, originals); workspace.setStatus("已将 " + oldId + " 重命名为 " + newId + " 并替换 " + changed + " 个按键引用");
        } catch (Exception error) { workspace.setStatus("样式实体重命名被阻止:" + safeErrorMessage(error)); }
    }

    private void confirmDeleteStyleEntity(ThemeProjectFile styleFile, String id, EntityUsage previewUsage) {
        if (ThemeStyleEntities.isReserved(id)) { workspace.setStatus("保留组件样式不能删除:" + id); return; }
        if (previewUsage.total > 0 || !previewUsage.uncertain.isEmpty()) { workspace.setStatus("删除被阻止:" + previewUsage.total + " 个引用;无法确定的键盘:" + android.text.TextUtils.join(", ", previewUsage.uncertain)); return; }
        new android.app.AlertDialog.Builder(this).setTitle("删除无引用的样式实体?").setMessage(id + "\n只删除该实体的静态样式语句;提交时还会再次检查克隆使用者。").setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> deleteStyleEntityTransaction(styleFile, id)).show();
    }

    private void deleteStyleEntityTransaction(ThemeProjectFile styleFile, String id) {
        if (!ensureAssetWritable()) return;
        try {
            EntityUsage usage = collectEntityUsage(styleFile, id); if (usage.total > 0 || !usage.uncertain.isEmpty()) throw new IOException("复核后引用已变化;请重新打开实体管理器");
            String source = new String(readFileBytes(styleFile.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(styleFile.getFile(), ThemeStyleEntities.delete(source, id)); originals.put(styleFile.getFile(), source); applyProjectSourceTransaction(changes, originals); workspace.setStatus("已删除无引用的样式实体 " + id);
        } catch (Exception error) { workspace.setStatus("样式实体删除被阻止:" + safeErrorMessage(error)); }
    }

    private void applyProjectSourceTransaction(java.util.LinkedHashMap<File, String> changes, java.util.Map<File, String> expectedOriginals) throws Exception { applyProjectSourceTransaction(changes, expectedOriginals, null); }
    private void applyProjectSourceTransaction(java.util.LinkedHashMap<File, String> changes, java.util.Map<File, String> expectedOriginals, java.util.Collection<File> expectedLuaManifest) throws Exception {
        if (changes.isEmpty()) return;
        final class Backup { final byte[] bytes; final String localHash; final String remoteHash; Backup(byte[] bytes, String localHash, String remoteHash) { this.bytes = bytes; this.localHash = localHash; this.remoteHash = remoteHash; } }
        java.util.LinkedHashMap<File, Backup> backups = new java.util.LinkedHashMap<>(); String root = project.getRoot().getCanonicalPath();
        validateProjectTransactionSnapshot(root, expectedOriginals, expectedLuaManifest);
        for (java.util.Map.Entry<File, String> change : changes.entrySet()) {
            File file = change.getKey(); if (!file.getCanonicalPath().startsWith(root + File.separator)) throw new IOException("事务文件超出项目根目录");
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(change.getValue()); for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("已更新的 " + file.getName() + " 包含 Lua 错误");
            byte[] bytes = readFileBytes(file, 4L * 1024 * 1024); String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8); String localHash = ThemeSaveCoordinator.Companion.fingerprint(text), expected = expectedOriginals == null ? null : expectedOriginals.get(file); if (expected != null && !ThemeSaveCoordinator.Companion.fingerprint(expected).equals(localHash)) throw new IOException("准备事务时项目文件已变化:" + file.getName()); String remoteHash = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(file); if (remoteHash != null && !remoteHash.equals(localHash)) throw new IOException("导入项目与本地缓存不一致,事务前请重新加载:" + file.getName()); backups.put(file, new Backup(bytes, localHash, remoteHash));
        }
        validateProjectTransactionSnapshot(root, expectedOriginals, expectedLuaManifest);
        for (java.util.Map.Entry<File, Backup> entry : backups.entrySet()) {
            String current = new String(readFileBytes(entry.getKey(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); if (!entry.getValue().localHash.equals(ThemeSaveCoordinator.Companion.fingerprint(current))) throw new IOException("事务开始前项目文件已变化:" + entry.getKey().getName());
            if (importedProjectTreeUri != null && !entry.getValue().remoteHash.equals(fingerprintImportedProjectFile(entry.getKey()))) throw new IOException("事务开始前导入项目文件已变化:" + entry.getKey().getName());
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
            File file = expected.getKey(); String path = file.getCanonicalPath(); if (!path.startsWith(root + File.separator) || !file.isFile()) throw new IOException("项目快照文件不可用:" + file.getName());
            String current = new String(readFileBytes(file, 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); String expectedHash = ThemeSaveCoordinator.Companion.fingerprint(expected.getValue()); if (!expectedHash.equals(ThemeSaveCoordinator.Companion.fingerprint(current))) throw new IOException("引用扫描后项目文件已变化:" + file.getName());
            if (importedProjectTreeUri != null && !expectedHash.equals(fingerprintImportedProjectFile(file))) throw new IOException("引用扫描后导入项目已变化:" + file.getName());
        }
        if (expectedLuaManifest != null) {
            java.util.ArrayList<File> current = new java.util.ArrayList<>(); collectProjectLuaFiles(project.getRoot(), root, new java.util.HashSet<>(), current); java.util.HashSet<String> expectedPaths = new java.util.HashSet<>(), currentPaths = new java.util.HashSet<>(); for (File file : expectedLuaManifest) expectedPaths.add(file.getCanonicalPath()); for (File file : current) currentPaths.add(file.getCanonicalPath()); if (!expectedPaths.equals(currentPaths)) throw new IOException("引用扫描后项目 Lua 文件集合已变化,请重新打开预设管理器");
        }
    }

    private interface StyleIdAction { void run(String id) throws Exception; }
    private void promptStyleId(String title, String initial, StyleIdAction action) { if (!ensureAssetWritable()) return; LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "样式标识", initial); new android.app.AlertDialog.Builder(this).setTitle(title).setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { action.run(id.getText().toString().trim()); workspace.setStatus(title + " 已完成"); } catch (Exception error) { workspace.setStatus(title + " 失败:" + safeErrorMessage(error)); } }).show(); }
    private void confirmDeleteStyle(ThemeProjectFile file) { if (!ensureAssetWritable()) return; if (isCurrentProjectFile(file)) { workspace.setStatus("删除当前样式前请先打开另一个文件"); return; } new android.app.AlertDialog.Builder(this).setTitle("删除样式?").setMessage(file.getName()).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> { try { ThemeProjectMutator.validateStyleDeletion(project, file); File directory = file.getFile().getParentFile(); if (importedProjectTreeUri != null) deleteImportedProjectPath(directory); deleteDirectory(directory); if (directory.exists()) throw new IOException("无法删除本地样式缓存"); refreshProjectAfterAssetMutation(); workspace.setStatus("已删除样式 " + file.getName()); } catch (Exception error) { workspace.setStatus("样式删除被阻止:" + safeErrorMessage(error)); } }).show(); }

    private void loadRecentProject(Uri uri, String prefix, String name) throws IOException {
        DocumentFile tree = DocumentFile.fromTreeUri(this, uri); if (tree == null) throw new IOException("最近目录不可用"); DocumentFile source = prefix == null ? tree : tree.findFile(prefix); if (source == null || !source.isDirectory()) throw new IOException("最近项目目录不可用");
        File cache = new File(getCacheDir(), "theme-editor-recent-" + System.nanoTime()); copyDocumentTree(source, cache); importedProjectUri = uri; importedProjectTreeUri = uri; importedProjectTreePrefix = prefix; openedImportedFingerprint = null; loadProject(cache, name);
    }

    private void showStructurePageState() { viewModel.setCurrentPage("structure"); }
    private void showStructurePage() {
        showStructurePageState();
        ThemeEditorModel model = workspace.getModel(); StringBuilder text = new StringBuilder("生效优先级:行布局(rows) > 弹性盒(flex_box) > 绝对定位(keys) > 按键映射(key_maps)\n当前布局:").append(layoutModeText(model.layoutMode)).append("\n按键数:").append(model.keys.size());
        if (model.layoutMode == ThemeEditorModel.LayoutMode.ROWS) text.append("\n行数:").append(model.rows.size()).append("\n使用“行管理”并拖动按键跨行移动。");
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) text.append("\n容器数:").append(model.flexContainers.size()).append("\n使用“弹性盒”编辑和调整父级。");
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) text.append("\n页面数:").append(model.keyMapPages.size()).append("\n使用“页面”执行批量操作。");
        else if (model.layoutMode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) text.append("\n使用“按键”执行网格、对齐、分布和锁定。");
        else text.append("\n没有字面量键盘布局,请使用 Lua 源代码页面。");
        String[] actions = model.layoutMode == ThemeEditorModel.LayoutMode.NONE ? new String[]{"关闭"} : new String[]{"迁移布局...", "关闭"};
        new android.app.AlertDialog.Builder(this).setTitle("键盘结构").setMessage(text.toString()).setItems(actions, (dialog, which) -> { if (model.layoutMode != ThemeEditorModel.LayoutMode.NONE && which == 0) chooseLayoutMigrationTarget(model); }).show();
    }

    private void chooseLayoutMigrationTarget(ThemeEditorModel source) {
        java.util.ArrayList<ThemeEditorModel.LayoutMode> modes = new java.util.ArrayList<>(); java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (ThemeEditorModel.LayoutMode mode : new ThemeEditorModel.LayoutMode[]{ThemeEditorModel.LayoutMode.ROWS, ThemeEditorModel.LayoutMode.FLEX_BOX, ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS, ThemeEditorModel.LayoutMode.KEY_MAPS}) if (mode != source.layoutMode) { modes.add(mode); labels.add(layoutModeText(mode)); }
        new android.app.AlertDialog.Builder(this).setTitle("迁移 " + layoutModeText(source.layoutMode) + " 到").setItems(labels.toArray(new String[0]), (dialog, which) -> showLayoutMigrationPreview(source, modes.get(which))).setNegativeButton("取消", null).show();
    }

    private void showLayoutMigrationPreview(ThemeEditorModel source, ThemeEditorModel.LayoutMode target) {
        try {
            ThemeLayoutMigration.Preview preview = ThemeLayoutMigration.preview(source, target); StringBuilder message = new StringBuilder();
            message.append("转换 ").append(preview.getKeyCount()).append(" 个按键;容器数 ").append(preview.getSourceContainerCount()).append(" → ").append(preview.getTargetContainerCount()).append("。\n");
            if (preview.getOmittedKeyMapPages() > 0) message.append("省略非活动分页:").append(preview.getOmittedKeyMapPages()).append(" 页。\n");
            for (String note : preview.getNotes()) message.append("\n• ").append(note);
            String[] actions = {"复制备份并转换", "转换", "隐藏原数据并转换", "取消"};
            new android.app.AlertDialog.Builder(this).setTitle("迁移预览:" + layoutModeText(source.layoutMode) + " → " + layoutModeText(target)).setMessage(message.toString()).setItems(actions, (dialog, which) -> { if (which < 3) applyLayoutMigration(source, target, which == 0, which == 2); }).show();
        } catch (Exception error) { workspace.setStatus("迁移预览失败:" + safeErrorMessage(error)); }
    }

    private static void assertLayoutMigrationSafe(com.osfans.trime.editor.core.ThemeDocument document) throws IOException {
        java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
        for (String root : new String[]{"rows", "flex_box", "keys", "key_maps"}) if (containsRawLua(document.get(root))) throw new IOException("布局根包含动态值,请使用 Lua 源代码页面:" + root);
        for (com.osfans.trime.editor.core.ThemeSourceStatement statement : document.getSourceStatements()) { String path = statement.getPath(); if (path == null) continue; String root = path.contains(".") ? path.substring(0, path.indexOf('.')) : path; if (root.equals("rows") || root.equals("flex_box") || root.equals("keys") || root.equals("key_maps")) counts.put(path, counts.containsKey(path) ? counts.get(path) + 1 : 1); }
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) if (entry.getValue() > 1) throw new IOException("布局字段存在重复赋值,请使用 Lua 源代码页面:" + entry.getKey());
    }

    private static boolean containsRawLua(ThemeValue value) {
        if (value instanceof ThemeValue.RawLuaNode) return true;
        if (value instanceof ThemeValue.LuaTable) for (ThemeValue child : ((ThemeValue.LuaTable) value).getFields().values()) if (containsRawLua(child)) return true;
        return false;
    }

    private void clearMigrationHistory() { migrationUndoDocument = null; migrationRedoDocument = null; migrationSourceMode = null; migrationTargetMode = null; editsAfterSourceTransaction = 0; sourceTransactionUndone = false; applyingMigration = false; }

    private void applyLayoutMigration(ThemeEditorModel source, ThemeEditorModel.LayoutMode target, boolean backup, boolean hideOriginal) {
        if (!ensureWritable() || editor == null) return;
        com.osfans.trime.editor.core.ThemeDocument before = null;
        try {
            if (migrationUndoDocument != null) throw new IOException("开始另一项布局迁移前请先保存或重新加载");
            if (!syncModel(source)) throw new IOException("迁移前无法同步当前布局");
            before = editor.getDocument();
            assertLayoutMigrationSafe(before);
            File migrationBackup = backup ? createLayoutMigrationBackup(before) : null;
            ThemeLayoutMigration.Result result = ThemeLayoutMigration.migrate(before, source, target, hideOriginal);
            String candidate = com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(result.getDocument(), com.osfans.trime.editor.core.ThemeWriteMode.HYBRID);
            com.osfans.trime.editor.core.ParseResult verified = new ThemeLuaParser().parse(candidate); for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : verified.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("迁移后的源码未通过静态解析校验");
            migrationUndoDocument = before; migrationRedoDocument = verified.getDocument(); migrationSourceMode = source.layoutMode; migrationTargetMode = target;
            editor.replaceDocument(verified.getDocument()); applyingMigration = true;
            try { if (!workspace.replaceModelAsAtomic(toUiModel(verified.getDocument()), "已将 " + layoutModeText(source.layoutMode) + " 迁移为 " + layoutModeText(target) + ",作为一个撤销步骤")) throw new IOException("工作区拒绝迁移"); }
            finally { applyingMigration = false; }
            layoutEditable = true; if (migrationBackup != null) workspace.setStatus("已将 " + layoutModeText(source.layoutMode) + " 迁移为 " + layoutModeText(target) + ";备份:keyboards/.editor-backups/" + migrationBackup.getName());
        } catch (Exception error) { applyingMigration = false; if (before != null) editor.replaceDocument(before); clearMigrationHistory(); workspace.setStatus("布局迁移失败,已保留原草稿:" + safeErrorMessage(error)); }
    }

    private File createLayoutMigrationBackup(com.osfans.trime.editor.core.ThemeDocument source) throws IOException {
        if (project == null || !(repository instanceof DirectoryThemeProjectRepository)) throw new IOException("创建迁移备份前必须打开项目键盘");
        ThemeProjectFile selected = ((DirectoryThemeProjectRepository) repository).getSelected(); if (selected.getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("创建迁移备份前请先打开键盘资源");
        File backup = new File(project.getRoot(), "keyboards/.editor-backups/" + selected.getName() + "-" + System.currentTimeMillis() + ".lua");
        new FileThemeProjectRepository(backup).write(com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(source, com.osfans.trime.editor.core.ThemeWriteMode.HYBRID));
        try { mirrorCreatedProjectFile(backup); } catch (Exception error) { backup.delete(); try { deleteImportedProjectPath(backup); } catch (Exception ignored) { } throw error; }
        return backup;
    }

    private void showExportInstallPageState() { viewModel.setCurrentPage("export_install"); }
    private void showExportInstallPage() {
        showExportInstallPageState();
        int fileCount = project == null ? editor == null ? 0 : 1 : safeProjectFileCount(project.getRoot());
        long bytes = project == null ? editor == null ? 0 : editor.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length : projectBytes(project.getRoot());
        String summary = "当前状态:" + (viewModel.getDirty() ? "有未保存草稿,导出可显式包含草稿" : "已保存") + "\n模型版本:" + viewModel.getModelRevision() + " / 已保存版本:" + viewModel.getSavedRevision() + "\n预计文件:" + fileCount + " 个,约 " + bytes + " 字节\n安装前会强制保存、备份并校验;没有可靠公开刷新接口时只提供手动刷新说明。";
        String[] actions = {"选择导出类型与资源", "分享导出包", "查看上次导出结果", "安装到已授权目录", "回滚上次安装"};
        new android.app.AlertDialog.Builder(this).setTitle("导出与安装").setMessage(summary).setItems(actions, (dialog, which) -> {
            if (which == 0) showExportOptions(false); else if (which == 1) showExportOptions(true); else if (which == 2) showExportResult(); else if (which == 3) chooseInstallTarget(); else rollbackLastInstall(true);
        }).setNegativeButton("关闭", null).show();
    }

    private int safeProjectFileCount(File root) {
        try { return fileManifest(root).size(); }
        catch (IOException error) { workspace.setStatus("项目清单检查失败:" + safeErrorMessage(error)); return 0; }
    }

    private static long projectBytes(File root) {
        try { return projectBytes(root.getCanonicalFile(), root.getCanonicalFile(), new java.util.HashSet<>()); }
        catch (IOException ignored) { return 0; }
    }

    private static long projectBytes(File root, File current, java.util.Set<String> visited) throws IOException {
        String rootPath = root.getCanonicalPath(), currentPath = current.getCanonicalPath();
        if (!(currentPath.equals(rootPath) || currentPath.startsWith(rootPath + File.separator)) || !current.getAbsolutePath().equals(currentPath) || !visited.add(currentPath)) return 0;
        long total = 0; File[] files = current.listFiles(); if (files == null) return 0;
        for (File file : files) { String path = file.getCanonicalPath(); if (!file.getAbsolutePath().equals(path) || !path.startsWith(rootPath + File.separator)) continue; total += file.isDirectory() ? projectBytes(root, file, visited) : file.isFile() ? file.length() : 0; }
        return total;
    }

    private android.content.SharedPreferences editorPreferences() {
        return getSharedPreferences("theme_editor_settings", MODE_PRIVATE);
    }

    private void showEditorSettings() {
        viewModel.setCurrentPage("editor_settings");
        android.content.SharedPreferences preferences = editorPreferences();
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        android.widget.CheckBox autoDraft = new android.widget.CheckBox(this); autoDraft.setText("进入后台时保存私有草稿"); autoDraft.setChecked(preferences.getBoolean("auto_draft", true)); fields.addView(autoDraft);
        TextView directory = new TextView(this); String directoryUri = preferences.getString("default_project_uri", null); directory.setText(directoryUri == null ? "默认项目目录:未设置,新建时通过 SAF 选择" : "默认项目目录:已获得 SAF 授权"); fields.addView(directory);
        android.widget.Button chooseDirectory = new android.widget.Button(this); chooseDirectory.setText("选择默认项目目录"); chooseDirectory.setOnClickListener(view -> defaultProjectTreeLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))); fields.addView(chooseDirectory);
        TextView deviceLabel = new TextView(this); deviceLabel.setText("默认预览设备"); fields.addView(deviceLabel);
        String[] devices = {"保持当前尺寸", "手机竖屏 360×300", "手机横屏 720×260", "平板 600×420"};
        android.widget.Spinner device = new android.widget.Spinner(this); device.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, devices)); device.setSelection(preferences.getInt("preview_device", 0)); fields.addView(device);
        TextView appearance = new TextView(this); appearance.setText("编辑器外观:深色画布(与 layout-3-canvas 原型一致)"); fields.addView(appearance);
        TextView logging = new TextView(this); logging.setText("常规日志:不收集、不上传,不记录用户输入、资源路径或 URI;安装回滚仅在应用私有目录保存授权目标事务记录"); fields.addView(logging);
        android.widget.Button clearPrivate = new android.widget.Button(this); clearPrivate.setText("清理私有草稿与安装记录"); clearPrivate.setOnClickListener(view -> clearPrivateEditorRecords()); fields.addView(clearPrivate);
        android.widget.Button help = new android.widget.Button(this); help.setText("帮助、隐私与已知限制"); help.setOnClickListener(view -> showEditorHelp()); fields.addView(help);
        TextView version = new TextView(this); version.setText("版本:" + editorVersionText()); fields.addView(version);
        new android.app.AlertDialog.Builder(this).setTitle("编辑器设置").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> {
            int selected = device.getSelectedItemPosition(); preferences.edit().putBoolean("auto_draft", autoDraft.isChecked()).putInt("preview_device", selected).apply();
            if (selected > 0) { ThemeEditorModel preview = workspace.getModel(); if (selected == 1) { preview.previewWidth = 360; preview.previewHeight = 300; } else if (selected == 2) { preview.previewWidth = 720; preview.previewHeight = 260; } else { preview.previewWidth = 600; preview.previewHeight = 420; } workspace.setModelKeepingHistory(preview); viewModel.recordPreviewChange(); }
            workspace.setStatus("编辑器设置已应用;主题内容未修改");
        }).show();
    }

    private String editorVersionText() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "未知"; }
    }

    private void showEditorHelp() {
        String message = "使用流程:\n1.在项目主页新建、打开目录或导入 ZIP。\n2.在工作台选择按键并编辑属性、布局、事件和资源。\n3.使用诊断页检查错误,再保存、导出或安装。\n\n安全与隐私:\n用户 Lua、命令、脚本、Intent 和回调只按静态数据读取,绝不在预览中执行。编辑器不上传主题、输入内容、资源路径或 URI,也不收集崩溃日志。\n\n已知限制:\n静态预览不能证明动态 Lua、共享运行时资源和真实 Trime2 状态;没有公开刷新接口时安装后需由用户在 Trime2 中手动重新部署或切换主题。最终显示效果以目标 Trime2 版本实机读取为准。";
        new android.app.AlertDialog.Builder(this).setTitle("帮助与已知限制").setMessage(message).setPositiveButton("关闭", null).show();
    }

    private void clearPrivateEditorRecords() {
        deleteRecoveryDraft(); deleteCodeBuffer();
        File journal = new File(getFilesDir(), "theme-editor-install.journal"); if (journal.exists()) journal.delete();
        File[] privateFiles = getFilesDir().listFiles(); if (privateFiles != null) for (File file : privateFiles) if (file.getName().startsWith("theme-editor-recovery-corrupt-")) file.delete();
        lastInstallTarget = null; lastInstallBackup = null; lastBackupManifest = null; lastBackupHashManifest = null; invalidateOptionsMenu();
        workspace.setStatus("已清理编辑器私有草稿、代码缓冲区和安装事务记录");
    }

    private void showRecoveryStatus() {
        viewModel.setCurrentPage("recovery");
        boolean hasDraft = recoveryDraftFile().isFile() && recoveryMetaFile().isFile();
        boolean identityMatches = false;
        if (hasDraft) try { identityMatches = recoveryMetaIdentity().equals(recoveryIdentity()); } catch (Exception ignored) { }
        File journal = new File(getFilesDir(), "theme-editor-install.journal"), corrupt = recoveryCorruptReportFile();
        String text = hasDraft ? "存在私有 Lua 草稿。身份匹配:" + (identityMatches ? "是,可恢复到当前文件" : "否,可作为独立副本恢复") + "。恢复不会自动覆盖用户文件。" : "没有可恢复的私有草稿。";
        if (corrupt.isFile()) text += "\n存在已隔离的损坏草稿诊断;原始损坏内容未被执行。";
        if (journal.isFile()) text += "\n存在安装事务记录;如有已校验备份,可从导出与安装页回滚。";
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        if (hasDraft) { actions.add(identityMatches ? "恢复并检查草稿" : "作为独立副本恢复"); actions.add("查看草稿诊断"); actions.add("删除私有草稿"); }
        if (corrupt.isFile()) { actions.add("查看损坏草稿报告"); actions.add("导出损坏草稿报告"); }
        if (actions.isEmpty()) actions.add("关闭");
        new android.app.AlertDialog.Builder(this).setTitle("恢复状态").setMessage(text).setItems(actions.toArray(new String[0]), (dialog, which) -> {
            String action = actions.get(which);
            if (action.equals("恢复并检查草稿")) { recoveryPrompted = false; offerRecoveryDraft(); }
            else if (action.equals("作为独立副本恢复")) restoreRecoveryDraftStandalone();
            else if (action.equals("查看草稿诊断")) showRecoveryDraftDiagnostics();
            else if (action.equals("删除私有草稿")) new android.app.AlertDialog.Builder(this).setTitle("删除私有草稿?").setMessage("只删除应用私有恢复副本,不会删除用户主题文件。").setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> { deleteRecoveryDraft(); workspace.setStatus("私有恢复草稿已删除"); }).show();
            else if (action.equals("查看损坏草稿报告")) showCorruptRecoveryReport();
            else if (action.equals("导出损坏草稿报告")) exportCorruptRecoveryReport();
        }).setNegativeButton("关闭", null).show();
    }

    private void showRecoveryDraftDiagnostics() {
        try {
            String source = verifiedRecoveryDraftSource(false);
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(source);
            int errors = 0, warnings = 0; for (com.osfans.trime.editor.core.ThemeDiagnostic item : parsed.getDiagnostics()) { if (item.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) errors++; else if (item.getSeverity() == com.osfans.trime.editor.core.Severity.WARNING) warnings++; }
            String message = "身份匹配:" + (recoveryMetaIdentity().equals(recoveryIdentity()) ? "是" : "否,只能作为独立副本恢复") + "\nSchema:" + recoveryMetaValue("schema", "旧版") + "\nChecksum:" + (recoveryMetaValue("checksum", null) == null ? "旧版草稿未记录" : "匹配") + "\n静态诊断:错误 " + errors + " / 警告 " + warnings + "\nLua 安全边界:只解析数据,绝不执行草稿中的脚本、命令或回调。";
            new android.app.AlertDialog.Builder(this).setTitle("恢复草稿诊断").setMessage(message).setPositiveButton("关闭", null).show();
        } catch (Exception error) { workspace.setStatus("恢复草稿检查失败:" + safeErrorMessage(error)); }
    }

    private void restoreRecoveryDraftStandalone() {
        try {
            String source = verifiedRecoveryDraftSource(true);
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(source);
            for (com.osfans.trime.editor.core.ThemeDiagnostic item : parsed.getDiagnostics()) if (item.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("草稿 Lua 存在错误:" + item.getMessage());
            releaseSession(); repository = null; project = null; projectSnapshot = null; importedProjectUri = null; importedProjectTreeUri = null; importedProjectTreePrefix = null; currentUri = null; openedFingerprint = null; openedImportedFingerprint = null; openedSourceFingerprint = null; projectDisplayName = "独立恢复副本";
            editor = new ThemeEditor(parsed.getDocument()); layoutEditable = findLayoutRoot(editor.getDocument()) != null; viewModel.setCurrentUri(null); viewModel.markLoaded("recovery-copy:" + ThemeSaveCoordinator.Companion.fingerprint(recoveryMetaIdentity()), null); viewModel.recordEdit();
            workspace.setModel(layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); restoreWorkspaceState(); clearMigrationHistory(); invalidateOptionsMenu(); workspace.setStatus("已将草稿作为独立未保存副本恢复;请另存为 Lua,原项目未被修改");
        } catch (Exception error) { workspace.setStatus("独立恢复失败:" + safeErrorMessage(error)); }
    }

    private String recoveryMetaIdentity() throws IOException {
        String text = readSmallText(recoveryMetaFile(), 16 * 1024); int newline = text.indexOf('\n'); return (newline < 0 ? text : text.substring(0, newline)).trim();
    }

    private String recoveryMetaValue(String key, String fallback) throws IOException {
        String prefix = key + "="; for (String line : readSmallText(recoveryMetaFile(), 16 * 1024).split("\n")) if (line.startsWith(prefix)) return line.substring(prefix.length()).trim(); return fallback;
    }

    private String verifiedRecoveryDraftSource(boolean quarantineOnFailure) throws IOException {
        if (!recoveryDraftFile().isFile() || !recoveryMetaFile().isFile()) throw new IOException("恢复草稿不存在");
        String source = readSmallText(recoveryDraftFile(), 4 * 1024 * 1024); String schemaValue = recoveryMetaValue("schema", null); String expected = recoveryMetaValue("checksum", null);
        if (schemaValue != null) { int schema; try { schema = Integer.parseInt(schemaValue); } catch (NumberFormatException error) { schema = -1; } if (schema < 1 || schema > ThemeProjectCreator.EDITOR_SCHEMA_VERSION) { IOException error = new IOException("恢复草稿 schema 不兼容:" + schemaValue); if (quarantineOnFailure) quarantineRecoveryDraft(error.getMessage(), source); throw error; } }
        if (expected != null && !expected.equals(ThemeSaveCoordinator.Companion.fingerprint(source))) {
            IOException error = new IOException("恢复草稿 checksum 不匹配"); if (quarantineOnFailure) quarantineRecoveryDraft(error.getMessage(), source); throw error;
        }
        return source;
    }

    private void quarantineRecoveryDraft(String reason, String source) {
        long time = System.currentTimeMillis(); File corrupt = new File(getFilesDir(), "theme-editor-recovery-corrupt-" + time + ".lua"), meta = new File(getFilesDir(), "theme-editor-recovery-corrupt-" + time + ".meta");
        try {
            if (recoveryDraftFile().isFile() && !recoveryDraftFile().renameTo(corrupt)) writePrivateText(corrupt, source == null ? readSmallText(recoveryDraftFile(), 4 * 1024 * 1024) : source);
            if (recoveryMetaFile().isFile()) recoveryMetaFile().renameTo(meta);
            writePrivateText(recoveryCorruptReportFile(), "Trime2 主题编辑器恢复诊断\n时间:" + time + "\n原因:" + (reason == null ? "草稿静态校验失败" : reason.replace('\n', ' ')) + "\n隔离 Lua:" + corrupt.getName() + "\n隔离元数据:" + meta.getName() + "\n安全边界:损坏草稿从未执行,用户主题文件未被覆盖。\n");
        } catch (Exception ignored) { }
        deleteRecoveryDraft();
    }

    private File recoveryCorruptReportFile() { return new File(getFilesDir(), "theme-editor-recovery-corrupt-report.txt"); }

    private void showCorruptRecoveryReport() {
        try { new android.app.AlertDialog.Builder(this).setTitle("损坏草稿恢复诊断").setMessage(readSmallText(recoveryCorruptReportFile(), 64 * 1024)).setPositiveButton("关闭", null).show(); }
        catch (Exception error) { workspace.setStatus("损坏草稿报告不可读:" + safeErrorMessage(error)); }
    }

    private void exportCorruptRecoveryReport() {
        try { lastExportKind = com.osfans.trime.editor.project.ThemeExportKind.COMPATIBILITY_REPORT; lastExportReport = readSmallText(recoveryCorruptReportFile(), 64 * 1024); setPendingTextExport(lastExportReport); exportTextLauncher.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE, "theme-editor-recovery-report.txt")); }
        catch (Exception error) { workspace.setStatus("损坏草稿报告导出失败:" + safeErrorMessage(error)); }
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

    private void releaseSession() {
        synchronized (ACTIVE_WRITE_SESSIONS) { if (sessionKey != null && viewModel.getSessionToken().equals(ACTIVE_WRITE_SESSIONS.get(sessionKey))) ACTIVE_WRITE_SESSIONS.remove(sessionKey); }
        sessionKey = null; readOnlySession = false; if (workspace != null) { workspace.setClipboardScope(null); workspace.setReadOnly(false); }
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

    private boolean restoreProjectState() {
        String relative = viewModel.getProjectFile(), displayName = viewModel.getProjectDisplayName();
        try {
            String treeValue = viewModel.getImportedProjectTreeUri();
            if (treeValue != null && !treeValue.isEmpty()) {
                loadRecentProject(Uri.parse(treeValue), viewModel.getImportedProjectTreePrefix(), displayName);
                return project != null;
            }
            String rootValue = viewModel.getProjectRoot(), importedValue = viewModel.getImportedProjectUri();
            if (rootValue == null || rootValue.isEmpty()) return false;
            File root = new File(rootValue).getCanonicalFile();
            if (!root.isDirectory()) {
                if (importedValue == null || importedValue.isEmpty()) return false;
                loadUri(Uri.parse(importedValue));
                return project != null;
            }
            importedProjectUri = importedValue == null || importedValue.isEmpty() ? null : Uri.parse(importedValue);
            importedProjectTreeUri = null; importedProjectTreePrefix = null;
            loadProject(root, displayName);
            return project != null;
        } catch (Exception error) {
            workspace.setStatus("项目状态恢复失败:" + safeErrorMessage(error));
            return false;
        }
    }

    private void captureProjectState() {
        if (project == null) {
            viewModel.setProjectRoot(null); viewModel.setProjectDisplayName(null); viewModel.setProjectFile(null);
            viewModel.setImportedProjectUri(null); viewModel.setImportedProjectTreeUri(null); viewModel.setImportedProjectTreePrefix(null);
            return;
        }
        try {
            File root = project.getRoot().getCanonicalFile(); viewModel.setProjectRoot(root.getCanonicalPath()); viewModel.setProjectDisplayName(projectDisplayName);
            if (repository instanceof DirectoryThemeProjectRepository) {
                File selected = ((DirectoryThemeProjectRepository) repository).getSelected().getFile().getCanonicalFile();
                viewModel.setProjectFile(selected.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator) ? selected.getCanonicalPath().substring(root.getCanonicalPath().length() + 1).replace(File.separatorChar, '/') : null);
            } else viewModel.setProjectFile(null);
            viewModel.setImportedProjectUri(importedProjectUri == null ? null : importedProjectUri.toString());
            viewModel.setImportedProjectTreeUri(importedProjectTreeUri == null ? null : importedProjectTreeUri.toString());
            viewModel.setImportedProjectTreePrefix(importedProjectTreePrefix);
        } catch (IOException error) {
            viewModel.setProjectRoot(null); viewModel.setProjectFile(null);
        }
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
                .setNegativeButton("放弃", (dialog, which) -> { if (discardCurrentDraft()) loadProjectFile(file); })
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

    private static ThemeProjectFile restoredProjectFile(ThemeProject project, String relativePath) {
        if (project == null || relativePath == null || relativePath.isEmpty()) return null;
        try {
            File root = project.getRoot().getCanonicalFile(), target = new File(root, relativePath).getCanonicalFile();
            if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator) || !target.isFile()) return null;
            if (target.equals(project.getMainFile().getCanonicalFile())) return new ThemeProjectFile("main.lua", project.getMainFile(), ThemeProjectFile.Kind.MAIN);
            for (ThemeProjectFile file : project.getStyles()) if (target.equals(file.getFile().getCanonicalFile())) return file;
            for (ThemeProjectFile file : project.getKeyboards()) if (target.equals(file.getFile().getCanonicalFile())) return file;
        } catch (IOException ignored) { }
        return null;
    }

    private void loadProject(File root, String displayName) {
        try {
            clearMigrationHistory();
            project = ThemeProject.Companion.discover(root); projectDisplayName = displayName == null || displayName.trim().isEmpty() ? root.getName() : displayName;
            projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
            com.osfans.trime.editor.core.ParseResult main = projectSnapshot.getMain();
            ThemeProjectFile selected = restoredProjectFile(project, restoringProjectFile);
            if (selected == null) selected = projectSnapshot.getKeyboardSource();
            if (selected == null) selected = new ThemeProjectFile("main", project.getMainFile(), ThemeProjectFile.Kind.MAIN);
            repository = new DirectoryThemeProjectRepository(project, selected);
            currentUri = Uri.fromFile(selected.getFile());
            viewModel.setCurrentUri(currentUri); claimSession(sessionIdentity());
            editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            com.osfans.trime.editor.core.ParseResult parsed = editor.load(repository);
            openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(repository.read());
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            viewModel.markLoaded(recoveryIdentity(), openedSourceFingerprint); viewModel.setCurrentFile(currentUri == null ? null : currentUri.toString());
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            restoreWorkspaceState();
            openedFingerprint = ThemeSourceFingerprint.Companion.capture(selected.getFile());
            openedImportedFingerprint = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(selected.getFile());
            int diagnosticCount = ThemeProjectDiagnostics.INSTANCE.collect(projectSnapshot, new ThemeFieldRegistry()).size() + parsed.getDiagnostics().size();
            workspace.setStatus("项目 " + root.getName() + ": " + project.getStyles().size() + " 个样式," + project.getKeyboards().size() + " 个键盘," + diagnosticCount + " 条诊断");
            invalidateOptionsMenu();
            offerRecoveryDraft();
        } catch (Exception error) {
            project = null;
            workspace.setStatus("项目加载失败:" + safeErrorMessage(error));
            Toast.makeText(this, "无法加载主题项目", Toast.LENGTH_LONG).show();
        }
    }

    /** Opens the theme currently selected in the input method, if its directory is statically readable. */
    private boolean openInputThemeProject() {
        try {
            String theme = Config.getTheme();
            if (theme == null || theme.trim().isEmpty()) return false;
            File root = new File(Config.getThemeDir(), theme).getCanonicalFile();
            if (!new File(root, "main.lua").isFile()) return false;
            clearMigrationHistory();
            project = ThemeProject.Companion.discover(root);
            projectDisplayName = theme;
            String explicitStyle = Config.getStyle();
            String explicitKeyboard = activeKeyboardName(project);
            projectSnapshot = ThemeProjectSnapshot.Companion.loadSelected(project, explicitStyle, explicitKeyboard, new ThemeLuaParser());
            ThemeProjectFile selected = projectSnapshot.getKeyboardSource();
            if (selected == null) selected = projectSnapshot.getStyleSource();
            if (selected == null) selected = new ThemeProjectFile("main", project.getMainFile(), ThemeProjectFile.Kind.MAIN);
            repository = new DirectoryThemeProjectRepository(project, selected);
            currentUri = Uri.fromFile(selected.getFile());
            viewModel.setCurrentUri(currentUri);
            claimSession(sessionIdentity());
            editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            com.osfans.trime.editor.core.ParseResult parsed = editor.load(repository);
            openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(repository.read());
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            viewModel.markLoaded(recoveryIdentity(), openedSourceFingerprint);
            viewModel.setCurrentFile(currentUri == null ? null : currentUri.toString());
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            openedFingerprint = ThemeSourceFingerprint.Companion.capture(selected.getFile());
            openedImportedFingerprint = null;
            int diagnosticCount = ThemeProjectDiagnostics.INSTANCE.collect(projectSnapshot, new ThemeFieldRegistry()).size() + parsed.getDiagnostics().size();
            workspace.setStatus("已加载输入法当前主题 " + theme + ":" + project.getStyles().size() + " 个样式," + project.getKeyboards().size() + " 个键盘," + diagnosticCount + " 条诊断" + (layoutEditable ? "" : ";当前键盘为动态或无静态布局,画布仅预览"));
            invalidateOptionsMenu();
            return true;
        } catch (Exception error) {
            project = null; projectSnapshot = null; repository = null; currentUri = null;
            workspace.setStatus("无法打开输入法当前主题:" + safeErrorMessage(error));
            return false;
        }
    }

    /** Reads the input method's saved default keyboard id, accepting only files discovered in the project. */
    private String activeKeyboardName(ThemeProject project) {
        String schema = "";
        try { schema = Rime.getCurrentRimeSchema(); } catch (Throwable ignored) { }
        if (schema == null || schema.isEmpty() || ".default".equals(schema)) schema = Function.getPref(this).getString("select_schema_id", "");
        if (schema == null || schema.isEmpty()) return null;
        String saved = Function.loadString(this, Config.getTheme() + "_" + schema + "_keyboard", "");
        if (saved == null || saved.trim().isEmpty()) return null;
        String name = saved.trim();
        return project.keyboard(name) != null ? name : null;
    }

    private void loadTree(Uri uri) {
        File root = new File(getCacheDir(), "theme-editor-tree-" + System.nanoTime());
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(this, uri);
            if (tree == null) throw new IOException("无法打开主题文件夹");
            copyDocumentTree(tree, root);
            showImportPreflight(root, uri, true, null, tree.getName());
        } catch (Exception error) {
            deleteDirectory(root); workspace.setStatus("文件夹导入失败:" + safeErrorMessage(error));
            Toast.makeText(this, "无法导入主题文件夹", Toast.LENGTH_LONG).show();
        }
    }

    private static final class DocumentCopyBudget {
        int files; long bytes; final java.util.HashSet<String> paths = new java.util.HashSet<>();
    }

    private void copyDocumentTree(DocumentFile source, File destination) throws IOException {
        File root = destination.getCanonicalFile();
        copyDocumentTree(source, root, root, "", 0, new DocumentCopyBudget());
    }

    private void copyDocumentTree(DocumentFile source, File root, File destination, String prefix, int depth, DocumentCopyBudget budget) throws IOException {
        if (depth > 12) throw new IOException("主题目录层级超过 12 层限制");
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("无法创建缓存目录");
        String rootPath = root.getCanonicalPath(), destinationPath = destination.getCanonicalPath();
        if (!(destinationPath.equals(rootPath) || destinationPath.startsWith(rootPath + File.separator))) throw new IOException("主题目录超出缓存根目录");
        for (DocumentFile child : source.listFiles()) {
            String name = child.getName();
            if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\") || name.length() > 120) throw new IOException("主题包含非法文件名");
            for (int i = 0; i < name.length(); i++) if (name.charAt(i) < 0x20) throw new IOException("主题文件名包含控制字符");
            String relative = prefix.isEmpty() ? name : prefix + "/" + name;
            if (relative.length() > 240 || !budget.paths.add(relative.toLowerCase(java.util.Locale.ROOT))) throw new IOException("主题包含过长、重复或大小写冲突路径:" + relative);
            File target = new File(destination, name).getCanonicalFile();
            if (!target.getCanonicalPath().startsWith(rootPath + File.separator)) throw new IOException("主题条目超出缓存根目录");
            if (child.isDirectory()) {
                copyDocumentTree(child, root, target, relative, depth + 1, budget);
            } else if (child.isFile()) {
                if (++budget.files > 500) throw new IOException("主题文件数量超过 500 个限制");
                try (InputStream input = getContentResolver().openInputStream(child.getUri())) {
                    if (input == null) throw new IOException("无法读取 " + name);
                    if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) throw new IOException("无法创建缓存父目录");
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        byte[] buffer = new byte[8192]; int count;
                        while ((count = input.read(buffer)) != -1) { budget.bytes += count; if (budget.bytes > 64L * 1024 * 1024) throw new IOException("主题目录超过 64 MiB 限制"); output.write(buffer, 0, count); }
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
        File root = new File(getCacheDir(), "theme-editor-import-" + System.nanoTime());
        try {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("无法打开 ZIP");
                com.osfans.trime.editor.project.ThemeProjectArchive.extractZip(input, root);
            }
            File main = findMainLua(root);
            if (main == null) throw new IOException("ZIP 中不包含唯一明确的 main.lua");
            String archiveName = documentName(uri); if (archiveName.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) archiveName = archiveName.substring(0, archiveName.length() - 4);
            String displayName = main.getParentFile().equals(root) ? archiveName : main.getParentFile().getName();
            showImportPreflight(main.getParentFile(), uri, false, null, displayName);
        } catch (Exception error) {
            deleteDirectory(root); workspace.setStatus("ZIP 导入失败:" + safeErrorMessage(error));
            Toast.makeText(this, "无法导入 ZIP", Toast.LENGTH_LONG).show();
        }
    }

    private void showImportPreflight(File root, Uri sourceUri, boolean writableTree, String prefix, String displayName) throws IOException {
        ThemeProject candidate = ThemeProject.Companion.discover(root);
        ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(candidate, new ThemeLuaParser());
        java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics = ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry());
        java.util.Map<String, Long> manifest = fileManifest(root);
        int luaFiles = 0, errors = 0, warnings = 0, unsupported = 0, missing = 0;
        long totalBytes = 0; java.util.ArrayList<String> scripts = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Long> entry : manifest.entrySet()) { totalBytes += entry.getValue(); if (entry.getKey().toLowerCase(java.util.Locale.ROOT).endsWith(".lua")) luaFiles++; if (entry.getKey().startsWith("scripts/")) scripts.add(entry.getKey()); }
        for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : diagnostics) {
            if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) errors++; else if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.WARNING) warnings++;
            if (diagnostic.getCode().startsWith("lua.dynamic") || diagnostic.getMessage().contains("未结构化") || diagnostic.getMessage().contains("兼容性")) unsupported++;
            if (diagnostic.getMessage().contains("未找到") || diagnostic.getMessage().contains("缺失") || diagnostic.getMessage().contains("不存在")) missing++;
        }
        StringBuilder report = new StringBuilder("来源:").append(writableTree ? "已授权主题目录" : "ZIP 只读副本").append("\n项目:").append(displayName == null ? root.getName() : displayName).append("\n文件:").append(manifest.size()).append(" 个 / ").append(totalBytes).append(" 字节\nLua:").append(luaFiles).append(" 个 · 样式:").append(candidate.getStyles().size()).append(" 个 · 键盘:").append(candidate.getKeyboards().size()).append(" 个 · 资源:").append(candidate.getResources().size()).append(" 个\n静态诊断:错误 ").append(errors).append(" / 警告 ").append(warnings).append(" / 缺失项 ").append(missing).append(" / 动态或不支持项 ").append(unsupported).append("\n覆盖项:0 · 冲突项:0(先导入独立缓存,不会覆盖当前主题)");
        if (!scripts.isEmpty()) report.append("\n\n脚本清单:").append(android.text.TextUtils.join(", ", scripts)).append("\n脚本仅复制和报告,绝不执行。");
        int shown = 0; for (String path : manifest.keySet()) { if (shown++ >= 12) { report.append("\n...其余 ").append(manifest.size() - 12).append(" 个文件"); break; } report.append("\n• ").append(path); }
        final int errorCount = errors;
        new android.app.AlertDialog.Builder(this).setTitle("导入预检").setMessage(report.toString()).setNegativeButton("取消导入", (dialog, which) -> deleteDirectory(root)).setNeutralButton("查看诊断", (dialog, which) -> showImportPreflightDiagnostics(root, sourceUri, writableTree, prefix, displayName, diagnostics)).setPositiveButton(errors > 0 ? "仍以诊断模式导入" : "确认导入", (dialog, which) -> {
            importedProjectUri = sourceUri; importedProjectTreeUri = writableTree ? sourceUri : null; importedProjectTreePrefix = prefix; openedImportedFingerprint = null;
            if (writableTree) rememberRecentProject(sourceUri, displayName, prefix);
            loadProject(root, displayName);
            workspace.setStatus("导入完成:" + manifest.size() + " 个文件," + candidate.getResources().size() + " 个资源," + errorCount + " 个错误;未执行任何 Lua 或脚本");
        }).setOnCancelListener(dialog -> deleteDirectory(root)).show();
    }

    private void showImportPreflightDiagnostics(File root, Uri sourceUri, boolean writableTree, String prefix, String displayName, java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (com.osfans.trime.editor.core.ThemeDiagnostic item : diagnostics) {
            text.append(diagnosticSeverityText(item.getSeverity())).append(" [").append(item.getCode()).append("] ");
            if (item.getPath() != null) text.append(item.getPath()).append(": ");
            text.append(diagnosticMessageText(item.getMessage())).append('\n');
        }
        if (text.length() == 0) text.append("没有发现静态诊断。未执行任何 Lua、脚本、命令或回调。");
        new android.app.AlertDialog.Builder(this).setTitle("导入诊断 · " + diagnostics.size() + " 条").setMessage(text.toString()).setNegativeButton("取消导入", (dialog, which) -> deleteDirectory(root)).setPositiveButton("返回预检", (dialog, which) -> { try { showImportPreflight(root, sourceUri, writableTree, prefix, displayName); } catch (Exception error) { deleteDirectory(root); workspace.setStatus("导入预检恢复失败:" + safeErrorMessage(error)); } }).setOnCancelListener(dialog -> deleteDirectory(root)).show();
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
            viewModel.markLoaded(recoveryIdentity(), openedSourceFingerprint); viewModel.setCurrentFile(currentUri == null ? null : currentUri.toString());
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            restoreWorkspaceState();
            if (repository instanceof DirectoryThemeProjectRepository) {
                openedFingerprint = ThemeSourceFingerprint.Companion.capture(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            } else {
                openedFingerprint = null;
            }
            if (repository instanceof DirectoryThemeProjectRepository && importedProjectTreeUri != null) openedImportedFingerprint = fingerprintImportedProjectFile(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            else if (importedProjectTreeUri == null) openedImportedFingerprint = null;
            workspace.setStatus("已加载 " + currentFileDisplayName() + " (" + parsed.getDiagnostics().size() + " 条诊断)" + (layoutEditable ? "" : ";此文件中没有结构化键盘布局"));
            offerRecoveryDraft();
        } catch (Exception error) {
            workspace.setStatus("加载失败:" + safeErrorMessage(error));
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
        if (!filePath.startsWith(rootPath + File.separator)) throw new IOException("导入文件超出项目根目录");
        String[] parts = filePath.substring(rootPath.length() + 1).split(java.util.regex.Pattern.quote(File.separator));
        if (parts.length == 0) throw new IOException("导入文件没有相对路径");
        DocumentFile parent = DocumentFile.fromTreeUri(this, importedProjectTreeUri); if (parent == null) throw new IOException("导入项目权限不可用");
        if (importedProjectTreePrefix != null && !importedProjectTreePrefix.isEmpty()) { DocumentFile child = parent.findFile(importedProjectTreePrefix); if (child == null || !child.isDirectory()) throw new IOException("创建的项目目录不可用"); parent = child; }
        for (int i = 0; i < parts.length - 1; i++) {
            DocumentFile next = parent.findFile(parts[i]); if (next == null && create) next = parent.createDirectory(parts[i]);
            if (next == null || !next.isDirectory()) return null; parent = next;
        }
        DocumentFile target = parent.findFile(parts[parts.length - 1]);
        if (target == null && create) target = parent.createFile(mimeForName(parts[parts.length - 1]), parts[parts.length - 1]);
        return new ImportedDocumentRef(parent, target, parts[parts.length - 1]);
    }

    private String fingerprintImportedProjectFile(File file) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(file, false); if (ref == null || ref.file == null || !ref.file.isFile()) throw new IOException("导入源文件缺失");
        return fingerprintDocument(ref.file);
    }

    private static String fingerprintStream(InputStream input) throws IOException {
        try { java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); StringBuilder result = new StringBuilder(); for (byte value : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff)); return result.toString(); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IOException("SHA-256 不可用", error); }
    }

    private void writeImportedProjectFile(File cacheFile, String source) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(cacheFile, false); if (ref == null || ref.file == null || !ref.file.isFile()) throw new IOException("导入目标文件缺失");
        DocumentFile target = ref.file, parent = ref.parent; String name = ref.name;
        DocumentFile backup = parent.createFile(mimeForName(name), "." + name + ".editor-backup-" + System.nanoTime());
        DocumentFile temporary = parent.createFile(mimeForName(name), "." + name + ".editor-temp-" + System.nanoTime());
        if (backup == null || temporary == null) { if (backup != null) backup.delete(); if (temporary != null) temporary.delete(); throw new IOException("无法创建 SAF 保存事务文件"); }
        boolean backupReady = false, targetDeleted = false, restored = false;
        try {
            String originalFingerprint = fingerprintDocument(target); copyDocumentFile(target, backup);
            if (!originalFingerprint.equals(fingerprintDocument(backup))) throw new IOException("SAF 备份校验失败");
            backupReady = true; writeDocumentText(temporary, source);
            String expected = ThemeSaveCoordinator.Companion.fingerprint(source);
            if (!expected.equals(fingerprintDocument(temporary))) throw new IOException("SAF 临时文件校验失败");
            if (!target.delete()) throw new IOException("无法替换导入源文件"); targetDeleted = true;
            DocumentFile replacement = parent.createFile(mimeForName(name), name); if (replacement == null) throw new IOException("无法创建导入替代文件");
            copyDocumentFile(temporary, replacement);
            if (!expected.equals(fingerprintDocument(replacement))) throw new IOException("SAF 替代文件校验失败");
            backup.delete();
            temporary.delete();
        } catch (Exception error) {
            if (backupReady && targetDeleted) {
                DocumentFile current = parent.findFile(name); if (current != null) current.delete();
                DocumentFile replacement = parent.createFile(mimeForName(name), name);
                if (replacement != null) try { copyDocumentFile(backup, replacement); restored = fingerprintDocument(backup).equals(fingerprintDocument(replacement)); } catch (Exception ignored) { }
            }
            temporary.delete(); if (restored || !backupReady) backup.delete();
            throw new IOException(restored ? "SAF 保存失败;备份已恢复" : backupReady ? "SAF 保存失败;已保留备份用于恢复" : "SAF 保存失败且尚未替换原文件", error);
        }
    }

    private void writeDocumentText(DocumentFile file, String source) throws IOException {
        try (java.io.OutputStream output = getContentResolver().openOutputStream(file.getUri(), "wt")) { if (output == null) throw new IOException("无法写入 SAF 文件"); output.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    private void copyDocumentFile(DocumentFile source, DocumentFile target) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(source.getUri()); java.io.OutputStream output = getContentResolver().openOutputStream(target.getUri(), "wt")) { if (input == null || output == null) throw new IOException("无法复制 SAF 文件"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
    }
    private String fingerprintDocument(DocumentFile file) throws IOException { try (InputStream input = getContentResolver().openInputStream(file.getUri())) { if (input == null) throw new IOException("无法校验 SAF 文件"); return fingerprintStream(input); } }

    private void refreshImportedCacheFile() throws IOException {
        if (!(repository instanceof DirectoryThemeProjectRepository) || importedProjectTreeUri == null) return;
        File cacheFile = ((DirectoryThemeProjectRepository) repository).getSelected().getFile(); ImportedDocumentRef ref = importedDocumentRef(cacheFile, false); if (ref == null || ref.file == null) throw new IOException("导入源文件缺失");
        try (InputStream input = getContentResolver().openInputStream(ref.file.getUri()); FileOutputStream output = new FileOutputStream(cacheFile, false)) { if (input == null) throw new IOException("无法读取导入源文件"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); output.getFD().sync(); }
        openedImportedFingerprint = fingerprintDocument(ref.file);
    }

    private void mirrorExistingProjectFile(File file) throws IOException { if (importedProjectTreeUri != null) writeImportedProjectFile(file, new String(readFileBytes(file, 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8)); }
    private void mirrorCreatedProjectFile(File file) throws IOException { if (importedProjectTreeUri != null) { ImportedDocumentRef ref = importedDocumentRef(file, true); if (ref == null || ref.file == null) throw new IOException("无法创建 SAF 项目文件"); try (FileInputStream input = new FileInputStream(file); java.io.OutputStream output = getContentResolver().openOutputStream(ref.file.getUri(), "wt")) { if (output == null) throw new IOException("无法镜像项目文件"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); } } }
    private void mirrorRenamedProjectFile(File old, File renamed) throws IOException { if (importedProjectTreeUri != null) { mirrorCreatedProjectFile(renamed); try { deleteImportedProjectPath(old); } catch (IOException error) { try { deleteImportedProjectPath(renamed); } catch (Exception ignored) { } throw error; } } }
    private void mirrorCreatedProjectDirectory(File directory) throws IOException { if (importedProjectTreeUri == null) return; File[] children = directory.listFiles(); if (children == null) return; for (File child : children) { if (child.isDirectory()) mirrorCreatedProjectDirectory(child); else mirrorCreatedProjectFile(child); } }
    private void mirrorRenamedProjectDirectory(File old, File renamed) throws IOException { if (importedProjectTreeUri != null) { mirrorCreatedProjectDirectory(renamed); try { deleteImportedProjectPath(old); } catch (IOException error) { try { deleteImportedProjectPath(renamed); } catch (Exception ignored) { } throw error; } } }
    private void deleteImportedProjectPath(File path) throws IOException { if (importedProjectTreeUri == null) return; ImportedDocumentRef ref = importedDocumentRef(path.isDirectory() ? new File(path, "main.lua") : path, false); DocumentFile target = path.isDirectory() ? (ref == null ? null : ref.parent) : (ref == null ? null : ref.file); if (target != null && !target.delete()) throw new IOException("无法删除 SAF 项目路径"); }
    private void refreshProjectAfterAssetMutation() throws IOException { project = ThemeProject.Companion.discover(project.getRoot()); projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser()); invalidateOptionsMenu(); }

    private static String findLayoutRoot(com.osfans.trime.editor.core.ThemeDocument document) {
        if (document.get("rows") instanceof ThemeValue.LuaTable) return "rows";
        if (document.get("flex_box") instanceof ThemeValue.LuaTable) return "flex_box";
        if (document.get("keys") instanceof ThemeValue.LuaTable) return "keys";
        if (document.get("key_maps") instanceof ThemeValue.LuaTable) return "key_maps";
        return null;
    }

    private boolean syncUndoModel(ThemeEditorModel model) {
        if (migrationUndoDocument != null && !sourceTransactionUndone && editsAfterSourceTransaction == 0
                && migrationSourceMode == model.layoutMode
                && migrationTargetMode == layoutModeForRoot(findLayoutRoot(editor.getDocument()))) {
            editor.replaceDocument(migrationUndoDocument);
            sourceTransactionUndone = true;
            return true;
        }
        boolean synced = syncModel(model);
        if (synced && migrationUndoDocument != null && !sourceTransactionUndone && editsAfterSourceTransaction > 0) editsAfterSourceTransaction--;
        return synced;
    }

    private boolean syncRedoModel(ThemeEditorModel model) {
        if (migrationRedoDocument != null && sourceTransactionUndone
                && migrationTargetMode == model.layoutMode
                && migrationSourceMode == layoutModeForRoot(findLayoutRoot(editor.getDocument()))) {
            editor.replaceDocument(migrationRedoDocument);
            sourceTransactionUndone = false;
            return true;
        }
        boolean synced = syncModel(model);
        if (synced && migrationUndoDocument != null && !sourceTransactionUndone) editsAfterSourceTransaction++;
        return synced;
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
        } catch (Exception error) { workspace.setStatus("结构化更新被阻止:" + safeErrorMessage(error)); return false; }
    }

    private void showKeyEventManager(ThemeEditorModel.Key key) {
        if (!ensureAssetWritable()) return;
        try {
            if (key == null || key.sourcePath == null || key.sourcePath.isEmpty()) throw new IOException("此按键没有稳定源码路径;请保存/重载或使用 Lua 源代码");
            if (!(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD || editor == null) throw new IOException("请先打开项目键盘");
            java.util.List<ThemeKeyEvents.Slot> slots = ThemeKeyEvents.read(editor.getDocument(), key.sourcePath); ThemeKeyEvents.Options options = ThemeKeyEvents.options(editor.getDocument(), key.sourcePath); ThemeKeyEvents.Hints hints = ThemeKeyEvents.hints(editor.getDocument(), key.sourcePath);
            String[] labels = new String[slots.size() + 4]; for (int i = 0; i < slots.size(); i++) { ThemeKeyEvents.Slot slot = slots.get(i); labels[i] = slot.getName() + " — " + keyEventSourceText(slot.getSource()) + eventSlotSummary(slot); } labels[slots.size()] = "滑动重复(swipe_repeatable) — " + nullableBoolean(options.getSwipeRepeatable()); labels[slots.size() + 1] = "发送绑定(send_bindings) — " + nullableBoolean(options.getSendBindings()) + "; 生效值=" + options.getEffectiveSendBindings() + " (来源:" + options.getSendBindingsSource() + ")"; labels[slots.size() + 2] = "事件提示 — 缺失值回退到事件标签"; labels[slots.size() + 3] = "长按/重复点击时间 — 从按键样式实体继承";
            new android.app.AlertDialog.Builder(this).setTitle("按键事件——仅静态").setMessage("不会执行任何事件、命令、脚本、Intent、上屏或回调。").setItems(labels, (dialog, which) -> { if (which < slots.size()) editKeyEventSlot(key, slots.get(which)); else if (which < slots.size() + 2) editKeyEventOptions(key, options); else if (which == slots.size() + 2) editKeyEventHints(key, hints); else workspace.setStatus("长按时间(long_click_time)和重复点击时间(repeat_click_time)属于解析后的按键样式;请编辑该样式实体,而不是此按键源"); }).setNegativeButton("关闭", null).setNeutralButton("查看 Lua", (dialog, which) -> showCodeEditor()).show();
        } catch (Exception error) { workspace.setStatus("按键事件管理被阻止:" + safeErrorMessage(error)); }
    }

    private static String nullableBoolean(Boolean value) { return value == null ? "inherit" : value ? "true" : "false"; }
    private static String keyEventSourceText(ThemeKeyEvents.Source source) {
        if (source == ThemeKeyEvents.Source.MISSING) return "未设置";
        if (source == ThemeKeyEvents.Source.STRING) return "字符串/预设引用";
        if (source == ThemeKeyEvents.Source.INLINE_EVENT) return "内联事件表";
        if (source == ThemeKeyEvents.Source.FULL_KEY_REPLACEMENT) return "完整按键替代";
        return "原始 Lua";
    }

    private static String eventSlotSummary(ThemeKeyEvents.Slot slot) { if (slot.getLiteral() != null) return " = " + slot.getLiteral(); if (slot.getEvent() != null) return " = " + presetSummary(slot.getEvent()); return slot.getRisky() ? " [仅代码]" : ""; }

    private void editKeyEventSlot(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        if (slot.getSource() == ThemeKeyEvents.Source.RAW_LUA || slot.getSource() == ThemeKeyEvents.Source.FULL_KEY_REPLACEMENT) { workspace.setStatus(slot.getName() + " 为" + keyEventSourceText(slot.getSource()) + ";请使用 Lua 源代码"); showCodeEditor(); return; }
        boolean stringOnly = java.util.Arrays.asList(ThemeKeyEvents.STRING_ONLY_SLOTS).contains(slot.getName());
        String[] modes = stringOnly ? new String[]{"字符串/预设引用", "清除"} : new String[]{"字符串/预设引用", "内联事件表", "清除"}; int selected = slot.getSource() == ThemeKeyEvents.Source.INLINE_EVENT ? 1 : slot.getSource() == ThemeKeyEvents.Source.MISSING ? modes.length - 1 : 0;
        new android.app.AlertDialog.Builder(this).setTitle("编辑 " + slot.getName()).setMessage(stringOnly ? "当前 Trime 运行时仅以字符串形式使用此状态替换值。" : "仅选择静态源;不会执行任何操作。").setSingleChoiceItems(modes, selected, (dialog, which) -> { dialog.dismiss(); if (which == 0) editKeyEventString(key, slot); else if (!stringOnly && which == 1) editInlineKeyEvent(key, slot); else commitKeyEventChange(key, document -> ThemeKeyEvents.updateString(document, key.sourcePath, slot.getName(), null), "已清除 " + slot.getName()); }).setNegativeButton("取消", null).show();
    }

    private void editKeyEventString(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        LinearLayout fields = new LinearLayout(this); EditText value = simpleField(fields, "字面事件或预设标识", slot.getLiteral() == null ? "" : slot.getLiteral());
        new android.app.AlertDialog.Builder(this).setTitle(slot.getName() + " 字符串源").setMessage(".lua 后缀或命令预设会保留,但绝不会在预览中执行。").setView(fields).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> commitKeyEventChange(key, document -> ThemeKeyEvents.updateString(document, key.sourcePath, slot.getName(), value.getText().toString()), "已更新 " + slot.getName() + " 字符串事件")).show();
    }

    private void editInlineKeyEvent(ThemeEditorModel.Key key, ThemeKeyEvents.Slot slot) {
        ThemePresetEvents.Event event = slot.getEvent() == null ? new ThemePresetEvents.Event(slot.getName(), "", "", "", "", "", "", "", "", "", "", java.util.Collections.emptyList(), "", false, false, true, null) : slot.getEvent();
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); EditText label = simpleField(fields, "标签(label)", event.getLabel()); EditText send = simpleField(fields, "发送按键(send)", event.getSend()); EditText text = simpleField(fields, "文本(text)", event.getText()); EditText commit = simpleField(fields, "上屏文本(commit)", event.getCommit()); EditText command = simpleField(fields, "命令(command,绝不执行)", event.getCommand()); EditText option = simpleField(fields, "选项(option)", event.getOption()); EditText select = simpleField(fields, "选择(select)", event.getSelect()); EditText toggle = simpleField(fields, "切换(toggle)", event.getToggle()); EditText preview = simpleField(fields, "预览(preview)", event.getPreview()); EditText description = simpleField(fields, "说明(description)", event.getDescription()); EditText states = simpleField(fields, "状态(states):每行一个;\\0 表示空值,\\n 表示内嵌换行", formatEventStates(event.getStates())); states.setSingleLine(false); states.setMinLines(3); EditText shiftLock = simpleField(fields, "Shift 锁定(shift_lock)", event.getShiftLock()); EditText index = simpleField(fields, "索引(index,保留;效果不可靠)", event.getIndex() == null ? "" : event.getIndex().toString()); android.widget.CheckBox repeatable = new android.widget.CheckBox(this); repeatable.setText("可重复(repeatable)"); repeatable.setChecked(event.getRepeatable()); fields.addView(repeatable); android.widget.CheckBox sticky = new android.widget.CheckBox(this); sticky.setText("保持(sticky)"); sticky.setChecked(event.getSticky()); fields.addView(sticky); android.widget.CheckBox functional = new android.widget.CheckBox(this); functional.setText("功能键(functional)"); functional.setChecked(event.getFunctional()); fields.addView(functional); android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle(slot.getName() + " 内联事件").setMessage("仅静态表;任何字段都不会执行。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("应用", (dialog, which) -> { try { java.util.ArrayList<String> nextStates = parseEventStates(states.getText().toString()); Double nextIndex = index.getText().toString().trim().isEmpty() ? null : Double.valueOf(index.getText().toString().trim()); ThemePresetEvents.Event next = new ThemePresetEvents.Event(slot.getName(), send.getText().toString(), text.getText().toString(), commit.getText().toString(), command.getText().toString(), option.getText().toString(), select.getText().toString(), toggle.getText().toString(), label.getText().toString(), preview.getText().toString(), description.getText().toString(), nextStates, shiftLock.getText().toString().trim(), repeatable.isChecked(), sticky.isChecked(), functional.isChecked(), nextIndex); commitKeyEventChange(key, document -> ThemeKeyEvents.updateInline(document, key.sourcePath, slot.getName(), next), "已更新 " + slot.getName() + " 内联事件;未执行任何操作"); } catch (Exception error) { workspace.setStatus("内联事件被阻止:" + safeErrorMessage(error)); } }).show();
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
            if (!ensureAssetWritable() || !(repository instanceof DirectoryThemeProjectRepository)) return; ThemeProjectFile file = ((DirectoryThemeProjectRepository) repository).getSelected(); if (file.getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("请先打开键盘文件"); String latest = new String(readFileBytes(file.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); String loaded = com.osfans.trime.editor.core.ThemeLuaWriter.INSTANCE.write(editor.getDocument(), com.osfans.trime.editor.core.ThemeWriteMode.HYBRID); if (!ThemeSaveCoordinator.Companion.fingerprint(latest).equals(ThemeSaveCoordinator.Companion.fingerprint(loaded))) throw new IOException("键盘文件已在编辑器外变化,编辑此按键前请重新加载"); com.osfans.trime.editor.core.ThemeDocument document = ThemeKeyEvents.parseDocument(latest); if (document.get(key.sourcePath) == null) throw new IOException("按键源码路径已变化,请重新加载键盘"); String updated = ThemeKeyEvents.verifiedSource(mutation.apply(document)); java.util.LinkedHashMap<File, String> changes = new java.util.LinkedHashMap<>(), originals = new java.util.LinkedHashMap<>(); changes.put(file.getFile(), updated); originals.put(file.getFile(), latest); applyProjectSourceTransaction(changes, originals); workspace.setStatus(success);
        } catch (Exception error) { workspace.setStatus("按键事件更新被阻止:" + safeErrorMessage(error)); }
    }

    private void copyStyleEntity(ThemeEditorModel.Key key) {
        try {
            if (project == null || editor == null || !(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("请先打开项目键盘");
            ThemeProjectFile styleSource = resolvedStyleSource(editor.getDocument()); if (styleSource == null) throw new IOException("无法静态解析键盘样式资源");
            String source = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.ArrayList<String> entityIds = new java.util.ArrayList<>(); for (ThemeStyleEntities.Entry entry : ThemeStyleEntities.list(source)) entityIds.add(entry.getId());
            String styleId = ThemeKeyStyleBatch.effectiveStyleId(key, entityIds); ThemeStyleEntities.Snapshot snapshot = ThemeStyleEntities.extract(source, styleId);
            workspace.storeStyleEntityClipboard(snapshot);
        } catch (Exception error) { workspace.setStatus("样式实体复制被阻止:" + safeErrorMessage(error)); }
    }

    private void promptPasteStyleEntity(java.util.List<ThemeEditorModel.Key> keys) {
        if (!ensureAssetWritable()) return;
        try {
            if (keys == null || keys.isEmpty()) throw new IOException("请先选择一个或多个目标按键");
            ThemeEditorClipboard.Payload payload = workspace.styleEntityClipboard();
            if (payload == null || payload.styleEntity == null) throw new IOException("私有剪贴板中没有完整的样式实体");
            if (project == null || editor == null || !(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("请先打开目标项目键盘");
            ThemeProjectFile styleSource = resolvedStyleSource(editor.getDocument()); if (styleSource == null) throw new IOException("无法静态解析目标键盘样式资源");
            boolean crossProject = workspace.isCrossProjectClipboard(payload); ThemeStyleEntities.Snapshot snapshot = payload.styleEntity;
            java.util.ArrayList<String> missing = missingStyleEntityResources(styleSource, snapshot);
            if (!missing.isEmpty()) throw new IOException("目标项目缺少样式资源:" + android.text.TextUtils.join(", ", missing));
            String original = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            String expectedLocal = ThemeSaveCoordinator.Companion.fingerprint(original), expectedRemote = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(styleSource.getFile());
            LinearLayout fields = new LinearLayout(this); EditText id = simpleField(fields, "新样式实体标识", snapshot.getId() + "_copy");
            String message = "来源实体:" + snapshot.getId() + "\n目标样式资源:" + styleSource.getName() + "\n目标按键:" + keys.size() + " 个" + (snapshot.getCloneParent() == null ? "" : "\n克隆依赖:" + snapshot.getCloneParent()) + (snapshot.getReferencedResources().isEmpty() ? "" : "\n资源:" + android.text.TextUtils.join(", ", snapshot.getReferencedResources())) + (crossProject ? "\n\n跨项目粘贴不会保留 URI/路径元数据;依赖已按名称核对。" : "");
            new android.app.AlertDialog.Builder(this).setTitle("粘贴完整样式实体").setMessage(message).setView(fields).setNegativeButton("取消", null).setPositiveButton("粘贴", (dialog, which) -> pasteStyleEntity(styleSource, snapshot, id.getText().toString().trim(), keys, expectedLocal, expectedRemote)).show();
        } catch (Exception error) { workspace.setStatus("样式实体粘贴被阻止:" + safeErrorMessage(error)); Toast.makeText(this, safeErrorMessage(error), Toast.LENGTH_LONG).show(); }
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
            if (!expectedLocal.equals(ThemeSaveCoordinator.Companion.fingerprint(original))) throw new IOException("复核后目标样式已变化,请重新打开粘贴操作");
            if (importedProjectTreeUri != null && (expectedRemote == null || !expectedRemote.equals(fingerprintImportedProjectFile(styleSource.getFile())))) throw new IOException("复核后导入目标样式已变化,请重新加载项目");
            String updated = ThemeStyleEntities.paste(original, snapshot, targetId); new FileThemeProjectRepository(styleSource.getFile()).write(updated);
            try { mirrorExistingProjectFile(styleSource.getFile()); refreshProjectAfterAssetMutation(); }
            catch (Exception error) { try (FileOutputStream output = new FileOutputStream(styleSource.getFile(), false)) { output.write(backup); output.getFD().sync(); } if (importedProjectTreeUri != null) try { writeImportedProjectFile(styleSource.getFile(), original); } catch (Exception restoreError) { error.addSuppressed(restoreError); } try { refreshProjectAfterAssetMutation(); } catch (Exception refreshError) { error.addSuppressed(refreshError); } throw error; }
            workspace.applyStyleEntityReference(keys, targetId); workspace.setStatus("已粘贴完整样式实体 " + targetId + " 并校验了依赖");
        } catch (Exception error) { workspace.setStatus("样式实体粘贴失败,未覆盖较新数据:" + safeErrorMessage(error)); Toast.makeText(this, "无法粘贴样式实体", Toast.LENGTH_LONG).show(); }
    }

    private void reviewBatchStyleEntities(java.util.List<ThemeEditorModel.Key> keys, String background, String textColor) {
        if (!ensureWritable()) return;
        try {
            if (project == null || editor == null || !(repository instanceof DirectoryThemeProjectRepository) || ((DirectoryThemeProjectRepository) repository).getSelected().getKind() != ThemeProjectFile.Kind.KEYBOARD) throw new IOException("请先打开项目键盘");
            ThemeProjectFile styleSource = resolvedStyleSource(editor.getDocument());
            if (styleSource == null) throw new IOException("无法静态解析键盘样式资源");
            String styleSourceText = new String(readFileBytes(styleSource.getFile(), 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8); java.util.ArrayList<String> entityIds = new java.util.ArrayList<>(); for (ThemeStyleEntities.Entry entry : ThemeStyleEntities.list(styleSourceText)) entityIds.add(entry.getId());
            java.util.LinkedHashSet<String> styleIds = new java.util.LinkedHashSet<>(); for (ThemeEditorModel.Key key : keys) styleIds.add(ThemeKeyStyleBatch.effectiveStyleId(key, entityIds));
            validateBatchStyleBackground(styleSource, background);
            ThemeKeyStyleBatch.Report report = ThemeKeyStyleBatch.references(project, styleIds, styleSource.getName());
            String localFingerprint = ThemeSaveCoordinator.Companion.fingerprint(styleSourceText);
            String remoteFingerprint = importedProjectTreeUri == null ? null : fingerprintImportedProjectFile(styleSource.getFile());
            StringBuilder message = new StringBuilder("样式资源:").append(styleSource.getName()).append("\n所选按键:").append(keys.size()).append("\n样式实体:").append(android.text.TextUtils.join(", ", report.getStyleIds())).append("\n已保存项目中使用该资源的引用数:").append(report.getTotalReferences());
            for (ThemeKeyStyleBatch.Reference reference : report.getReferences()) message.append("\n• ").append(reference.getKeyboardId()).append(": ").append(reference.getCount()).append(" 个按键或容器节点");
            if (!report.getUncertainKeyboardIds().isEmpty()) message.append("\n引用无法确定的动态或无效布局:").append(android.text.TextUtils.join(", ", report.getUncertainKeyboardIds()));
            message.append("\n\n共享这些样式实体的全部已列出按键都会继承颜色或背景变化。存在不确定键盘时,静态计数可能不完整。不会执行 Lua 或回调。");
            new android.app.AlertDialog.Builder(this).setTitle("修改共享样式实体?").setMessage(message.toString()).setNegativeButton("取消", null).setPositiveButton("应用事务", (dialog, which) -> applyBatchStyleEntities(styleSource, styleIds, background, textColor, localFingerprint, remoteFingerprint)).show();
        } catch (Exception error) { workspace.setStatus("样式批量更新被阻止:" + safeErrorMessage(error)); Toast.makeText(this, safeErrorMessage(error), Toast.LENGTH_LONG).show(); }
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
        if (background.startsWith("/") || background.startsWith("\\") || background.contains("..")) throw new IOException("背景资源必须使用项目相对路径");
        File fromStyle = new File(styleSource.getFile().getParentFile(), background), fromProject = new File(project.getRoot(), background);
        String root = project.getRoot().getCanonicalPath(); File resolved = fromStyle.isFile() ? fromStyle : fromProject;
        if (!resolved.isFile() || !resolved.getCanonicalPath().startsWith(root + File.separator)) throw new IOException("背景资源在项目中不存在:" + background);
    }

    private void applyBatchStyleEntities(ThemeProjectFile styleSource, java.util.Set<String> styleIds, String background, String textColor, String expectedLocalFingerprint, String expectedRemoteFingerprint) {
        byte[] backup = null;
        try {
            backup = readFileBytes(styleSource.getFile(), 4L * 1024 * 1024); String original = new String(backup, java.nio.charset.StandardCharsets.UTF_8);
            if (!expectedLocalFingerprint.equals(ThemeSaveCoordinator.Companion.fingerprint(original))) throw new IOException("复核后样式文件已变化,请重新打开批量编辑器");
            if (importedProjectTreeUri != null && (expectedRemoteFingerprint == null || !expectedRemoteFingerprint.equals(fingerprintImportedProjectFile(styleSource.getFile())))) throw new IOException("复核后导入样式文件已变化,请重新加载项目");
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
        } catch (Exception error) { workspace.setStatus("样式批量更新失败,未覆盖较新数据:" + safeErrorMessage(error)); Toast.makeText(this, "无法更新样式实体", Toast.LENGTH_LONG).show(); }
    }

    private String currentFileDisplayName() {
        if (repository instanceof DirectoryThemeProjectRepository) return ((DirectoryThemeProjectRepository) repository).getSelected().getFile().getName();
        if (currentUri != null && currentUri.getLastPathSegment() != null) return new File(currentUri.getLastPathSegment()).getName();
        return "当前主题文件";
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
        TextView toolbarHeightNote = new TextView(this); toolbarHeightNote.setText("工具栏视图(ToolbarView)使用候选高度(candidate.height)填充;当前运行时不使用工具栏高度(toolbar.height)。"); fields.addView(toolbarHeightNote);
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
        compatibility.setText("剪贴板键盘视图(ClipboardKeyboardView)使用剪贴板按键(clipboard.key)渲染标签和工具按钮;瀑布流适配器(WaterfallAdapter)使用剪贴板项目(clipboard.item)渲染剪贴板及短语行。");
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
                            throw new IllegalArgumentException("更新后的源码未通过静态解析校验:" + diagnostic.getMessage());
                    editor.replaceDocument(parsed.getDocument()); workspace.setModel(stylePreviewModel(editor.getDocument()));
                    viewModel.recordEdit(); workspace.setStatus(success); dialog.dismiss();
                } catch (Exception error) {
                    workspace.setStatus("组件样式更新被阻止:" + safeErrorMessage(error));
                    Toast.makeText(this, safeErrorMessage(error), Toast.LENGTH_LONG).show();
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
                            throw new IllegalArgumentException("更新后的源码未通过静态解析校验:" + diagnostic.getMessage());
                    }
                    editor.replaceDocument(parsed.getDocument());
                    workspace.setModel(stylePreviewModel(editor.getDocument()));
                    viewModel.recordEdit();
                    workspace.setStatus("预编辑/编码窗口字段已应用;保存后生效");
                    dialog.dismiss();
                } catch (Exception error) {
                    workspace.setStatus("预编辑/编码窗口更新被阻止:" + safeErrorMessage(error));
                    Toast.makeText(this, safeErrorMessage(error), Toast.LENGTH_LONG).show();
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
            TextView blocked = new TextView(this); blocked.setText("仅源代码可编辑:" + safeErrorMessage(error)); blocked.setEnabled(false); parent.addView(blocked);
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
            if (value < 0 || value > 0xffffffffL) throw new NumberFormatException("超出无符号 32 位范围");
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(path + " 必须是无符号 #AARRGGBB、0xAARRGGBB 或十进制颜色值");
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
        if (text.isEmpty()) throw new IllegalArgumentException(input.path + " 不能为空;如需移除字段请选择继承");
        if (input.original.getExplicit() && text.equals(input.initialText)) return source;
        if (input.kind == ComponentScalarInput.COLOR_OR_RESOURCE) return ThemeComponentStyles.updateColorOrResource(source, input.path, text);
        if (input.kind == ComponentScalarInput.COLOR) {
            long color = parseUnsignedColor(text, input.path);
            return ThemeComponentStyles.updateColorOrResource(source, input.path, color);
        }
        double number = Double.parseDouble(text);
        if (input.kind == ComponentScalarInput.NUMBER && number != Math.rint(number))
            throw new IllegalArgumentException(input.path + " 在 Trime2 运行时中必须为整数");
        return ThemeComponentStyles.updateNumber(source, input.path, number);
    }

    private void showStyleEditorState() { viewModel.setCurrentPage("style_editor"); }
    private void showStyleEditor() {
        showStyleEditorState();
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
            if (changed) { viewModel.recordEdit(); workspace.setStatus("样式属性已应用;保存后生效"); }
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

    private void showCodeEditorState() { viewModel.setCurrentPage("code_editor"); }
    private void showCodeEditor() {
        showCodeEditorState();
        if (!ensureWritable()) return;
        if (repository == null || editor == null) { Toast.makeText(this, "编辑源代码前请先打开 Lua 文件", Toast.LENGTH_LONG).show(); return; }
        final String[] openedSource = {editor.source()};
        final ThemeEditorModel[] openedModel = {workspace.getModel()};
        final boolean[] closeWithoutBuffer = {false};
        final String restoredBuffer = readCodeBuffer(openedSource[0]);
        final EditText source = new EditText(this);
        source.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        source.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        source.setSingleLine(false); source.setText(restoredBuffer == null ? openedSource[0] : restoredBuffer); source.setSelection(source.length());
        int padding = (int) (16 * getResources().getDisplayMetrics().density); source.setPadding(padding, padding, padding, padding);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Lua 源代码(字段名保留原名,说明见差异摘要)")
                .setView(source)
                .setNegativeButton("取消", null)
                .setNeutralButton("文件与查找", null)
                .setPositiveButton("应用", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> showCodeTools(dialog, source, openedSource, closeWithoutBuffer));
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(source.getText().toString());
                boolean hasErrors = false;
                for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) { hasErrors = true; break; }
                if (hasErrors) { new android.app.AlertDialog.Builder(this).setTitle("Lua 代码无法应用").setMessage(luaDiagnosticSummary(parsed.getDiagnostics())).setPositiveButton("返回代码", null).show(); return; }
                if (luaSourceChanged(openedSource[0])) { showLuaSourceConflict(dialog, source, openedSource, openedModel); return; }
                com.osfans.trime.editor.core.ThemeDocument candidateDocument = parsed.getDocument();
                String candidateRoot = findLayoutRoot(candidateDocument);
                boolean candidateEditable = candidateRoot != null && !containsRawLua(candidateDocument.get(candidateRoot));
                boolean candidateStructuredEditable = isCurrentStyleFile() || candidateEditable;
                ThemeEditorModel candidateModel = isCurrentStyleFile() ? luaStylePreviewModel(candidateDocument) : candidateEditable ? toUiModel(candidateDocument) : new ThemeEditorModel();
                String summary = luaDiffSummary(openedSource[0], source.getText().toString(), openedModel[0], candidateModel, candidateDocument, parsed.getDiagnostics(), candidateStructuredEditable, isCurrentStyleFile());
                new android.app.AlertDialog.Builder(this).setTitle("确认 Lua 同步").setMessage(summary)
                        .setNegativeButton("取消", null)
                        .setNeutralButton("保留可视化模型", (d, w) -> { dialog.dismiss(); workspace.setStatus("已保留可视化模型,Lua 代码未应用"); })
                        .setPositiveButton("应用代码", (d, w) -> {
                            if (luaSourceChanged(openedSource[0])) { showLuaSourceConflict(dialog, source, openedSource, openedModel); return; }
                            if (migrationUndoDocument != null) { workspace.setStatus("已有尚未保存的源码级事务;请先保存或重新加载后再应用代码"); return; }
                            com.osfans.trime.editor.core.ThemeDocument previousDocument = editor.getDocument();
                            migrationUndoDocument = previousDocument; migrationRedoDocument = candidateDocument;
                            migrationSourceMode = openedModel[0].layoutMode; migrationTargetMode = candidateModel.layoutMode;
                            editor.replaceDocument(candidateDocument); applyingMigration = true;
                            boolean applied;
                            try { applied = workspace.replaceModelAsAtomic(candidateModel, "Lua 代码已作为一个可撤销事务应用,保存后生效"); }
                            finally { applyingMigration = false; }
                            if (!applied) { editor.replaceDocument(previousDocument); clearMigrationHistory(); workspace.setStatus("工作区拒绝替换,Lua 代码未应用"); return; }
                            layoutEditable = candidateEditable; closeWithoutBuffer[0] = true; deleteCodeBuffer(); dialog.dismiss();
                        }).show();
            });
        });
        dialog.setOnDismissListener(ignored -> {
            if (closeWithoutBuffer[0]) deleteCodeBuffer();
            else persistCodeBuffer(openedSource[0], source.getText().toString());
            if (!isChangingConfigurations()) viewModel.setCurrentPage("editor");
        });
        dialog.show();
        if (restoredBuffer != null) workspace.setStatus("已恢复当前文件尚未应用的代码缓冲区");
    }

    private void showCodeTools(android.app.AlertDialog codeDialog, EditText source, String[] openedSource, boolean[] closeWithoutBuffer) {
        String[] tools = {"选择项目 Lua 文件", "查找并替换当前文件"};
        new android.app.AlertDialog.Builder(this).setTitle("代码工具").setItems(tools, (dialog, which) -> {
            if (which == 0) showCodeFilePicker(codeDialog, source, openedSource[0], closeWithoutBuffer); else showCodeFindReplace(source);
        }).setNegativeButton("关闭", null).show();
    }

    private void showCodeFilePicker(android.app.AlertDialog codeDialog, EditText source, String originalSource, boolean[] closeWithoutBuffer) {
        if (project == null) { workspace.setStatus("当前不是主题项目,没有项目 Lua 文件列表"); return; }
        java.util.ArrayList<ThemeProjectFile> files = new java.util.ArrayList<>();
        files.add(new ThemeProjectFile("main.lua", project.getMainFile(), ThemeProjectFile.Kind.MAIN)); files.addAll(project.getStyles()); files.addAll(project.getKeyboards());
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) { ThemeProjectFile file = files.get(i); labels[i] = (file.getKind() == ThemeProjectFile.Kind.MAIN ? "主题入口 / " : file.getKind() == ThemeProjectFile.Kind.STYLE ? "样式 / " : "键盘 / ") + file.getName(); }
        new android.app.AlertDialog.Builder(this).setTitle("项目 Lua 文件").setItems(labels, (dialog, which) -> {
            Runnable switchFile = () -> { closeWithoutBuffer[0] = true; deleteCodeBuffer(); codeDialog.dismiss(); requestProjectFileSwitch(files.get(which)); };
            if (ThemeSaveCoordinator.Companion.fingerprint(originalSource).equals(ThemeSaveCoordinator.Companion.fingerprint(source.getText().toString()))) switchFile.run();
            else new android.app.AlertDialog.Builder(this).setTitle("代码缓冲区尚未应用").setMessage("切换文件会丢弃当前代码缓冲区。是否继续?").setNegativeButton("取消", null).setPositiveButton("丢弃并切换", (d, w) -> switchFile.run()).show();
        }).setNegativeButton("关闭", null).show();
    }

    private void showCodeFindReplace(EditText source) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        EditText find = simpleField(fields, "查找文本(仅当前文件)", ""); EditText replace = simpleField(fields, "替换为", ""); TextView result = new TextView(this); result.setPadding(0, 12, 0, 0); fields.addView(result);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle("查找并替换").setView(fields).setNegativeButton("关闭", null).setNeutralButton("查找", null).setPositiveButton("全部替换", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> { String needle = find.getText().toString(); if (needle.isEmpty()) { result.setText("查找内容不能为空"); return; } int count = countLiteral(source.getText().toString(), needle); result.setText("当前文件匹配 " + count + " 处"); });
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> { String needle = find.getText().toString(); if (needle.isEmpty()) { result.setText("查找内容不能为空"); return; } String value = source.getText().toString(); int count = countLiteral(value, needle); source.setText(value.replace(needle, replace.getText().toString())); source.setSelection(source.length()); result.setText("已替换 " + count + " 处;仍需返回代码页确认应用"); });
        });
        dialog.show();
    }

    private static int countLiteral(String value, String needle) {
        int count = 0, offset = 0; while ((offset = value.indexOf(needle, offset)) >= 0) { count++; offset += needle.length(); } return count;
    }

    private boolean luaSourceChanged(String openedSource) {
        return !ThemeSaveCoordinator.Companion.fingerprint(openedSource).equals(ThemeSaveCoordinator.Companion.fingerprint(editor.source()));
    }

    private void showLuaSourceConflict(android.app.AlertDialog codeDialog, EditText source, String[] openedSource, ThemeEditorModel[] openedModel) {
        new android.app.AlertDialog.Builder(this).setTitle("源代码发生冲突")
                .setMessage("打开代码编辑器后,当前结构化模型或源代码已经变化。请选择如何处理,系统不会静默覆盖。")
                .setNegativeButton("取消", null)
                .setNeutralButton("重新打开代码", (dialog, which) -> { openedSource[0] = editor.source(); openedModel[0] = workspace.getModel(); source.setText(openedSource[0]); source.setSelection(source.length()); })
                .setPositiveButton("保留当前模型", (dialog, which) -> { codeDialog.dismiss(); workspace.setStatus("已保留当前可视化模型,代码未应用"); })
                .show();
    }

    private ThemeEditorModel luaStylePreviewModel(com.osfans.trime.editor.core.ThemeDocument style) {
        ThemeEditorModel model = ThemeEditorModel.sample();
        model.layoutMode = ThemeEditorModel.LayoutMode.NONE;
        applyStyleDocument(model, style);
        return model;
    }

    private static String luaDiagnosticSummary(java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) return "没有发现 Lua 诊断。";
        StringBuilder text = new StringBuilder("Lua 诊断共 ").append(diagnostics.size()).append(" 条:");
        int shown = 0;
        for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : diagnostics) {
            if (shown++ >= 8) { text.append("\n其余诊断已省略。"); break; }
            String level = diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR ? "错误" : diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.WARNING ? "警告" : "提示";
            text.append("\n第").append(diagnostic.getLine()).append("行").append(level).append(":").append(luaDiagnosticText(diagnostic.getMessage()));
        }
        return text.toString();
    }

    private static String luaDiagnosticText(String message) {
        if (message == null || message.isEmpty()) return "Lua 代码无法解析";
        if (message.contains("Lua 长括号未闭合") || message.contains("Unclosed Lua long bracket")) return "长括号字符串未闭合";
        if (message.contains("Lua 字符串未闭合") || message.contains("Unterminated Lua string")) return "字符串未闭合";
        if (message.contains("不匹配") || message.contains("Unmatched")) return "括号不匹配";
        if (message.contains("未闭合") || message.contains("Unclosed")) return "括号未闭合";
        if (message.contains("不支持") || message.contains("Unsupported")) return "不支持的 Lua 表达式,将按原始代码保留";
        return "Lua 语法或字段内容异常";
    }

    private static String luaDiffSummary(String before, String after, ThemeEditorModel oldModel, ThemeEditorModel next, com.osfans.trime.editor.core.ThemeDocument document, java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics, boolean editable, boolean styleDocument) {
        com.osfans.trime.editor.core.ParseResult oldParsed = new ThemeLuaParser().parse(before);
        String oldRoot = findLayoutRoot(oldParsed.getDocument());
        String newRoot = findLayoutRoot(document);
        StringBuilder text = new StringBuilder();
        text.append("字符数:").append(before.length()).append(" → ").append(after.length()).append(";行数:").append(lineCount(before)).append(" → ").append(lineCount(after));
        text.append("\n诊断数量:").append(oldParsed.getDiagnostics().size()).append(" → ").append(diagnostics == null ? 0 : diagnostics.size());
        text.append("\nRawLuaNode 数量(动态或未建模 Lua):").append(rawLuaCount(oldParsed.getDocument())).append(" → ").append(rawLuaCount(document));
        text.append("\n布局根:").append(luaField(oldRoot, "无")).append(" → ").append(luaField(newRoot, "无"));
        text.append(";布局模式:").append(layoutModeText(oldModel == null ? ThemeEditorModel.LayoutMode.NONE : oldModel.layoutMode)).append(" → ").append(layoutModeText(next.layoutMode));
        text.append("\n按键数量:").append(count(oldModel, 0)).append(" → ").append(count(next, 0));
        text.append(";行数量:").append(count(oldModel, 1)).append(" → ").append(count(next, 1));
        text.append(";Flex 容器数量:").append(count(oldModel, 2)).append(" → ").append(count(next, 2));
        text.append(";分页数量:").append(count(oldModel, 3)).append(" → ").append(count(next, 3));
        boolean wasEditable = styleDocument || oldRoot != null && !containsRawLua(oldParsed.getDocument().get(oldRoot));
        text.append("\n结构化编辑能力:").append(editable ? "可用" : wasEditable ? "将失去(动态 Lua 仍不会执行,请使用 Lua 源代码编辑)" : "仍不可用(动态 Lua 仍不会执行)");
        return text.toString();
    }

    private static int lineCount(String text) { return text == null || text.isEmpty() ? 0 : text.split("\\n", -1).length; }
    private static String luaField(String value, String fallback) { return value == null ? fallback : value + "(布局根字段 " + value + ")"; }
    private static String layoutModeText(ThemeEditorModel.LayoutMode mode) { if (mode == ThemeEditorModel.LayoutMode.ROWS) return "行布局(rows)"; if (mode == ThemeEditorModel.LayoutMode.FLEX_BOX) return "弹性盒布局(flex_box)"; if (mode == ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) return "绝对键布局(keys)"; if (mode == ThemeEditorModel.LayoutMode.KEY_MAPS) return "分页键映射(key_maps)"; return "无"; }
    private static int count(ThemeEditorModel model, int kind) { if (model == null) return 0; if (kind == 0) return model.keys.size(); if (kind == 1) return model.rows.size(); if (kind == 2) return model.flexContainers.size(); return model.keyMapPages.size(); }
    private static int rawLuaCount(ThemeValue value) { if (value instanceof ThemeValue.RawLuaNode) return 1; if (value instanceof ThemeValue.LuaTable) { int total = 0; for (ThemeValue child : ((ThemeValue.LuaTable) value).getFields().values()) total += rawLuaCount(child); return total; } return 0; }
    private static int rawLuaCount(com.osfans.trime.editor.core.ThemeDocument document) { int total = 0; for (com.osfans.trime.editor.core.ThemeNode node : document.getNodes()) total += rawLuaCount(node.getValue()); return total; }

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
        try {
            validateProjectForInstall(); DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri); if (tree == null || !tree.canWrite()) throw new IOException("安装目标不可写");
            String themeName = installThemeName(), parent = tree.getName() == null ? "已授权目录" : tree.getName(); DocumentFile existing = tree.findFile(themeName); if (existing != null && !existing.isDirectory()) throw new IOException("安装目标名称已被文件占用");
            java.util.Map<String, Long> manifest = fileManifest(project.getRoot()); long bytes = 0; for (long size : manifest.values()) bytes += size;
            String backupName = existing == null ? "首次安装,无需覆盖备份" : themeName + ".backup-<时间>";
            String message = "目标:" + parent + "/" + themeName + "\n操作:" + (existing == null ? "创建新主题目录" : "覆盖现有同名主题") + "\n预计写入:" + manifest.size() + " 个文件 / " + bytes + " 字节\n备份:" + backupName + "\n校验:文件数、大小、SHA-256、main.lua、默认样式、默认键盘和资源完整性\n可用空间:SAF 提供方未公开可靠容量;空间不足会停止复制并回滚。\n\n安装不会执行用户 Lua、脚本、命令或回调。";
            new android.app.AlertDialog.Builder(this).setTitle("安装前确认").setMessage(message).setNegativeButton("取消", null).setPositiveButton(existing == null ? "确认安装" : "备份并覆盖", (dialog, which) -> performInstallToTree(treeUri)).show();
        } catch (Exception error) { workspace.setStatus("安装预检失败:" + safeErrorMessage(error)); Toast.makeText(this, "无法准备安装", Toast.LENGTH_LONG).show(); }
    }

    private String installThemeName() {
        String name = projectDisplayName == null || projectDisplayName.trim().isEmpty() ? project.getRoot().getName() : projectDisplayName.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_"); return name.isEmpty() ? "theme" : name;
    }

    private void performInstallToTree(Uri treeUri) {
        DocumentFile target = null, backup = null;
        boolean targetExisted = false, targetCreated = false, backupReady = false, targetMutated = false;
        java.util.Map<String, Long> backupManifest = null;
        java.util.Map<String, String> backupHashes = null;
        try {
            validateProjectForInstall(); DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri); if (tree == null || !tree.canWrite()) throw new IOException("安装目标不可写");
            String themeName = installThemeName(); target = tree.findFile(themeName); targetExisted = target != null; if (target != null && !target.isDirectory()) throw new IOException("安装目标名称已被文件占用");
            if (target != null) {
                backup = tree.createDirectory(themeName + ".backup-" + System.currentTimeMillis()); if (backup == null) throw new IOException("无法创建安装备份");
                copyDocumentToDocument(target, backup); backupManifest = documentManifest(backup); backupHashes = documentHashManifest(backup);
                if (!backupManifest.equals(documentManifest(target)) || !backupHashes.equals(documentHashManifest(target))) throw new IOException("备份大小或 SHA-256 校验失败");
                backupReady = true;
            } else {
                target = tree.createDirectory(themeName); if (target == null) throw new IOException("无法创建目标主题目录");
                targetCreated = true;
            }
            writeInstallJournal(backupReady ? "BACKUP_READY" : "TARGET_CREATED", target, backupReady ? backup : null, null);
            targetMutated = true;
            clearDocumentDirectory(target); copyProjectToDocument(project.getRoot(), target);
            java.util.Map<String, Long> expected = fileManifest(project.getRoot()), installed = documentManifest(target); java.util.Map<String, String> expectedHashes = fileHashManifest(project.getRoot()), installedHashes = documentHashManifest(target);
            if (!expected.equals(installed) || !expectedHashes.equals(installedHashes)) throw new IOException("已安装文件数量、大小或 SHA-256 校验失败");
            validateInstalledDocumentProject(target); writeInstallJournal("COMPLETED", target, backupReady ? backup : null, null);
            lastInstallTarget = target; lastInstallBackup = backupReady ? backup : null; lastBackupManifest = backupReady ? backupManifest : null; lastBackupHashManifest = backupReady ? backupHashes : null; invalidateOptionsMenu();
            workspace.setStatus("主题已安装并重新导入校验:" + themeName + ";请在 Trime2 中手动重新部署或切换主题刷新" + (backupReady ? ";备份 " + backup.getName() : ""));
            new android.app.AlertDialog.Builder(this).setTitle("安装完成").setMessage("目标文件已按数量、大小和 SHA-256 回读校验,并重新静态验证主题入口。当前没有可靠公开刷新接口,请返回 Trime2 设置执行重新部署,或切换到其他主题后再切回。编辑器不会伪造刷新成功。").setPositiveButton("知道了", null).show();
        } catch (Exception error) {
            boolean recovered = false;
            boolean originalUntouched = targetExisted && !targetMutated;
            if (targetExisted && targetMutated && backupReady) recovered = rollbackInstall(target, backup, backupManifest, backupHashes);
            else if (targetCreated && target != null) try { recovered = target.delete(); } catch (Exception ignored) { }

            boolean recoveryAvailable = !recovered && targetMutated && backupReady && target != null && backup != null;
            if (recoveryAvailable) {
                writeInstallJournal("FAILED", target, backup, safeErrorMessage(error));
                lastInstallTarget = target; lastInstallBackup = backup; lastBackupManifest = backupManifest; lastBackupHashManifest = backupHashes; invalidateOptionsMenu();
            } else if (targetMutated) {
                writeInstallJournal(recovered ? "ROLLED_BACK" : "FAILED", target, backupReady ? backup : null, safeErrorMessage(error));
            } else if (backup != null) {
                try { backup.delete(); } catch (Exception ignored) { }
            }

            String recoveryStatus;
            String recoveryMessage;
            if (originalUntouched) {
                recoveryStatus = ";备份未完成,原主题未被修改";
                recoveryMessage = "安装在覆盖前停止,原主题保持不变。不完整备份不会用于回滚,可检查存储空间或授权后重试。";
            } else if (target == null) {
                recoveryStatus = ";安装目标未被修改";
                recoveryMessage = "安装在创建或打开目标前停止,没有修改目标主题。请检查目录授权或项目诊断后重试。";
            } else if (recovered && targetExisted) {
                recoveryStatus = ";备份已恢复并校验";
                recoveryMessage = "安装失败,原主题已从完整备份恢复并校验。可修正问题后重试。";
            } else if (recovered) {
                recoveryStatus = ";未完成的新目标已清理";
                recoveryMessage = "首次安装失败,未完成的新主题目录已清理。可修正问题后重试。";
            } else if (recoveryAvailable) {
                recoveryStatus = ";自动回滚失败,完整备份已保留";
                recoveryMessage = "安装失败且自动回滚未完成。已保留经过校验的完整备份,可立即再次回滚或保留现场稍后处理。";
            } else {
                recoveryStatus = ";未能清理未完成目标";
                recoveryMessage = "安装失败且未能清理未完成目标。当前没有可安全使用的覆盖备份,请保留现场并检查目标目录。";
            }
            workspace.setStatus("安装失败:" + safeErrorMessage(error) + recoveryStatus);
            showInstallFailureActions(treeUri, recoveryAvailable, recoveryMessage);
        }
    }

    private void showInstallFailureActions(Uri treeUri, boolean recoveryAvailable, String message) {
        java.util.ArrayList<String> actions = new java.util.ArrayList<>(); actions.add("重试安装"); if (recoveryAvailable) actions.add("立即从备份回滚"); actions.add("保留当前状态并关闭");
        new android.app.AlertDialog.Builder(this).setTitle("安装未完成").setMessage(message).setItems(actions.toArray(new String[0]), (dialog, which) -> { String action = actions.get(which); if (action.equals("重试安装")) installToTree(treeUri); else if (action.equals("立即从备份回滚")) rollbackLastInstall(false); }).setNegativeButton("关闭", null).show();
    }

    private void validateInstalledDocumentProject(DocumentFile target) throws IOException {
        File verifyRoot = new File(getCacheDir(), "theme-editor-install-verify-" + System.nanoTime());
        try {
            copyDocumentTree(target, verifyRoot); ThemeProject installed = ThemeProject.Companion.discover(verifyRoot); ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(installed, new ThemeLuaParser());
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry())) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("安装目标重新导入校验:" + diagnostic.getMessage());
            if (snapshot.getStyleSource() == null || snapshot.getKeyboardSource() == null) throw new IOException("安装目标默认样式或默认键盘无法解析");
        } finally { deleteDirectory(verifyRoot); }
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
        File root = source.getCanonicalFile(); java.util.LinkedHashMap<String, File> files = new java.util.LinkedHashMap<>();
        collectSafeProjectFiles(root, root, new java.util.HashSet<>(), files);
        for (java.util.Map.Entry<String, File> entry : files.entrySet()) {
            String[] parts = entry.getKey().split("/"); DocumentFile parent = destination;
            for (int i = 0; i < parts.length - 1; i++) { DocumentFile child = parent.findFile(parts[i]); if (child == null) child = parent.createDirectory(parts[i]); if (child == null || !child.isDirectory()) throw new IOException("无法创建 " + parts[i]); parent = child; }
            DocumentFile outputFile = parent.findFile(parts[parts.length - 1]); if (outputFile != null) outputFile.delete();
            outputFile = parent.createFile(mimeForName(parts[parts.length - 1]), parts[parts.length - 1]);
            if (outputFile == null) throw new IOException("无法创建 " + parts[parts.length - 1]); copyFileToDocument(entry.getValue(), outputFile);
        }
    }

    private static void collectSafeProjectFiles(File root, File current, java.util.Set<String> visited, java.util.Map<String, File> output) throws IOException {
        String rootPath = root.getCanonicalPath(), currentPath = current.getCanonicalPath();
        if (!(currentPath.equals(rootPath) || currentPath.startsWith(rootPath + File.separator)) || !current.getAbsolutePath().equals(currentPath)) throw new IOException("项目包含符号链接或根外目录");
        if (!visited.add(currentPath)) return;
        File[] files = current.listFiles(); if (files == null) return;
        for (File file : files) {
            String path = file.getCanonicalPath();
            if (!file.getAbsolutePath().equals(path) || !path.startsWith(rootPath + File.separator)) throw new IOException("项目包含符号链接或根外文件");
            if (file.isDirectory()) collectSafeProjectFiles(root, file, visited, output);
            else if (file.isFile()) output.put(path.substring(rootPath.length() + 1).replace(File.separatorChar, '/'), file);
        }
    }

    private void copyFileToDocument(File source, DocumentFile destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source); java.io.OutputStream output = getContentResolver().openOutputStream(destination.getUri(), "wt")) {
            if (output == null) throw new IOException("无法写入 " + source.getName()); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private void copyDocumentToDocument(DocumentFile source, DocumentFile destination) throws IOException {
        for (DocumentFile child : source.listFiles()) {
            String name = child.getName() == null ? "unnamed" : child.getName();
            if (child.isDirectory()) { DocumentFile dir = destination.createDirectory(name); if (dir == null) throw new IOException("无法备份 " + name); copyDocumentToDocument(child, dir); }
            else if (child.isFile()) { DocumentFile file = destination.createFile(child.getType() == null ? mimeForName(name) : child.getType(), name); if (file == null) throw new IOException("无法备份 " + name); try (InputStream input = getContentResolver().openInputStream(child.getUri()); java.io.OutputStream output = getContentResolver().openOutputStream(file.getUri(), "wt")) { if (input == null || output == null) throw new IOException("无法复制备份文件 " + name); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); } }
        }
    }

    private static String mimeForName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT); if (lower.endsWith(".lua")) return "text/x-lua"; if (lower.endsWith(".png")) return "image/png"; if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg"; if (lower.endsWith(".ogg")) return "audio/ogg"; if (lower.endsWith(".mp3")) return "audio/mpeg"; if (lower.endsWith(".ttf")) return "font/ttf"; if (lower.endsWith(".otf")) return "font/otf"; return "application/octet-stream";
    }

    private void clearDocumentDirectory(DocumentFile directory) throws IOException { for (DocumentFile child : directory.listFiles()) if (!child.delete()) throw new IOException("无法清理 " + child.getName()); }

    private java.util.Map<String, Long> fileManifest(File root) throws IOException { java.util.LinkedHashMap<String, File> files = new java.util.LinkedHashMap<>(); File canonical = root.getCanonicalFile(); collectSafeProjectFiles(canonical, canonical, new java.util.HashSet<>(), files); java.util.LinkedHashMap<String, Long> result = new java.util.LinkedHashMap<>(); for (java.util.Map.Entry<String, File> entry : files.entrySet()) result.put(entry.getKey(), entry.getValue().length()); return result; }
    private java.util.Map<String, String> fileHashManifest(File root) throws IOException { java.util.LinkedHashMap<String, File> files = new java.util.LinkedHashMap<>(); File canonical = root.getCanonicalFile(); collectSafeProjectFiles(canonical, canonical, new java.util.HashSet<>(), files); java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>(); for (java.util.Map.Entry<String, File> entry : files.entrySet()) try (FileInputStream input = new FileInputStream(entry.getValue())) { result.put(entry.getKey(), fingerprintStream(input)); } return result; }
    private java.util.Map<String, Long> documentManifest(DocumentFile root) { java.util.LinkedHashMap<String, Long> result = new java.util.LinkedHashMap<>(); collectDocumentManifest(root, "", result); return result; }
    private void collectDocumentManifest(DocumentFile directory, String prefix, java.util.Map<String, Long> out) { for (DocumentFile file : directory.listFiles()) { String name = file.getName() == null ? "unnamed" : file.getName(); String path = prefix.isEmpty() ? name : prefix + "/" + name; if (file.isDirectory()) collectDocumentManifest(file, path, out); else if (file.isFile()) out.put(path, file.length()); } }
    private java.util.Map<String, String> documentHashManifest(DocumentFile root) throws IOException { java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>(); collectDocumentHashManifest(root, "", result); return result; }
    private void collectDocumentHashManifest(DocumentFile directory, String prefix, java.util.Map<String, String> out) throws IOException { if (out.size() > 500) throw new IOException("SAF 文件清单超过限制"); for (DocumentFile file : directory.listFiles()) { String name = file.getName(); if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")) throw new IOException("SAF 文件名无效"); String path = prefix.isEmpty() ? name : prefix + "/" + name; if (path.length() > 240 || out.containsKey(path)) throw new IOException("SAF 清单包含重复或过长路径"); if (file.isDirectory()) collectDocumentHashManifest(file, path, out); else if (file.isFile()) out.put(path, fingerprintDocument(file)); } }

    private boolean rollbackInstall(DocumentFile target, DocumentFile backup, java.util.Map<String, Long> expected, java.util.Map<String, String> expectedHashes) {
        if (target == null || backup == null || expected == null || expectedHashes == null) return false;
        try { clearDocumentDirectory(target); copyDocumentToDocument(backup, target); return expected.equals(documentManifest(target)) && expectedHashes.equals(documentHashManifest(target)); } catch (Exception ignored) { return false; }
    }

    private void rollbackLastInstall(boolean confirm) {
        if (lastInstallTarget == null || lastInstallBackup == null) { Toast.makeText(this, "没有可用的安装备份", Toast.LENGTH_LONG).show(); return; }
        if (confirm) { new android.app.AlertDialog.Builder(this).setTitle("回滚上次安装?").setMessage(lastInstallBackup.getName()).setNegativeButton("取消", null).setPositiveButton("回滚", (dialog, which) -> rollbackLastInstall(false)).show(); return; }
        boolean success = rollbackInstall(lastInstallTarget, lastInstallBackup, lastBackupManifest, lastBackupHashManifest); writeInstallJournal(success ? "ROLLED_BACK" : "ROLLBACK_FAILED", lastInstallTarget, lastInstallBackup, null); workspace.setStatus(success ? "安装备份已恢复并校验" : "回滚校验失败"); if (success) { lastInstallBackup = null; lastInstallTarget = null; lastBackupManifest = null; lastBackupHashManifest = null; invalidateOptionsMenu(); }
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
            boolean completed = "COMPLETED".equals(state);
            boolean interrupted = "BACKUP_READY".equals(state) || "FAILED".equals(state) || "ROLLBACK_FAILED".equals(state);
            if ((!completed && !interrupted) || targetValue == null || targetValue.isEmpty() || backupValue == null || backupValue.isEmpty()) return;
            DocumentFile target = documentFromPersistedUri(Uri.parse(targetValue)); DocumentFile backup = documentFromPersistedUri(Uri.parse(backupValue));
            if (target == null || backup == null || !target.isDirectory() || !backup.isDirectory()) return;
            lastInstallTarget = target; lastInstallBackup = backup; lastBackupManifest = documentManifest(backup); lastBackupHashManifest = documentHashManifest(backup); invalidateOptionsMenu();
            if (interrupted) {
                new android.app.AlertDialog.Builder(this).setTitle("主题安装未完成").setMessage("存在已校验的备份,是否立即恢复?").setNegativeButton("稍后", null).setPositiveButton("恢复备份", (dialog, which) -> rollbackLastInstall(false)).show();
            }
        } catch (Exception ignored) { }
    }

    private void showDiagnostics() {
        viewModel.setCurrentPage("diagnostics");
        java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics = currentDiagnostics();
        int errors = 0, warnings = 0, information = 0;
        for (com.osfans.trime.editor.core.ThemeDiagnostic item : diagnostics) {
            if (item.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) errors++;
            else if (item.getSeverity() == com.osfans.trime.editor.core.Severity.WARNING) warnings++;
            else information++;
        }
        String[] actions = {"全部(" + diagnostics.size() + ")", "错误(" + errors + ")", "警告(" + warnings + ")", "提示(" + information + ")", "兼容性报告", "复制兼容性报告"};
        new android.app.AlertDialog.Builder(this).setTitle("诊断与兼容性").setMessage("静态检查不会执行 Lua。请选择要查看的内容。").setItems(actions, (dialog, which) -> {
            if (which <= 3) showDiagnosticList(diagnostics, which);
            else { String report = compatibilityReport(diagnostics); if (which == 4) new android.app.AlertDialog.Builder(this).setTitle("兼容性报告").setMessage(report).setPositiveButton("关闭", null).show(); else { android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE); clipboard.setPrimaryClip(android.content.ClipData.newPlainText("主题兼容性报告", report)); workspace.setStatus("兼容性报告已复制"); } }
        }).setNegativeButton("关闭", null).show();
    }

    private java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> currentDiagnostics() {
        if (projectSnapshot != null) return ThemeProjectDiagnostics.INSTANCE.collect(projectSnapshot, new ThemeFieldRegistry());
        if (editor != null) return editor.diagnostics();
        return java.util.Collections.emptyList();
    }

    private void showDiagnosticList(java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics, int filter) {
        StringBuilder text = new StringBuilder(); int shown = 0;
        for (com.osfans.trime.editor.core.ThemeDiagnostic item : diagnostics) {
            if (filter == 1 && item.getSeverity() != com.osfans.trime.editor.core.Severity.ERROR) continue;
            if (filter == 2 && item.getSeverity() != com.osfans.trime.editor.core.Severity.WARNING) continue;
            if (filter == 3 && item.getSeverity() != com.osfans.trime.editor.core.Severity.INFO) continue;
            text.append(diagnosticSeverityText(item.getSeverity())).append("  [").append(item.getCode()).append("]  ");
            if (item.getPath() != null && !item.getPath().isEmpty()) text.append(item.getPath()).append(": ");
            if (item.getLine() > 0) text.append("第").append(item.getLine()).append("行: ");
            text.append(diagnosticMessageText(item.getMessage())).append('\n'); shown++;
        }
        if (shown == 0) text.append("当前筛选没有诊断信息");
        new android.app.AlertDialog.Builder(this).setTitle("诊断信息 · " + shown + " 条").setMessage(text.toString()).setPositiveButton("关闭", null).show();
    }

    private String compatibilityReport(java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics) {
        int errors = 0, warnings = 0, information = 0;
        for (com.osfans.trime.editor.core.ThemeDiagnostic item : diagnostics) { if (item.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) errors++; else if (item.getSeverity() == com.osfans.trime.editor.core.Severity.WARNING) warnings++; else information++; }
        ThemeFieldCoverage coverage = new ThemeFieldRegistry().coverage();
        StringBuilder report = new StringBuilder("# Trime2 主题兼容性报告\n\n");
        report.append("编辑器版本: ").append(editorVersionText()).append("\n");
        report.append("编辑器模型版本: ").append(ThemeProjectCreator.EDITOR_SCHEMA_VERSION).append("\n");
        report.append("目标 Trime2: ").append(ThemeProjectCreator.EDITOR_SOURCE).append("\n");
        report.append("源码提交: ").append(com.osfans.trime.BuildConfig.BUILD_COMMIT_HASH).append("\n");
        report.append("项目: ").append(projectDisplayName == null ? project == null ? "单个 Lua 文件" : project.getRoot().getName() : projectDisplayName).append("\n");
        report.append("当前文件: ").append(currentFileDisplayName()).append("\n");
        report.append("Lua 安全边界:仅静态解析,未执行用户 Lua、命令、脚本或回调\n");
        report.append("诊断:错误 ").append(errors).append(" / 警告 ").append(warnings).append(" / 提示 ").append(information).append("\n");
        report.append("字段覆盖:总数 ").append(coverage.getTotal()).append(" / 缺失 ").append(coverage.getMissing()).append("\n\n");
        for (com.osfans.trime.editor.core.ThemeDiagnostic item : diagnostics) report.append("- [").append(diagnosticSeverityText(item.getSeverity())).append("] [").append(item.getCode()).append("] ").append(item.getPath() == null ? "" : item.getPath() + ": ").append(diagnosticMessageText(item.getMessage())).append(";建议:").append(diagnosticFixSuggestion(item)).append('\n');
        return report.toString();
    }

    private static String diagnosticFixSuggestion(com.osfans.trime.editor.core.ThemeDiagnostic item) {
        if (item.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) return "先在诊断路径处修正源码或字段,再保存、导出或安装";
        if (item.getCode().startsWith("resource.")) return "确认资源存在、非空且引用使用项目相对路径";
        if (item.getCode().startsWith("lua.dynamic")) return "保留原始 Lua,仅在源码页人工核对;编辑器不会执行它";
        if (item.getCode().startsWith("layout.")) return "检查布局尺寸、重叠、容器和按键顺序";
        return "结合目标 Trime2 版本实机复核";
    }

    private static String safeErrorMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message != null && message.matches(".*[\u3400-\u9fff].*")) return message;
        if (error instanceof NumberFormatException) return "输入格式无效";
        if (error instanceof IllegalArgumentException) return "静态校验未通过,请检查字段或使用 Lua 源代码页面";
        if (error instanceof IOException) return "文件事务未完成,原数据已保留或回滚";
        return "操作未完成,请检查当前项目状态";
    }

    private static String diagnosticSeverityText(com.osfans.trime.editor.core.Severity severity) {
        if (severity == com.osfans.trime.editor.core.Severity.ERROR) return "错误";
        if (severity == com.osfans.trime.editor.core.Severity.WARNING) return "警告";
        return "提示";
    }

    private static String diagnosticMessageText(String message) {
        if (message == null || message.isEmpty()) return "未提供诊断详情";
        if (message.equals("主题没有样式入口(styles/*/main.lua)") || message.equals("Theme has no styles/main.lua")) return "主题没有样式入口(styles/*/main.lua)";
        if (message.equals("主题没有键盘文件(keyboards/*.lua)") || message.equals("Theme has no keyboards")) return "主题没有键盘文件(keyboards/*.lua)";
        if (message.startsWith("引用的资源为空:")) return message;
        if (message.startsWith("Referenced resource is empty:")) return "引用的资源为空:" + message.substring(message.indexOf(':') + 1);
        if (message.startsWith("动态 Lua 可能引用此资源")) return "动态 Lua 可能引用此资源,已禁止安全删除";
        if (message.startsWith("Dynamic Lua may reference this resource")) return "动态 Lua 可能引用此资源,已禁止安全删除";
        if (message.equals("原始 Lua 已保留且不会执行") || message.equals("Raw Lua is preserved and not executed")) return "原始 Lua 已保留且不会执行";
        if (message.startsWith("行缺少字面量按键表(keys)") || message.startsWith("Row has no literal keys table")) return "行缺少字面量按键表(keys)";
        if (message.startsWith("Row key widths total")) return "行内按键宽度总和超过 100%:" + message.substring("Row key widths total".length());
        if (message.startsWith("绝对定位按键宽高必须为正数") || message.startsWith("Absolute key width and height")) return "绝对定位按键宽高必须为正数";
        if (message.startsWith("绝对定位按键超出 0..100 布局边界") || message.startsWith("Absolute key extends outside")) return "绝对定位按键超出 0..100 布局边界";
        if (message.startsWith("Absolute keys overlap:")) return "绝对定位按键重叠:" + message.substring(message.indexOf(':') + 1);
        if (message.startsWith("符号页没有名称(name)") || message.startsWith("Symbol page has no name")) return "符号页没有名称(name)";
        if (message.startsWith("符号页没有按键(keys)") || message.startsWith("Symbol page has no keys")) return "符号页没有按键(keys)";
        return message;
    }

    private void showResourcesState() { viewModel.setCurrentPage("resources"); }
    private void showResources() {
        showResourcesState();
        if (project == null) { Toast.makeText(this, "请先打开主题目录", Toast.LENGTH_LONG).show(); return; }
        ThemeResourceManager manager = new ThemeResourceManager(project.getRoot(), allProjectLuaSource());
        String[] filters = {"全部资源", "图片", "字体", "声音", "脚本"};
        ThemeResource.Kind[] kinds = {null, ThemeResource.Kind.IMAGE, ThemeResource.Kind.FONT, ThemeResource.Kind.SOUND, ThemeResource.Kind.SCRIPT};
        String[] sorts = {"路径升序", "路径降序", "大小升序", "大小降序"};
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        TextView filterLabel = new TextView(this); filterLabel.setText("资源类型"); fields.addView(filterLabel);
        android.widget.Spinner filter = new android.widget.Spinner(this); filter.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filters)); fields.addView(filter);
        TextView sortLabel = new TextView(this); sortLabel.setText("排序方式"); fields.addView(sortLabel);
        android.widget.Spinner sort = new android.widget.Spinner(this); sort.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sorts)); fields.addView(sort);
        ThemeResourceStats allStats = manager.statistics();
        TextView summary = new TextView(this); summary.setText("全部资源 " + allStats.getTotal() + " 项 · 已引用 " + allStats.getReferenced() + " · 动态待确认 " + allStats.getDynamicUncertain() + " · 未使用 " + allStats.getUnused()); summary.setPadding(0, 12, 0, 0); fields.addView(summary);
        new android.app.AlertDialog.Builder(this).setTitle("主题资源").setView(fields).setNegativeButton("关闭", null).setNeutralButton("导入资源", (dialog, which) -> chooseResourceType()).setPositiveButton("查看列表", (dialog, which) -> {
            int sortIndex = sort.getSelectedItemPosition();
            ThemeResourceIndex.Sort order = sortIndex < 2 ? ThemeResourceIndex.Sort.PATH : ThemeResourceIndex.Sort.SIZE;
            boolean ascending = sortIndex == 0 || sortIndex == 2;
            java.util.List<ThemeResource> resources = manager.list(kinds[filter.getSelectedItemPosition()], order, ascending);
            showResourceList(resources);
        }).show();
    }

    private void showResourceList(java.util.List<ThemeResource> resources) {
        if (resources.isEmpty()) { new android.app.AlertDialog.Builder(this).setTitle("资源列表").setMessage("当前筛选没有资源").setPositiveButton("关闭", null).show(); return; }
        ThemeResourceStats stats = ThemeResourceIndex.INSTANCE.statistics(resources);
        String[] labels = new String[resources.size()];
        for (int i = 0; i < resources.size(); i++) { ThemeResource resource = resources.get(i); labels[i] = (resource.getReferenced() ? "已引用  " : resource.getReferenceUncertain() ? "需检查引用  " : "未使用  ") + resourceKindText(resource.getKind()) + "  " + resource.getRelativePath() + "  " + resource.getSize() + " 字节"; }
        new android.app.AlertDialog.Builder(this).setTitle("资源列表 · " + stats.getTotal() + " 项").setItems(labels, (dialog, which) -> showResourceActions(resources.get(which))).setNegativeButton("关闭", null).show();
    }

    private String allProjectLuaSource() {
        if (project == null) return editor == null ? "" : editor.source();
        StringBuilder source = new StringBuilder();
        try {
            java.util.ArrayList<File> files = new java.util.ArrayList<>();
            collectProjectLuaFiles(project.getRoot(), project.getRoot().getCanonicalPath(), new java.util.HashSet<>(), files);
            for (File file : files) source.append('\n').append(new String(readFileBytes(file, 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        if (editor != null && repository instanceof DirectoryThemeProjectRepository) source.append('\n').append(editor.source());
        return source.toString();
    }

    private void chooseResourceType() {
        if (!ensureWritable()) return;
        String[] types = {"图片", "字体", "声音", "脚本"}; String[] folders = {"images", "fonts", "sounds", "scripts"}; String[] mime = {"image/*", "font/*", "audio/*", "text/*"};
        new android.app.AlertDialog.Builder(this).setTitle("导入资源类型").setItems(types, (dialog, which) -> {
            setPendingResourceFolder(folders[which]); importResourceLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime[which]).addCategory(Intent.CATEGORY_OPENABLE));
        }).setNegativeButton("取消", null).show();
    }

    private void importResource(Uri uri) {
        if (!ensureWritable()) { setPendingResourceFolder(null); return; }
        if (project == null || pendingResourceFolder == null) { setPendingResourceFolder(null); return; }
        try {
            String folderName = pendingResourceFolder;
            DocumentFile document = DocumentFile.fromSingleUri(this, uri); String name = document == null ? null : document.getName();
            if (name == null || name.trim().isEmpty()) name = "resource";
            name = name.replaceAll("[^A-Za-z0-9._ -]", "_").replace("..", "_"); if (name.isEmpty() || name.equals(".") || name.equals("..")) throw new IOException("资源名称无效");
            File folder = new File(project.getRoot(), folderName); if (!folder.exists() && !folder.mkdirs()) throw new IOException("无法创建资源文件夹");
            File target = new File(folder, name).getCanonicalFile(); if (!target.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator)) throw new IOException("资源名称无效");
            if (!target.exists()) { importResourceToTarget(uri, folderName, target, false); return; }
            String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name, extension = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ""; File unique = target; int suffix = 2; while (unique.exists()) unique = new File(folder, base + "-" + suffix++ + extension);
            final File duplicate = unique;
            String[] actions = {"跳过现有文件", "覆盖并保留事务备份", "保留两份(" + duplicate.getName() + ")", "取消"};
            new android.app.AlertDialog.Builder(this).setTitle("资源同名冲突").setMessage("目标:" + folderName + "/" + name + "\n覆盖会先备份本地缓存和已授权 SAF 文件;保留两份不会更改现有引用。").setItems(actions, (dialog, which) -> {
                if (which == 0) { setPendingResourceFolder(null); workspace.setStatus("已跳过同名资源,现有文件未修改"); }
                else if (which == 1) importResourceToTarget(uri, folderName, target, true);
                else if (which == 2) importResourceToTarget(uri, folderName, duplicate, false);
                else setPendingResourceFolder(null);
            }).setOnCancelListener(dialog -> setPendingResourceFolder(null)).show();
        } catch (Exception error) { setPendingResourceFolder(null); workspace.setStatus("资源导入失败:" + safeErrorMessage(error)); Toast.makeText(this, "无法导入资源", Toast.LENGTH_LONG).show(); }
    }

    private void importResourceToTarget(Uri uri, String folderName, File target, boolean overwrite) {
        File staged = new File(getCacheDir(), "theme-editor-resource-import-" + System.nanoTime()), backup = null;
        try {
            long total = 0; try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(staged)) { if (input == null) throw new IOException("无法读取资源"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) { total += count; if (total > 64L * 1024 * 1024) throw new IOException("资源超过 64 MiB 限制"); output.write(buffer, 0, count); } output.getFD().sync(); }
            if (total == 0) throw new IOException("资源文件为空");
            if (folderName.equals("images")) validateImportedImage(staged);
            if (target.exists()) {
                if (!overwrite || !target.isFile()) throw new IOException("同名资源状态已变化,请重新导入");
                backup = new File(target.getParentFile(), "." + target.getName() + ".editor-import-backup-" + System.nanoTime());
                if (!target.renameTo(backup)) throw new IOException("无法备份现有本地资源");
            }
            copyExportFile(staged, target);
            if (importedProjectTreeUri != null) {
                if (overwrite) replaceImportedResourceTransaction(target); else mirrorNewResourceToImportedTree(target);
            }
            if (backup != null && !backup.delete()) workspace.setStatus("资源已覆盖;本地事务备份未能自动清理:" + backup.getName());
            project = ThemeProject.Companion.discover(project.getRoot()); projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser()); workspace.setStatus("已导入并校验 " + target.getName() + (overwrite ? ";同名资源已覆盖" : ""));
        } catch (Exception error) {
            if (target.exists()) target.delete();
            if (backup != null && backup.exists() && !backup.renameTo(target)) error.addSuppressed(new IOException("本地资源备份恢复失败"));
            workspace.setStatus("资源导入失败:" + safeErrorMessage(error)); Toast.makeText(this, "无法导入资源", Toast.LENGTH_LONG).show();
        } finally { staged.delete(); setPendingResourceFolder(null); }
    }

    private static void validateImportedImage(File file) throws IOException {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("所选文件不是 Android 可解码图片");
        if ((long) bounds.outWidth * (long) bounds.outHeight > 64L * 1024 * 1024) throw new IOException("图片像素数量超过 6400 万限制");
    }

    private void replaceImportedResourceTransaction(File local) throws IOException {
        ImportedDocumentRef ref = importedDocumentRef(local, false); if (ref == null || ref.file == null || !ref.file.isFile()) throw new IOException("SAF 同名资源已变化或不存在");
        DocumentFile parent = ref.parent, original = ref.file;
        DocumentFile backup = parent.createFile(mimeForName(ref.name), "." + ref.name + ".editor-import-backup-" + System.nanoTime());
        DocumentFile temporary = parent.createFile(mimeForName(ref.name), "." + ref.name + ".editor-import-temp-" + System.nanoTime());
        if (backup == null || temporary == null) { if (backup != null) backup.delete(); if (temporary != null) temporary.delete(); throw new IOException("无法创建 SAF 资源事务文件"); }
        boolean originalDeleted = false, restored = false;
        try {
            copyDocumentFile(original, backup); String originalHash = fingerprintDocument(original); if (!originalHash.equals(fingerprintDocument(backup))) throw new IOException("SAF 资源备份校验失败");
            copyLocalToDocument(local, temporary); String expected; try (FileInputStream input = new FileInputStream(local)) { expected = fingerprintStream(input); } if (!expected.equals(fingerprintDocument(temporary))) throw new IOException("SAF 资源临时文件校验失败");
            if (!original.delete()) throw new IOException("无法替换 SAF 同名资源"); originalDeleted = true;
            DocumentFile replacement = parent.createFile(mimeForName(ref.name), ref.name); if (replacement == null) throw new IOException("无法创建 SAF 资源替代文件"); copyDocumentFile(temporary, replacement);
            if (!expected.equals(fingerprintDocument(replacement))) throw new IOException("SAF 资源替代文件校验失败");
            backup.delete(); temporary.delete();
        } catch (Exception error) {
            if (originalDeleted) { DocumentFile current = parent.findFile(ref.name); if (current != null) current.delete(); DocumentFile replacement = parent.createFile(mimeForName(ref.name), ref.name); if (replacement != null) try { copyDocumentFile(backup, replacement); restored = fingerprintDocument(backup).equals(fingerprintDocument(replacement)); } catch (Exception ignored) { } }
            temporary.delete(); if (restored || !originalDeleted) backup.delete();
            throw new IOException(restored ? "SAF 资源覆盖失败;备份已恢复" : "SAF 资源覆盖失败;事务备份已保留", error);
        }
    }

    private void copyLocalToDocument(File local, DocumentFile target) throws IOException {
        try (FileInputStream input = new FileInputStream(local); java.io.OutputStream output = getContentResolver().openOutputStream(target.getUri(), "wt")) { if (output == null) throw new IOException("无法写入 SAF 资源"); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
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

    private static String resourceKindText(ThemeResource.Kind kind) {
        if (kind == ThemeResource.Kind.IMAGE) return "图片";
        if (kind == ThemeResource.Kind.FONT) return "字体";
        if (kind == ThemeResource.Kind.SOUND) return "声音";
        if (kind == ThemeResource.Kind.SCRIPT) return "脚本";
        return "其他";
    }

    private void showResourceActions(ThemeResource resource) {
        boolean image = resource.getKind() == ThemeResource.Kind.IMAGE;
        String[] actions = image ? new String[]{"预览图片", "复制相对路径", "查看静态引用者", "重命名未引用资源", "删除"} : new String[]{"复制相对路径", "查看静态引用者", "重命名未引用资源", "删除"};
        new android.app.AlertDialog.Builder(this).setTitle(resource.getRelativePath()).setMessage(resourceKindText(resource.getKind()) + " • " + resource.getSize() + " 字节 • " + (resource.getReferenced() ? "已引用" : resource.getReferenceUncertain() ? "可能动态引用" : "未使用")).setItems(actions, (dialog, which) -> {
            int action = image ? which - 1 : which;
            if (image && which == 0) { showImageResourcePreview(resource); return; }
            if (action == 0) { android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE); clipboard.setPrimaryClip(android.content.ClipData.newPlainText("主题资源", resource.getRelativePath())); workspace.setStatus("资源路径已复制"); }
            else if (action == 1) showResourceReferences(resource);
            else if (action == 2) promptRenameResource(resource);
            else confirmResourceDelete(resource);
        }).setNegativeButton("关闭", null).show();
    }

    private void showImageResourcePreview(ThemeResource resource) {
        try {
            File file = new File(project.getRoot(), resource.getRelativePath()).getCanonicalFile(); String root = project.getRoot().getCanonicalPath() + File.separator;
            if (!file.isFile() || !file.getCanonicalPath().startsWith(root) || !file.getAbsolutePath().equals(file.getCanonicalPath())) throw new IOException("图片路径不安全或文件不存在");
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Android 无法解码此图片");
            int sample = 1; while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2;
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options(); options.inSampleSize = sample; android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) throw new IOException("图片预览解码失败");
            android.widget.ImageView image = new android.widget.ImageView(this); image.setAdjustViewBounds(true); image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER); image.setImageBitmap(bitmap); image.setPadding(24, 24, 24, 24);
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle(resource.getRelativePath()).setMessage(bounds.outWidth + "×" + bounds.outHeight + " · 仅预览,不裁剪、不旋转、不转换格式").setView(image).setPositiveButton("关闭", null).create();
            dialog.setOnDismissListener(ignored -> { image.setImageDrawable(null); bitmap.recycle(); }); dialog.show();
        } catch (Exception error) { workspace.setStatus("图片预览失败:" + safeErrorMessage(error)); }
    }

    private void showResourceReferences(ThemeResource resource) {
        java.util.ArrayList<String> references = new java.util.ArrayList<>();
        if (project != null) try {
            int sameNames = 0; for (ThemeResource item : project.getResources()) if (new File(item.getRelativePath()).getName().equalsIgnoreCase(new File(resource.getRelativePath()).getName())) sameNames++;
            collectResourceReferences(project.getRoot(), project.getRoot().getCanonicalPath(), new java.util.HashSet<>(), resource, sameNames == 1, references);
        } catch (Exception ignored) { }
        String message = references.isEmpty() ? resource.getReferenceUncertain() ? "存在动态 Lua 资源表达式,无法静态定位具体引用者。" : "未找到静态字符串引用。" : android.text.TextUtils.join("\n", references);
        new android.app.AlertDialog.Builder(this).setTitle("资源引用者").setMessage(message).setPositiveButton("关闭", null).show();
    }

    private void collectResourceReferences(File directory, String rootPath, java.util.Set<String> visited, ThemeResource resource, boolean allowBasename, java.util.List<String> output) throws IOException {
        String directoryPath = directory.getCanonicalPath(); if (!directory.getAbsolutePath().equals(directoryPath) || (!directoryPath.equals(rootPath) && !directoryPath.startsWith(rootPath + File.separator)) || !visited.add(directoryPath)) return;
        File[] files = directory.listFiles(); if (files == null) return;
        for (File file : files) {
            String canonical = file.getCanonicalPath(); if (!file.getAbsolutePath().equals(canonical) || !canonical.startsWith(rootPath + File.separator)) continue;
            if (file.isDirectory()) collectResourceReferences(file, rootPath, visited, resource, allowBasename, output);
            else if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lua")) {
                String source = new String(readFileBytes(file, 4L * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8);
                int count = ThemeResourceIndex.INSTANCE.literalReferenceCount(source, resource.getRelativePath(), allowBasename);
                if (count > 0) output.add(file.getCanonicalPath().substring(rootPath.length() + 1).replace(File.separatorChar, '/') + " · " + count + " 处");
            }
        }
    }

    private void promptRenameResource(ThemeResource resource) {
        if (!ensureAssetWritable()) return;
        if (resource.getReferenced() || resource.getReferenceUncertain()) { workspace.setStatus("被引用或动态待确认的资源不能直接重命名"); return; }
        File current = new File(project.getRoot(), resource.getRelativePath());
        EditText name = new EditText(this); name.setSingleLine(true); name.setText(current.getName()); name.setSelection(name.length());
        new android.app.AlertDialog.Builder(this).setTitle("重命名未引用资源").setView(name).setNegativeButton("取消", null).setPositiveButton("重命名", (dialog, which) -> renameResourceTransaction(current, name.getText().toString().trim())).show();
    }

    private void renameResourceTransaction(File current, String newName) {
        ThemeResource latest = null; String relative;
        try { String root = project.getRoot().getCanonicalPath(), path = current.getCanonicalPath(); if (!current.getAbsolutePath().equals(path) || !path.startsWith(root + File.separator)) throw new IOException("资源路径超出项目根目录"); relative = path.substring(root.length() + 1).replace(File.separatorChar, '/'); }
        catch (IOException error) { workspace.setStatus("资源状态检查失败:" + safeErrorMessage(error)); return; }
        for (ThemeResource item : new ThemeResourceManager(project.getRoot(), allProjectLuaSource()).list()) if (item.getRelativePath().equals(relative)) { latest = item; break; }
        if (latest == null || latest.getReferenced() || latest.getReferenceUncertain()) { workspace.setStatus("资源状态已变化,重命名被阻止"); return; }
        if (newName.isEmpty() || newName.contains("/") || newName.contains("\\") || newName.equals(".") || newName.equals("..")) { workspace.setStatus("资源名称无效"); return; }
        File target = new File(current.getParentFile(), newName);
        try {
            String folderPath = current.getParentFile().getCanonicalPath() + File.separator;
            if (!target.getCanonicalPath().startsWith(folderPath) || target.exists()) throw new IOException("目标资源名称无效或已存在");
            if (!current.renameTo(target)) throw new IOException("无法重命名本地资源");
            try { if (importedProjectTreeUri != null) mirrorRenamedProjectFile(current, target); }
            catch (Exception error) { target.renameTo(current); throw error; }
            project = ThemeProject.Companion.discover(project.getRoot()); projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser()); workspace.setStatus("资源已重命名为 " + newName);
        } catch (Exception error) { workspace.setStatus("资源重命名失败:" + safeErrorMessage(error)); }
    }

    private void confirmResourceDelete(ThemeResource resource) {
        if (!ensureWritable()) return;
        if (resource.getReferenced() || resource.getReferenceUncertain()) { Toast.makeText(this, "被引用或动态解析的资源不能删除", Toast.LENGTH_LONG).show(); return; }
        new android.app.AlertDialog.Builder(this).setTitle("删除未使用的资源?").setMessage(resource.getRelativePath()).setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> {
            File local = new File(project.getRoot(), resource.getRelativePath()); byte[] backup = null; try { if (local.isFile() && local.length() <= 64L * 1024 * 1024) backup = readFileBytes(local, 64L * 1024 * 1024); } catch (IOException ignored) { }
            ResourceDeleteResult result = new ThemeResourceManager(project.getRoot(), allProjectLuaSource()).delete(resource.getRelativePath());
            if (result instanceof ResourceDeleteResult.Deleted) {
                try { if (importedProjectTreeUri != null) deleteImportedResource(local); project = ThemeProject.Companion.discover(project.getRoot()); workspace.setStatus("已删除 " + resource.getRelativePath()); }
                catch (Exception error) { if (backup != null) try { File restore = new File(project.getRoot(), resource.getRelativePath()); restore.getParentFile().mkdirs(); try (FileOutputStream output = new FileOutputStream(restore)) { output.write(backup); output.getFD().sync(); } } catch (Exception ignored) { } try { project = ThemeProject.Companion.discover(project.getRoot()); } catch (Exception ignored) { } workspace.setStatus("资源删除已回滚:" + safeErrorMessage(error)); }
            } else if (result instanceof ResourceDeleteResult.Referenced) workspace.setStatus("删除被阻止:资源已被引用"); else workspace.setStatus("资源删除失败");
        }).show();
    }

    private static final class ExportRequest {
        final com.osfans.trime.editor.project.ThemeExportKind kind;
        final com.osfans.trime.editor.project.ThemeExportOptions options;
        final boolean directoryOutput;
        final boolean share;
        ExportRequest(com.osfans.trime.editor.project.ThemeExportKind kind, com.osfans.trime.editor.project.ThemeExportOptions options, boolean directoryOutput, boolean share) { this.kind = kind; this.options = options; this.directoryOutput = directoryOutput; this.share = share; }
    }

    private static final class PreparedExport {
        final ExportRequest request;
        final File root;
        final com.osfans.trime.editor.project.ThemeExportPlan plan;
        final String report;
        final String baseName;
        PreparedExport(ExportRequest request, File root, com.osfans.trime.editor.project.ThemeExportPlan plan, String report, String baseName) { this.request = request; this.root = root; this.plan = plan; this.report = report; this.baseName = baseName; }
    }

    private void showExportOptions(boolean share) {
        if (editor == null) { Toast.makeText(this, "导出前请先打开主题", Toast.LENGTH_LONG).show(); return; }
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
        TextView scopeLabel = new TextView(this); scopeLabel.setText("导出类型"); fields.addView(scopeLabel);
        String[] projectKinds = share ? new String[]{"完整主题 ZIP", "仅 Lua ZIP", "仅资源 ZIP", "当前键盘 ZIP", "当前样式 ZIP", "兼容性报告 Markdown"} : new String[]{"完整主题 ZIP", "完整主题目录", "仅 Lua ZIP", "仅资源 ZIP", "当前键盘 ZIP", "当前样式 ZIP", "兼容性报告 Markdown"};
        String[] singleKinds = {"仅 Lua ZIP", "兼容性报告 Markdown"};
        android.widget.Spinner kind = new android.widget.Spinner(this); kind.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, project == null ? singleKinds : projectKinds)); fields.addView(kind);
        android.widget.CheckBox images = exportCheckBox(fields, "包含图片(images)", true);
        android.widget.CheckBox fonts = exportCheckBox(fields, "包含字体(fonts)", true);
        android.widget.CheckBox sounds = exportCheckBox(fields, "包含音效(sounds)", true);
        android.widget.CheckBox scripts = exportCheckBox(fields, "包含脚本(scripts,仅复制不执行)", true);
        android.widget.CheckBox unused = exportCheckBox(fields, "排除静态确定未使用的资源", false);
        android.widget.CheckBox comments = exportCheckBox(fields, "保留 Lua 注释", true);
        android.widget.CheckBox report = exportCheckBox(fields, "附带兼容性报告", true);
        TextView note = new TextView(this); note.setText("导出先在应用缓存建立独立快照并重新校验。未保存草稿只进入本次快照,不会隐式保存到用户主题目录。动态引用不确定资源不会被当作未使用资源删除。"); note.setPadding(0, 12, 0, 0); fields.addView(note);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(fields, new android.widget.ScrollView.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle(share ? "分享导出包" : "导出选项").setView(scroll).setNegativeButton("取消", null).setPositiveButton("生成清单", (dialog, which) -> {
            try {
                int selected = kind.getSelectedItemPosition(); boolean directory = false; com.osfans.trime.editor.project.ThemeExportKind exportKind;
                if (project == null) exportKind = selected == 0 ? com.osfans.trime.editor.project.ThemeExportKind.LUA_ONLY : com.osfans.trime.editor.project.ThemeExportKind.COMPATIBILITY_REPORT;
                else if (share) exportKind = selected == 0 ? com.osfans.trime.editor.project.ThemeExportKind.FULL_THEME : com.osfans.trime.editor.project.ThemeExportKind.values()[selected];
                else if (selected == 0 || selected == 1) { exportKind = com.osfans.trime.editor.project.ThemeExportKind.FULL_THEME; directory = selected == 1; }
                else exportKind = com.osfans.trime.editor.project.ThemeExportKind.values()[selected - 1];
                com.osfans.trime.editor.project.ThemeExportOptions options = new com.osfans.trime.editor.project.ThemeExportOptions(images.isChecked(), fonts.isChecked(), sounds.isChecked(), scripts.isChecked(), unused.isChecked(), comments.isChecked(), report.isChecked());
                showExportPreflight(prepareExport(new ExportRequest(exportKind, options, directory, share)));
            } catch (Exception error) { workspace.setStatus("导出清单生成失败:" + safeErrorMessage(error)); Toast.makeText(this, "无法准备导出", Toast.LENGTH_LONG).show(); }
        }).show();
    }

    private android.widget.CheckBox exportCheckBox(LinearLayout parent, String text, boolean checked) {
        android.widget.CheckBox value = new android.widget.CheckBox(this); value.setText(text); value.setChecked(checked); parent.addView(value); return value;
    }

    private PreparedExport prepareExport(ExportRequest request) throws IOException {
        if (workspace.getModel().layoutMode != ThemeEditorModel.LayoutMode.NONE && !syncModel(workspace.getModel())) throw new IOException("当前可视化模型无法同步到 Lua");
        String draftSource = editor.source();
        com.osfans.trime.editor.core.ParseResult currentCheck = new ThemeLuaParser().parse(draftSource);
        for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : currentCheck.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("当前 Lua 存在错误:" + diagnostic.getMessage());
        File sourceRoot = new File(getCacheDir(), "theme-editor-export-source-" + System.nanoTime());
        File outputRoot = new File(getCacheDir(), "theme-editor-share/export-" + System.nanoTime());
        try {
            if (project == null) {
                if (!sourceRoot.mkdirs()) throw new IOException("无法创建单文件导出快照");
                writeExportText(new File(sourceRoot, "main.lua"), draftSource);
            } else {
                copyDirectory(project.getRoot(), sourceRoot);
                if (repository instanceof DirectoryThemeProjectRepository) {
                    File selected = ((DirectoryThemeProjectRepository) repository).getSelected().getFile(); String rootPath = project.getRoot().getCanonicalPath(), selectedPath = selected.getCanonicalPath();
                    if (!selectedPath.startsWith(rootPath + File.separator)) throw new IOException("当前主题文件超出项目根目录");
                    writeExportText(new File(sourceRoot, selectedPath.substring(rootPath.length() + 1)), draftSource);
                }
            }
            ThemeProject sourceProject = ThemeProject.Companion.discover(sourceRoot);
            ThemeProjectFile sourceCurrent = exportedCurrentFile(sourceProject);
            String sourceLua = projectLuaSource(sourceRoot);
            com.osfans.trime.editor.project.ThemeExportPlan plan = com.osfans.trime.editor.project.ThemeProjectExportPlanner.plan(sourceProject, request.kind, request.options, sourceCurrent, sourceLua);
            java.util.List<com.osfans.trime.editor.core.ThemeDiagnostic> diagnostics;
            try { diagnostics = ThemeProjectDiagnostics.INSTANCE.collect(ThemeProjectSnapshot.Companion.load(sourceProject, new ThemeLuaParser()), new ThemeFieldRegistry()); }
            catch (Exception ignored) { diagnostics = currentCheck.getDiagnostics(); }
            String report = compatibilityReport(diagnostics);
            if (request.kind != com.osfans.trime.editor.project.ThemeExportKind.COMPATIBILITY_REPORT) {
                if (!outputRoot.mkdirs()) throw new IOException("无法创建导出包缓存");
                for (com.osfans.trime.editor.project.ThemeExportEntry entry : plan.getEntries()) {
                    File target = new File(outputRoot, entry.getRelativePath()).getCanonicalFile(); String rootPath = outputRoot.getCanonicalPath();
                    if (!target.getCanonicalPath().startsWith(rootPath + File.separator)) throw new IOException("导出条目路径无效:" + entry.getRelativePath());
                    if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) throw new IOException("无法创建导出条目目录");
                    if (!plan.getIncludeComments() && entry.getRelativePath().toLowerCase(java.util.Locale.ROOT).endsWith(".lua")) writeExportText(target, com.osfans.trime.editor.project.ThemeLuaCommentFilter.strip(readSmallText(entry.getSource(), 4 * 1024 * 1024)));
                    else copyExportFile(entry.getSource(), target);
                }
                if (plan.getIncludeDiagnosticReport()) writeExportText(uniqueExportReportFile(outputRoot), report);
                validatePreparedExport(outputRoot, request.kind == com.osfans.trime.editor.project.ThemeExportKind.FULL_THEME);
            }
            String base = safeExportBaseName(projectDisplayName == null ? project == null ? "theme" : project.getRoot().getName() : projectDisplayName);
            return new PreparedExport(request, request.kind == com.osfans.trime.editor.project.ThemeExportKind.COMPATIBILITY_REPORT ? null : outputRoot, plan, report, base);
        } catch (Exception error) {
            deleteDirectory(outputRoot);
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException(error.getMessage() == null ? "导出静态校验未通过" : error.getMessage(), error);
        } finally { deleteDirectory(sourceRoot); }
    }

    private ThemeProjectFile exportedCurrentFile(ThemeProject sourceProject) {
        if (!(repository instanceof DirectoryThemeProjectRepository)) return null;
        ThemeProjectFile current = ((DirectoryThemeProjectRepository) repository).getSelected();
        if (current.getKind() == ThemeProjectFile.Kind.KEYBOARD) return sourceProject.keyboard(current.getName());
        if (current.getKind() == ThemeProjectFile.Kind.STYLE) return sourceProject.style(current.getName());
        return new ThemeProjectFile("main", sourceProject.getMainFile(), ThemeProjectFile.Kind.MAIN);
    }

    private String projectLuaSource(File root) throws IOException {
        java.util.ArrayList<File> files = new java.util.ArrayList<>(); collectProjectLuaFiles(root, root.getCanonicalPath(), new java.util.HashSet<>(), files); StringBuilder source = new StringBuilder();
        for (File file : files) source.append('\n').append(readSmallText(file, 4 * 1024 * 1024));
        return source.toString();
    }

    private static void copyExportFile(File source, File target) throws IOException {
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); output.getFD().sync(); }
        if (source.length() != target.length()) throw new IOException("导出条目回读大小不一致:" + target.getName());
    }

    private static void writeExportText(File target, String source) throws IOException {
        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) throw new IOException("无法创建导出文件目录");
        try (FileOutputStream output = new FileOutputStream(target, false)) { output.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.getFD().sync(); }
        if (!source.equals(readSmallText(target, 4 * 1024 * 1024))) throw new IOException("导出文本回读不一致:" + target.getName());
    }

    private static File uniqueExportReportFile(File root) {
        File result = new File(root, "theme-editor-compatibility-report.md"); int suffix = 2;
        while (result.exists()) result = new File(root, "theme-editor-compatibility-report-" + suffix++ + ".md");
        return result;
    }

    private static String safeExportBaseName(String value) {
        String safe = value == null ? "theme" : value.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (safe.isEmpty()) safe = "theme"; return safe.length() > 64 ? safe.substring(0, 64) : safe;
    }

    private void validatePreparedExport(File root, boolean requireTheme) throws IOException {
        java.util.Map<String, Long> manifest = fileManifest(root); if (manifest.isEmpty()) throw new IOException("导出清单为空");
        for (String path : manifest.keySet()) if (path.toLowerCase(java.util.Locale.ROOT).endsWith(".lua") && !path.startsWith("scripts/")) {
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(readSmallText(new File(root, path), 4 * 1024 * 1024));
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("导出 Lua 校验:" + path + ":" + diagnostic.getMessage());
        }
        if (requireTheme) {
            ThemeProject verified = ThemeProject.Companion.discover(root); ThemeProjectSnapshot snapshot = ThemeProjectSnapshot.Companion.load(verified, new ThemeLuaParser());
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : ThemeProjectDiagnostics.INSTANCE.collect(snapshot, new ThemeFieldRegistry())) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("完整主题校验:" + diagnostic.getMessage());
        }
    }

    private void showExportPreflight(PreparedExport prepared) {
        StringBuilder message = new StringBuilder("类型:").append(exportKindLabel(prepared.request.kind, prepared.request.directoryOutput)).append("\n文件:").append(prepared.plan.getEntries().size() + (prepared.plan.getIncludeDiagnosticReport() ? 1 : 0)).append(" 个 · 资源体积:").append(prepared.plan.getTotalBytes()).append(" 字节\n排除:").append(prepared.plan.getExcludedCount()).append(" 个项目条目\nLua 注释:").append(prepared.plan.getIncludeComments() ? "保留" : "明确移除(只处理临时副本)").append("\n未保存草稿:").append(viewModel.getDirty() ? "包含在本次快照,不写回原项目" : "无");
        for (String warning : prepared.plan.getWarnings()) message.append("\n警告:").append(warning);
        int shown = 0; for (com.osfans.trime.editor.project.ThemeExportEntry entry : prepared.plan.getEntries()) { if (shown++ >= 14) { message.append("\n...其余 ").append(prepared.plan.getEntries().size() - 14).append(" 个文件"); break; } message.append("\n• ").append(entry.getRelativePath()).append(" (").append(entry.getSize()).append(" B)"); }
        if (prepared.plan.getIncludeDiagnosticReport()) message.append("\n• 生成的兼容性报告 Markdown");
        boolean containsScripts = false; for (com.osfans.trime.editor.project.ThemeExportEntry entry : prepared.plan.getEntries()) if (entry.getRelativePath().startsWith("scripts/")) { containsScripts = true; break; }
        if (prepared.request.options.getIncludeScripts() && containsScripts) message.append("\n\n风险确认:包内含脚本。编辑器只复制和列出脚本,从未执行、验证其运行结果或伪造预览。");
        new android.app.AlertDialog.Builder(this).setTitle("导出前清单").setMessage(message.toString()).setNegativeButton("取消", (dialog, which) -> deleteDirectory(prepared.root)).setPositiveButton(prepared.request.share ? "确认并分享" : "确认导出", (dialog, which) -> executePreparedExport(prepared)).setOnCancelListener(dialog -> deleteDirectory(prepared.root)).show();
    }

    private static String exportKindLabel(com.osfans.trime.editor.project.ThemeExportKind kind, boolean directory) {
        if (kind == com.osfans.trime.editor.project.ThemeExportKind.FULL_THEME) return directory ? "完整主题目录" : "完整主题 ZIP";
        if (kind == com.osfans.trime.editor.project.ThemeExportKind.LUA_ONLY) return "仅 Lua ZIP";
        if (kind == com.osfans.trime.editor.project.ThemeExportKind.RESOURCES_ONLY) return "仅资源 ZIP";
        if (kind == com.osfans.trime.editor.project.ThemeExportKind.CURRENT_KEYBOARD) return "当前键盘 ZIP";
        if (kind == com.osfans.trime.editor.project.ThemeExportKind.CURRENT_STYLE) return "当前样式 ZIP";
        return "兼容性报告 Markdown";
    }

    private void executePreparedExport(PreparedExport prepared) {
        try {
            lastExportReport = prepared.report; lastExportKind = prepared.request.kind;
            if (prepared.request.kind == com.osfans.trime.editor.project.ThemeExportKind.COMPATIBILITY_REPORT) {
                if (lastExportArtifact != null) deleteDirectory(lastExportArtifact); lastExportArtifact = null; lastExportUri = null;
                if (prepared.request.share) { shareTextReport(prepared.report); showExportResult(); }
                else { setPendingTextExport(prepared.report); exportTextLauncher.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/markdown").putExtra(Intent.EXTRA_TITLE, prepared.baseName + "-compatibility.md")); }
                return;
            }
            if (prepared.request.directoryOutput) {
                setPendingDirectoryExport(prepared.root, prepared.baseName);
                exportDirectoryLauncher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
                return;
            }
            File zip = new File(getCacheDir(), "theme-editor-share/" + prepared.baseName + "-" + System.currentTimeMillis() + ".zip");
            com.osfans.trime.editor.project.ThemeProjectArchive.exportDirectory(prepared.root, zip);
            verifyExportArchive(zip, fileManifest(prepared.root), prepared.request.kind == com.osfans.trime.editor.project.ThemeExportKind.FULL_THEME);
            deleteDirectory(prepared.root);
            if (prepared.request.share) { rememberExportResult(zip, null); shareLocalArtifact(zip); showExportResult(); }
            else { setPendingExport(zip); exportZipLauncher.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").putExtra(Intent.EXTRA_TITLE, zip.getName())); }
        } catch (Exception error) { deleteDirectory(prepared.root); workspace.setStatus("导出失败:" + safeErrorMessage(error)); Toast.makeText(this, "无法导出主题", Toast.LENGTH_LONG).show(); }
    }

    private void verifyExportArchive(File zip, java.util.Map<String, Long> expected, boolean requireTheme) throws IOException {
        File verifyRoot = new File(getCacheDir(), "theme-editor-export-verify-" + System.nanoTime());
        try (FileInputStream input = new FileInputStream(zip)) { com.osfans.trime.editor.project.ThemeProjectArchive.extractZip(input, verifyRoot); }
        try {
            java.util.Map<String, Long> actual = fileManifest(verifyRoot); if (!expected.equals(actual)) throw new IOException("导出 ZIP 解包清单不一致");
            validatePreparedExport(verifyRoot, requireTheme);
        } finally { deleteDirectory(verifyRoot); }
    }

    private void rememberExportResult(File artifact, Uri uri) {
        if (lastExportArtifact != null && !lastExportArtifact.equals(artifact)) deleteDirectory(lastExportArtifact);
        lastExportArtifact = artifact; lastExportUri = uri;
    }

    private void shareTextReport(String report) {
        File root = new File(getCacheDir(), "theme-editor-share/report-" + System.nanoTime());
        try {
            if (!root.mkdirs()) throw new IOException("无法创建报告分享缓存"); writeExportText(new File(root, "theme-editor-compatibility-report.md"), report);
            File zip = new File(getCacheDir(), "theme-editor-share/theme-editor-report-" + System.currentTimeMillis() + ".zip"); com.osfans.trime.editor.project.ThemeProjectArchive.exportDirectory(root, zip); verifyExportArchive(zip, fileManifest(root), false); shareLocalArtifact(zip); workspace.setStatus("兼容性报告已打包为 ZIP 并准备分享");
        } catch (Exception error) { workspace.setStatus("报告分享失败:" + safeErrorMessage(error)); }
        finally { deleteDirectory(root); }
    }

    private void shareLocalArtifact(File artifact) {
        try {
            File shareFile = artifact;
            if (artifact.isDirectory()) { shareFile = new File(getCacheDir(), "theme-editor-share/theme-result-" + System.currentTimeMillis() + ".zip"); com.osfans.trime.editor.project.ThemeProjectArchive.exportDirectory(artifact, shareFile); verifyExportArchive(shareFile, fileManifest(artifact), lastExportKind == com.osfans.trime.editor.project.ThemeExportKind.FULL_THEME); }
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".themeeditor.fileprovider", shareFile);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, contentUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); intent.setClipData(android.content.ClipData.newRawUri("theme", contentUri));
            startActivity(Intent.createChooser(intent, "分享主题导出包")); scheduleSharedArtifactCleanup(shareFile, contentUri); workspace.setStatus("已准备安全分享 " + shareFile.getName() + ";临时授权将在 10 分钟后回收");
        } catch (Exception error) { workspace.setStatus("分享失败:" + safeErrorMessage(error)); }
    }

    private void scheduleSharedArtifactCleanup(File artifact, Uri contentUri) {
        final File shared = artifact; final Uri granted = contentUri;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try { revokeUriPermission(granted, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            try { File root = new File(getCacheDir(), "theme-editor-share").getCanonicalFile(), file = shared.getCanonicalFile(); if (file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) deleteDirectory(file); } catch (Exception ignored) { }
            if (shared.equals(lastExportArtifact)) lastExportArtifact = null;
        }, 10L * 60L * 1000L);
    }

    private void showExportResult() {
        if (lastExportKind == null && lastExportUri == null && lastExportArtifact == null && lastExportReport == null) { Toast.makeText(this, "还没有导出结果", Toast.LENGTH_LONG).show(); return; }
        String message = "类型:" + (lastExportKind == null ? "文本" : exportKindLabel(lastExportKind, lastExportArtifact != null && lastExportArtifact.isDirectory())) + "\n系统目标:" + (lastExportUri == null ? "未记录(分享缓存)" : "已写入 SAF 目标") + "\n本地验证副本:" + (lastExportArtifact != null && lastExportArtifact.exists() ? "可用" : "不可用") + "\n输出已在提交前和提交后按可用边界重新读取校验。";
        java.util.ArrayList<String> actions = new java.util.ArrayList<>(); if (lastExportUri != null) actions.add("打开系统目标"); if (lastExportArtifact != null && lastExportArtifact.exists() || lastExportReport != null) actions.add("分享"); if (lastExportKind == com.osfans.trime.editor.project.ThemeExportKind.FULL_THEME && lastExportArtifact != null && lastExportArtifact.exists()) actions.add("重新导入验证"); if (lastExportReport != null) actions.add("查看兼容性报告");
        new android.app.AlertDialog.Builder(this).setTitle("导出完成").setMessage(message).setItems(actions.toArray(new String[0]), (dialog, which) -> {
            String action = actions.get(which); if (action.equals("打开系统目标")) openLastExportTarget(); else if (action.equals("分享")) { if (lastExportArtifact != null && lastExportArtifact.exists()) shareLocalArtifact(lastExportArtifact); else shareTextReport(lastExportReport); } else if (action.equals("重新导入验证")) requestWorkspaceReplacement("重新导入刚导出的完整主题", this::reimportLastExport); else new android.app.AlertDialog.Builder(this).setTitle("兼容性报告").setMessage(lastExportReport).setPositiveButton("关闭", null).show();
        }).setNegativeButton("关闭", null).show();
    }

    private void openLastExportTarget() {
        try { startActivity(new Intent(Intent.ACTION_VIEW).setData(lastExportUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
        catch (Exception error) { workspace.setStatus("系统没有可打开此导出目标的应用;可从文件管理器查看"); }
    }

    private void reimportLastExport() {
        if (lastExportArtifact == null || !lastExportArtifact.exists() || lastExportKind != com.osfans.trime.editor.project.ThemeExportKind.FULL_THEME) { workspace.setStatus("没有可重新导入的完整主题验证副本"); return; }
        File root = new File(getCacheDir(), "theme-editor-reimport-" + System.nanoTime());
        try {
            if (lastExportArtifact.isDirectory()) copyDirectory(lastExportArtifact, root); else try (FileInputStream input = new FileInputStream(lastExportArtifact)) { com.osfans.trime.editor.project.ThemeProjectArchive.extractZip(input, root); }
            File main = findMainLua(root); if (main == null) throw new IOException("导出结果没有唯一明确的 main.lua");
            showImportPreflight(main.getParentFile(), null, false, null, "重新导入验证 · " + (projectDisplayName == null ? "theme" : projectDisplayName));
        } catch (Exception error) { deleteDirectory(root); workspace.setStatus("重新导入验证失败:" + safeErrorMessage(error)); }
    }

    private static void deleteDirectory(File file) { if (file == null || !file.exists()) return; try { if (!file.getAbsolutePath().equals(file.getCanonicalPath())) { file.delete(); return; } } catch (IOException ignored) { return; } File[] children = file.listFiles(); if (children != null) for (File child : children) deleteDirectory(child); file.delete(); }

    private static void copyDirectory(File source, File destination) throws IOException {
        File root = source.getCanonicalFile();
        copyDirectory(root, root, destination, new java.util.HashSet<>());
    }

    private static void copyDirectory(File root, File source, File destination, java.util.Set<String> visited) throws IOException {
        String rootPath = root.getCanonicalPath(), sourcePath = source.getCanonicalPath();
        if (!(sourcePath.equals(rootPath) || sourcePath.startsWith(rootPath + File.separator)) || !source.getAbsolutePath().equals(sourcePath)) throw new IOException("项目复制拒绝符号链接或根外目录");
        if (!visited.add(sourcePath)) return;
        if (!destination.exists() && !destination.mkdirs()) throw new IOException("无法创建导出目录");
        File[] children = source.listFiles(); if (children == null) return;
        for (File child : children) {
            String childPath = child.getCanonicalPath();
            if (!child.getAbsolutePath().equals(childPath) || !childPath.startsWith(rootPath + File.separator)) throw new IOException("项目复制拒绝符号链接或根外文件");
            File target = new File(destination, child.getName());
            if (child.isDirectory()) copyDirectory(root, child, target, visited);
            else if (child.isFile()) try (FileInputStream input = new FileInputStream(child); FileOutputStream output = new FileOutputStream(target)) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); }
        }
    }

    private void syncProjectMainFileIfNeeded() throws IOException {
        if (project == null) return;
        com.osfans.trime.editor.project.FileThemeProjectRepository main =
                new com.osfans.trime.editor.project.FileThemeProjectRepository(project.getMainFile());
        com.osfans.trime.editor.core.ParseResult parsed = main.load(new ThemeLuaParser());
        if (parsed.getDocument().get("keyboard") == null && project.getKeyboards().isEmpty()) {
            throw new IOException("主题项目没有键盘入口");
        }
    }

    private void savePendingSource(Uri uri) {
        String source = pendingSaveSource; setPendingSaveSource(null); if (source == null) { workspace.setStatus("没有待保存的 Lua 源代码"); return; }
        try {
            UriThemeProjectRepository target = new UriThemeProjectRepository(getContentResolver(), uri); target.write(source);
            String verified = target.read(); if (!source.equals(verified)) throw new IOException("保存的源码回读校验不一致");
            com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(verified); for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("保存的源码未通过静态解析校验");
            repository = target; project = null; projectSnapshot = null; currentUri = uri; importedProjectUri = null; importedProjectTreeUri = null; importedProjectTreePrefix = null; openedImportedFingerprint = null; viewModel.setCurrentUri(uri); claimSession(sessionIdentity()); editor.replaceDocument(parsed.getDocument()); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(verified); openedFingerprint = null; layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            workspace.setModel(layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); restoreWorkspaceState(); viewModel.markSaved(openedSourceFingerprint); deleteRecoveryDraft(); workspace.markSaved("已保存并校验:" + currentFileDisplayName()); invalidateOptionsMenu();
            Runnable replacement = pendingWorkspaceReplacement; pendingWorkspaceReplacement = null;
            if (replacement != null) replacement.run();
        } catch (Exception error) { setPendingSaveSource(source); pendingWorkspaceReplacement = null; workspace.setStatus("保存失败:" + safeErrorMessage(error)); Toast.makeText(this, "无法保存 Lua 源代码", Toast.LENGTH_LONG).show(); }
    }

    private void saveModel(ThemeEditorModel model) {
        if (!ensureWritable()) return;
        if (repository == null) {
            try {
                setPendingSaveSource(editor == null ? "" : editor.source());
                com.osfans.trime.editor.core.ParseResult check = new ThemeLuaParser().parse(pendingSaveSource);
                for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : check.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("Lua 源代码存在错误");
                saveLuaLauncher.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/x-lua").putExtra(Intent.EXTRA_TITLE, "main.lua"));
            } catch (Exception error) { setPendingSaveSource(null); workspace.setStatus("保存被阻止:" + safeErrorMessage(error)); }
            return;
        }
        String previousSource = null;
        try {
            if (editor == null) editor = new ThemeEditor(com.osfans.trime.editor.core.ThemeDefaults.INSTANCE.document());
            if (!isCurrentStyleFile() && migrationUndoDocument == null) {
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
            if (savedErrors) throw new IOException("保存的源码未通过静态解析校验");
            editor.replaceDocument(saved.getDocument());
            layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            if (repository instanceof DirectoryThemeProjectRepository) openedFingerprint = ThemeSourceFingerprint.Companion.capture(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            if (project != null) projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
            clearMigrationHistory();
            workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
            restoreWorkspaceState();
            viewModel.markSaved(openedSourceFingerprint);
            deleteRecoveryDraft();
            workspace.markSaved("已保存并校验");
        } catch (Exception error) {
            if (previousSource != null && importedProjectTreeUri != null) try { repository.write(previousSource); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(previousSource); } catch (Exception ignored) { }
            workspace.setStatus("保存失败:" + safeErrorMessage(error));
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
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) throw new IOException("外部源代码存在 Lua 错误");
            editor.replaceDocument(parsed.getDocument()); openedSourceFingerprint = ThemeSaveCoordinator.Companion.fingerprint(repository.read()); layoutEditable = findLayoutRoot(editor.getDocument()) != null;
            if (repository instanceof DirectoryThemeProjectRepository) openedFingerprint = ThemeSourceFingerprint.Companion.capture(((DirectoryThemeProjectRepository) repository).getSelected().getFile());
            if (project != null) projectSnapshot = ThemeProjectSnapshot.Companion.load(project, new ThemeLuaParser());
            viewModel.markLoaded(recoveryIdentity(), openedSourceFingerprint); viewModel.setCurrentFile(currentUri == null ? null : currentUri.toString()); workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel()); restoreWorkspaceState(); deleteRecoveryDraft(); clearMigrationHistory(); workspace.markSaved("已重新加载外部主题源代码");
        } catch (Exception error) { workspace.setStatus("重新加载失败:" + safeErrorMessage(error)); Toast.makeText(this, "无法重新加载外部主题", Toast.LENGTH_LONG).show(); }
    }

    private String readCodeBuffer(String baseSource) {
        File buffer = codeBufferFile(), metadata = codeBufferMetaFile();
        if (!buffer.isFile() || !metadata.isFile()) return null;
        try {
            String[] values = readSmallText(metadata, 8192).split("\n", -1);
            String identity = ThemeSaveCoordinator.Companion.fingerprint(recoveryIdentity());
            String base = ThemeSaveCoordinator.Companion.fingerprint(baseSource);
            if (values.length < 2 || !identity.equals(values[0]) || !base.equals(values[1])) { deleteCodeBuffer(); return null; }
            return readSmallText(buffer, 4 * 1024 * 1024);
        } catch (Exception error) { deleteCodeBuffer(); return null; }
    }

    private void persistCodeBuffer(String baseSource, String bufferSource) {
        if (currentUri == null || bufferSource.equals(baseSource)) { deleteCodeBuffer(); return; }
        try {
            String identity = ThemeSaveCoordinator.Companion.fingerprint(recoveryIdentity());
            String base = ThemeSaveCoordinator.Companion.fingerprint(baseSource);
            writePrivateText(codeBufferFile(), bufferSource);
            writePrivateText(codeBufferMetaFile(), identity + "\n" + base);
        } catch (Exception error) { deleteCodeBuffer(); workspace.setStatus("代码缓冲区保存失败:" + safeErrorMessage(error)); }
    }

    private void deleteCodeBuffer() { File buffer = codeBufferFile(), metadata = codeBufferMetaFile(); if (buffer.exists()) buffer.delete(); if (metadata.exists()) metadata.delete(); }
    private File codeBufferFile() { return new File(getFilesDir(), "theme-editor-code-buffer.lua"); }
    private File codeBufferMetaFile() { return new File(getFilesDir(), "theme-editor-code-buffer.meta"); }

    private String recoveryIdentity() {
        String identity = sessionIdentity(); String file = currentUri == null ? "" : currentUri.toString();
        if (project != null && repository instanceof DirectoryThemeProjectRepository) try { file = ((DirectoryThemeProjectRepository) repository).getSelected().getFile().getCanonicalPath().substring(project.getRoot().getCanonicalPath().length()).replace(File.separatorChar, '/'); } catch (IOException ignored) { }
        if (identity == null && viewModel != null && viewModel.getProjectId() != null) identity = "session:" + viewModel.getProjectId();
        return (identity == null ? "" : identity) + "|" + file;
    }

    private void offerRecoveryDraft() {
        if (recoveryPrompted) return;
        File draft = recoveryDraftFile(), meta = recoveryMetaFile(); if (!draft.isFile() || !meta.isFile()) return;
        try {
            String uri = recoveryMetaIdentity(); if (!recoveryIdentity().equals(uri)) return;
            String source = verifiedRecoveryDraftSource(true); com.osfans.trime.editor.core.ParseResult parsed = new ThemeLuaParser().parse(source);
            for (com.osfans.trime.editor.core.ThemeDiagnostic diagnostic : parsed.getDiagnostics()) if (diagnostic.getSeverity() == com.osfans.trime.editor.core.Severity.ERROR) { quarantineRecoveryDraft("Lua 静态诊断:" + diagnostic.getMessage(), source); workspace.setStatus("恢复草稿损坏,已隔离并生成恢复诊断"); return; }
            recoveryPrompted = true;
            Runnable restore = () -> {
                editor.replaceDocument(parsed.getDocument()); layoutEditable = findLayoutRoot(editor.getDocument()) != null;
                workspace.setModel(isCurrentStyleFile() ? stylePreviewModel(editor.getDocument()) : layoutEditable ? toUiModel(editor.getDocument()) : new ThemeEditorModel());
                restoreWorkspaceState();
                if (!restoringDirtySession) viewModel.recordEdit();
                workspace.setStatus(restoringDirtySession ? "已在配置变化后恢复未保存草稿" : "已恢复私有草稿;请保存以提交更改");
            };
            if (restoringDirtySession) restore.run();
            else new android.app.AlertDialog.Builder(this).setTitle("恢复未保存的主题草稿?").setMessage("此 Lua 文件存在有效的私有草稿。").setNegativeButton("丢弃草稿", (dialog, which) -> deleteRecoveryDraft()).setPositiveButton("恢复", (dialog, which) -> restore.run()).show();
        } catch (Exception error) { if (draft.isFile()) quarantineRecoveryDraft(safeErrorMessage(error), null); }
    }

    private void persistRecoveryDraft() { persistRecoveryDraft(false); }

    private void persistRecoveryDraft(boolean force) {
        if ((!force && !editorPreferences().getBoolean("auto_draft", true)) || !viewModel.getDirty() || editor == null) return;
        try { String source = editor.source(); writePrivateText(recoveryDraftFile(), source); writePrivateText(recoveryMetaFile(), recoveryIdentity() + "\nschema=" + ThemeProjectCreator.EDITOR_SCHEMA_VERSION + "\nchecksum=" + ThemeSaveCoordinator.Companion.fingerprint(source)); }
        catch (Exception ignored) { }
    }

    private static String readSmallText(File file, int limit) throws IOException {
        if (file.length() > limit) throw new IOException("恢复草稿超过大小限制");
        try (FileInputStream input = new FileInputStream(file); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) { if (output.size() + count > limit) throw new IOException("恢复草稿超过大小限制"); output.write(buffer, 0, count); } return output.toString("UTF-8"); }
    }

    private static void writePrivateText(File destination, String source) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) { output.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); output.getFD().sync(); }
        if (destination.exists() && !destination.delete()) throw new IOException("无法替换恢复草稿");
        if (!temporary.renameTo(destination)) { temporary.delete(); throw new IOException("无法提交恢复草稿"); }
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
                ThemeProjectFile styleSource = null;
                if (project != null && editor != null) {
                    if (projectSnapshot != null && projectSnapshot.getStyleSource() != null) styleSource = projectSnapshot.getStyleSource();
                    else styleSource = resolvedStyleSource(editor.getDocument());
                }
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
        if (isFinishing()) releaseSession();
        super.onDestroy();
    }

    @Override protected void onStop() { persistRecoveryDraft(); super.onStop(); }

    private void restoreWorkspaceState() {
        if (workspace == null) return;
        ThemeEditorModel model = workspace.getModel();
        model.previewZoom = viewModel.getZoom();
        model.previewPanX = viewModel.getPanX();
        model.previewPanY = viewModel.getPanY();
        try { model.previewPanel = ThemeEditorModel.PreviewPanel.valueOf(viewModel.getPreviewState()); }
        catch (IllegalArgumentException ignored) { model.previewPanel = ThemeEditorModel.PreviewPanel.KEYBOARD; }
        try { model.inputMode = ThemeEditorModel.InputMode.valueOf(viewModel.getInputMode()); }
        catch (IllegalArgumentException ignored) { model.inputMode = ThemeEditorModel.InputMode.CHINESE; }
        model.showCandidate = viewModel.getShowCandidate();
        model.showToolbar = viewModel.getShowToolbar();
        model.showComposition = viewModel.getShowComposition();
        model.pressedPreview = viewModel.getPressedPreview();
        model.candidateCount = viewModel.getCandidateCount();
        model.candidateComments = viewModel.getCandidateComments();
        model.previewPaging = viewModel.getPreviewPaging();
        model.previewHasMenu = viewModel.getPreviewHasMenu();
        model.previewWidth = viewModel.getPreviewWidth();
        model.previewHeight = viewModel.getPreviewHeight();
        if (viewModel.getPreviewRevision() == 0) {
            int device = editorPreferences().getInt("preview_device", 0);
            if (device == 1) { model.previewWidth = 360; model.previewHeight = 300; }
            else if (device == 2) { model.previewWidth = 720; model.previewHeight = 260; }
            else if (device == 3) { model.previewWidth = 600; model.previewHeight = 420; }
        }
        workspace.setModel(model);
        workspace.restoreEditorState(viewModel.getSelectedKeyId(), viewModel.getInspectorTab());
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        ThemeEditorModel savedModel = workspace.getModel();
        persistRecoveryDraft(true);
        captureProjectState();
        viewModel.setZoom(savedModel.previewZoom);
        viewModel.setPanX(savedModel.previewPanX);
        viewModel.setPanY(savedModel.previewPanY);
        viewModel.setPreviewState(savedModel.previewPanel.name());
        viewModel.setInputMode(savedModel.inputMode.name());
        viewModel.setShowCandidate(savedModel.showCandidate);
        viewModel.setShowToolbar(savedModel.showToolbar);
        viewModel.setShowComposition(savedModel.showComposition);
        viewModel.setPressedPreview(savedModel.pressedPreview);
        viewModel.setCandidateCount(savedModel.candidateCount);
        viewModel.setCandidateComments(savedModel.candidateComments);
        viewModel.setPreviewPaging(savedModel.previewPaging);
        viewModel.setPreviewHasMenu(savedModel.previewHasMenu);
        viewModel.setPreviewWidth(savedModel.previewWidth);
        viewModel.setPreviewHeight(savedModel.previewHeight);
        viewModel.setCurrentFile(currentUri == null ? null : currentUri.toString());
        if (currentUri != null) viewModel.setCurrentUri(currentUri);
        super.onSaveInstanceState(outState);
    }

    public ThemeEditorWorkspace getWorkspace() { return workspace; }
    public void setCallbacks(ThemeEditorCallbacks callbacks) { workspace.setCallbacks(callbacks); }
    public void setThemeModel(ThemeEditorModel model) { workspace.setModel(model); }
}
