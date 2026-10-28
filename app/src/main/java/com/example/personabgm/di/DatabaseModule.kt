package com.example.personabgm.di

import android.content.Context
import androidx.room.Room
import com.example.personabgm.data.local.db.ManifestDao
import com.example.personabgm.data.local.db.PersonaDatabase
import com.example.personabgm.data.local.db.ZoneDao
import com.example.personabgm.data.local.db.ZoneStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // No fallbackToDestructiveMigration: zones are user data (spec 7.2).
    @Provides
    @Singleton
    fun providePersonaDatabase(@ApplicationContext context: Context): PersonaDatabase =
        Room.databaseBuilder(context, PersonaDatabase::class.java, "persona.db").build()

    @Provides fun provideZoneDao(db: PersonaDatabase): ZoneDao = db.zoneDao()
    @Provides fun provideZoneStateDao(db: PersonaDatabase): ZoneStateDao = db.zoneStateDao()
    @Provides fun provideManifestDao(db: PersonaDatabase): ManifestDao = db.manifestDao()
}
