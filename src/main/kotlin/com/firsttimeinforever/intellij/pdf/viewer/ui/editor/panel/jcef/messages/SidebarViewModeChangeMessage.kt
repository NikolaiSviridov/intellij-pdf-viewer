package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef.messages

import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef.SidebarViewMode
import kotlinx.serialization.Serializable

@Serializable
data class SidebarViewModeChangeMessage(val mode: String) {
    companion object {
        fun from(mode: SidebarViewMode) =
            SidebarViewModeChangeMessage(mode.displayName)
    }
}
