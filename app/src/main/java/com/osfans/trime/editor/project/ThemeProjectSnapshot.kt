/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.ParseResult
import com.osfans.trime.editor.core.Severity
import com.osfans.trime.editor.core.ThemeDiagnostic
import com.osfans.trime.editor.core.ThemeLuaParser

/** Parsed project inputs and their origins. Dynamic Lua is never evaluated. */
data class ThemeProjectSnapshot(
    val project: ThemeProject,
    val main: ParseResult,
    val style: ParseResult?,
    val keyboard: ParseResult?,
    val styleSource: ThemeProjectFile?,
    val keyboardSource: ThemeProjectFile?,
    val diagnostics: List<ThemeDiagnostic>,
) {
    val allDiagnostics: List<ThemeDiagnostic> get() = diagnostics +
        main.diagnostics + (style?.diagnostics ?: emptyList()) + (keyboard?.diagnostics ?: emptyList())

    companion object {
        fun load(project: ThemeProject, parser: ThemeLuaParser = ThemeLuaParser()): ThemeProjectSnapshot {
            val main = parser.parse(project.mainFile.readText(Charsets.UTF_8))
            val selected = ThemeProjectSelector.select(project, main)
            var styleSource = selected.styleSource
            val diagnostics = selected.diagnostics.toMutableList()
            if (styleSource == null) {
                styleSource = project.style("light")
                if (styleSource != null) diagnostics += ThemeDiagnostic(0, 0, Severity.INFO, "已回退到 styles/light/main.lua", "style")
            }
            val style = styleSource?.let { parser.parse(it.file.readText(Charsets.UTF_8)) }
            val keyboard = selected.keyboardSource?.let { parser.parse(it.file.readText(Charsets.UTF_8)) }
            return ThemeProjectSnapshot(project, main, style, keyboard, styleSource, selected.keyboardSource, diagnostics)
        }
    }
}
