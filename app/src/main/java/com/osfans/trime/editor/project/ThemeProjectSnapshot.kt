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
        fun load(project: ThemeProject, parser: ThemeLuaParser = ThemeLuaParser()): ThemeProjectSnapshot =
            loadSelected(project, null, null, parser)

        /**
         * Loads a snapshot using optional caller-selected static project files.
         *
         * Names supplied by the caller are only resolved against files already discovered by [ThemeProject].
         * A missing explicit name falls back to the literal selection parsed from `main.lua`; no Lua code is
         * evaluated. Java callers can invoke this as `ThemeProjectSnapshot.loadSelected(...)`.
         */
        @JvmStatic
        fun loadSelected(
            project: ThemeProject,
            explicitStyle: String?,
            explicitKeyboard: String?,
            parser: ThemeLuaParser,
        ): ThemeProjectSnapshot {
            val main = parser.parse(project.mainFile.readText(Charsets.UTF_8))
            val selected = ThemeProjectSelector.select(project, main)
            val diagnostics = selected.diagnostics.toMutableList()
            var styleSource = selectExplicit(
                explicitName = explicitStyle,
                staticName = selected.style,
                staticSource = selected.styleSource,
                label = "样式",
                path = "style",
                find = project::style,
                diagnostics = diagnostics,
            )
            val keyboardSource = selectExplicit(
                explicitName = explicitKeyboard,
                staticName = selected.keyboard,
                staticSource = selected.keyboardSource,
                label = "键盘",
                path = "keyboard",
                find = project::keyboard,
                diagnostics = diagnostics,
            )
            if (styleSource == null) {
                styleSource = project.style("light")
                if (styleSource != null) diagnostics += ThemeDiagnostic(0, 0, Severity.INFO, "已回退到 styles/light/main.lua", "style")
            }
            val style = styleSource?.let { parser.parse(it.file.readText(Charsets.UTF_8)) }
            val keyboard = keyboardSource?.let { parser.parse(it.file.readText(Charsets.UTF_8)) }
            return ThemeProjectSnapshot(project, main, style, keyboard, styleSource, keyboardSource, diagnostics)
        }

        private fun selectExplicit(
            explicitName: String?,
            staticName: String,
            staticSource: ThemeProjectFile?,
            label: String,
            path: String,
            find: (String) -> ThemeProjectFile?,
            diagnostics: MutableList<ThemeDiagnostic>,
        ): ThemeProjectFile? {
            if (explicitName.isNullOrEmpty()) return staticSource
            val explicitSource = find(explicitName)
            if (explicitSource != null) {
                diagnostics += ThemeDiagnostic(0, 0, Severity.INFO, "已使用显式$label:$explicitName", path)
                return explicitSource
            }
            diagnostics += ThemeDiagnostic(0, 0, Severity.WARNING, "未找到显式${label}文件:$explicitName", path)
            diagnostics += ThemeDiagnostic(0, 0, Severity.INFO, "显式${label}不可用;已回退到 main.lua 静态${label}:$staticName", path)
            return staticSource
        }
    }
}
