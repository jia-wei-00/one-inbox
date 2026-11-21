package one.inbox.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import one.inbox.data.db.AppDatabase
import one.inbox.data.db.InboxDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "one-inbox.db").build()

    @Provides
    fun inboxDao(db: AppDatabase): InboxDao = db.inboxDao()
}
