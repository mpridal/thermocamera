package io.github.mpridal.thermocamera.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "thermocamera.db").build()

    @Provides fun profiles(db: AppDatabase) = db.profiles()
    @Provides fun projects(db: AppDatabase) = db.projects()
    @Provides fun snapshots(db: AppDatabase) = db.snapshots()
}
