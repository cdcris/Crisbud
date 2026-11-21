package android.com.crisbud.db

import app.cash.sqldelight.ColumnAdapter

enum class RecordKind(val code: Long) {
    EXPENSE(0),
    INCOME(1);

    companion object {
        fun fromIncome(income: Boolean): RecordKind = if (income) INCOME else EXPENSE

        internal val adapter = object : ColumnAdapter<RecordKind, Long> {
            override fun decode(databaseValue: Long) = fromIncome(databaseValue != 0L)
            override fun encode(value: RecordKind) = value.code
        }
    }
}

enum class RecurringCadence(val databaseValue: String) {
    MONTHLY("Monthly"),
    UNRECOGNIZED("");

    companion object {
        internal fun fromDatabase(value: String) =
            if (value.equals(MONTHLY.databaseValue, ignoreCase = true)) MONTHLY else UNRECOGNIZED
    }
}
