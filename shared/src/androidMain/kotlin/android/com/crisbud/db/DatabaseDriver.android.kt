package android.com.crisbud.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver

object DatabaseContext {
    lateinit var application: Context
}

actual fun createDatabaseDriver(): SqlDriver =
    AndroidSqliteDriver(CrisbudDatabase.Schema, DatabaseContext.application, "crisbud.db")
