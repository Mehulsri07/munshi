package com.opuscube.munshi.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opuscube.munshi.api.Profile
import com.opuscube.munshi.brain.BrainState
import com.opuscube.munshi.onboarding.OnboardingUi
import com.opuscube.munshi.onboarding.Step
import com.opuscube.munshi.ui.components.*
import com.opuscube.munshi.ui.theme.Fraunces
import com.opuscube.munshi.ui.theme.Inter
import com.opuscube.munshi.ui.theme.Tokens
import java.util.Locale

data class OnboardingEnv(val modelName: String, val brain: BrainState, val micGranted: Boolean, val notifyGranted: Boolean, val ramGb: Int = 8)

data class OnboardingActions(
    val next: () -> Unit, val back: () -> Unit, val scan: () -> Unit, val typeUrl: (String) -> Unit,
    val phone: (String) -> Unit, val lookup: () -> Unit, val profile: (Profile) -> Unit, val save: () -> Unit,
    val askMic: () -> Unit, val askNotify: () -> Unit, val voice: () -> Unit, val finish: () -> Unit,
)

// The Figma Onboarding frames: one idea per screen, the one action always in the dock.
@Composable
fun OnboardingContent(ui: OnboardingUi, env: OnboardingEnv, a: OnboardingActions) {
    BackHandler(enabled = ui.step != Step.Welcome && ui.step != Step.Connect) { a.back() }
    when (ui.step) {
        Step.Welcome -> Welcome(a)
        Step.Connect -> Connect(ui, a)
        Step.Number -> Number(ui, a)
        Step.Otp -> Otp(ui, a)
        Step.Shop -> Shop(ui, a)
        Step.Permissions -> Permissions(ui, env, a)
        Step.Brain -> Brain(ui, env, a)
        Step.Voice -> Voice(ui, a)
    }
}

// ------------------------------------------------------------------------------- shared frame

@Composable
private fun Frame(ui: OnboardingUi, dock: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Tokens.Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
        LedgerPage {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 24.dp, top = 24.dp, bottom = 200.dp), content = content)
            InkDock(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Tokens.SpaceDockInset.dp)) {
                ui.error?.let { Text(it, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Tokens.RedOnInk) }
                dock()
            }
        }
    }
}

@Composable
private fun ColumnScope.Heading(step: Int, kicker: String, title: String, body: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..6) Box(Modifier.size(if (i == step) 28.dp else 14.dp, 4.dp).background(if (i <= step) Tokens.Red else Tokens.Rule, RoundedCornerShape(2.dp)))
        Text("  $step / 6", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Ink3)
    }
    Spacer(Modifier.height(28.dp))
    Kicker(kicker)
    Spacer(Modifier.height(10.dp))
    Text(title, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, color = Tokens.Ink)
    body?.let { Spacer(Modifier.height(10.dp)); Text(it, fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, color = Tokens.Ink2) }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Note(text: String, c: Color, icon: ImageVector? = null) {
    Row(Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(Tokens.RadiusPill.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon?.let { Icon(it, null, Modifier.size(16.dp), tint = c) }
        Text(text, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c)
    }
}

@Composable
private fun Badge(icon: ImageVector, tint: Color, bg: Color) =
    Box(Modifier.size(44.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = tint) }

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    Column(modifier.fillMaxWidth().background(Tokens.Card, shape).border(1.dp, Tokens.Rule, shape).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun DockNote(text: String, onClick: (() -> Unit)? = null) =
    Text(text, (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).fillMaxWidth().alpha(0.6f),
        fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ivory, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

// ------------------------------------------------------------------------------- the steps

@Composable
private fun Welcome(a: OnboardingActions) = Frame(OnboardingUi(Step.Welcome), dock = {
    MunshiButton("Shuru karein", a.next, Modifier.fillMaxWidth())
    DockNote("Paytm for Business ke saath")
}) {
    Spacer(Modifier.height(72.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Munshi", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 58.sp, color = Tokens.Ink)
        Box(Modifier.size(12.dp).background(Tokens.Red, CircleShape))
    }
    Spacer(Modifier.height(12.dp))
    Text("Aapki dukaan ka munshi. Bas boliye, kaam ho jaayega.", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 22.sp, lineHeight = 30.sp, color = Tokens.Ink2)
    Spacer(Modifier.height(24.dp))
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(MunshiIcons.Speaker, null, Modifier.size(16.dp), tint = Tokens.Red); Kicker("Munshi bol raha hai")
        }
        Text("“Sharma ji ka ₹450 udhaar 12 din se baaki hai. Reminder bhej doon?”", fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 25.sp, color = Tokens.Ink)
    }
    Spacer(Modifier.height(24.dp))
    for ((icon, line) in listOf(MunshiIcons.Bell to "Paise aate hi bolkar batata hai", MunshiIcons.Rupee to "Udhaar yaad dilaakar wapas laata hai", MunshiIcons.Mic to "Hindi mein boliye, Munshi samjhega")) {
        Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(Tokens.RedSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = Tokens.Red) }
            Text(line, fontFamily = Inter, fontSize = 15.sp, color = Tokens.Ink)
        }
    }
}

