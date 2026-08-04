/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.ThemeLuaParser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** R1/R2: ThemePreviewStyles 样式映射与取值辅助方法。 */
class ThemePreviewStylesTest : StringSpec({
    "maps key stroke/elevation/font/gravity/padding/show onto the render model" {
        val style = ThemeLuaParser().parse(
            """
            key = {
                background = 0xff112233,
                text_color = 0xff445566,
                corner_radius = 10,
                stroke_width = 2,
                stroke_color = 0xff778899,
                elevation = 4,
                shadow_color = 0xffaabbcc,
                font = "DroidSansMono.ttf",
                gravity = "center|center",
                padding = { left = 2, top = 3, right = 4, bottom = 5 },
                show = true,
            }
            """.trimIndent(),
        ).document
        val model = ThemeEditorModel.sample()
        ThemePreviewStyles.applyStyleDocument(model, style)
        model.keyCornerRadius shouldBe 2f
        model.keys.forEach { key ->
            key.fillColor shouldBe 0xff112233.toInt()
            key.textColor shouldBe 0xff445566.toInt()
            key.strokeWidth shouldBe 2f
            key.strokeColor shouldBe 0xff778899.toInt()
            key.elevation shouldBe 4f
            key.shadowColor shouldBe 0xffaabbcc.toInt()
            key.font shouldBe "DroidSansMono.ttf"
            key.gravity shouldBe "center|center"
            key.paddingLeft shouldBe 2f
            key.paddingTop shouldBe 3f
            key.paddingRight shouldBe 4f
            key.paddingBottom shouldBe 5f
            key.show shouldBe true
        }
    }

    "uses safe defaults when the style omits key fields" {
        val style = ThemeLuaParser().parse("key = { background = 0xff010203 }\n").document
        val model = ThemeEditorModel.sample()
        ThemePreviewStyles.applyStyleDocument(model, style)
        model.keys.forEach { key ->
            key.strokeWidth shouldBe 0f
            key.strokeColor shouldBe 0
            key.elevation shouldBe 0f
            key.shadowColor shouldBe 0
            key.font shouldBe ""
            key.gravity shouldBe ""
            key.paddingLeft shouldBe 0f
            key.show shouldBe true
        }
    }

    "stringValue resolves font fallback arrays" {
        val style = ThemeLuaParser().parse("key = { font = { \"a.ttf\", \"b.ttf\" } }\n").document
        ThemePreviewStyles.stringValue(style.get("key.font"), "") shouldBe "a.ttf"
    }

    "numberValue and colorValue parse literals" {
        val style = ThemeLuaParser().parse("keyboard = { height = 240, background = 0xff000001 }\n").document
        ThemePreviewStyles.numberValue(style.get("keyboard.height"), 0f) shouldBe 240f
        ThemePreviewStyles.colorValue(style.get("keyboard.background"), 0) shouldBe 0xff000001.toInt()
        ThemePreviewStyles.booleanValue(style.get("keyboard.height"), true) shouldBe true
    }
})
