/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.Severity
import com.osfans.trime.editor.core.ThemeDiagnostic
import com.osfans.trime.editor.core.ThemeDiagnostics
import com.osfans.trime.editor.core.ThemeFieldRegistry

object ThemeProjectDiagnostics {
    private fun projectLuaSource(project: ThemeProject): String = buildString {
        project.root.walkTopDown().filter { it.isFile && it.extension.equals("lua", true) }.forEach { file ->
            try { append('\n').append(file.readText(Charsets.UTF_8)) } catch (_: Exception) { }
        }
    }

    fun collect(snapshot: ThemeProjectSnapshot, registry: ThemeFieldRegistry = ThemeFieldRegistry()): List<ThemeDiagnostic> = buildList {
        addAll(snapshot.allDiagnostics)
        addAll(ThemeDiagnostics.validate(snapshot.main.document, registry))
        snapshot.style?.let { addAll(ThemeDiagnostics.validate(it.document, registry)) }
        snapshot.keyboard?.let { addAll(ThemeDiagnostics.validate(it.document, registry)) }
        addAll(ThemeDiagnostics.resources(ThemeResourceIndex.scan(snapshot.project.root, projectLuaSource(snapshot.project))))
        addAll(ThemeDiagnostics.coverage(registry))
        if (snapshot.project.styles.isEmpty()) add(ThemeDiagnostic(0, 0, Severity.ERROR, "Theme has no styles/main.lua", "styles"))
        if (snapshot.project.keyboards.isEmpty()) add(ThemeDiagnostic(0, 0, Severity.ERROR, "Theme has no keyboards", "keyboards"))
    }
}
