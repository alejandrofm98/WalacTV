package com.example.walactv.shared.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual fun createSqlDriver(): SqlDriver {
    val driver = JdbcSqliteDriver("jdbc:sqlite:walactv_content.db")
    try {
        ContentDatabase.Schema.create(driver)
    } catch (e: Exception) {
        if (e.message?.contains("already exists") != true) {
            throw e
        }
    }
    return driver
}
