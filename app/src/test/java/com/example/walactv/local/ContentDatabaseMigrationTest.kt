package com.example.walactv.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentDatabaseMigrationTest {
    @Test
    fun migrationsFormContiguousUpgradePathToCurrentVersion() {
        val migrations = ContentDatabase.MIGRATIONS.sortedBy { it.startVersion }

        assertEquals(5, migrations.size)
        assertEquals(1, migrations.first().startVersion)
        assertEquals(6, migrations.last().endVersion)

        migrations.zipWithNext().forEach { (current, next) ->
            assertEquals(next.startVersion, current.endVersion)
        }
    }
}
