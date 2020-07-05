package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef.messages

import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef.transformColorRgba
import kotlinx.serialization.Serializable
import java.awt.Color

@Serializable
class SetThemeColorsMessage(
    val background: String,
    val foreground: String,
    val icons: String
) {
    companion object {
        fun from(background: Color, foreground: Color, icons: Color) =
            SetThemeColorsMessage(
                transformColorRgba(background),
                transformColorRgba(foreground),
                transformColorRgba(icons)
            )
    }
}
