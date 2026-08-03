/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ThemePanelComponentsTest : StringSpec({
    "reads literal nested panel roots and explicit fields" {
        val source = """
            candidate = {
              expanded = {
                filter_bar = { show = false, gravity = "bottom", vendor = 1 },
                tool_bar = { gravity = "left", keys = { "hide", "char_filter" } }
              }
            }
            symbol = {
              tab_bar = { gravity = "bottom", height = 36 },
              tool_bar = { gravity = "top", height = 44, keys = { "page_up", "BackSpace" } }
            }
        """.trimIndent() + "\n"

        ThemePanelComponents.readCandidateFilter(source).let {
            it.show shouldBe false
            it.gravity shouldBe "bottom"
            it.showExplicit shouldBe true
            it.gravityExplicit shouldBe true
        }
        ThemePanelComponents.readToolbar(
            source,
            ThemePanelComponents.Panel.CANDIDATE_EXPANDED,
        ).let {
            it.gravity shouldBe "left"
            it.height shouldBe null
            it.keys shouldBe listOf("hide", "char_filter")
            it.keysExplicit shouldBe true
        }
        ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.SYMBOL).let {
            it.gravity shouldBe "top"
            it.height shouldBe 44.0
            it.keys shouldBe listOf("page_up", "BackSpace")
        }
        ThemePanelComponents.readTabBar(source, ThemePanelComponents.Panel.SYMBOL).let {
            it.gravity shouldBe "bottom"
            it.height shouldBe 36.0
        }
    }

    "reads clone roots with later dotted overrides without evaluating the clone" {
        val source = """
            clipboard = table.clone(candidate.expanded)
            -- static editor must use this later override
            clipboard.tool_bar = { gravity = "right", keys = { "hide", "undo" }, height = 48 }
            clipboard.tab_bar = { gravity = "top", height = 40 }
        """.trimIndent() + "\n"

        ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.CLIPBOARD).let {
            it.keys shouldBe listOf("hide", "undo")
            it.height shouldBe 48.0
            it.inherited shouldBe true
            it.sourcePath shouldBe "clipboard.tool_bar"
        }
        ThemePanelComponents.readTabBar(source, ThemePanelComponents.Panel.CLIPBOARD).let {
            it.gravity shouldBe "top"
            it.inherited shouldBe true
        }

        val updated = ThemePanelComponents.updateToolbar(
            source,
            ThemePanelComponents.Panel.CLIPBOARD,
            "bottom",
            52.0,
            listOf("page_up", "page_down", "undo"),
        )
        updated shouldContain "clipboard = table.clone(candidate.expanded)"
        updated shouldContain "-- static editor must use this later override"
        ThemePanelComponents.readToolbar(updated, ThemePanelComponents.Panel.CLIPBOARD).let {
            it.gravity shouldBe "bottom"
            it.height shouldBe 52.0
            it.keys shouldBe listOf("page_up", "page_down", "undo")
        }
    }

    "reports runtime defaults while preserving missing flags per panel" {
        ThemePanelComponents.readCandidateFilter("").let {
            it.show shouldBe true
            it.gravity shouldBe "left"
            it.showExplicit shouldBe false
            it.gravityExplicit shouldBe false
        }
        ThemePanelComponents.readToolbar("", ThemePanelComponents.Panel.CANDIDATE_EXPANDED).let {
            it.gravity shouldBe "right"
            it.keys shouldBe listOf("hide", "page_up", "page_down", "char_filter")
            it.gravityExplicit shouldBe false
            it.heightExplicit shouldBe false
            it.keysExplicit shouldBe false
        }
        ThemePanelComponents.readToolbar("", ThemePanelComponents.Panel.SYMBOL).keys shouldBe
            listOf("hide", "page_up", "page_down", "BackSpace")
        ThemePanelComponents.readToolbar("", ThemePanelComponents.Panel.CLIPBOARD).keys shouldBe
            listOf("hide", "page_up", "page_down", "undo")
        ThemePanelComponents.readTabBar("", ThemePanelComponents.Panel.SYMBOL).let {
            it.gravity shouldBe null
            it.height shouldBe null
            it.gravityExplicit shouldBe false
            it.heightExplicit shouldBe false
        }
    }

    "round trips toolbar snapshots and preserves unrelated source comments and fields" {
        val source = """
            -- before toolbar
            symbol.tool_bar = {
              gravity = "right",
              keys = { "hide" },
              height = 48,
              custom_color = 0xff00ff,
              vendor_value = calculate_value()
            } -- toolbar comment
            unrelated = { message = "same" }
        """.trimIndent() + "\n"

        val updated = ThemePanelComponents.updateToolbar(
            source,
            ThemePanelComponents.Panel.SYMBOL,
            "bottom",
            52.5,
            listOf("page_up", "page_down", "BackSpace"),
        )
        updated shouldContain "-- before toolbar"
        updated shouldContain "-- toolbar comment"
        updated shouldContain "custom_color = 16711935"
        updated shouldContain "vendor_value = calculate_value()"
        updated shouldContain "unrelated = { message = \"same\" }"
        ThemePanelComponents.readToolbar(updated, ThemePanelComponents.Panel.SYMBOL).let {
            it.gravity shouldBe "bottom"
            it.height shouldBe 52.5
            it.keys shouldBe listOf("page_up", "page_down", "BackSpace")
        }
    }

    "nullable snapshot values remove explicit fields and restore defaults" {
        var source = "clipboard.tool_bar = { gravity = \"left\", height = 50, keys = { \"hide\" }, custom = true }\n"
        source = ThemePanelComponents.updateToolbar(
            source,
            ThemePanelComponents.Panel.CLIPBOARD,
            null,
            null,
            null,
        )
        source shouldContain "custom = true"
        ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.CLIPBOARD).let {
            it.gravity shouldBe "right"
            it.height shouldBe null
            it.keys shouldBe listOf("hide", "page_up", "page_down", "undo")
            it.gravityExplicit shouldBe false
            it.heightExplicit shouldBe false
            it.keysExplicit shouldBe false
        }
    }

    "round trips both tab bars and candidate filter" {
        var source = "-- heading\nunrelated = 7\n"
        source = ThemePanelComponents.updateTabBar(
            source,
            ThemePanelComponents.Panel.SYMBOL,
            "bottom",
            37.0,
        )
        source = ThemePanelComponents.updateTabBar(
            source,
            ThemePanelComponents.Panel.CLIPBOARD,
            "top",
            null,
        )
        source = ThemePanelComponents.updateCandidateFilter(source, false, "right")

        source shouldContain "-- heading"
        source shouldContain "unrelated = 7"
        ThemePanelComponents.readTabBar(source, ThemePanelComponents.Panel.SYMBOL).let {
            it.gravity shouldBe "bottom"
            it.height shouldBe 37.0
        }
        ThemePanelComponents.readTabBar(source, ThemePanelComponents.Panel.CLIPBOARD).let {
            it.gravity shouldBe "top"
            it.height shouldBe null
            it.heightExplicit shouldBe false
        }
        ThemePanelComponents.readCandidateFilter(source).let {
            it.show shouldBe false
            it.gravity shouldBe "right"
        }
    }

    "validates gravity boolean number and candidate toolbar height" {
        listOf("center", "start", "TOP").forEach { gravity ->
            shouldThrow<IllegalArgumentException> {
                ThemePanelComponents.readToolbar(
                    "symbol.tool_bar = { gravity = \"$gravity\", keys = {} }\n",
                    ThemePanelComponents.Panel.SYMBOL,
                )
            }
        }
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.readTabBar(
                "symbol.tab_bar = { gravity = \"left\" }\n",
                ThemePanelComponents.Panel.SYMBOL,
            )
        }
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.readCandidateFilter("candidate.expanded.filter_bar = { show = \"true\" }\n")
        }
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.readToolbar(
                "symbol.tool_bar = { height = \"48\", keys = {} }\n",
                ThemePanelComponents.Panel.SYMBOL,
            )
        }
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { height ->
            shouldThrow<IllegalArgumentException> {
                ThemePanelComponents.updateTabBar(
                    "",
                    ThemePanelComponents.Panel.CLIPBOARD,
                    "top",
                    height,
                )
            }
        }
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.updateToolbar(
                "",
                ThemePanelComponents.Panel.CANDIDATE_EXPANDED,
                "right",
                48.0,
                listOf("hide"),
            )
        }
    }

    "enforces contiguous literal string arrays and blocks direct event tables" {
        val invalid = listOf(
            "symbol.tool_bar = { keys = \"hide\" }\n",
            "symbol.tool_bar = { keys = { \"hide\", 2 } }\n",
            "symbol.tool_bar = { keys = { hide = \"hide\" } }\n",
            "symbol.tool_bar = { keys = { [2] = \"page_down\" } }\n",
            "symbol.tool_bar = { keys = { { send = \"BackSpace\" } } }\n",
        )
        invalid.forEach { source ->
            shouldThrow<IllegalArgumentException> {
                ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.SYMBOL)
            }
        }
    }

    "blocks Raw Lua components and modeled fields rather than executing or replacing them" {
        val sources = listOf(
            "symbol.tool_bar = make_toolbar(dangerous())\n",
            "symbol.tool_bar = { gravity = choose_gravity(), keys = { \"hide\" } }\n",
            "symbol.tool_bar = { keys = build_keys() }\n",
            "clipboard.tab_bar = table.clone(candidate)\n",
            "candidate.expanded.filter_bar = { show = should_show() }\n",
        )
        sources.forEach { source ->
            shouldThrow<IllegalArgumentException> {
                when {
                    source.startsWith("clipboard.tab_bar") -> ThemePanelComponents.updateTabBar(
                        source,
                        ThemePanelComponents.Panel.CLIPBOARD,
                        "top",
                        40.0,
                    )
                    source.startsWith("candidate") -> ThemePanelComponents.updateCandidateFilter(source, true, "left")
                    else -> ThemePanelComponents.updateToolbar(
                        source,
                        ThemePanelComponents.Panel.SYMBOL,
                        "right",
                        48.0,
                        listOf("hide"),
                    )
                }
            }
        }
    }

    "rejects duplicate relevant assignments and honors a later root" {
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.readToolbar(
                "symbol.tool_bar = { keys = { \"hide\" } }\nsymbol.tool_bar = { keys = { \"page_up\" } }\n",
                ThemePanelComponents.Panel.SYMBOL,
            )
        }

        val source = """
            symbol.tool_bar = { gravity = "left", keys = { "old" } }
            symbol = {
              marker = "keep",
              tool_bar = { gravity = "bottom", height = 41, keys = { "effective" } }
            }
        """.trimIndent() + "\n"
        ThemePanelComponents.readToolbar(source, ThemePanelComponents.Panel.SYMBOL).keys shouldBe
            listOf("effective")
        val updated = ThemePanelComponents.updateToolbar(
            source,
            ThemePanelComponents.Panel.SYMBOL,
            "top",
            42.0,
            listOf("new"),
        )
        updated shouldContain "symbol.tool_bar = { gravity = \"left\", keys = { \"old\" } }"
        updated shouldContain "marker = \"keep\""
        ThemePanelComponents.readToolbar(updated, ThemePanelComponents.Panel.SYMBOL).let {
            it.gravity shouldBe "top"
            it.height shouldBe 42.0
            it.keys shouldBe listOf("new")
            it.sourcePath shouldBe "symbol"
        }
    }

    "blocks dynamic later roots bracket notation and unsupported nested field assignments" {
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.updateToolbar(
                "symbol.tool_bar = { keys = { \"safe\" } }\nsymbol = make_symbol()\n",
                ThemePanelComponents.Panel.SYMBOL,
                "right",
                null,
                listOf("hide"),
            )
        }
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.readToolbar(
                "symbol[\"tool_bar\"] = { keys = { \"hide\" } }\n",
                ThemePanelComponents.Panel.SYMBOL,
            )
        }
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.updateToolbar(
                "symbol.tool_bar.gravity = \"left\"\n",
                ThemePanelComponents.Panel.SYMBOL,
                "right",
                null,
                listOf("hide"),
            )
        }
    }

    "candidate panel cannot be used with tab bar APIs" {
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.readTabBar("", ThemePanelComponents.Panel.CANDIDATE_EXPANDED)
        }
        shouldThrow<IllegalArgumentException> {
            ThemePanelComponents.updateTabBar(
                "",
                ThemePanelComponents.Panel.CANDIDATE_EXPANDED,
                "top",
                null,
            )
        }
    }

    "creates missing ancestor tables instead of emitting invalid dotted assignments" {
        val symbol = ThemePanelComponents.updateTabBar("", ThemePanelComponents.Panel.SYMBOL, "top", 40.0)
        symbol shouldContain "symbol = {"
        symbol shouldContain "tab_bar = {"
        symbol.contains("symbol.tab_bar =") shouldBe false
        ThemePanelComponents.readTabBar(symbol, ThemePanelComponents.Panel.SYMBOL).height shouldBe 40.0

        val candidate = ThemePanelComponents.updateCandidateFilter("unrelated = true\n", false, "bottom")
        candidate shouldContain "candidate = {"
        candidate shouldContain "expanded = {"
        ThemePanelComponents.readCandidateFilter(candidate).show shouldBe false
    }

})
