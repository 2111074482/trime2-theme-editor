/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ThemeFieldRegistryTest : StringSpec({
    "exposes coverage metadata for unsupported interactions" {
        val registry = ThemeFieldRegistry()
        registry.find("key.swipe_left")?.consumption shouldBe ConsumptionStatus.PARSED_NOT_TRIGGERED
        registry.find("double_click")?.previewSupport shouldBe PreviewSupport.DISABLED_WITH_REASON
        registry.coverage().missing shouldBe 0
    }

    "component metadata matches runtime consumption boundaries" {
        val registry = ThemeFieldRegistry()
        registry.find("candidate.expanded.height")?.editorSupport shouldBe EditorSupport.CODE_ONLY
        registry.find("toolbar.height")?.editorSupport shouldBe EditorSupport.CODE_ONLY
        registry.find("toolbar.text_color")?.editorSupport shouldBe EditorSupport.CODE_ONLY
        registry.find("clipboard.key.pressed.translation_y")?.consumption shouldBe ConsumptionStatus.CONSUMED
        registry.find("clipboard.item.pressed.translation_y")?.consumption shouldBe ConsumptionStatus.CONSUMED
        registry.find("symbol.tool_bar.pressed.translation_y")?.editorSupport shouldBe EditorSupport.CODE_ONLY
        registry.find("clipboard.tool_bar.pressed.translation_y")?.previewSupport shouldBe PreviewSupport.DISABLED_WITH_REASON
    }
    "accepts preserved dynamic expressions for typed fields" {
        val registry = ThemeFieldRegistry()
        registry.validate("candidate.height", ThemeValue.RawLuaNode("base_height * scale")) shouldBe null
    }

})
