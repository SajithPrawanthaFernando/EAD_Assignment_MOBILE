// ui/owner/OwnerBookingScreen.kt
package com.ead.evcharge.ui.owner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ead.evcharge.data.local.AppDatabase
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.local.entity.BookingEntity
import com.ead.evcharge.data.remote.RetrofitInstance
import com.ead.evcharge.data.repository.BookingRepository
import com.ead.evcharge.utils.QRCodeGenerator
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerBookingScreen(tokenManager: TokenManager) {
    val context = LocalContext.current

    // Get user NIC
    val userNic by tokenManager.getUserNic().collectAsState(initial = null)
    val safeNic = userNic ?: ""

    // Setup repository
    val database = remember { AppDatabase.getDatabase(context) }
    val bookingRepository = remember {
        BookingRepository(database.bookingDao(), RetrofitInstance.api)
    }

    // Collect all bookings from database (no filtering)
    val allBookings by bookingRepository.getBookings(safeNic).collectAsState(initial = emptyList())

    // Sync bookings on first load
    LaunchedEffect(userNic) {
        if (!userNic.isNullOrEmpty()) {
            bookingRepository.syncBookings(userNic!!)
        }
    }

    // Group bookings by status
    val bookingsByStatus = remember(allBookings) {
        allBookings.groupBy { it.status }
    }

    // Define status order and colors
    val statusOrder = listOf("Pending", "Approved", "Charging", "Completed", "Cancelled")
    val statusColors = mapOf(
        "Pending" to Color(0xFFFFA726),
        "Approved" to Color(0xFF66BB6A),
        "Charging" to Color(0xFF4CAF50),
        "Completed" to Color(0xFF64B5F6),
        "Cancelled" to Color(0xFFE57373)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Bookings")
                        if (allBookings.isNotEmpty()) {
                            Text(
                                "${allBookings.size} total bookings",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (allBookings.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Bookings Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Your booking history will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Display bookings grouped by status in defined order
                statusOrder.forEach { status ->
                    val bookingsInStatus = bookingsByStatus[status] ?: emptyList()

                    if (bookingsInStatus.isNotEmpty()) {
                        // Status header
                        item {
                            StatusHeader(
                                status = status,
                                count = bookingsInStatus.size,
                                color = statusColors[status] ?: Color.Gray
                            )
                        }

                        // Bookings for this status
                        items(bookingsInStatus) { booking ->
                            BookingListItem(
                                booking = booking,
                                statusColor = statusColors[status] ?: Color.Gray
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun StatusHeader(status: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = color.copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($count)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun BookingListItem(booking: BookingEntity, statusColor: Color) {
    var showQRCode by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Station name and status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.stationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${booking.id.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = booking.status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(12.dp))

            // Booking details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BookingDetailItem(
                    icon = Icons.Default.DateRange,
                    label = "Start Time",
                    value = formatBookingTime(booking.startTimeUtc)
                )

                BookingDetailItem(
                    icon = Icons.Default.List,
                    label = "Slot",
                    value = booking.slotLabel
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Show QR Code button
            OutlinedButton(
                onClick = { showQRCode = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Show QR Code", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    // QR Code Dialog
    if (showQRCode) {
        BookingQRCodeDialog(
            booking = booking,
            onDismiss = { showQRCode = false }
        )
    }
}

@Composable
fun BookingQRCodeDialog(
    booking: BookingEntity,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(booking.id) {
        QRCodeGenerator.generateQRCode(booking.id, 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Booking QR Code",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    booking.stationName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Booking QR Code",
                        modifier = Modifier
                            .size(280.dp)
                            .padding(16.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    )
                } ?: run {
                    CircularProgressIndicator()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Booking details
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        QRInfoRow(label = "Booking ID:", value = booking.id.take(16) + "...")
                        Spacer(modifier = Modifier.height(4.dp))
                        QRInfoRow(label = "Slot:", value = booking.slotLabel)
                        Spacer(modifier = Modifier.height(4.dp))
                        QRInfoRow(label = "Status:", value = booking.status)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Scan this code at the charging station",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun QRInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun BookingDetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

fun formatBookingTime(isoString: String): String {
    return try {
        val inputFormatWithMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormatWithMillis.timeZone = TimeZone.getTimeZone("UTC")

        val date = try {
            inputFormatWithMillis.parse(isoString)
        } catch (e: Exception) {
            val inputFormatNoMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormatNoMillis.timeZone = TimeZone.getTimeZone("UTC")
            inputFormatNoMillis.parse(isoString)
        }

        val outputFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        "N/A"
    }
}
