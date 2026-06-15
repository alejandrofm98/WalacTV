package com.example.walactv.shared.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.java.KoinJavaComponent

actual fun createSqlDriver(): SqlDriver {
    val context: Context = KoinJavaComponent.get(Context::class.java)
    return AndroidSqliteDriver(
        schema = ContentDatabase.Schema,
        context = context,
        name = "walactv_content.db",
    )
}
