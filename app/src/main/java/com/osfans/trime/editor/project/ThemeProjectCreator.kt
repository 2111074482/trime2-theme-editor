/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File

/** Creates a conservative literal theme project without executing Lua. */
object ThemeProjectCreator {
    enum class Palette { LIGHT, DARK }
    enum class KeyboardTemplate { ROWS, FLEX_BOX, KEY_MAPS, ABSOLUTE_KEYS }

    data class Spec(
        val directoryName: String,
        val themeName: String,
        val author: String,
        val styleName: String,
        val keyboardName: String,
        val palette: Palette,
        val keyboardTemplate: KeyboardTemplate,
    ) {
        fun validated(): Spec {
            require(SAFE_NAME.matches(directoryName)) { "目录标识只能包含英文字母、数字、空格、下划线和连字符" }
            require(SAFE_ID.matches(styleName)) { "样式标识必须是 Lua 安全标识符" }
            require(SAFE_ID.matches(keyboardName)) { "键盘标识必须是 Lua 安全标识符" }
            require(themeName.isNotBlank()) { "主题名称不能为空" }
            require(author.isNotBlank()) { "作者不能为空" }
            return this
        }
    }

    @JvmStatic
    fun create(root: File, spec: Spec): ThemeProject {
        spec.validated()
        require(!root.exists() || root.listFiles().isNullOrEmpty()) { "目标项目目录不为空" }
        require(root.exists() || root.mkdirs()) { "无法创建项目目录" }
        write(root.resolve("main.lua"), mainSource(spec))
        write(root.resolve("styles/${spec.styleName}/main.lua"), styleSource(spec))
        write(root.resolve("keyboards/${spec.keyboardName}.lua"), keyboardSource(spec))
        listOf("images", "fonts", "sounds", "scripts").forEach { root.resolve(it).mkdirs() }
        return ThemeProject.discover(root)
    }

    @JvmStatic fun mainSource(spec: Spec): String = buildString {
        append("name = ").append(luaString(spec.themeName)).append('\n')
        append("author = ").append(luaString(spec.author)).append('\n')
        append("style = ").append(luaString(spec.styleName)).append('\n')
        append("keyboard = ").append(luaString(spec.keyboardName)).append('\n')
    }

    @JvmStatic fun styleSource(spec: Spec): String {
        val dark = spec.palette == Palette.DARK
        val background = if (dark) "0xff202124" else "0xffdddddd"
        val keyBackground = if (dark) "0xff3c4043" else "0xffffffff"
        val text = if (dark) "0xffffffff" else "0xff000000"
        val pressed = if (dark) "0xff5f6368" else "0xff888888"
        return """name = ${luaString(if (dark) "深色" else "浅色")}
author = ${luaString(spec.author)}
background = $background
keyboard = { height = 240, background = $background }
key = {
  text_color = $text,
  text_size = 22,
  background = $keyBackground,
  corner_radius = 8,
  pressed = { background = $pressed, text_color = $text },
  hint = { show = true, text_color = $text, text_size = 12 },
  long_click = { show = true, text_color = $text, text_size = 12 },
}
candidate = { height = 48, background = $background, text_color = $text, text_size = 22 }
toolbar = { height = 40, background = $background, text_color = $text }
symbol = { background = $background, text_color = $text }
preedit = { background = $background, text_color = $text, text_size = 18 }
composition = { background = $background, text_color = $text, text_size = 18 }
"""
    }

    @JvmStatic fun keyboardSource(spec: Spec): String {
        val header = "name = ${luaString(spec.keyboardName)}\nauthor = ${luaString(spec.author)}\nkey_width = 10\nkey_height = 21\n"
        val layout = when (spec.keyboardTemplate) {
            KeyboardTemplate.ROWS -> """rows = {
  { keys = { { click = "q" }, { click = "w" }, { click = "e" }, { click = "r" }, { click = "t" }, { click = "y" }, { click = "u" }, { click = "i" }, { click = "o" }, { click = "p" } } },
  { keys = { { width = 5 }, { click = "a" }, { click = "s" }, { click = "d" }, { click = "f" }, { click = "g" }, { click = "h" }, { click = "j" }, { click = "k" }, { click = "l" } } },
  { keys = { { click = "Shift_L", width = 15 }, { click = "z" }, { click = "x" }, { click = "c" }, { click = "v" }, { click = "b" }, { click = "n" }, { click = "m" }, { click = "BackSpace", width = 15 } } },
  { keys = { { click = "Keyboard_symbols", width = 15 }, { click = "space", width = 55 }, { click = "Return", width = 30 } } },
}
"""
            KeyboardTemplate.FLEX_BOX -> """flex_box = {
  direction = "row",
  { grow = 1, direction = "column", keys = { { click = "1" }, { click = "4" }, { click = "7" }, { click = "BackSpace" } } },
  { grow = 1, direction = "column", keys = { { click = "2" }, { click = "5" }, { click = "8" }, { click = "0" } } },
  { grow = 1, direction = "column", keys = { { click = "3" }, { click = "6" }, { click = "9" }, { click = "Return" } } },
}
"""
            KeyboardTemplate.KEY_MAPS -> """key_maps = {
  { name = "常用", keys = { ",", ".", "?", "!", ":", ";", "/", "\\\\", "@", "#" } },
  { name = "中文", keys = { ",", "。", "?", "!", ":", "、", "“", "”", "《", "》" } },
}
"""
            KeyboardTemplate.ABSOLUTE_KEYS -> """keys = {
  { click = "a", x = 5, y = 5, width = 20, height = 18 },
  { click = "b", x = 28, y = 5, width = 20, height = 18 },
  { click = "space", x = 20, y = 28, width = 60, height = 18 },
  { click = "Return", x = 70, y = 52, width = 25, height = 18 },
}
"""
        }
        return header + layout
    }

    private fun write(file: File, source: String) {
        require(file.parentFile?.let { it.exists() || it.mkdirs() } != false) { "无法创建项目文件的父目录" }
        file.writeText(source, Charsets.UTF_8)
    }

    private fun luaString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    const val EDITOR_SCHEMA_VERSION = 1
    const val EDITOR_SOURCE = "Trime2 0.7.9.2 theme editor"

    private val SAFE_NAME = Regex("^[A-Za-z0-9_ -]{1,64}$")
    private val SAFE_ID = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
}