@Composable
private fun Connect(ui: OnboardingUi, a: OnboardingActions) {
    var typing by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("http://") }
    Frame(ui, dock = {
        if (typing) MunshiButton("Jodiye", { a.typeUrl(url) }, Modifier.fillMaxWidth(), icon = MunshiIcons.Check)
        else MunshiButton("QR scan kijiye", a.scan, Modifier.fillMaxWidth(), icon = MunshiIcons.Camera)
    }) {
        Heading(1, "Setup", "Munshi se jodiye", "Munshi team ke laptop par /control kholiye aur wahan dikh raha QR scan kijiye.")
        val dash = Tokens.Ink.copy(alpha = 0.25f)
        Column(Modifier.fillMaxWidth().height(230.dp).background(Tokens.Card, RoundedCornerShape(18.dp)).drawBehind {
            drawRoundRect(dash, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 18f))))
        }, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(MunshiIcons.Qr, null, Modifier.size(64.dp), tint = Tokens.Ink)
            Text("Laptop ki screen par QR", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
        }
        Spacer(Modifier.height(16.dp))
        if (typing) FieldRow("Server ka pata", url, { url = it }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
        else Row(Modifier.clickable { typing = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ya server ka pata likhiye", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Tokens.Ink2)
            Icon(MunshiIcons.ChevronRight, null, Modifier.size(16.dp), tint = Tokens.Ink2)
        }
    }
}

@Composable
private fun Number(ui: OnboardingUi, a: OnboardingActions) = Frame(ui, dock = {
    MunshiButton(if (ui.busy) "Dhoondh raha hoon…" else "OTP bhejo", a.lookup, Modifier.fillMaxWidth(), enabled = !ui.busy)
}) {
    Heading(2, "Aapki pehchaan", "Paytm for Business number", "Isi number se Munshi aapki dukaan pehchaanega.")
    FieldRow("Mobile number", ui.phone, a.phone, big = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))
    Spacer(Modifier.height(12.dp))
    Note("Demo: koi bhi number chalega", Tokens.Warn)
}

