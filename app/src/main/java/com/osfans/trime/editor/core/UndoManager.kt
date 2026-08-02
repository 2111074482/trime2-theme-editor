/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

class ThemeUndoManager(private val limit: Int = 100) {
    private val undo = ArrayDeque<ThemeDocument>(); private val redo = ArrayDeque<ThemeDocument>()
    fun record(document: ThemeDocument) { if (undo.lastOrNull() != document) { undo.addLast(document); while (undo.size > limit) undo.removeFirst() }; redo.clear() }
    fun undo(current: ThemeDocument): ThemeDocument? { val previous = undo.removeLastOrNull() ?: return null; redo.addLast(current); return previous }
    fun redo(current: ThemeDocument): ThemeDocument? { val next = redo.removeLastOrNull() ?: return null; undo.addLast(current); return next }
    fun canUndo() = undo.isNotEmpty(); fun canRedo() = redo.isNotEmpty(); fun clear() { undo.clear(); redo.clear() }
}
