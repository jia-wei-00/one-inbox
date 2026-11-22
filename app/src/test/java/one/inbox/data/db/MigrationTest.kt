package one.inbox.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import one.inbox.data.InboxRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context = RuntimeEnvironment.getApplication() as Context

    /** A database exactly as v1.0/v1.1 of the app created it, holding one captured message. */
    private fun createV1(name: String) {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`key` TEXT NOT NULL, `packageName` TEXT NOT NULL, `title` TEXT NOT NULL, `isGroup` INTEGER NOT NULL, `lastMessageAt` INTEGER NOT NULL, `preview` TEXT NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dedupeKey` TEXT NOT NULL, `conversationKey` TEXT NOT NULL, `packageName` TEXT NOT NULL, `sender` TEXT NOT NULL, `isFromMe` INTEGER NOT NULL, `text` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `unread` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_dedupeKey` ON `messages` (`dedupeKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationKey` ON `messages` (`conversationKey`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '33177528fd9b3aa464fcab9efce1beb8')")
            db.execSQL("INSERT INTO conversations VALUES ('wa:alice', 'com.whatsapp', 'Alice', 0, 1000, 'hi')")
            db.execSQL("INSERT INTO messages (dedupeKey, conversationKey, packageName, sender, isFromMe, text, timestamp, unread) VALUES ('a', 'wa:alice', 'com.whatsapp', 'Alice', 0, 'hi', 1000, 1)")
            db.version = 1
        }
    }

    @Test
    fun v1DatabaseMigratesKeepingMessagesAndSupportsCleanup() = runTest {
        createV1("migrate.db")
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migrate.db")
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = InboxRepository(db.inboxDao())
            assertEquals(listOf("hi"), repo.messages("wa:alice").first().map { it.text })
            repo.deleteConversation("wa:alice")
            assertTrue(repo.conversations(null).first().isEmpty())
            assertEquals(setOf("a"), repo.knownDedupeKeys(listOf("a")))
        } finally {
            db.close()
        }
    }
}