@Composable
private fun Otp(ui: OnboardingUi, a: OnboardingActions) = Frame(ui, dock = {
    MunshiButton("Aage", a.next, Modifier.fillMaxWidth(), icon = MunshiIcons.Check, enabled = ui.otp.length == 6)
}) {
    Heading(2, "Aapki pehchaan", "OTP daaliye", "${ui.phone.ifBlank { "Aapke number" }} par 6 ankon ka OTP bheja gaya.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // fits a 360 dp phone
        for (i in 0 until 6) {
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.size(44.dp, 56.dp).background(Tokens.Card, shape).border(1.5.dp, Tokens.Ink.copy(alpha = 0.3f), shape), contentAlignment = Alignment.Center) {
                Text(ui.otp.getOrNull(i)?.toString().orEmpty(), fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Tokens.Ink)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (ui.otp.length == 6) Note("Demo: OTP apne aap bhara gaya", Tokens.Ok, MunshiIcons.Check)
    else Text("OTP aa raha hai…", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink3)
}

@Composable
private fun Shop(ui: OnboardingUi, a: OnboardingActions) = Frame(ui, dock = {
    MunshiButton("Haan, sahi hai", a.save, Modifier.fillMaxWidth(), icon = MunshiIcons.Check, enabled = !ui.busy && ui.profile != null)
}) {
    Heading(3, "Aapki dukaan", "Yeh aapki dukaan hai?")
    val p = ui.profile
    if (p == null) { Text("Jaankari aa rahi hai…", fontFamily = Inter, fontSize = 15.sp, color = Tokens.Ink3); return@Frame }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(MunshiIcons.Check, null, Modifier.size(16.dp), tint = Tokens.Ok)
        Text("Paytm se mili jaankari · badalne ke liye tap kijiye", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ok)
    }
    Spacer(Modifier.height(12.dp))
    Card {
        FieldRow("Dukaan ka naam", p.shopName, { a.profile(p.copy(shopName = it)) })
        FieldRow("Ilaaka", p.locality, { a.profile(p.copy(locality = it)) })
        FieldRow("Kaam", p.category, { a.profile(p.copy(category = it)) })
        FieldRow("Aapka naam", p.ownerName, { a.profile(p.copy(ownerName = it)) })
        FieldRow("Munshi aapko kya bulaye", p.addressAs, { a.profile(p.copy(addressAs = it)) }, accent = true)
    }
}

@Composable
private fun Permissions(ui: OnboardingUi, env: OnboardingEnv, a: OnboardingActions) = Frame(ui, dock = {
    MunshiButton("Aage", a.next, Modifier.fillMaxWidth())
}) {
    Heading(4, "Ijaazat", "Do cheezein chahiye", "Munshi sirf inhi ke liye poochta hai. Baad mein Settings se badal sakte hain.")
    Permission(MunshiIcons.Mic, "Mic", "Boliye dabaakar Munshi se baat karne ke liye", env.micGranted, a.askMic)
    Spacer(Modifier.height(12.dp))
    Permission(MunshiIcons.Bell, "Notifications", "Paise aane par aur link khatam hone par batane ke liye", env.notifyGranted, a.askNotify)
}

@Composable
private fun Permission(icon: ImageVector, title: String, why: String, granted: Boolean, ask: () -> Unit) {
    val shape = RoundedCornerShape(Tokens.RadiusCard.dp)
    Row(Modifier.fillMaxWidth().background(Tokens.Card, shape).border(1.dp, Tokens.Rule, shape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Badge(icon, Tokens.Red, Tokens.RedSoft)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Tokens.Ink)
            Text(why, fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Ink2)
        }
        if (granted) Note("Mil gayi", Tokens.Ok, MunshiIcons.Check)
        else Text("Dein", Modifier.background(Tokens.Ink, RoundedCornerShape(Tokens.RadiusPill.dp)).clickable(onClick = ask).padding(horizontal = 14.dp, vertical = 8.dp),
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Ivory)
    }
}

