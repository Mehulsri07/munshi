package com.opuscube.munshi.conductor

sealed interface Answer {
    data object Yes : Answer
    data object No : Answer
    data class Text(val text: String) : Answer
    data object Skip : Answer
}
