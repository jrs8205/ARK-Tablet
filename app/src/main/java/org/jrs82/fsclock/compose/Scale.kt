package org.jrs82.fsclock.compose

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/** cqh/cqw-style scaling: dh/dw are % of the stage height/width,
 *  sh is a font size scaled to the stage, ds is a Dp tied to the font unit
 *  (for fixed-width digit slots — keeps the same proportion to the font). */
class Scale(
    val chPx: Float,
    val cwPx: Float,
    private val textPx: Float,
    val density: Density,
) {
    fun dh(n: Float): Dp = with(density) { (chPx * n).toDp() }
    fun dw(n: Float): Dp = with(density) { (cwPx * n).toDp() }
    fun sh(n: Float): TextUnit = with(density) { (textPx * n).toSp() }
    fun ds(n: Float): Dp = with(density) { (textPx * n).toDp() }
}
