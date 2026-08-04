/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import android.content.ContentResolver
import android.net.Uri
import com.osfans.trime.editor.core.ParseResult
import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeLuaWriter
import java.io.File
import java.io.IOException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

interface ThemeProjectRepository {
    @Throws(IOException::class)
    fun read(): String
    @Throws(IOException::class)
    fun write(source: String)

    fun load(parser: ThemeLuaParser = ThemeLuaParser()): ParseResult = parser.parse(read())
    fun save(document: ThemeDocument) {
        val source = ThemeLuaWriter.write(document)
        val firstError = ThemeLuaParser().parse(source).diagnostics.firstOrNull { it.severity == com.osfans.trime.editor.core.Severity.ERROR }
        if (firstError != null) throw IOException("候选 Lua 未通过静态解析:第${firstError.line}行:${firstError.message}")
        write(source)
        val committed = read()
        if (committed != source) throw IOException("保存后的源代码回读校验不一致")
        val committedError = ThemeLuaParser().parse(committed).diagnostics.firstOrNull { it.severity == com.osfans.trime.editor.core.Severity.ERROR }
        if (committedError != null) throw IOException("保存后的 Lua 未通过静态解析:第${committedError.line}行:${committedError.message}")
    }
}

class FileThemeProjectRepository(private val file: File) : ThemeProjectRepository {
    override fun read(): String = file.readText(Charsets.UTF_8)
    override fun write(source: String) = writeTextTransaction(file, source)
}

