package dev.sajidali.guide.data

import dev.sajidali.guide.now

data class Event(
    val id: Int,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long
) {

    val isCurrent: Boolean
        get() = now in start..end

}
