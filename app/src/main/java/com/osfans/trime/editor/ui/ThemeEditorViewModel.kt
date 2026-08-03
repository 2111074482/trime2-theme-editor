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
        get() = state[DIRTY_KEY] ?: false
        set(value) { state[DIRTY_KEY] = value }

    var selectedKeyId: String?
        get() = state[SELECTED_KEY]
        set(value) { state[SELECTED_KEY] = value }

    var zoom: Float
        get() = state[ZOOM_KEY] ?: 1f
        set(value) { state[ZOOM_KEY] = value.coerceIn(0.5f, 4f) }

    val sessionToken: String
        get() = state.get<String>(SESSION_TOKEN_KEY) ?: java.util.UUID.randomUUID().toString().also { state[SESSION_TOKEN_KEY] = it }

    companion object {
        private const val URI_KEY = "theme_editor.uri"
        private const val DIRTY_KEY = "theme_editor.dirty"
        private const val SELECTED_KEY = "theme_editor.selected"
        private const val ZOOM_KEY = "theme_editor.zoom"
        private const val SESSION_TOKEN_KEY = "theme_editor.session_token"
    }
}
