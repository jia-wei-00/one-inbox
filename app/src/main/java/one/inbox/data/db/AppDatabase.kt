package one.inbox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, DeletedMessageEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao
}

/** v2 adds the deleted-message tombstones used by inbox cleanup. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_messages` " +
                "(`dedupeKey` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))",
        )
    }
}
