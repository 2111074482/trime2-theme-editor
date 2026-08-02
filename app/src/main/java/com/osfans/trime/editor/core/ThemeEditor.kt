/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

import com.osfans.trime.editor.project.ThemeProjectRepository

class ThemeEditor(
    var document: ThemeDocument,
    val registry: ThemeFieldRegistry = ThemeFieldRegistry(),
    private val history: ThemeUndoManager = ThemeUndoManager(),
) {
    fun set(path: String, value: ThemeValue): List<ThemeDiagnostic> {
        val error = registry.validate(path, value)
        if (error != null) return listOf(ThemeDiagnostic(0, 0, Severity.ERROR, error, path))
        history.record(document); document = document.set(path, value)
        return ThemeDiagnostics.validate(document, registry)
    }
    fun diagnostics(): List<ThemeDiagnostic> = ThemeDiagnostics.validate(document, registry)
    fun undo(): Boolean = history.undo(document)?.let { document = it; true } ?: false
    fun redo(): Boolean = history.redo(document)?.let { document = it; true } ?: false
    fun load(repository: ThemeProjectRepository): ParseResult { val result = repository.load(); document = result.document; history.clear(); return result }
    fun save(repository: ThemeProjectRepository) = repository.save(document)
    fun source(): String = ThemeLuaWriter.write(document)
}
