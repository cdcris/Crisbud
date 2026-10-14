package android.com.crisbud.db

expect fun currentIsoDate(): String

expect fun isoDateFromEpochMillis(millis: Long): String

expect fun epochMillisFromIsoDate(date: String): Long?
