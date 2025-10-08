// ui/owner/OwnerProfileScreen.kt
package com.ead.evcharge.ui.owner

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.ead.evcharge.data.remote.RetrofitInstance
import com.ead.evcharge.data.repository.UserRepository
import com.ead.evcharge.data.repository.BookingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerProfileScreen(
    tokenManager: TokenManager,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // User data states
    var userId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userNic by remember { mutableStateOf("") }
    var userPhone by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf("") }

    // Edit mode states
    var isEditMode by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Deactivate dialog state
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var isDeactivating by remember { mutableStateOf(false) }

    // Booking statistics
    var totalBookings by remember { mutableStateOf(0) }
    var activeBookings by remember { mutableStateOf(0) }
    var completedBookings by remember { mutableStateOf(0) }
    var cancelledBookings by remember { mutableStateOf(0) }
    var mostUsedStation by remember { mutableStateOf("N/A") }
    var lastBookingDate by remember { mutableStateOf("N/A") }

    // Setup repositories
    val database = remember { AppDatabase.getDatabase(context) }
    val userRepository = remember {
        UserRepository(database.userDao(), RetrofitInstance.api, tokenManager)
    }
    val bookingRepository = remember {
        BookingRepository(database.bookingDao(), RetrofitInstance.api)
    }

    // Load user data
    LaunchedEffect(Unit) {
        userId = tokenManager.getUserId().first() ?: ""
        userEmail = tokenManager.getUserEmail().first() ?: ""
        userNic = tokenManager.getUserNic().first() ?: ""

        // Load user from DB
        val currentUser = userRepository.getCurrentUser().first()
        currentUser?.let {
            userName = it.name
            userEmail = it.email
            userNic = it.nic
            userPhone = it.phone
            userRole = it.role
        }

        // Load booking statistics
        if (userNic.isNotEmpty()) {
            val bookings = bookingRepository.getBookings(userNic).first()

            totalBookings = bookings.size
            activeBookings = bookings.count {
                it.status.equals("Pending", ignoreCase = true) ||
                        it.status.equals("Approved", ignoreCase = true) ||
                        it.status.equals("Charging", ignoreCase = true)
            }
            completedBookings = bookings.count { it.status.equals("Completed", ignoreCase = true) }
            cancelledBookings = bookings.count { it.status.equals("Cancelled", ignoreCase = true) }

            // Find most used station
            val stationCounts = bookings.groupingBy { it.stationName }.eachCount()
            mostUsedStation = stationCounts.maxByOrNull { it.value }?.key ?: "N/A"

            // Get last booking date
            bookings.maxByOrNull { it.startTimeUtc }?.let { lastBooking ->
                lastBookingDate = formatDate(lastBooking.startTimeUtc)
            }
        }
    }

    // Update user function - NOW WITH API CALL
    fun updateUser() {
        scope.launch {
            isLoading = true
            try {
                // Call API to update EV Owner on the backend
                val result = userRepository.updateEvOwner(
                    nic = userNic,
                    name = editName,
                    phone = editPhone,
                    email = userEmail,
                    password = ""  // Empty means don't change password
                )

                if (result.isSuccess) {
                    // Get the updated user entity from result
                    val updatedUser = result.getOrNull()

                    // Update UI state with new values from server
                    userName = updatedUser?.name ?: editName
                    userPhone = updatedUser?.phone ?: editPhone
                    isEditMode = false

                    Toast.makeText(
                        context,
                        "Profile updated successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(
                        context,
                        "Update failed: $error",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Update failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isLoading = false
            }
        }
    }

    // Deactivate account function
    fun deactivateAccount() {
        scope.launch {
            isDeactivating = true
            try {
                val response = RetrofitInstance.api.deactivateUser(userNic)

                if (response.isSuccessful) {
                    Toast.makeText(
                        context,
                        "Account deactivated successfully. Contact admin to reactivate.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Only clear token and logout - don't delete local data
                    tokenManager.clearToken()

                    // Logout
                    onLogout()
                } else {
                    Toast.makeText(
                        context,
                        "Failed to deactivate account: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isDeactivating = false
                showDeactivateDialog = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Insights") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (!isEditMode) {
                        IconButton(onClick = {
                            isEditMode = true
                            editName = userName
                            editPhone = userPhone
                        }) {
                            Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // User Info Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = userName.ifEmpty { "User" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Email
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // NIC (Non-editable)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NIC: ${userNic.ifEmpty { "Not set" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }

                    if (userPhone.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        // Phone
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = userPhone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Edit Mode
            AnimatedVisibility(
                visible = isEditMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Edit Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Email and NIC cannot be changed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.AccountCircle, null) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("Phone") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Phone, null) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isEditMode = false },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { updateUser() },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Booking Overview
            Text(
                "Booking Overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Total",
                    value = totalBookings.toString(),
                    icon = Icons.Default.List,
                    color = Color(0xFF2196F3)
                )
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Active",
                    value = activeBookings.toString(),
                    icon = Icons.Default.DateRange,
                    color = Color(0xFFFFA726)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Completed",
                    value = completedBookings.toString(),
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF4CAF50)
                )
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Cancelled",
                    value = cancelledBookings.toString(),
                    icon = Icons.Default.Close,
                    color = Color(0xFFE57373)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Charging Insights
            Text(
                "Charging Insights",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InsightRow(
                        icon = Icons.Default.LocationOn,
                        label = "Favorite Station",
                        value = mostUsedStation
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    InsightRow(
                        icon = Icons.Default.DateRange,
                        label = "Last Booking",
                        value = lastBookingDate
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout Button
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Deactivate Account Button
            OutlinedButton(
                onClick = { showDeactivateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deactivate Account", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Deactivate Account Confirmation Dialog
    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeactivating) showDeactivateDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Deactivate Account?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to deactivate your account?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "This action will:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Temporarily disable your account access")
                    Text("• Prevent you from making new bookings")
                    Text("• Keep your data and booking history intact")
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "You can contact an admin to reactivate your account at any time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { deactivateAccount() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = !isDeactivating
                ) {
                    if (isDeactivating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("Deactivate")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeactivateDialog = false },
                    enabled = !isDeactivating
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MiniStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun InsightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun formatDate(isoString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val cleanString = isoString.replace("Z", "").substringBefore(".")
        val date = inputFormat.parse(cleanString)
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date ?: Date())
    } catch (e: Exception) {
        "N/A"
    }
}
