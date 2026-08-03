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
    fun save(document: ThemeDocument) = write(ThemeLuaWriter.write(document))
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
    override fun read(): String = (resolver.openInputStream(uri) ?: throw IOException("Cannot open theme source")).use { it.reader(Charsets.UTF_8).readText() }
    override fun write(source: String) = (resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write theme source")).use { it.writer(Charsets.UTF_8).use { writer -> writer.write(source) } }
}


internal fun writeTextTransaction(destination: File, source: String) {
    destination.parentFile?.let { parent ->
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create ${parent.absolutePath}")
    }
    val temporary = File(destination.parentFile, ".${destination.name}.editor-${System.nanoTime()}.tmp")
    try {
        FileOutputStream(temporary).use { output ->
            output.write(source.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        replaceFileTransaction(temporary, destination)
    } finally {
        if (temporary.exists()) temporary.delete()
    }
}

internal fun replaceFileTransaction(temporary: File, destination: File) {
    require(temporary.isFile) { "Replacement source must be a file" }
    val backup = File(destination.parentFile, ".${destination.name}.backup-${System.nanoTime()}")
    var backedUp = false
    try {
        if (destination.exists()) {
            if (!destination.isFile || !destination.renameTo(backup)) throw IOException("Cannot back up ${destination.name}")
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
            if (destination.length() != temporary.length()) throw IOException("Replacement verification failed for ${destination.name}")
            temporary.delete()
        }
        if (backedUp && backup.exists() && !backup.delete()) throw IOException("Cannot remove backup for ${destination.name}")
    } catch (error: Exception) {
        if (destination.exists()) destination.delete()
        if (backedUp && backup.exists() && !backup.renameTo(destination)) {
            throw IOException("File replacement failed and backup restoration failed for ${destination.name}", error)
        }
        throw IOException("File replacement failed for ${destination.name}", error)
    } finally {
        if (temporary.exists()) temporary.delete()
    }
}

object ThemeProjectArchive {
    @JvmStatic
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
        replaceFileTransaction(temporary, destination)
    }

    @JvmStatic
    @JvmOverloads
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
