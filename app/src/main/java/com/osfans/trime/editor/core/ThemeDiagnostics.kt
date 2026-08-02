/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

enum class Severity { INFO, WARNING, ERROR }
data class ThemeDiagnostic(val line: Int, val column: Int, val severity: Severity, val message: String, val path: String? = null)

object ThemeDiagnostics {
    fun validate(document: ThemeDocument, registry: ThemeFieldRegistry = ThemeFieldRegistry()): List<ThemeDiagnostic> = buildList {
        document.nodes.forEach { node ->
            validateValue(node.source, node.value, node.line, registry, this)
        }
    }

    private fun validateValue(path: String, value: ThemeValue, line: Int, registry: ThemeFieldRegistry, out: MutableList<ThemeDiagnostic>) {
        registry.validate(path, value)?.let { out.add(ThemeDiagnostic(line, 1, Severity.ERROR, it, path)) }
        if (value is ThemeValue.LuaTable) value.fields.forEach { (key, child) -> validateValue("$path.$key", child, line, registry, out) }
        if (value is ThemeValue.RawLuaNode) out.add(ThemeDiagnostic(line, 1, Severity.INFO, "Raw Lua is not validated", path))
    }
}
