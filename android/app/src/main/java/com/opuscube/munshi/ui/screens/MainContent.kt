package com.opuscube.munshi.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.conductor.Mode
import com.opuscube.munshi.conductor.QrState
import com.opuscube.munshi.conductor.ShelfLine
import com.opuscube.munshi.conductor.UiState
import com.opuscube.munshi.ui.components.*
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import com.opuscube.munshi.ui.theme.rupees

data class MainActions(
    val openShop: () -> Unit, val yes: () -> Unit, val no: () -> Unit, val mic: () -> Unit, val camera: () -> Unit,
    val week: () -> Unit, val settings: () -> Unit, val udhaar: () -> Unit, val closeShop: () -> Unit, val relink: (String) -> Unit,
)

// The counter screen (Figma C1–C6): the page shows what is happening, the dock only what can be done now.
@Composable
fun MainContent(state: UiState, actions: MainActions, today: String, modifier: Modifier = Modifier, dateLine: String = "", animate: Boolean = true) {
    // Paper fills the whole screen; the page and the dock stay inside the safe area, clear of the status bar,
    // the three-button navigation bar and any cutout (Android 15+ draws apps edge to edge).
    BoxWithConstraints(modifier.fillMaxSize().background(Tokens.Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                LedgerPage(Modifier.weight(1.45f)) { Page(state, actions, today, dateLine, k = 1.3f, animate = animate) }
                InkDock(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) { Spacer(Modifier.weight(1f)); Dock(state, actions) }
            }
        } else {
            LedgerPage {
                Page(state, actions, today, dateLine, bottomPad = 240.dp, animate = animate)
                InkDock(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Tokens.SpaceDockInset.dp)) { Dock(state, actions) }
            }
        }
    }
}

// k scales the type up on the wide tablet page, where phone sizes look lost.
@Composable
private fun Page(s: UiState, a: MainActions, today: String, dateLine: String, bottomPad: Dp = 24.dp, k: Float = 1f, animate: Boolean = true) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 24.dp, top = 8.dp, bottom = bottomPad)) {
        TopBar(statusOf(s.live, s.mind), s.ledger?.total, flash = s.payQrs.any { it.state == QrState.Paid }, onBrand = a.settings, onWeek = a.week)
        s.banner?.let { Spacer(Modifier.height(10.dp)); Banner(it) }
        Spacer(Modifier.height(22.dp))
        when {
            s.ask != null -> AskBlock(s, k)
            s.mode == Mode.Working && s.asked != null -> ThinkingBlock(s.asked, k)
            !s.shopOpen -> BriefBlock(s, dateLine, k)
            else -> OpenBlock(s, a, k)
        }
        val heard = s.heard
        if (s.mode == Mode.Listening && heard != null) { Spacer(Modifier.height(14.dp)); LiveCaption(heard) }
        if ((s.shopOpen || s.ask != null) && s.payQrs.isNotEmpty()) { Spacer(Modifier.height(22.dp)); QrStack(s.payQrs, today, a.relink, animate = animate) }
    }
}

fun kickerFor(type: String?): String = when (type) {
    "udhaar_overdue" -> "Udhaar"
    "local_demand_spike" -> "Mauka"
    "group_buy_open" -> "Group order"
    "winback" -> "Grahak wapas"
    "working_capital" -> "Loan"
    "restock" -> "Shelf se"
    else -> "Munshi pooch raha hai"
}

@Composable
private fun BriefBlock(s: UiState, dateLine: String, k: Float) {
    if (dateLine.isNotEmpty()) { Kicker(dateLine, color = Tokens.Ink3); Spacer(Modifier.height(10.dp)) }
    Text("Shubh prabhat, ${s.profile?.addressAs ?: "Ramesh ji"}", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = (34 * k).sp, lineHeight = (40 * k).sp, color = Tokens.Ink)
    val b = s.brief ?: return
    Spacer(Modifier.height(8.dp))
    Text(if (b.items.isEmpty()) "Aaj koi naya kaam nahi." else "Aaj ke ${b.items.size} kaam se ${rupees(b.total)} aa sakte hain.",
        fontFamily = Inter, fontSize = (15 * k).sp, lineHeight = (22 * k).sp, color = Tokens.Ink2)
    Spacer(Modifier.height(8.dp))
    b.items.forEach { TaskRow(kickerFor(it.type), it.title, it.amount) }
}

