package com.opuscube.munshi.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.opuscube.munshi.R
import java.text.NumberFormat
import java.util.Locale

val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic),
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold),
)

private val indian = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"))
fun rupees(n: Int): String = "₹" + indian.format(n)

private val DAYS = listOf("Somvaar", "Mangalvaar", "Budhvaar", "Guruvaar", "Shukravaar", "Shanivaar", "Ravivaar")
private val MONTHS = listOf("Janvari", "Farvari", "March", "April", "Mai", "June", "July", "Agast", "Sitambar", "Oktober", "Navambar", "Disambar")

// The brief's date line in Roman Hindi: "Somvaar, 26 Oktober".
fun romanDate(d: java.time.LocalDate): String = "${DAYS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
