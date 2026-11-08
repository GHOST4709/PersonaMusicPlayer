package com.example.personabgm.ui.zones

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.personabgm.domain.model.LatLng
import com.example.personabgm.ui.components.ShimmerBox
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.LatLng as MapLatLng

/**
 * Zones on a map (spec 3.1, 3.2): a circle per zone in the theme's colours, press-and-hold to drop a new
 * zone, tap a circle or pin to select it. The theme-coloured skeleton shows until the map has rendered.
 */
@Composable
fun ZoneMap(
    rows: List<ZoneRow>,
    selectedId: String?,
    deviceLocation: LatLng?,
    myLocationEnabled: Boolean,
    onSelect: (String) -> Unit,
    onDropPin: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val camera = rememberCameraPositionState()
    val mapStyle = remember { MapStyleOptions(DARK_MAP_STYLE) }
    var loaded by remember { mutableStateOf(false) }
    var framed by remember { mutableStateOf(false) }

    val selected = rows.firstOrNull { it.zone.id == selectedId }?.zone
    val firstTarget: MapLatLng? = (selected ?: rows.firstOrNull()?.zone)
        ?.let { MapLatLng(it.lat, it.lng) }
        ?: deviceLocation?.let { MapLatLng(it.lat, it.lng) }

    // Frame something useful once, after the map exists: the selected or first zone, else where you are.
    LaunchedEffect(firstTarget, loaded) {
        if (loaded && !framed && firstTarget != null) {
            camera.move(CameraUpdateFactory.newLatLngZoom(firstTarget, ZONE_ZOOM))
            framed = true
        }
    }
    // Selecting a zone (from the map or the list) brings it into view.
    LaunchedEffect(selected?.id, loaded) {
        if (loaded && selected != null) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(MapLatLng(selected.lat, selected.lng), ZONE_ZOOM))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT)
            .border(1.dp, colors.outline, RoundedCornerShape(2.dp))
            // The map itself is not screen-reader friendly; the same zones are listed below it.
            .semantics { contentDescription = "Map of your zones. The same zones are listed below the map." },
    ) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = myLocationEnabled, mapStyleOptions = mapStyle),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = myLocationEnabled,
            ),
            onMapLoaded = { loaded = true },
            onMapLongClick = { onDropPin(it.latitude, it.longitude) },
        ) {
            rows.forEach { row ->
                ZoneOverlay(row = row, selected = row.zone.id == selectedId, onSelect = onSelect)
            }
        }
        if (!loaded) ShimmerBox(Modifier.matchParentSize(), shape = RectangleShape)
    }
}

@GoogleMapComposable
@Composable
private fun ZoneOverlay(row: ZoneRow, selected: Boolean, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val zone = row.zone
    val position = remember(zone.id, zone.lat, zone.lng) { MapLatLng(zone.lat, zone.lng) }
    val markerState = remember(zone.id, zone.lat, zone.lng) { MarkerState(position = position) }

    // Disabled zones are drawn grey; the zone you are standing in is drawn in the accent colour.
    val ring = when {
        !zone.enabled -> colors.outline
        selected || row.inside -> colors.secondary
        else -> colors.primary
    }
    Circle(
        center = position,
        radius = zone.radiusM.toDouble(),
        fillColor = ring.copy(alpha = if (selected) 0.30f else 0.16f),
        strokeColor = ring,
        strokeWidth = if (selected) 6f else 3f,
        clickable = true,
        onClick = { onSelect(zone.id) },
    )
    Marker(
        state = markerState,
        title = zone.name,
        onClick = {
            onSelect(zone.id)
            false // keep the default behaviour of showing the title
        },
    )
}

private val MAP_HEIGHT = 240.dp
private const val ZONE_ZOOM = 16f

/** A muted dark map so the theme-coloured zone circles stand out. Works with all three themes. */
private const val DARK_MAP_STYLE = """
[
  {"elementType":"geometry","stylers":[{"color":"#1d2330"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8a93a6"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#1d2330"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2e3648"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1420"}]},
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]}
]
"""