@Composable
private fun AskBlock(s: UiState, k: Float) {
    val ask = s.ask ?: return
    s.shelf?.let { ShelfBlock(it, k); Spacer(Modifier.height(24.dp)) }
    Kicker(kickerFor(ask.type), speaking = s.mode == Mode.Speaking)
    Spacer(Modifier.height(12.dp))
    val d = ask.display
    if (d != null) {
        Text(d.who, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = (24 * k).sp, color = Tokens.Ink)
        RupeeFigure(d.amount, size = (Tokens.TypeRupeeSize * k).sp)
        Spacer(Modifier.height(8.dp))
        Text(d.rest, fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = (Tokens.TypeLineSize * k).sp, lineHeight = (25 * k).sp, color = Tokens.Ink2)
    } else {
        Text(ask.line.text, fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = (22 * k).sp, lineHeight = (30 * k).sp, color = Tokens.Ink)
    }
    if (ask.type == "udhaar_overdue") {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag("WhatsApp reminder"); Tag("Link 1 ghante ka") }
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(MunshiIcons.Mic, null, Modifier.size(16.dp), tint = Tokens.Ink3)
        Text("Kuch aur poochna ho toh Boliye dabaiye", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
    }
}

@Composable
private fun Tag(text: String) =
    Text(text, Modifier.background(Tokens.Ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens.RadiusTag.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Ink2)

@Composable
private fun ShelfBlock(lines: List<ShelfLine>, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(MunshiIcons.Camera, null, Modifier.size(16.dp), tint = Tokens.Red); Kicker("Shelf dekh li")
    }
    Spacer(Modifier.height(8.dp))
    val low = lines.filter { it.level != "ok" }.map { it.name }
    Text(if (low.isEmpty()) "Shelf mein sab theek hai" else "${low.joinToString(" aur ")} kam hain",
        fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = (30 * k).sp, lineHeight = (36 * k).sp, color = Tokens.Ink)
    Spacer(Modifier.height(12.dp))
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    Column(Modifier.fillMaxWidth().background(Tokens.Card, shape).border(1.dp, Tokens.Rule, shape).padding(horizontal = 16.dp)) {
        lines.forEachIndexed { i, l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(l.name, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Tokens.Ink)
                    l.count?.let { Text("$it packet dikhe", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3) }
                }
                val (word, c) = when (l.level) { "ok" -> "Theek" to Tokens.Ok; "empty" -> "Khatam" to Tokens.Red; else -> "Kam" to Tokens.Warn }
                Pill(word, c)
            }
            if (i < lines.lastIndex) HorizontalDivider(color = Tokens.Rule)
        }
    }
}

@Composable
private fun Pill(text: String, c: Color) =
    Text(text, Modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(Tokens.RadiusPill.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c)

@Composable
private fun ThinkingBlock(asked: String, k: Float) {
    Kicker("Aapne poocha", color = Tokens.Ink3)
    Spacer(Modifier.height(10.dp))
    Text("“${asked.replaceFirstChar { it.uppercase() }}”", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = (30 * k).sp, lineHeight = (36 * k).sp, color = Tokens.Ink)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { for (a in listOf(1f, 0.6f, 0.3f)) Box(Modifier.size(8.dp).background(Tokens.Red.copy(alpha = a), CircleShape)) }
        Text("Ek second, dekhta hoon", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Tokens.Ink2)
    }
}

