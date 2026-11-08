package com.example.personabgm.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** Marks text as a heading so screen-reader users can jump between sections. */
fun Modifier.headingSemantics(): Modifier = semantics(mergeDescendants = true) { heading() }

/** Announces changes to this text (for example "Crossfading..." or an error) without interrupting speech. */
fun Modifier.politeLiveRegion(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
