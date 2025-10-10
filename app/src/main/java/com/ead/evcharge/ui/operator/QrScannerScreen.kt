package com.ead.evcharge.ui.operator

import android.Manifest
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.navigation.getBottomNavItemsForRole
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    tokenManager: TokenManager,
    navController: NavController, // added navController
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val viewModel: QrViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return QrViewModel(tokenManager) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var scannedValue by remember { mutableStateOf<String?>(null) }

    // Flag to hide the camera after scan
    var cameraVisible by remember { mutableStateOf(true) }

    // Navigation state setup (required for bottom bar)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val bottomNavItems = getBottomNavItemsForRole("operator") // show operator nav

    // Ask for camera permission on start
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Booking QR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {   // added bottom navigation bar
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {

            // Show camera only while scanning
            if (cameraVisible && cameraPermissionState.status.isGranted) {
                AndroidView(factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER // keeps camera centered
                    }

                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build()
                        val selector = CameraSelector.DEFAULT_BACK_CAMERA
                        val scanner = BarcodeScanning.getClient()

                        val analysis = ImageAnalysis.Builder().build()
                        analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.rawValue?.let {
                                            if (scannedValue == null) {
                                                scannedValue = it
                                                cameraVisible = false // hide camera immediately
                                                viewModel.loadBookingDetails(it)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else imageProxy.close()
                        }

                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            ctx as LifecycleOwner,
                            selector,
                            preview,
                            analysis
                        )
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                    modifier = Modifier
                    .width(300.dp) // camera view size
                    .height(300.dp) // square center box
                    .clip(MaterialTheme.shapes.medium)
                    .shadow(8.dp))
            }

            // Handle different UI states cleanly
            when (val state = uiState) {
                is QrUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is QrUiState.BookingDetails -> {
                    val booking = state.data
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Booking Details",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Divider(Modifier.padding(vertical = 8.dp))

                                Text(
                                    "Booking ID: ${booking.id}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Owner NIC: ${booking.ownerNic}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Station ID: ${booking.stationId}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Slot ID: ${booking.slotId}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Start Time: ${booking.startTimeUtc}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Status: ${booking.status}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (booking.status.lowercase() == "completed")
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // Determine button states
                                val status = booking.status.lowercase()
                                val isChargingEnabled = status == "pending" || status == "approved"
                                val isFinalizeEnabled = status == "charging"
                                val isDisabled = status == "completed" || status == "cancelled"

                                // Display message for terminal states
                                when (status) {
                                    "completed" -> Text(
                                        "This booking is already completed.",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )

                                    "cancelled" -> Text(
                                        "This booking is cancelled.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = { viewModel.startCharging(booking.id) },
                                        enabled = isChargingEnabled && !isDisabled,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            "Start Charging",
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }

                                    Button(
                                        onClick = { viewModel.finalizeBooking(booking.id) },
                                        enabled = isFinalizeEnabled && !isDisabled,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            "Finalize Booking",
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is QrUiState.SuccessMessage -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    viewModel.reset()
                    cameraVisible = true // re-enable camera for next scan
                }

                is QrUiState.Error -> {
                    Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_SHORT).show()
                    viewModel.reset()
                    cameraVisible = true // allow retry
                }

                else -> Unit
            }
        }
    }
}
