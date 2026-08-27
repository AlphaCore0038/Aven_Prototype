package com.aven.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN promptTokens INTEGER")
        db.execSQL("ALTER TABLE messages ADD COLUMN completionTokens INTEGER")
        db.execSQL("ALTER TABLE messages ADD COLUMN tokensPerSecond REAL")
        db.execSQL("ALTER TABLE messages ADD COLUMN timeToFirstTokenMs INTEGER")
    }
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AvenDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: AvenDatabase? = null

        fun getInstance(context: Context): AvenDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AvenDatabase::class.java,
                    "aven.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}