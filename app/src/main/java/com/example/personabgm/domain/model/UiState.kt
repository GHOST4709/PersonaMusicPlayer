package com.example.personabgm.domain.model

/** Loading / Content / Error pattern for all network and database actions (spec 3.2). */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Error(val message: String, val retry: (() -> Unit)? = null) : UiState<Nothing>
}
