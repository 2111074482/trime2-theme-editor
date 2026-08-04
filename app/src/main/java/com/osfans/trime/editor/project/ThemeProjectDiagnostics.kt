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
        val prefix = project.root.canonicalPath.trimEnd(java.io.File.separatorChar) + java.io.File.separator
        val files = listOf(project.mainFile) + project.styles.map { it.file } + project.keyboards.map { it.file }
        files.distinctBy { it.absolutePath }.forEach { file ->
            try {
                val canonical = file.canonicalFile
                if (canonical.isFile && canonical.canonicalPath.startsWith(prefix) && canonical.length() <= 4L * 1024 * 1024) append('\n').append(canonical.readText(Charsets.UTF_8))
            } catch (_: Exception) { }
        }
    }

    fun collect(snapshot: ThemeProjectSnapshot, registry: ThemeFieldRegistry = ThemeFieldRegistry()): List<ThemeDiagnostic> = buildList {
        addAll(snapshot.allDiagnostics)
        addAll(ThemeDiagnostics.validate(snapshot.main.document, registry))
        snapshot.style?.let { addAll(ThemeDiagnostics.validate(it.document, registry)) }
        snapshot.keyboard?.let { addAll(ThemeDiagnostics.validate(it.document, registry)) }
        val luaSource = projectLuaSource(snapshot.project)
        addAll(ThemeDiagnostics.resources(ThemeResourceIndex.scan(snapshot.project.root, luaSource)))
        ThemeResourceIndex.missingStaticReferences(snapshot.project.root, luaSource).forEach { path ->
            add(ThemeDiagnostic(0, 0, Severity.ERROR, "静态引用的资源不存在:$path", path, "resource.missing.$path"))
        }
        addAll(ThemeDiagnostics.coverage(registry))
        if (snapshot.project.styles.isEmpty()) add(ThemeDiagnostic(0, 0, Severity.ERROR, "主题没有样式入口(styles/*/main.lua)", "styles"))
        if (snapshot.project.keyboards.isEmpty()) add(ThemeDiagnostic(0, 0, Severity.ERROR, "主题没有键盘文件(keyboards/*.lua)", "keyboards"))
    }
}
