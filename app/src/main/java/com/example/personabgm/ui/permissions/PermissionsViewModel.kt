package com.example.personabgm.ui.permissions

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personabgm.domain.permissions.HealthIssue
import com.example.personabgm.domain.permissions.PermissionHealth
import com.example.personabgm.domain.permissions.PermissionSnapshot
import com.example.personabgm.permissions.LocationServicesMonitor
import com.example.personabgm.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app-wide view of permissions: one snapshot, refreshed on resume, after a permission result, and
 * whenever Location Services are toggled. Drives onboarding, the Permission Health card, and the GPS banner.
 */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val checker: PermissionChecker,
    monitor: LocationServicesMonitor,
) : ViewModel() {

    private val _snapshot = MutableStateFlow(checker.snapshot())
    val snapshot: StateFlow<PermissionSnapshot> = _snapshot.asStateFlow()

    /** Non-critical issues the person chose to hide until the next launch. */
    private val dismissed = MutableStateFlow<Set<HealthIssue>>(emptySet())

    val issues: StateFlow<List<HealthIssue>> = combine(_snapshot, dismissed) { snapshot, hidden ->
        PermissionHealth.issues(snapshot, Build.VERSION.SDK_INT).filter { it !in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { monitor.changes.collect { refresh() } }
    }

    fun refresh() {
        _snapshot.value = checker.snapshot()
    }

    fun dismiss(issue: HealthIssue) {
        if (!PermissionHealth.isCritical(issue)) dismissed.value = dismissed.value + issue
    }
}
