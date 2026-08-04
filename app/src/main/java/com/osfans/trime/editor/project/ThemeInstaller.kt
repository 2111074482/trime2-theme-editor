/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Local directory installer with a manifest-backed backup and rollback. */
class ThemeInstaller {
    fun install(source: File, target: File): InstallResult {
        require(source.isDirectory) { "主题源必须是目录" }
        ThemeProject.discover(source)
        val parent = requireNotNull(target.parentFile) { "安装目标缺少父目录" }
        val targetExisted = target.exists()
        val backup = File(parent, ".${target.name}.backup-${System.currentTimeMillis()}")
        var backupManifest: Map<String, String>? = null
        var backupReady = false
        var targetMutated = false
        try {
            if (targetExisted) {
                copyDirectory(target, backup)
                backupManifest = fileManifest(target)
                require(backupManifest == fileManifest(backup)) { "主题安装备份校验失败" }
                backupReady = true
            }
            targetMutated = true
            if (target.exists() && !target.deleteRecursively()) throw IOException("无法清理安装目标")
            copyDirectory(source, target)
            ThemeProject.discover(target)
            require(fileManifest(source) == fileManifest(target)) { "主题安装回读校验失败" }
            return InstallResult.Success(backup.takeIf { backupReady }, backupManifest)
        } catch (error: Exception) {
            if (targetMutated) {
                if (target.exists()) target.deleteRecursively()
                if (backupReady) {
                    copyDirectory(backup, target)
                    if (backupManifest != fileManifest(target)) {
                        throw IOException("主题安装失败且备份回滚校验失败", error)
                    }
                }
            } else if (backup.exists()) {
                backup.deleteRecursively()
            }
            throw IOException(if (targetMutated && backupReady) "主题安装失败并已回滚" else "主题安装失败且原目标未修改", error)
        }
    }

    fun rollback(result: InstallResult.Success, target: File): Boolean {
        val backup = result.backup ?: return false
        val expected = result.backupManifest ?: return false
        if (!backup.isDirectory || fileManifest(backup) != expected) return false
        return runCatching {
            if (target.exists() && !target.deleteRecursively()) return@runCatching false
            copyDirectory(backup, target)
            fileManifest(target) == expected
        }.getOrDefault(false)
    }

    private fun copyDirectory(source: File, destination: File) {
        require(source.isDirectory && source.absolutePath == source.canonicalPath) { "复制源包含符号链接或无效目录" }
        require(destination.exists() || destination.mkdirs()) { "无法创建目录:${destination.name}" }
        source.listFiles()?.forEach { child ->
            require(child.absolutePath == child.canonicalPath) { "复制源包含符号链接:${child.name}" }
            val target = File(destination, child.name)
            if (child.isDirectory) copyDirectory(child, target) else child.copyTo(target, overwrite = true)
        }
    }

    private fun fileManifest(root: File): Map<String, String> {
        val canonicalRoot = root.canonicalFile
        require(canonicalRoot.isDirectory && root.absolutePath == canonicalRoot.absolutePath) { "目录包含无效根路径" }
        val prefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        return canonicalRoot.walkTopDown().filter { it.isFile }.associate { file ->
            val canonical = file.canonicalFile
            require(file.absolutePath == canonical.absolutePath && canonical.path.startsWith(prefix)) { "目录包含符号链接或根外文件" }
            canonical.path.removePrefix(prefix).replace(File.separatorChar, '/') to sha256(canonical)
        }.toSortedMap()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

sealed class InstallResult {
    data class Success(val backup: File?, val backupManifest: Map<String, String>? = null) : InstallResult()
}
