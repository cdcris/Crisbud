package android.com.crisbud.db

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun currentIsoDate(): String = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }.stringFromDate(NSDate())
