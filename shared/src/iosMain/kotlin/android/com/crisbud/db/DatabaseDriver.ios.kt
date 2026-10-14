package android.com.crisbud.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual fun createDatabaseDriver(): SqlDriver = NativeSqliteDriver(CrisbudDatabase.Schema, "crisbud.db")
