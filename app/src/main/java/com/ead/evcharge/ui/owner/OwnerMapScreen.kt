// ui/owner/OwnerMapScreen.kt
package com.ead.evcharge.ui.owner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ead.evcharge.data.local.AppDatabase
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.local.entity.StationEntity
import com.ead.evcharge.data.model.BookingRequest
import com.ead.evcharge.data.remote.RetrofitInstance
import com.ead.evcharge.data.repository.StationRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerMapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Setup repository and token manager
    val database = remember { AppDatabase.getDatabase(context) }
    val stationRepository = remember {
        StationRepository(database.stationDao(), RetrofitInstance.api)
    }
    val tokenManager = remember { TokenManager(context) }
    val userNic by tokenManager.getUserNic().collectAsState(initial = null)

    // Get stations from database
    val stations by stationRepository.getActiveStations().collectAsState(initial = emptyList())

    // State variables
    var hasLocationPermission by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<StationEntity?>(null) }
    var showBookingDialog by remember { mutableStateOf(false) }
    var bookingStation by remember { mutableStateOf<StationEntity?>(null) }

    // NEW: Nearby mode states
    var isNearbyModeEnabled by remember { mutableStateOf(true) }
    var showLegend by remember { mutableStateOf(false) }
    var nearbyStationIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchRadius by remember { mutableStateOf(20.0) }
    var pulseRadius by remember { mutableStateOf(50.0) }

    // NEW: Calculate distances and categorize stations
    val stationsWithDistance = remember(stations, currentLocation, isNearbyModeEnabled, nearbyStationIds) {
        if (isNearbyModeEnabled && currentLocation != null) {
            stations.map { station ->
                val distance = stationRepository.calculateDistance(
                    currentLocation!!.latitude,
                    currentLocation!!.longitude,
                    station.lat,
                    station.lng
                )
                val isNearby = nearbyStationIds.contains(station.id)
                Triple(station, distance, isNearby)
            }.sortedBy { it.second }
        } else {
            stations.map { Triple(it, null, false) }
        }
    }

    // Request location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Sync stations on first load
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        stationRepository.syncStationsFromServer()
    }

    // UPDATED: Get current location function with nearby API
    fun getCurrentLocation(onLocationReceived: (LatLng) -> Unit) {
        try {
            if (!hasLocationPermission) {
                Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                return
            }

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        currentLocation = latLng
                        onLocationReceived(latLng)

                        // NEW: Fetch nearby stations if mode is enabled
                        if (isNearbyModeEnabled) {
                            scope.launch {
                                isSyncing = true
                                val result = stationRepository.getNearbyStationIds(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    radiusKm = searchRadius
                                )
                                isSyncing = false

                                if (result.isSuccess) {
                                    nearbyStationIds = result.getOrNull() ?: emptySet()
                                    Toast.makeText(
                                        context,
                                        "Found ${nearbyStationIds.size} stations within ${searchRadius.toInt()}km",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "Unable to get location", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera position
    val defaultLocation = LatLng(6.9271, 79.8612)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 13f)
    }

    // NEW: Animate the pulse effect
    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            while (true) {
                animate(
                    initialValue = 50f,
                    targetValue = 150f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                ) { value, _ ->
                    pulseRadius = value.toDouble()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isNearbyModeEnabled) "Nearby & All Stations" else "Find Charging Stations")
                        if (stations.isNotEmpty()) {
                            if (isNearbyModeEnabled && nearbyStationIds.isNotEmpty()) {
                                Text(
                                    "${nearbyStationIds.size} nearby • ${stations.size} total",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    "${stations.size} stations available",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // NEW: Legend button
                    if (isNearbyModeEnabled) {
                        IconButton(onClick = { showLegend = !showLegend }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Legend",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                val result = stationRepository.syncStationsFromServer()
                                isSyncing = false

                                if (result.isSuccess) {
                                    Toast.makeText(context, "Stations updated", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Sync failed: ${result.exceptionOrNull()?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Stations",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Google Map
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = false,  // Changed to false for custom marker
                    mapType = MapType.NORMAL
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = true
                ),
                onMapClick = { selectedStation = null }
            ) {
                // NEW: Calculate dynamic circle size based on zoom
                val currentZoom = cameraPositionState.position.zoom
                val baseRadius = 100.0 * 2.0.pow(15.0 - currentZoom)
                val animatedPulseRadius = baseRadius * (pulseRadius / 100.0)

                // NEW: Custom pulsing location circles (no marker)
                currentLocation?.let { location ->
                    Circle(
                        center = location,
                        radius = animatedPulseRadius,
                        fillColor = Color(0x20E91E63),
                        strokeColor = Color(0xFFE91E63),
                        strokeWidth = 3f,
                        zIndex = 999f
                    )

                    Circle(
                        center = location,
                        radius = baseRadius * 0.4,
                        fillColor = Color(0x40E91E63),
                        strokeColor = Color(0xFFF48FB1),
                        strokeWidth = 2f,
                        zIndex = 999f
                    )
                }

                // UPDATED: Add markers for each charging station with distance colors
                stationsWithDistance.forEach { (station, distance, isNearby) ->
                    val markerColor = if (isNearbyModeEnabled && isNearby) {
                        when {
                            distance == null -> BitmapDescriptorFactory.HUE_VIOLET
                            distance < 5.0 -> BitmapDescriptorFactory.HUE_GREEN
                            distance < 10.0 -> BitmapDescriptorFactory.HUE_AZURE
                            distance < 20.0 -> BitmapDescriptorFactory.HUE_ORANGE
                            else -> BitmapDescriptorFactory.HUE_RED
                        }
                    } else {
                        if (isNearbyModeEnabled) {
                            BitmapDescriptorFactory.HUE_VIOLET
                        } else {
                            BitmapDescriptorFactory.HUE_RED
                        }
                    }

                    Marker(
                        state = MarkerState(position = LatLng(station.lat, station.lng)),
                        title = station.name,
                        snippet = buildString {
                            if (distance != null && isNearbyModeEnabled) {
                                append("%.1f km away • ".format(distance))
                            }
                            append("${station.slots.count { it.available }}/${station.slots.size} available • ${station.type}")
                        },
                        icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                        alpha = if (isNearbyModeEnabled && !isNearby) 0.5f else 1.0f,
                        onClick = {
                            selectedStation = station
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(station.lat, station.lng),
                                        15f
                                    ),
                                    1000
                                )
                            }
                            true
                        }
                    )
                }
            }

            // Zoom Controls
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val currentZoom = cameraPositionState.position.zoom
                            cameraPositionState.animate(
                                CameraUpdateFactory.zoomTo(currentZoom + 1f),
                                500
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Icon(Icons.Default.Add, "Zoom In", tint = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.height(8.dp))

                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val currentZoom = cameraPositionState.position.zoom
                            if (currentZoom > 2f) {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.zoomTo(currentZoom - 1f),
                                    500
                                )
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Icon(Icons.Default.Close, "Zoom Out", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // UPDATED: Bottom controls with nearby toggle
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                // NEW: Toggle nearby mode button
                FloatingActionButton(
                    onClick = {
                        isNearbyModeEnabled = !isNearbyModeEnabled
                        if (isNearbyModeEnabled && hasLocationPermission && currentLocation != null) {
                            Toast.makeText(context, "Nearby mode enabled", Toast.LENGTH_SHORT).show()
                        } else {
                            nearbyStationIds = emptySet()
                            Toast.makeText(context, "Showing all stations", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = if (isNearbyModeEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isNearbyModeEnabled) Icons.Default.LocationOn else Icons.Default.Place,
                        contentDescription = "Toggle Nearby Mode",
                        tint = if (isNearbyModeEnabled)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // My Location Button
                FloatingActionButton(
                    onClick = {
                        if (hasLocationPermission) {
                            getCurrentLocation { location ->
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(location, 15f),
                                        1000
                                    )
                                }
                                Toast.makeText(context, "Moved to your location", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.MyLocation, "My Location", tint = MaterialTheme.colorScheme.onSecondary)
                }
            }

            // NEW: Legend card
            if (showLegend && isNearbyModeEnabled) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Distance Legend",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showLegend = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LegendItem(Color(0xFF4CAF50), "< 5 km")
                        LegendItem(Color(0xFF2196F3), "5-10 km")
                        LegendItem(Color(0xFFFFA726), "10-20 km")
                        LegendItem(Color(0xFFF44336), "> 20 km")
                        LegendItem(Color(0xFF9C27B0), "Other stations")
                    }
                }
            }

            // UPDATED: Station details card with distance
            selectedStation?.let { station ->
                val stationData = stationsWithDistance.find { it.first.id == station.id }
                val distance = stationData?.second
                val isNearby = stationData?.third ?: false

                RealStationDetailsCard(
                    station = station,
                    distance = distance,
                    isNearby = isNearby && isNearbyModeEnabled,
                    onDismiss = { selectedStation = null },
                    onNavigate = {
                        val uri = Uri.parse("google.navigation:q=${station.lat},${station.lng}")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Google Maps not installed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBook = {
                        bookingStation = station
                        showBookingDialog = true
                        selectedStation = null
                    }
                )
            }
        }
    }

    // Booking Dialog
    if (showBookingDialog && bookingStation != null && userNic != null) {
        BookingDialog(
            station = bookingStation!!,
            ownerNic = userNic!!,
            onDismiss = {
                showBookingDialog = false
                bookingStation = null
            },
            onConfirm = { slotId, startTimeUtc ->
                scope.launch {
                    try {
                        val response = RetrofitInstance.api.createBooking(
                            BookingRequest(
                                ownerNic = userNic!!,
                                stationId = bookingStation!!.id,
                                slotId = slotId,
                                startTimeUtc = startTimeUtc
                            )
                        )

                        if (response.isSuccessful) {
                            Toast.makeText(context, "Booking confirmed!", Toast.LENGTH_LONG).show()
                            showBookingDialog = false
                            bookingStation = null
                            stationRepository.syncStationsFromServer()
                        } else {
                            Toast.makeText(
                                context,
                                "Booking failed: ${response.message()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}

// NEW: Legend item composable
@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, shape = RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// UPDATED: Station details card with distance parameter
@Composable
fun RealStationDetailsCard(
    station: StationEntity,
    distance: Double? = null,
    isNearby: Boolean = false,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    onBook: () -> Unit
) {
    val availableSlots = station.slots.count { it.available }
    val totalSlots = station.slots.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Surface(
                                color = if (station.active) Color(0xFF4CAF50) else Color(0xFFE57373),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (station.active) "Active" else "Inactive",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }

                            // NEW: Show distance badge
                            if (distance != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = when {
                                        !isNearby -> Color(0xFF9C27B0)
                                        distance < 5.0 -> Color(0xFF4CAF50)
                                        distance < 10.0 -> Color(0xFF2196F3)
                                        distance < 20.0 -> Color(0xFFFFA726)
                                        else -> Color(0xFFF44336)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "%.1f km".format(distance),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoChip(
                        icon = Icons.Default.List,
                        text = "$availableSlots/$totalSlots available",
                        isAvailable = availableSlots > 0
                    )
                    InfoChip(
                        icon = Icons.Default.Phone,
                        text = station.type,
                        isAvailable = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Navigate")
                    }

                    Button(
                        onClick = onBook,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = station.active && availableSlots > 0
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Book Now")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isAvailable: Boolean
) {
    Surface(
        color = if (isAvailable)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isAvailable)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isAvailable)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
