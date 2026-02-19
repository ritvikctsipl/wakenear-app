package com.comprint.wakenear

import android.Manifest
import android.content.Intent
import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.comprint.wakenear.service.LocationService
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: LocationViewModel = viewModel()) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    // Map view reference
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    // Request permissions on first launch
    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    // Listen for location updates from service
    DisposableEffect(Unit) {
        LocationService.onLocationUpdate = { location ->
            viewModel.updateCurrentLocation(GeoPoint(location.latitude, location.longitude))
        }
        onDispose {
            LocationService.onLocationUpdate = null
        }
    }

    // Update map overlays when destination/radius changes
    LaunchedEffect(uiState.destination, uiState.radiusMeters) {
        val map = mapView ?: return@LaunchedEffect
        val dest = uiState.destination ?: return@LaunchedEffect
        updateMapOverlays(map, dest, uiState.radiusMeters)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(5.0)
                    controller.setCenter(GeoPoint(20.5937, 78.9629)) // India

                    // Tap listener
                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            viewModel.setDestination(p)
                            updateMapOverlays(this@apply, p, uiState.radiusMeters)

                            // Reverse geocode
                            try {
                                val geocoder = Geocoder(ctx, Locale.getDefault())
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(p.latitude, p.longitude, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val addr = addresses[0]
                                    val name = addr.getAddressLine(0) ?: "${p.latitude}, ${p.longitude}"
                                    viewModel.setDestination(p, name)
                                }
                            } catch (_: Exception) {
                                viewModel.setDestination(p, "${String.format("%.4f", p.latitude)}, ${String.format("%.4f", p.longitude)}")
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    })
                    overlays.add(0, eventsOverlay)
                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top search bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search location...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    if (searchQuery.isNotBlank()) {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            @Suppress("DEPRECATION")
                            val results = geocoder.getFromLocationName(searchQuery, 1)
                            if (!results.isNullOrEmpty()) {
                                val addr = results[0]
                                val point = GeoPoint(addr.latitude, addr.longitude)
                                val name = addr.getAddressLine(0) ?: searchQuery
                                viewModel.setDestination(point, name)
                                mapView?.controller?.animateTo(point)
                                mapView?.controller?.setZoom(15.0)
                                mapView?.let { updateMapOverlays(it, point, uiState.radiusMeters) }
                            } else {
                                Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            )

            // Favorites chips
            if (uiState.favorites.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.favorites) { fav ->
                        AssistChip(
                            onClick = {
                                val point = GeoPoint(fav.latitude, fav.longitude)
                                viewModel.setDestination(point, fav.name)
                                mapView?.controller?.animateTo(point)
                                mapView?.controller?.setZoom(15.0)
                                mapView?.let { updateMapOverlays(it, point, uiState.radiusMeters) }
                            },
                            label = { Text(fav.name, maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.deleteFavorite(fav) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                                }
                            }
                        )
                    }
                }
            }
        }

        // Bottom panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Destination info card
            if (uiState.destination != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.destinationName.isNotEmpty()) uiState.destinationName
                                    else "Destination set",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 2
                                )
                                if (uiState.isMonitoring && uiState.distanceRemaining != null) {
                                    Text(
                                        text = "${uiState.distanceRemaining!!.toInt()}m away",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { showSaveDialog = true }) {
                                Icon(Icons.Default.FavoriteBorder, contentDescription = "Save")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Radius slider
                        Text(
                            text = "Wake-up radius: ${uiState.radiusMeters.toInt()}m",
                            fontSize = 12.sp
                        )
                        Slider(
                            value = uiState.radiusMeters,
                            onValueChange = {
                                viewModel.setRadius(it)
                                uiState.destination?.let { dest ->
                                    mapView?.let { map -> updateMapOverlays(map, dest, it) }
                                }
                            },
                            valueRange = 200f..2000f,
                            steps = 17
                        )

                        // Start/Stop button
                        Button(
                            onClick = {
                                if (uiState.isMonitoring) {
                                    // Stop
                                    val intent = Intent(context, LocationService::class.java).apply {
                                        action = LocationService.ACTION_STOP
                                    }
                                    context.startService(intent)
                                    viewModel.setMonitoring(false)
                                } else {
                                    // Start
                                    val dest = uiState.destination!!
                                    val intent = Intent(context, LocationService::class.java).apply {
                                        action = LocationService.ACTION_START
                                        putExtra(LocationService.EXTRA_DEST_LAT, dest.latitude)
                                        putExtra(LocationService.EXTRA_DEST_LNG, dest.longitude)
                                        putExtra(LocationService.EXTRA_RADIUS, uiState.radiusMeters)
                                    }
                                    context.startForegroundService(intent)
                                    viewModel.setMonitoring(true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (uiState.isMonitoring) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            } else {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            }
                        ) {
                            Icon(
                                if (uiState.isMonitoring) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (uiState.isMonitoring) "STOP MONITORING" else "START MONITORING",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Instruction card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = "Tap the map or search to set your destination",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Save favorite dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Favorite") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("Location name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (saveName.isNotBlank()) {
                        viewModel.saveFavorite(saveName)
                        saveName = ""
                        showSaveDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun updateMapOverlays(map: MapView, destination: GeoPoint, radiusMeters: Float) {
    // Remove old markers and circles (keep MapEventsOverlay at index 0)
    val eventsOverlay = map.overlays.firstOrNull { it is MapEventsOverlay }
    map.overlays.clear()
    if (eventsOverlay != null) {
        map.overlays.add(eventsOverlay)
    }

    // Add radius circle
    val circle = Polygon(map).apply {
        points = Polygon.pointsAsCircle(destination, radiusMeters.toDouble())
        fillPaint.color = 0x304CAF50.toInt() // semi-transparent green
        outlinePaint.color = 0xFF4CAF50.toInt()
        outlinePaint.strokeWidth = 3f
    }
    map.overlays.add(circle)

    // Add marker
    val marker = Marker(map).apply {
        position = destination
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = "Destination"
    }
    map.overlays.add(marker)

    map.invalidate()
}
