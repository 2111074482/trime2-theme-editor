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
        val duplicateOwner = parts.indices
            .map { parts.take(it + 1).joinToString(".") }
            .firstOrNull { owner -> document.sourceStatements.count { it.path == owner } > 1 }
        if (duplicateOwner != null) {
            return listOf(ThemeDiagnostic(0, 0, Severity.ERROR, "字段 '$duplicateOwner' 存在重复赋值,必须使用 Lua 源代码编辑器", path))
        }
        val rawAncestor = parts.indices
            .map { parts.take(it + 1).joinToString(".") }
            .firstOrNull { document.get(it) is ThemeValue.RawLuaNode }
        if (rawAncestor != null) {
            return listOf(ThemeDiagnostic(0, 0, Severity.ERROR, "原始 Lua 路径 '$rawAncestor' 不能结构化编辑,请使用 Lua 源代码编辑器", path))
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
