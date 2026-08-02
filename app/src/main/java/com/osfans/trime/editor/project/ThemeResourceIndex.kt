/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File

/** Lightweight resource inventory used by diagnostics and safe deletion UI. */
data class ThemeResource(
    val relativePath: String,
    val kind: Kind,
    val size: Long,
    val referenced: Boolean,
) {
    enum class Kind { IMAGE, FONT, SOUND, SCRIPT, OTHER }
}

object ThemeResourceIndex {
    fun scan(root: File, source: String = ""): List<ThemeResource> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown().filter { it.isFile }.map { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            ThemeResource(relative, kind(relative), file.length(), isReferenced(relative, source))
        }.toList()
    }

    fun canDelete(resource: ThemeResource): Boolean = !resource.referenced

    private fun kind(path: String): ThemeResource.Kind = when {
        path.startsWith("images/") -> ThemeResource.Kind.IMAGE
        path.startsWith("fonts/") -> ThemeResource.Kind.FONT
        path.startsWith("sounds/") -> ThemeResource.Kind.SOUND
        path.startsWith("scripts/") -> ThemeResource.Kind.SCRIPT
        else -> ThemeResource.Kind.OTHER
    }

    private fun isReferenced(path: String, source: String): Boolean {
        val name = path.substringAfterLast('/')
        return source.contains(path) || source.contains(name)
    }
}
