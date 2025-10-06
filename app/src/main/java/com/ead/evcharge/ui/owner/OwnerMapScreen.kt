// ui/owner/OwnerMapScreen.kt
package com.ead.evcharge.ui.owner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Dummy data for charging stations
data class ChargingStation(
    val id: String,
    val name: String,
    val location: LatLng,
    val address: String,
    val availableSlots: Int,
    val totalSlots: Int,
    val chargingSpeed: String,
    val pricePerHour: Double,
    val rating: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerMapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Location permission state
    var hasLocationPermission by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }

    // Request location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            Toast.makeText(context, "Location permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Request permission on first launch
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Get current location
    suspend fun getCurrentLocation(): LatLng? {
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val location = fusedLocationClient.lastLocation.await()
            location?.let {
                LatLng(it.latitude, it.longitude)
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
            null
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // Dummy charging stations
    val chargingStations = remember {
        listOf(
            ChargingStation(
                id = "1",
                name = "Central Charging Hub",
                location = LatLng(6.9271, 79.8612),
                address = "123 Main Street, Colombo",
                availableSlots = 3,
                totalSlots = 5,
                chargingSpeed = "Fast Charge (50kW)",
                pricePerHour = 500.0,
                rating = 4.5f
            ),
            ChargingStation(
                id = "2",
                name = "Mall Parking Station",
                location = LatLng(6.9319, 79.8478),
                address = "456 Shopping Center, Colombo 03",
                availableSlots = 0,
                totalSlots = 4,
                chargingSpeed = "Standard (22kW)",
                pricePerHour = 300.0,
                rating = 4.2f
            ),
            ChargingStation(
                id = "3",
                name = "Airport Fast Charge",
                location = LatLng(6.9167, 79.8667),
                address = "789 Airport Road",
                availableSlots = 2,
                totalSlots = 6,
                chargingSpeed = "Ultra Fast (150kW)",
                pricePerHour = 800.0,
                rating = 4.8f
            ),
            ChargingStation(
                id = "4",
                name = "Beach Side Charging",
                location = LatLng(6.9200, 79.8500),
                address = "Marine Drive",
                availableSlots = 1,
                totalSlots = 3,
                chargingSpeed = "Fast Charge (50kW)",
                pricePerHour = 450.0,
                rating = 4.3f
            )
        )
    }

    // Camera position state
    val defaultLocation = LatLng(6.9271, 79.8612)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 13f)
    }

    var selectedStation by remember { mutableStateOf<ChargingStation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Charging Stations") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
                onMapClick = {
                    // Dismiss station details when clicking on map
                    selectedStation = null
                }
            ) {
                // Add markers for each charging station
                chargingStations.forEach { station ->
                    Marker(
                        state = MarkerState(position = station.location),
                        title = station.name,
                        snippet = "${station.availableSlots}/${station.totalSlots} slots available",
                        onClick = {
                            selectedStation = station
                            // Animate camera to the selected station
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(station.location, 15f),
                                    1000
                                )
                            }
                            true
                        }
                    )
                }

                // Show current location marker if available
                currentLocation?.let { location ->
                    Marker(
                        state = MarkerState(position = location),
                        title = "You are here",
                        snippet = "Current Location"
                    )
                }
            }

            // Zoom Controls (Top Right)
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Zoom Out",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // My Location Button (Bottom Right)
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        scope.launch {
                            val location = getCurrentLocation()
                            if (location != null) {
                                currentLocation = location
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(location, 15f),
                                    1000
                                )
                                Toast.makeText(
                                    context,
                                    "Moved to your location",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
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
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "My Location",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Station details card when a marker is selected
            selectedStation?.let { station ->
                StationDetailsCard(
                    station = station,
                    onDismiss = { selectedStation = null },
                    onNavigate = {
                        val uri = Uri.parse(
                            "google.navigation:q=${station.location.latitude},${station.location.longitude}"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Google Maps not installed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onBook = {
                        Toast.makeText(
                            context,
                            "Booking ${station.name}...",
                            Toast.LENGTH_SHORT
                        ).show()
                        // TODO: Navigate to booking screen
                    }
                )
            }
        }
    }
}

@Composable
fun StationDetailsCard(
    station: ChargingStation,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    onBook: () -> Unit
) {
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
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header with close button
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
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFFFC107)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${station.rating}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Address
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = station.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Station info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoChip(
                        icon = Icons.Default.List,
                        text = "${station.availableSlots}/${station.totalSlots} Available",
                        isAvailable = station.availableSlots > 0
                    )
                    InfoChip(
                        icon = Icons.Default.Phone,
                        text = station.chargingSpeed,
                        isAvailable = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Price
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Rs. ${station.pricePerHour}/hour",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
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
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Navigate")
                    }

                    Button(
                        onClick = onBook,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = station.availableSlots > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
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
