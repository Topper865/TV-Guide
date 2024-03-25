package dev.sajidali.guide.data

data class Event(
    val id: Int,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long
)
