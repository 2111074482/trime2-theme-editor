/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File

/** Atomic repository for one Lua file inside a discovered theme directory. */
class DirectoryThemeProjectRepository(
    val project: ThemeProject,
    val selected: ThemeProjectFile,
) : ThemeProjectRepository {
    override fun read(): String = selected.file.readText(Charsets.UTF_8)

    override fun write(source: String) = writeTextTransaction(selected.file, source)
}
