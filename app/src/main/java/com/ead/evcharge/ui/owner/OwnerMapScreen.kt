// ui/owner/OwnerMapScreen.kt
package com.ead.evcharge.ui.owner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

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

    // Sync stations on first load
    LaunchedEffect(Unit) {
        stationRepository.syncStationsFromServer()
    }

    // State variables
    var hasLocationPermission by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<StationEntity?>(null) }
    var showBookingDialog by remember { mutableStateOf(false) }
    var bookingStation by remember { mutableStateOf<StationEntity?>(null) }

    // Request location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Get current location function
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
                        onLocationReceived(latLng)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Find Charging Stations")
                        if (stations.isNotEmpty()) {
                            Text(
                                "${stations.size} stations available",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
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
                    isMyLocationEnabled = hasLocationPermission,
                    mapType = MapType.NORMAL
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = true
                ),
                onMapClick = { selectedStation = null }
            ) {
                // Add markers for each charging station
                stations.forEach { station ->
                    Marker(
                        state = MarkerState(position = LatLng(station.lat, station.lng)),
                        title = station.name,
                        snippet = "${station.slots.count { it.available }}/${station.slots.size} available • ${station.type}",
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

                // Current location marker
                currentLocation?.let { location ->
                    Marker(
                        state = MarkerState(position = location),
                        title = "You are here"
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

            // My Location Button
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        getCurrentLocation { location ->
                            currentLocation = location
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.LocationOn, "My Location", tint = MaterialTheme.colorScheme.onPrimary)
            }

            // Station details card
            selectedStation?.let { station ->
                RealStationDetailsCard(
                    station = station,
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
                            // Refresh stations to update availability
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

@Composable
fun RealStationDetailsCard(
    station: StationEntity,
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
                // Header
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
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Station info
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

                // Action buttons
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
