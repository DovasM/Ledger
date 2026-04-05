package com.ledger.app.data.di

import android.content.Context
import com.ledger.app.data.LedgerBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLedgerBridge(@ApplicationContext context: Context): LedgerBridge {
        return LedgerBridge().also { it.open(context) }
    }
}
