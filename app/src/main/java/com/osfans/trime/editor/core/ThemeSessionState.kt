/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

/** Small state contract shared by UI, preview and save coordination. */
data class ThemeSessionState(
    val projectId: String,
    val modelRevision: Long = 0,
    val savedRevision: Long = 0,
    val draftRevision: Long = 0,
    val sourceFingerprint: String? = null,
    val previewRevision: Long = 0,
    val dirty: Boolean = false,
) {
    fun edited(): ThemeSessionState = copy(modelRevision = modelRevision + 1, dirty = true)
    fun previewChanged(): ThemeSessionState = copy(previewRevision = previewRevision + 1)
    fun saved(fingerprint: String): ThemeSessionState = copy(savedRevision = modelRevision, sourceFingerprint = fingerprint, dirty = false)
}
