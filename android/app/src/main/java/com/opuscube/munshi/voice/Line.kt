package com.opuscube.munshi.voice

// What Munshi says: the Roman Hinglish line (shown, and spoken by an en-IN voice) and,
// when the server knows it, the same line in Devanagari for a hi-IN voice.
data class Line(val text: String, val spoken: String? = null)
