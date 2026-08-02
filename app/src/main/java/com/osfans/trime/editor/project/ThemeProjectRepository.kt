/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import android.net.Uri
import com.osfans.trime.editor.core.ParseResult
import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeLuaWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

interface ThemeProjectRepository {
    @Throws(IOException::class)
    fun read(): String
    @Throws(IOException::class)
    fun write(source: String)

    fun load(parser: ThemeLuaParser = ThemeLuaParser()): ParseResult = parser.parse(read())
    fun save(document: ThemeDocument) = write(ThemeLuaWriter.write(document))
}

class FileThemeProjectRepository(private val file: File) : ThemeProjectRepository {
    override fun read(): String = file.readText(Charsets.UTF_8)
    override fun write(source: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.editor-${System.nanoTime()}.tmp")
        temporary.writeText(source, Charsets.UTF_8)
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}

/** Adapter for SAF or other URI providers. The caller owns stream lifetime. */
class UriThemeProjectRepository(
    private val uri: Uri,
    private val input: (Uri) -> InputStream,
    private val output: (Uri) -> OutputStream,
) : ThemeProjectRepository {
    override fun read(): String = input(uri).use { it.reader(Charsets.UTF_8).readText() }
    override fun write(source: String) = output(uri).use { it.writer(Charsets.UTF_8).use { writer -> writer.write(source) } }
}


object ThemeProjectArchive {
    fun exportDirectory(source: File, destination: File) {
        require(source.isDirectory) { "Theme source must be a directory" }
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.${System.nanoTime()}.tmp")
        ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(source).invariantSeparatorsPath
                require(relative.isNotEmpty() && !relative.startsWith("/") && !relative.contains(".."))
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun extractZip(input: java.io.InputStream, destination: File, maxFiles: Int = 500, maxBytes: Long = 64L * 1024 * 1024) {
        destination.mkdirs()
        var files = 0
        var bytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                require(name.isNotEmpty() && !name.startsWith('/') && name.split('/').none { it == ".." })
                require(++files <= maxFiles)
                val target = File(destination, name)
                require(target.canonicalPath.startsWith(destination.canonicalPath + File.separator))
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (zip.read(buffer).also { count = it } >= 0) {
                            bytes += count
                            require(bytes <= maxBytes)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
