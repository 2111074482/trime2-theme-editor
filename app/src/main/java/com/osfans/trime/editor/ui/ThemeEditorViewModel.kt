/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class ThemeEditorViewModel(private val state: SavedStateHandle) : ViewModel() {
    var currentUri: Uri?
        get() = state.get<String>(URI_KEY)?.let(Uri::parse)
        set(value) { state[URI_KEY] = value?.toString() }

    var dirty: Boolean
        get() = modelRevision != savedRevision
        set(value) {
            if (value) recordEdit() else markSaved(sourceFingerprint)
        }

    val modelRevision: Long
        get() = state[MODEL_REVISION_KEY] ?: 0L

    val savedRevision: Long
        get() = state[SAVED_REVISION_KEY] ?: 0L

    val previewRevision: Long
        get() = state[PREVIEW_REVISION_KEY] ?: 0L

    val sourceFingerprint: String?
        get() = state[SOURCE_FINGERPRINT_KEY]


    val projectId: String?
        get() = state[PROJECT_ID_KEY]

    fun recordEdit() {
        state[MODEL_REVISION_KEY] = modelRevision + 1L
    }

    fun recordPreviewChange() {
        state[PREVIEW_REVISION_KEY] = previewRevision + 1L
    }

    fun markSaved(fingerprint: String?) {
        state[SAVED_REVISION_KEY] = modelRevision
        state[SOURCE_FINGERPRINT_KEY] = fingerprint
    }

    fun markLoaded(projectId: String?, fingerprint: String?) {
        if (this.projectId != projectId) {
            state[MODEL_REVISION_KEY] = 0L
            state[SAVED_REVISION_KEY] = 0L
            state[PREVIEW_REVISION_KEY] = 0L
            state.remove<String>(SELECTED_KEY)
            state.remove<String>(CURRENT_FILE_KEY)
            state[INSPECTOR_TAB_KEY] = "basic"
        }
        state[PROJECT_ID_KEY] = projectId
        state[SAVED_REVISION_KEY] = modelRevision
        state[SOURCE_FINGERPRINT_KEY] = fingerprint
    }

    var selectedKeyId: String?
        get() = state[SELECTED_KEY]
        set(value) { state[SELECTED_KEY] = value }

    var currentPage: String
        get() = state[CURRENT_PAGE_KEY] ?: "editor"
        set(value) { state[CURRENT_PAGE_KEY] = value }

    var currentFile: String?
        get() = state[CURRENT_FILE_KEY]
        set(value) { state[CURRENT_FILE_KEY] = value }


    var projectRoot: String?
        get() = state[PROJECT_ROOT_KEY]
        set(value) { state[PROJECT_ROOT_KEY] = value }

    var projectDisplayName: String?
        get() = state[PROJECT_DISPLAY_NAME_KEY]
        set(value) { state[PROJECT_DISPLAY_NAME_KEY] = value }

    var projectFile: String?
        get() = state[PROJECT_FILE_KEY]
        set(value) { state[PROJECT_FILE_KEY] = value }

    var importedProjectUri: String?
        get() = state[IMPORTED_PROJECT_URI_KEY]
        set(value) { state[IMPORTED_PROJECT_URI_KEY] = value }

    var importedProjectTreeUri: String?
        get() = state[IMPORTED_PROJECT_TREE_URI_KEY]
        set(value) { state[IMPORTED_PROJECT_TREE_URI_KEY] = value }

    var importedProjectTreePrefix: String?
        get() = state[IMPORTED_PROJECT_TREE_PREFIX_KEY]
        set(value) { state[IMPORTED_PROJECT_TREE_PREFIX_KEY] = value }

    var inspectorTab: String
        get() = state[INSPECTOR_TAB_KEY] ?: "basic"
        set(value) { state[INSPECTOR_TAB_KEY] = value }

    var panX: Float
        get() = state[PAN_X_KEY] ?: 0f
        set(value) { state[PAN_X_KEY] = value }

    var panY: Float
        get() = state[PAN_Y_KEY] ?: 0f
        set(value) { state[PAN_Y_KEY] = value }

    var previewState: String
        get() = state[PREVIEW_STATE_KEY] ?: "KEYBOARD"
        set(value) { state[PREVIEW_STATE_KEY] = value }


    var inputMode: String
        get() = state[INPUT_MODE_KEY] ?: "CHINESE"
        set(value) { state[INPUT_MODE_KEY] = value }

    var showCandidate: Boolean
        get() = state[SHOW_CANDIDATE_KEY] ?: true
        set(value) { state[SHOW_CANDIDATE_KEY] = value }

    var showToolbar: Boolean
        get() = state[SHOW_TOOLBAR_KEY] ?: true
        set(value) { state[SHOW_TOOLBAR_KEY] = value }

    var showComposition: Boolean
        get() = state[SHOW_COMPOSITION_KEY] ?: true
        set(value) { state[SHOW_COMPOSITION_KEY] = value }

    var pressedPreview: Boolean
        get() = state[PRESSED_PREVIEW_KEY] ?: false
        set(value) { state[PRESSED_PREVIEW_KEY] = value }

    var candidateCount: Int
        get() = state[CANDIDATE_COUNT_KEY] ?: 4
        set(value) { state[CANDIDATE_COUNT_KEY] = value.coerceIn(0, 20) }

    var candidateComments: Boolean
        get() = state[CANDIDATE_COMMENTS_KEY] ?: false
        set(value) { state[CANDIDATE_COMMENTS_KEY] = value }

    var previewPaging: Boolean
        get() = state[PREVIEW_PAGING_KEY] ?: false
        set(value) { state[PREVIEW_PAGING_KEY] = value }

    var previewHasMenu: Boolean
        get() = state[PREVIEW_HAS_MENU_KEY] ?: false
        set(value) { state[PREVIEW_HAS_MENU_KEY] = value }

    var previewWidth: Float
        get() = state[PREVIEW_WIDTH_KEY] ?: 360f
        set(value) { state[PREVIEW_WIDTH_KEY] = value.coerceAtLeast(120f) }

    var previewHeight: Float
        get() = state[PREVIEW_HEIGHT_KEY] ?: 300f
        set(value) { state[PREVIEW_HEIGHT_KEY] = value.coerceAtLeast(100f) }

    var zoom: Float
        get() = state[ZOOM_KEY] ?: 1f
        set(value) { state[ZOOM_KEY] = value.coerceIn(0.5f, 4f) }


    var pendingResourceFolder: String?
        get() = state[PENDING_RESOURCE_FOLDER_KEY]
        set(value) { state[PENDING_RESOURCE_FOLDER_KEY] = value }

    var pendingExportPath: String?
        get() = state[PENDING_EXPORT_PATH_KEY]
        set(value) { state[PENDING_EXPORT_PATH_KEY] = value }

    var pendingDirectoryExportPath: String?
        get() = state[PENDING_DIRECTORY_EXPORT_PATH_KEY]
        set(value) { state[PENDING_DIRECTORY_EXPORT_PATH_KEY] = value }

    var pendingDirectoryExportName: String?
        get() = state[PENDING_DIRECTORY_EXPORT_NAME_KEY]
        set(value) { state[PENDING_DIRECTORY_EXPORT_NAME_KEY] = value }

    var pendingCreateSpec: String?
        get() = state[PENDING_CREATE_SPEC_KEY]
        set(value) { state[PENDING_CREATE_SPEC_KEY] = value }


    var pendingBuiltInTemplatePath: String?
        get() = state[PENDING_BUILT_IN_TEMPLATE_PATH_KEY]
        set(value) { state[PENDING_BUILT_IN_TEMPLATE_PATH_KEY] = value }

    var pendingBuiltInTemplateName: String?
        get() = state[PENDING_BUILT_IN_TEMPLATE_NAME_KEY]
        set(value) { state[PENDING_BUILT_IN_TEMPLATE_NAME_KEY] = value }

    var pendingTextExport: Boolean
        get() = state[PENDING_TEXT_EXPORT_KEY] ?: false
        set(value) { state[PENDING_TEXT_EXPORT_KEY] = value }

    var pendingSaveSource: Boolean
        get() = state[PENDING_SAVE_SOURCE_KEY] ?: false
        set(value) { state[PENDING_SAVE_SOURCE_KEY] = value }

    val sessionToken: String
        get() = state.get<String>(SESSION_TOKEN_KEY) ?: java.util.UUID.randomUUID().toString().also { state[SESSION_TOKEN_KEY] = it }

    companion object {
        private const val URI_KEY = "theme_editor.uri"
        private const val PROJECT_ID_KEY = "theme_editor.project_id"
        private const val MODEL_REVISION_KEY = "theme_editor.model_revision"
        private const val SAVED_REVISION_KEY = "theme_editor.saved_revision"
        private const val PREVIEW_REVISION_KEY = "theme_editor.preview_revision"
        private const val SOURCE_FINGERPRINT_KEY = "theme_editor.source_fingerprint"
        private const val SELECTED_KEY = "theme_editor.selected"
        private const val CURRENT_PAGE_KEY = "theme_editor.current_page"
        private const val CURRENT_FILE_KEY = "theme_editor.current_file"
        private const val PROJECT_ROOT_KEY = "theme_editor.project_root"
        private const val PROJECT_DISPLAY_NAME_KEY = "theme_editor.project_display_name"
        private const val PROJECT_FILE_KEY = "theme_editor.project_file"
        private const val IMPORTED_PROJECT_URI_KEY = "theme_editor.imported_project_uri"
        private const val IMPORTED_PROJECT_TREE_URI_KEY = "theme_editor.imported_project_tree_uri"
        private const val IMPORTED_PROJECT_TREE_PREFIX_KEY = "theme_editor.imported_project_tree_prefix"
        private const val INSPECTOR_TAB_KEY = "theme_editor.inspector_tab"
        private const val PAN_X_KEY = "theme_editor.pan_x"
        private const val PAN_Y_KEY = "theme_editor.pan_y"
        private const val PREVIEW_STATE_KEY = "theme_editor.preview_state"
        private const val INPUT_MODE_KEY = "theme_editor.input_mode"
        private const val SHOW_CANDIDATE_KEY = "theme_editor.show_candidate"
        private const val SHOW_TOOLBAR_KEY = "theme_editor.show_toolbar"
        private const val SHOW_COMPOSITION_KEY = "theme_editor.show_composition"
        private const val PRESSED_PREVIEW_KEY = "theme_editor.pressed_preview"
        private const val CANDIDATE_COUNT_KEY = "theme_editor.candidate_count"
        private const val CANDIDATE_COMMENTS_KEY = "theme_editor.candidate_comments"
        private const val PREVIEW_PAGING_KEY = "theme_editor.preview_paging"
        private const val PREVIEW_HAS_MENU_KEY = "theme_editor.preview_has_menu"
        private const val PREVIEW_WIDTH_KEY = "theme_editor.preview_width"
        private const val PREVIEW_HEIGHT_KEY = "theme_editor.preview_height"
        private const val ZOOM_KEY = "theme_editor.zoom"

        private const val PENDING_RESOURCE_FOLDER_KEY = "theme_editor.pending_resource_folder"
        private const val PENDING_EXPORT_PATH_KEY = "theme_editor.pending_export_path"
        private const val PENDING_DIRECTORY_EXPORT_PATH_KEY = "theme_editor.pending_directory_export_path"
        private const val PENDING_DIRECTORY_EXPORT_NAME_KEY = "theme_editor.pending_directory_export_name"
        private const val PENDING_CREATE_SPEC_KEY = "theme_editor.pending_create_spec"
        private const val PENDING_BUILT_IN_TEMPLATE_PATH_KEY = "theme_editor.pending_built_in_template_path"
        private const val PENDING_BUILT_IN_TEMPLATE_NAME_KEY = "theme_editor.pending_built_in_template_name"
        private const val PENDING_TEXT_EXPORT_KEY = "theme_editor.pending_text_export"
        private const val PENDING_SAVE_SOURCE_KEY = "theme_editor.pending_save_source"
        private const val SESSION_TOKEN_KEY = "theme_editor.session_token"
    }
}
