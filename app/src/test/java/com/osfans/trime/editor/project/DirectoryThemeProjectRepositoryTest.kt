/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class DirectoryThemeProjectRepositoryTest : StringSpec({
    "writes selected keyboard without changing main file" {
        val root = Files.createTempDirectory("theme-repository").toFile()
        root.resolve("main.lua").writeText("keyboard = 'qwerty'\n")
        root.resolve("keyboards/qwerty.lua").apply { parentFile.mkdirs(); writeText("rows = {}\n") }
        val project = ThemeProject.discover(root)
        val selected = project.keyboard("qwerty")!!
        DirectoryThemeProjectRepository(project, selected).write("rows = { { keys = {} } }\n")
        root.resolve("main.lua").readText() shouldBe "keyboard = 'qwerty'\n"
        root.resolve("keyboards/qwerty.lua").readText() shouldBe "rows = { { keys = {} } }\n"
        root.deleteRecursively()
    }

    "rejects invalid Lua before replacing the selected file" {
        val root = Files.createTempDirectory("theme-repository-invalid").toFile()
        root.resolve("main.lua").writeText("keyboard = 'qwerty'\n")
        root.resolve("keyboards/qwerty.lua").apply { parentFile.mkdirs(); writeText("rows = {}\n") }
        val project = ThemeProject.discover(root)
        var rejected = false
        try { DirectoryThemeProjectRepository(project, project.keyboard("qwerty")!!).write("rows = {\n") }
        catch (_: java.io.IOException) { rejected = true }
        rejected shouldBe true
        root.resolve("keyboards/qwerty.lua").readText() shouldBe "rows = {}\n"
        root.deleteRecursively()
    }

    "exports legal dotted names and rejects zip traversal" {
        val root = Files.createTempDirectory("theme-archive").toFile()
        root.resolve("images/icon..bak").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val archive = root.parentFile.resolve("theme-archive-${System.nanoTime()}.zip")
        ThemeProjectArchive.exportDirectory(root, archive)
        val extracted = Files.createTempDirectory("theme-archive-extracted").toFile()
        archive.inputStream().use { ThemeProjectArchive.extractZip(it, extracted) }
        extracted.resolve("images/icon..bak").readBytes().toList() shouldBe listOf<Byte>(1, 2, 3)

        val traversal = java.io.ByteArrayOutputStream().also { bytes ->
            java.util.zip.ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("../outside.lua"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val rejectedTarget = root.parentFile.resolve("theme-archive-rejected-${System.nanoTime()}")
        var rejected = false
        try { traversal.inputStream().use { ThemeProjectArchive.extractZip(it, rejectedTarget) } } catch (_: IllegalArgumentException) { rejected = true }
        rejected shouldBe true
        rejectedTarget.exists() shouldBe false
        archive.delete(); extracted.deleteRecursively(); root.deleteRecursively()
    }


    "archive rejects a project symlink instead of reading its target" {
        val root = Files.createTempDirectory("theme-archive-link").toFile()
        val outside = Files.createTempFile("theme-archive-secret", ".txt").toFile().apply { writeText("secret") }
        val link = root.resolve("images/linked.txt").apply { parentFile.mkdirs() }
        val linked = try { Files.createSymbolicLink(link.toPath(), outside.toPath()); true } catch (_: Exception) { false }
        if (linked) {
            var rejected = false
            try { ThemeProjectArchive.exportDirectory(root, root.parentFile.resolve("linked-${System.nanoTime()}.zip")) } catch (_: IllegalArgumentException) { rejected = true }
            rejected shouldBe true
        }
        root.deleteRecursively(); outside.delete()
    }

    "rejects duplicate and invalid zip entry names" {
        fun archive(vararg names: String): java.io.File {
            val file = Files.createTempFile("theme-duplicate", ".zip").toFile()
            java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
                names.forEach { name ->
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write("x".toByteArray())
                    zip.closeEntry()
                }
            }
            return file
        }
        listOf(
            archive("images/a.png", "images/A.png"),
            archive("images//a.png"),
            archive((1..13).joinToString("/") { "d$it" } + "/a.png"),
        ).forEach { zip ->
            val target = Files.createTempDirectory("theme-invalid-zip").toFile().apply { deleteRecursively() }
            var rejected = false
            try { zip.inputStream().use { ThemeProjectArchive.extractZip(it, target) } } catch (_: IllegalArgumentException) { rejected = true }
            rejected shouldBe true
            target.exists() shouldBe false
            zip.delete()
        }
    }

})
