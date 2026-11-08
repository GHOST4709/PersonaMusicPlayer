package com.example.personabgm.context

import com.example.personabgm.domain.model.TimeBucket
import com.example.personabgm.domain.model.WeatherType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lets the debug panel pretend it is raining or night so every weather and time variant can be heard without
 * waiting for the real thing. Only the debug panel (not reachable in release builds) ever sets these, so in
 * release they stay null and the real values always win.
 */
@Singleton
class DebugOverrides @Inject constructor() {
    val weather = MutableStateFlow<WeatherType?>(null)
    val time = MutableStateFlow<TimeBucket?>(null)
}
