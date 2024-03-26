package dev.sajidali.guide

import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ColorStateListDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.content.res.getDrawableOrThrow
import dev.sajidali.guide.data.DataProvider
import org.joda.time.DateTimeZone
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat

fun Context.getThemedAttribute(id: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(id, tv, true)
    return tv.data
}

fun Long.formatToPattern(pattern: String): String {

    return DateTimeFormat.forPattern(pattern).print(this)
}

fun Long.formatToPattern(pattern: String, timezone: String): String {

    return DateTimeFormat.forPattern(pattern).withZone(DateTimeZone.forID(timezone)).print(this)
}

fun TypedArray.getColorOrDrawable(index: Int, default: Int = -1): Drawable? {
    return try {
        getDrawableOrThrow(index)
    } catch (e: Throwable) {
        ColorDrawable(getColor(index, default))

    }
}

fun Long.toDayName(): String {
    val date = LocalDate(this)
    return date.dayOfWeek().asShortText + " " + date.dayOfMonth + "/" + date.monthOfYear
}

val <T> Collection<T>?.itemCount
    get() = this?.size ?: 0

val DataProvider?.itemCount
    get() = this?.size() ?: 0

val now: Long
    get() = System.currentTimeMillis()