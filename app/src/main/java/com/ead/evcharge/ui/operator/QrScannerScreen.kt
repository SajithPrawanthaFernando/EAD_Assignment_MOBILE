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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ead.evcharge.data.local.TokenManager
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    tokenManager: TokenManager,
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

    // Request permission as soon as screen loads
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
        }
    ) { padding ->
        if (cameraPermissionState.status.isGranted) {
            // Permission granted → show live camera preview
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
                                            if (scannedValue == null) { // Avoid duplicate triggers
                                                scannedValue = it
                                                viewModel.verifyQrToken(it)
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
                })
            }
        } else {
            // Permission not granted
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission is required to scan QR codes.")
            }
        }

        // Handle UI states
        when (val state = uiState) {
            is QrUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is QrUiState.Success -> {
                val bookingId = state.data.bookingId

                LaunchedEffect(bookingId) {
                    viewModel.loadBookingDetails(bookingId)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Booking verified: $bookingId",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator()
                    Button(
                        onClick = { viewModel.finalizeBooking(bookingId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Finalize Booking", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // Added BookingDetails UI (your requested block)
            is QrUiState.BookingDetails -> {
                val booking = state.data
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "Booking Details",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Booking ID: ${booking.id}")
                    Text("Owner NIC: ${booking.ownerNic}")
                    Text("Station ID: ${booking.stationId}")
                    Text("Slot ID: ${booking.slotId}")
                    Text("Start Time (UTC): ${booking.startTimeUtc}")
                    Text("Status: ${booking.status}")
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.finalizeBooking(booking.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Finalize Booking", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            is QrUiState.SuccessMessage -> {
                LaunchedEffect(state) {
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    viewModel.reset()
                }
            }

            is QrUiState.Error -> {
                LaunchedEffect(state) {
                    Toast.makeText(
                        context,
                        "Error: ${state.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.reset()
                }
            }
            else -> Unit
        }
    }
}
