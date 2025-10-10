package com.ead.evcharge.ui.operator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.model.SlotResponse
import com.ead.evcharge.data.model.StationResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorStationsScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit = {}
) {
    val viewModel: OperatorStationsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return OperatorStationsViewModel(tokenManager) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var selectedStation by remember { mutableStateOf<StationResponse?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stations") },
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
        when (val state = uiState) {
            is StationsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is StationsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is StationsUiState.Success -> {
                if (state.stations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No stations available")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.stations) { station ->
                            StationCard(
                                station = station,
                                onClick = { selectedStation = station }
                            )
                        }
                    }
                }
            }
        }

        // Station details dialog
        selectedStation?.let { station ->
            AlertDialog(
                onDismissRequest = { selectedStation = null },
                title = { Text(station.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Station ID: ${station.id}")
                        Text("Type: ${station.type}")
                        Text("Active: ${if (station.active) "Yes" else "No"}")
                        Text("Location: (${station.lat}, ${station.lng})")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Slots:", fontWeight = FontWeight.Bold)
                        if (station.slots.isEmpty()) {
                            Text("No slots available.")
                        } else {
                            station.slots.forEach { slot ->
                                SlotItem(slot)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedStation = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun StationCard(station: StationResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Type: ${station.type}")
            Text("Active: ${if (station.active) "Yes" else "No"}")
            Text("Total Slots: ${station.slots.size}")
        }
    }
}

@Composable
fun SlotItem(slot: SlotResponse) {
    val statusColor = if (slot.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Text(
        text = "• ${slot.label} (${if (slot.available) "Available" else "Occupied"})",
        color = statusColor
    )
}
