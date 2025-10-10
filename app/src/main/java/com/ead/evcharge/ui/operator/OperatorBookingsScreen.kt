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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.model.BookingDetailsResponse
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorBookingsScreen(
    tokenManager: TokenManager,
    onBack: () -> Unit
) {
    val viewModel: OperatorBookingsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return OperatorBookingsViewModel(tokenManager) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedBooking by remember { mutableStateOf<BookingDetailsResponse?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookings") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Filter Chips
            val statuses =
                listOf("All", "Pending", "Approved", "Charging", "Completed", "Cancelled")

            // Scrollable Filter Chips
            val scrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statuses.forEach { status ->
                    FilterChip(
                        selected = selectedFilter == status,
                        onClick = {
                            selectedFilter = status
                            viewModel.filterByStatus(status)
                        },
                        label = { Text(status) },
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Today’s Bookings Button
            Button(
                onClick = { viewModel.showTodaysBookings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Today's Bookings")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content
            when (val state = uiState) {
                is BookingsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is BookingsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is BookingsUiState.Success -> {
                    if (state.bookings.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No bookings found.")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.bookings) { booking ->
                                BookingCard(
                                    booking = booking,
                                    onClick = { selectedBooking = booking }
                                )
                            }
                        }
                    }
                }
            }

            // Booking details dialog
            selectedBooking?.let { booking ->
                AlertDialog(
                    onDismissRequest = { selectedBooking = null },
                    title = { Text("Booking Details") },
                    text = {
                        Column {
                            Text("Booking ID: ${booking.id}")
                            Text("Owner NIC: ${booking.ownerNic}")
                            Text("Station ID: ${booking.stationId}")
                            Text("Slot ID: ${booking.slotId}")
                            Text("Start Time: ${booking.startTimeUtc}")
                            Text("Status: ${booking.status}")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedBooking = null }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BookingCard(booking: BookingDetailsResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Booking ID: ${booking.id}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Owner NIC: ${booking.ownerNic}")
            Text("Status: ${booking.status}")
            Text("Start Time: ${booking.startTimeUtc}")
        }
    }
}