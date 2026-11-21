package android.com.crisbud.db

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT

actual fun currentIsoDate(): String = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }.stringFromDate(NSDate())

actual fun isoDateFromEpochMillis(millis: Long): String = NSDateFormatter().apply {
    dateFormat = "yyyy-MM-dd"
    timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis.toDouble() / 1000.0))

actual fun epochMillisFromIsoDate(date: String): Long? {
    val formatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
        timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
    }
    return formatter.dateFromString(date)?.timeIntervalSince1970?.let { (it * 1000).toLong() }
}
