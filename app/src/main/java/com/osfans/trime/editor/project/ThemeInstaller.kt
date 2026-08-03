/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File
import java.io.IOException

/** Local directory installer with a manifest-backed backup and rollback. */
class ThemeInstaller {
    fun install(source: File, target: File): InstallResult {
        require(source.isDirectory) { "Theme source must be a directory" }
        val project = ThemeProject.discover(source)
        val backup = File(target.parentFile, ".${target.name}.backup-${System.currentTimeMillis()}")
        if (target.exists()) copyDirectory(target, backup)
        try {
            if (target.exists()) target.deleteRecursively()
            copyDirectory(source, target)
            ThemeProject.discover(target)
            return InstallResult.Success(backup.takeIf { it.exists() })
        } catch (error: Exception) {
            if (target.exists()) target.deleteRecursively()
            if (backup.exists()) copyDirectory(backup, target)
            throw IOException("Theme install rolled back", error)
        }
    }

    fun rollback(result: InstallResult.Success, target: File): Boolean {
        val backup = result.backup ?: return false
        if (!backup.isDirectory) return false
        if (target.exists()) target.deleteRecursively()
        copyDirectory(backup, target)
        return ThemeProject.discover(target).mainFile.isFile
    }

    private fun copyDirectory(source: File, destination: File) {
        destination.mkdirs()
        source.listFiles()?.forEach { child ->
            val target = File(destination, child.name)
            if (child.isDirectory) copyDirectory(child, target) else child.copyTo(target, overwrite = true)
        }
    }
}

sealed class InstallResult {
    data class Success(val backup: File?) : InstallResult()
}
