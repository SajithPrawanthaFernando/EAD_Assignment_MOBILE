package com.ead.evcharge.ui.operator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ead.evcharge.data.local.TokenManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorBookingsScreen(
    tokenManager: TokenManager,
    viewModel: QrViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return QrViewModel(tokenManager) as T
            }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadTodayBookings() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's Bookings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is QrUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { CircularProgressIndicator() }

            is QrUiState.TodayBookings -> {
                val bookings = state.data
                if (bookings.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { Text("No bookings for today") }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        items(bookings) { booking ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Booking ID: ${booking.id}", style = MaterialTheme.typography.titleMedium)
                                    Text("Owner NIC: ${booking.ownerNic}")
                                    Text("Station ID: ${booking.stationId}")
                                    Text("Slot ID: ${booking.slotId}")
                                    Text("Start Time: ${booking.startTimeUtc}")
                                    Text("Status: ${booking.status}")
                                }
                            }
                        }
                    }
                }
            }

            is QrUiState.Error -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { Text("Error: ${state.message}") }

            else -> Unit
        }
    }
}
