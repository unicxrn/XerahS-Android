package com.xerahs.android.core.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create albums table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS albums (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Create tags table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tags (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL
            )
            """.trimIndent()
        )

        // Create junction table for history-tag relationship
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS history_tag_cross_ref (
                historyId TEXT NOT NULL,
                tagId TEXT NOT NULL,
                PRIMARY KEY (historyId, tagId),
                FOREIGN KEY (historyId) REFERENCES history(id) ON DELETE CASCADE,
                FOREIGN KEY (tagId) REFERENCES tags(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Recreate history table with foreign key to albums
        // SQLite doesn't support ALTER TABLE ADD FOREIGN KEY, so we must recreate
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS history_new (
                id TEXT NOT NULL PRIMARY KEY,
                filePath TEXT NOT NULL,
                thumbnailPath TEXT,
                url TEXT,
                deleteUrl TEXT,
                uploadDestination TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                fileName TEXT NOT NULL,
                fileSize INTEGER NOT NULL,
                albumId TEXT,
                FOREIGN KEY (albumId) REFERENCES albums(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO history_new (id, filePath, thumbnailPath, url, deleteUrl, uploadDestination, timestamp, fileName, fileSize)
            SELECT id, filePath, thumbnailPath, url, deleteUrl, uploadDestination, timestamp, fileName, fileSize FROM history
            """.trimIndent()
        )
        db.execSQL("DROP TABLE history")
        db.execSQL("ALTER TABLE history_new RENAME TO history")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add fileHash column to history
        db.execSQL("ALTER TABLE history ADD COLUMN fileHash TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_history_fileHash ON history(fileHash)")

        // Create upload_profiles table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS upload_profiles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                destination TEXT NOT NULL,
                isDefault INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Create custom_themes table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_themes (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                seedColor INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
