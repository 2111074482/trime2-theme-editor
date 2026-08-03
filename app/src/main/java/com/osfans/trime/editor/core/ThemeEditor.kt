/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

import com.osfans.trime.editor.project.ThemeProjectRepository

class ThemeEditor @JvmOverloads constructor(
    document: ThemeDocument,
    val registry: ThemeFieldRegistry = ThemeFieldRegistry(),
    private val history: ThemeUndoManager = ThemeUndoManager(),
) {
    var document: ThemeDocument = document
        private set

    fun set(path: String, value: ThemeValue): List<ThemeDiagnostic> {
        val parts = path.split('.').filter { it.isNotBlank() }
        val root = parts.firstOrNull().orEmpty()
        if (document.sourceStatements.count { it.path == path } > 1) {
            return listOf(ThemeDiagnostic(0, 0, Severity.ERROR, "Duplicate assignments for '$path' require the source editor", path))
        }
        val rawAncestor = parts.indices
            .map { parts.take(it + 1).joinToString(".") }
            .firstOrNull { document.get(it) is ThemeValue.RawLuaNode }
        if (rawAncestor != null) {
            return listOf(ThemeDiagnostic(0, 0, Severity.ERROR, "Cannot structurally edit raw Lua path '$rawAncestor'; use the source editor", path))
        }
        val error = registry.validate(path, value)
        if (error != null) return listOf(ThemeDiagnostic(0, 0, Severity.ERROR, error, path))
        val updated = document.set(path, value)
        if (updated != document) { history.record(document); document = updated }
        return ThemeDiagnostics.validate(document, registry)
    }
    fun diagnostics(): List<ThemeDiagnostic> = ThemeDiagnostics.validate(document, registry)
    fun replaceDocument(value: ThemeDocument) { history.clear(); document = value }
    fun undo(): Boolean = history.undo(document)?.let { document = it; true } ?: false
    fun redo(): Boolean = history.redo(document)?.let { document = it; true } ?: false
    fun load(repository: ThemeProjectRepository): ParseResult { val result = repository.load(); document = result.document; history.clear(); return result }
    fun save(repository: ThemeProjectRepository) = repository.save(document)
    fun source(): String = ThemeLuaWriter.write(document)
}
