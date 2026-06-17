package com.ledger.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.ledger.app.data.GemmaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LedgerAppEntryPoint {
    fun gemmaRepository(): GemmaRepository
}

@HiltAndroidApp
class LedgerApp : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            try {
                EntryPointAccessors
                    .fromApplication<LedgerAppEntryPoint>(this, LedgerAppEntryPoint::class.java)
                    .gemmaRepository()
                    .unloadModel()
            } catch (_: Exception) {}
        }
    }
}
