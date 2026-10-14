package android.com.crisbud.db

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

actual fun currentIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

actual fun isoDateFromEpochMillis(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(millis))

actual fun epochMillisFromIsoDate(date: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }.parse(date)?.time
}.getOrNull()