@Composable
private fun OpenBlock(s: UiState, a: MainActions, k: Float) {
    Kicker("Dukaan khuli hai", color = Tokens.Ok)
    Spacer(Modifier.height(10.dp))
    Text("Sab theek hai. Munshi sun raha hai.", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = (30 * k).sp, lineHeight = (36 * k).sp, color = Tokens.Ink)
    s.caption?.let {
        Spacer(Modifier.height(10.dp))
        Text(it.text, fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = (Tokens.TypeLineSize * k).sp, lineHeight = (25 * k).sp, color = Tokens.Ink2)
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        s.ledger?.let { Tally(rupees(it.total), "is hafte") }
        Tally("${s.payQrs.count { it.state == QrState.Open }}", "khule QR")
        Tally(rupees(s.khata.sumOf { it.amount }), "udhaar baaki ›", a.udhaar)
    }
}

@Composable
private fun Tally(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Text(value, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Tokens.Ink)
        Text(label, fontFamily = Inter, fontSize = 13.sp, color = if (onClick != null) Tokens.Red else Tokens.Ink3)
    }
}

@Composable
private fun LiveCaption(heard: String) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().background(Tokens.Card, shape).border(1.dp, Tokens.Rule, shape).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(MunshiIcons.Mic, null, Modifier.size(16.dp), tint = Tokens.Red)
        Text("“$heard…”", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = Tokens.Ink)
    }
}

// ---------------------------------------------------------------------------------- the dock

@Composable
private fun ColumnScope.Dock(s: UiState, a: MainActions) {
    s.mind?.let { DockLine(it) }
    if (!s.live) DockLine("Jud raha hai…")
    when {
        s.mode == Mode.Listening -> {
            Waveform()
            Text("Sun raha hoon…", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Tokens.Ivory)
        }
        s.ask != null -> {
            DockLine("Munshi pooch raha hai")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MunshiButton("Haan", a.yes, Modifier.weight(1f), icon = MunshiIcons.Check, enabled = s.live)
                MunshiButton("Nahi", a.no, Modifier.width(130.dp), style = ButtonStyle.Secondary, enabled = s.live)
            }
            Tools(a)
        }
        s.mode == Mode.Working -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MunshiIcons.Spark, null, Modifier.size(18.dp), tint = Tokens.RedOnInk)
                Text("Soch raha hoon… phone par, muft", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Ivory)
            }
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Tokens.RedOnInk, trackColor = Tokens.Ivory.copy(alpha = 0.12f))
            Tools(a, dim = true)
        }
        !s.shopOpen -> {
            MunshiButton("Dukaan kholo", a.openShop, Modifier.fillMaxWidth(), icon = MunshiIcons.Store, enabled = s.live)
            Tools(a)
        }
        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MunshiButton("Shelf", a.camera, style = ButtonStyle.Tool, icon = MunshiIcons.Camera)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MicButton(false, a.mic)
                Text("Boliye", Modifier.alpha(0.7f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Ivory)
            }
            MunshiButton("Band karo", a.closeShop, style = ButtonStyle.Tool, icon = MunshiIcons.Close)
        }
    }
}

@Composable
private fun DockLine(text: String) =
    Text(text, Modifier.alpha(0.7f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Ivory)

@Composable
private fun Tools(a: MainActions, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth().alpha(if (dim) 0.45f else 1f), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        MunshiButton("Boliye", a.mic, style = ButtonStyle.Tool, icon = MunshiIcons.Mic, enabled = !dim)
        MunshiButton("Shelf photo", a.camera, style = ButtonStyle.Tool, icon = MunshiIcons.Camera, enabled = !dim)
    }
}

// The red waveform while Munshi listens; still in previews and screenshot tests.
@Composable
private fun Waveform() {
    val base = listOf(10, 18, 30, 22, 40, 28, 16, 34, 46, 30, 20, 38, 26, 14, 24, 36, 18, 28, 12, 8)
    val still = LocalInspectionMode.current
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "phase")
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        base.forEachIndexed { i, h ->
            val f = if (still) 1f else 0.55f + 0.45f * (if (i % 2 == 0) phase else 1f - phase)
            Box(Modifier.width(4.dp).height((h * f).dp).background(Tokens.RedOnInk, RoundedCornerShape(2.dp)))
        }
    }
}
