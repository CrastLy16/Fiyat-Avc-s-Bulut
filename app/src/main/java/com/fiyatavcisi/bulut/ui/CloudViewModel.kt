package com.fiyatavcisi.bulut.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fiyatavcisi.bulut.data.CloudRepository
import com.fiyatavcisi.bulut.model.CloudSettings
import com.fiyatavcisi.bulut.model.CloudSnapshot
import com.fiyatavcisi.bulut.worker.CloudSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudUiState(
    val settings: CloudSettings = CloudSettings(),
    val snapshot: CloudSnapshot = CloudSnapshot(),
    val isSyncing: Boolean = false,
    val statusText: String = ""
)

class CloudViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CloudRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        CloudUiState(
            settings = repo.loadSettings(),
            snapshot = repo.loadCachedSnapshot()
        )
    )
    val uiState: StateFlow<CloudUiState> = _uiState.asStateFlow()

    init {
        CloudSyncWorker.schedule(application.applicationContext, _uiState.value.settings)
        if (_uiState.value.settings.snapshotRawUrl.isNotBlank()) {
            syncNow()
        }
    }

    fun updateSettings(transform: (CloudSettings) -> CloudSettings) {
        val next = transform(_uiState.value.settings)
        repo.saveSettings(next)
        CloudSyncWorker.schedule(getApplication(), next)
        _uiState.update { it.copy(settings = next) }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusText = "Bulut verisi güncelleniyor...") }
            val result = repo.fetchLatestSnapshot(_uiState.value.settings)
            result.onSuccess { snap ->
                _uiState.update {
                    it.copy(
                        snapshot = snap,
                        isSyncing = false,
                        statusText = "Bulut verisi güncel (${snap.products.size} ürün, ${snap.bigDeals.size} fırsat)"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        statusText = err.message ?: "Bulut bağlantısı başarısız"
                    )
                }
            }
        }
    }
}