/** Adapter for SAF or other URI providers. The caller owns stream lifetime. */
class UriThemeProjectRepository(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : ThemeProjectRepository {
    override fun read(): String = (resolver.openInputStream(uri) ?: throw IOException("无法打开主题源文件")).use { it.reader(Charsets.UTF_8).readText() }
    override fun write(source: String) = (resolver.openOutputStream(uri, "wt") ?: throw IOException("无法写入主题源文件")).use { it.writer(Charsets.UTF_8).use { writer -> writer.write(source) } }
}


internal fun writeTextTransaction(destination: File, source: String) {
    destination.parentFile?.let { parent ->
        if (!parent.exists() && !parent.mkdirs()) throw IOException("无法创建目标目录")
    }
    val temporary = File(destination.parentFile, ".${destination.name}.editor-${System.nanoTime()}.tmp")
    try {
        FileOutputStream(temporary).use { output ->
            output.write(source.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        val verified = temporary.readText(Charsets.UTF_8)
        if (verified != source) throw IOException("临时文件回读校验不一致:${destination.name}")
        val diagnostics = ThemeLuaParser().parse(verified).diagnostics
        val firstError = diagnostics.firstOrNull { it.severity == com.osfans.trime.editor.core.Severity.ERROR }
        if (firstError != null) throw IOException("临时 Lua 静态解析失败:${destination.name}:第${firstError.line}行:${firstError.message}")
        replaceFileTransaction(temporary, destination)
    } finally {
        if (temporary.exists()) temporary.delete()
    }
}

internal fun replaceFileTransaction(temporary: File, destination: File) {
    require(temporary.isFile) { "替换源必须是文件" }
    val backup = File(destination.parentFile, ".${destination.name}.backup-${System.nanoTime()}")
    var backedUp = false
    try {
        if (destination.exists()) {
            if (!destination.isFile || !destination.renameTo(backup)) throw IOException("无法备份 ${destination.name}")
            backedUp = true
        }
        if (!temporary.renameTo(destination)) {
            FileInputStream(temporary).use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (destination.length() != temporary.length()) throw IOException("替换文件 ${destination.name} 回读校验失败")
            temporary.delete()
        }
        if (backedUp && backup.exists() && !backup.delete()) throw IOException("无法删除 ${destination.name} 的事务备份")
    } catch (error: Exception) {
        if (destination.exists()) destination.delete()
        if (backedUp && backup.exists() && !backup.renameTo(destination)) {
            throw IOException("替换 ${destination.name} 失败且备份恢复失败", error)
        }
        throw IOException("替换文件 ${destination.name} 失败", error)
    } finally {
        if (temporary.exists()) temporary.delete()
    }
}

object ThemeProjectArchive {
    @JvmStatic
    fun exportDirectory(source: File, destination: File) {
        require(source.isDirectory) { "主题源必须是目录" }
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.${System.nanoTime()}.tmp")
        val root = source.canonicalFile
        require(source.absolutePath == root.absolutePath) { "主题源不支持符号链接" }
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                safeProjectFiles(root).forEach { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    require(relative.isNotEmpty() && !relative.startsWith("/") && relative.split('/').none { it == ".." })
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            replaceFileTransaction(temporary, destination)
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun safeProjectFiles(root: File): Sequence<File> = sequence {
        val prefix = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
        val directories = java.util.ArrayDeque<File>()
        val visitedDirectories = HashSet<String>()
        val visitedFiles = HashSet<String>()
        directories.add(root.canonicalFile)
        while (directories.isNotEmpty()) {
            val directory = directories.removeLast().canonicalFile
            val directoryPath = directory.canonicalPath
            require(directoryPath == root.canonicalPath || directoryPath.startsWith(prefix)) { "项目目录超出主题根目录" }
            if (!visitedDirectories.add(directoryPath)) continue
            val children = directory.listFiles() ?: emptyArray()
            for (child in children) {
                val canonical = child.canonicalFile
                require(child.absolutePath == canonical.absolutePath) { "项目不支持符号链接:${child.name}" }
                require(canonical.canonicalPath.startsWith(prefix)) { "项目条目超出主题根目录:${child.name}" }
                if (canonical.isDirectory) directories.add(canonical)
                else if (canonical.isFile && visitedFiles.add(canonical.canonicalPath)) yield(canonical)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun extractZip(
        input: java.io.InputStream,
        destination: File,
        maxFiles: Int = 500,
        maxBytes: Long = 64L * 1024 * 1024,
        maxDepth: Int = 12,
        maxPathLength: Int = 240,
    ) {
        val existed = destination.exists()
        require(!existed || destination.isDirectory && destination.listFiles().isNullOrEmpty()) { "ZIP 解压目标必须为空目录" }
        require(destination.mkdirs() || destination.isDirectory) { "无法创建 ZIP 解压目录" }
        val destinationPath = destination.canonicalPath.trimEnd(File.separatorChar) + File.separator
        val seen = HashSet<String>()
        var files = 0
        var bytes = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/').trimEnd('/')
                    val parts = name.split('/')
                    require(
                        name.isNotEmpty() && name.length <= maxPathLength && !name.startsWith('/') &&
                            parts.size <= maxDepth && parts.none { part ->
                                part.isBlank() || part == "." || part == ".." || part.any { it.code < 0x20 }
                            },
                    ) { "ZIP 包含非法路径:$name" }
                    val normalized = parts.joinToString("/").lowercase(java.util.Locale.ROOT)
                    require(seen.add(normalized)) { "ZIP 包含重复或大小写冲突条目:$name" }
                    require(++files <= maxFiles) { "ZIP 文件数量超过限制:$maxFiles" }
                    val target = File(destination, name)
                    require(target.canonicalPath.startsWith(destinationPath)) { "ZIP 条目超出解压目录:$name" }
                    if (entry.isDirectory) {
                        require(target.mkdirs() || target.isDirectory) { "无法创建 ZIP 目录:$name" }
                    } else {
                        require(target.parentFile?.mkdirs() != false || target.parentFile?.isDirectory == true) { "无法创建 ZIP 文件父目录:$name" }
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var count: Int
                            while (zip.read(buffer).also { count = it } >= 0) {
                                bytes += count
                                require(bytes <= maxBytes) { "ZIP 解压总大小超过限制:$maxBytes" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (error: Exception) {
            destination.listFiles()?.forEach { it.deleteRecursively() }
            if (!existed) destination.delete()
            throw error
        }
    }
}