@Composable
private fun Brain(ui: OnboardingUi, env: OnboardingEnv, a: OnboardingActions) = Frame(ui, dock = {
    MunshiButton("Aage badhein", a.next, Modifier.fillMaxWidth())
    if (env.brain !is BrainState.Ready) DockNote("Download peeche chalta rahega")
}) {
    Heading(5, "Munshi ka dimaag", "AI aapke phone par", "Gemma AI isi phone par chalta hai. Har sawaal muft, aur aapka hisaab phone se bahar nahi jaata.")
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Badge(MunshiIcons.Spark, Tokens.Warn, Tokens.Warn.copy(alpha = 0.14f))
            Column {
                Text(env.modelName, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Tokens.Ink)
                Text(if (env.brain == BrainState.Unsupported) "Is phone par AI nahi chalega" else "Aapke ${env.ramGb} GB phone ke liye sabse achha", fontFamily = Inter, fontSize = 13.sp, color = Tokens.Ink2)
            }
        }
        val (done, total) = when (val b = env.brain) { is BrainState.Downloading -> b.done to b.total; BrainState.Ready, BrainState.Loading -> 1L to 1L; else -> 0L to 1L }
        Box(Modifier.fillMaxWidth().height(8.dp).background(Tokens.Rule, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth((done.toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().background(if (env.brain == BrainState.Ready) Tokens.Ok else Tokens.Red, RoundedCornerShape(4.dp)))
        }
        Row(Modifier.fillMaxWidth()) {
            val left = when (val b = env.brain) {
                is BrainState.Downloading -> String.format(Locale.US, "%.1f GB / %.1f GB", b.done / 1e9, b.total / 1e9)
                BrainState.Ready -> "Taiyaar"
                BrainState.Loading -> "Taiyaar ho raha hai…"
                BrainState.Missing -> "Wi-Fi milte hi download hoga"
                is BrainState.Failed -> "Nahi chala: ${b.why}"
                BrainState.Unsupported -> "Templates se kaam chalega"
            }
            Text(left, Modifier.weight(1f), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (env.brain == BrainState.Ready) Tokens.Ok else Tokens.Ink)
            if (env.brain != BrainState.Ready) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(MunshiIcons.Wifi, null, Modifier.size(14.dp), tint = Tokens.Ink3)
                Text("Wi-Fi par", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Tokens.Ink3)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (env.brain != BrainState.Ready) Text("Tab tak Munshi bina AI ke kaam karega: paise, udhaar aur reminder sab chalte rahenge.",
        Modifier.fillMaxWidth().background(Tokens.Ink.copy(alpha = 0.04f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Ink2)
}

@Composable
private fun Voice(ui: OnboardingUi, a: OnboardingActions) = Frame(ui, dock = {
    MunshiButton("Taiyaar! Dukaan kholiye", a.finish, Modifier.fillMaxWidth(), icon = MunshiIcons.Check)
    DockNote("Baad mein bhi kar sakte hain", a.finish)
}) {
    Heading(6, "Aakhri kadam", "Ek baar bolkar dekhiye")
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(MunshiIcons.Speaker, null, Modifier.size(16.dp), tint = Tokens.Red); Kicker("Munshi bolega")
        }
        Text("“Namaste ${ui.profile?.addressAs ?: "Ramesh ji"}, main Munshi hoon. Sun pa rahe hain? Haan boliye.”",
            fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 25.sp, color = Tokens.Ink)
    }
    Spacer(Modifier.height(28.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MicButton(listening = ui.busy, onClick = { if (!ui.busy) a.voice() })
        val heard = ui.heard
        when {
            ui.busy -> Text("Sun raha hoon…", fontFamily = Inter, fontSize = 15.sp, color = Tokens.Ink2)
            heard != null -> Note("Aapne kaha: “$heard”", Tokens.Ok, MunshiIcons.Check)
            ui.voiceTried -> {
                Text("“Haan” sunaai nahi diya", fontFamily = Inter, fontSize = 15.sp, color = Tokens.Ink2)
                Text("Dobara", Modifier.clickable(onClick = a.voice).padding(6.dp), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Tokens.Red)
            }
            else -> Text("Mic dabaiye: Munshi bolega, phir sunega", fontFamily = Inter, fontSize = 15.sp, color = Tokens.Ink2)
        }
    }
}
