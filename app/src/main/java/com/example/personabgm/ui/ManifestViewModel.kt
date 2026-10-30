package com.example.personabgm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personabgm.data.repository.ManifestRepository
import com.example.personabgm.data.repository.RefreshResult
import com.example.personabgm.domain.model.Manifest
import com.example.personabgm.domain.model.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Library screen shows when a manifest exists. */
data class LibraryContent(
    val manifest: Manifest,
    val isRefreshing: Boolean,
    /** True when the latest refresh could not reach the server, so the list may be out of date. */
    val offline: Boolean,
)

/**
 * Loading / Content / Error for the track manifest (spec 3.2).
 *
 *  - Loading: no cached manifest yet and a fetch is running.
 *  - Content: a cached manifest exists (even if the latest refresh failed; see `offline`).
 *  - Error: no cached manifest and the fetch failed.
 */
@HiltViewModel
class ManifestViewModel @Inject constructor(
    private val repository: ManifestRepository,
) : ViewModel() {

    private sealed interface RefreshState {
        data object Running : RefreshState
        data object Idle : RefreshState
        data class Failed(val message: String, val offline: Boolean) : RefreshState
    }

    private val refreshState = MutableStateFlow<RefreshState>(RefreshState.Running)
    private var refreshJob: Job? = null

    val uiState: StateFlow<UiState<LibraryContent>> =
        combine<Manifest?, RefreshState, UiState<LibraryContent>>(repository.manifest, refreshState) { manifest, state ->
            when {
                manifest != null -> UiState.Content(
                    LibraryContent(
                        manifest = manifest,
                        isRefreshing = state is RefreshState.Running,
                        offline = state is RefreshState.Failed && state.offline,
                    ),
                )
                state is RefreshState.Failed -> UiState.Error(state.message, retry = this::refresh)
                else -> UiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshState.value = RefreshState.Running
        refreshJob = viewModelScope.launch {
            refreshState.value = when (val result = repository.refresh()) {
                is RefreshResult.Updated, RefreshResult.NotModified -> RefreshState.Idle
                is RefreshResult.NetworkError -> RefreshState.Failed(
                    message = "Can't reach the music server. Check your connection and try again.",
                    offline = true,
                )
                is RefreshResult.HttpError -> RefreshState.Failed(
                    message = "The music server returned an error (${result.code}). Try again in a moment.",
                    offline = false,
                )
                is RefreshResult.Invalid -> RefreshState.Failed(
                    message = "The track list couldn't be read. It may be malformed.",
                    offline = false,
                )
            }
        }
    }
}
