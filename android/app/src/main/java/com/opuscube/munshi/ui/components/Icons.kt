package com.opuscube.munshi.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Line icons from the Figma file (24 grid, 1.8 stroke, round caps). Tint with Icon(tint = …).
object MunshiIcons {
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"
    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    val Mic = icon("Mic", rrect(9f, 2.5f, 6f, 11.5f, 3f), "M5.5 10.5v.5a6.5 6.5 0 0 0 13 0v-.5", "M12 17.5v4")
    val Camera = icon("Camera", "M4 7.5h3l1.8-2.7h6.4L17 7.5h3a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1z", circle(12f, 13.5f, 3.6f))
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7.5")
    val Close = icon("Close", "M6.5 6.5l11 11M17.5 6.5l-11 11")
    val Store = icon("Store", "M3.5 9.5L5.5 4.5h13l2 5", "M4.5 9.5V20h15V9.5", "M9.5 20v-5.5h5V20")
    val Phone = icon("Phone", rrect(7f, 2.5f, 10f, 19f, 2.2f), "M11 18h2")
    val Bell = icon("Bell", "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.8 1.8H4.2z", "M10 21h4")
    val Spark = icon("Spark", "M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z", "M18.5 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z")
    val Qr = icon("Qr", rrect(4f, 4f, 6f, 6f, 1f), rrect(14f, 4f, 6f, 6f, 1f), rrect(4f, 14f, 6f, 6f, 1f), "M14 14h2.5v2.5H14zM17.5 17.5H20V20h-2.5zM14 19.5h2")
    val ChevronRight = icon("ChevronRight", "M9.5 6l6 6-6 6")
    val Wifi = icon("Wifi", "M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.8 16a4.8 4.8 0 0 1 6.4 0", circle(12f, 19.3f, 0.9f))
    val Speaker = icon("Speaker", "M4 9.5h3.5L12.5 5.5v13l-5-4H4z", "M16 9a4.2 4.2 0 0 1 0 6M18.6 6.5a8 8 0 0 1 0 11")
    val Rupee = icon("Rupee", "M7 4.5h10M7 8.5h10M7 4.5h3.5a4 4 0 0 1 0 8H7l8 7")
}
