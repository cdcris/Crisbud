package android.com.crisbud.db

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun currentIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
