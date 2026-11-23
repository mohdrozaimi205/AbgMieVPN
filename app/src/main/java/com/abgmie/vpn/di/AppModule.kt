package com.abgmie.vpn.di

import android.content.Context
import androidx.room.Room
import com.abgmie.vpn.data.db.AppDatabase
import com.abgmie.vpn.data.db.ServerDao
import com.abgmie.vpn.data.db.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "abgmie.db").build()

    @Provides fun serverDao(db: AppDatabase): ServerDao = db.servers()
    @Provides fun sessionDao(db: AppDatabase): SessionDao = db.sessions()

    @Provides @Singleton
    fun http(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
