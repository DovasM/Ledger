package com.ledger.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.ledger.app.data.GemmaModelRepository
import com.ledger.app.data.GemmaRepository
import com.ledger.app.data.ModelStatus
import com.ledger.app.data.PreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LedgerAppEntryPoint {
    fun gemmaRepository(): GemmaRepository
    fun gemmaModelRepository(): GemmaModelRepository
    fun preferencesRepository(): PreferencesRepository
}

@HiltAndroidApp
class LedgerApp : Application() {

    // Application-lifetime scope for background work not tied to any screen.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        maybeAutoLoadModel()
    }

    // If the user opted into auto-load and the model file is present, warm it into memory now
    // (off the main thread) so the first receipt scan doesn't pay the ~load latency. Best-effort:
    // any failure is swallowed — the AI screen can still load the model manually.
    private fun maybeAutoLoadModel() {
        val ep = EntryPointAccessors.fromApplication(this, LedgerAppEntryPoint::class.java)
        appScope.launch {
            try {
                if (!ep.preferencesRepository().aiAutoLoad.first()) return@launch
                if (ep.gemmaModelRepository().getModelStatus() !is ModelStatus.Ready) return@launch
                val repo = ep.gemmaRepository()
                if (repo.isNativeLibraryAvailable && !repo.isReady()) repo.loadModel()
            } catch (_: Exception) {}
        }
    }

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
