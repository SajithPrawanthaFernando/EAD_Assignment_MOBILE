package com.ead.evcharge.ui.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.model.BookingDetailsResponse
import com.ead.evcharge.data.model.VerifyQrRequest
import com.ead.evcharge.data.model.VerifyQrResponse
import com.ead.evcharge.data.remote.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class QrUiState {
    object Idle : QrUiState()
    object Loading : QrUiState()
    data class Success(val data: VerifyQrResponse) : QrUiState()
    data class SuccessMessage(val message: String) : QrUiState()
    data class Error(val message: String) : QrUiState()
    data class BookingDetails(val data: BookingDetailsResponse) : QrUiState()
    data class TodayBookings(val data: List<BookingDetailsResponse>) : QrUiState()
}

class QrViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<QrUiState>(QrUiState.Idle)
    val uiState: StateFlow<QrUiState> = _uiState

    private val apiService = RetrofitInstance.api

    fun verifyQrToken(scannedToken: String) {
        viewModelScope.launch {
            try {
                _uiState.value = QrUiState.Loading
                val token = "Bearer ${tokenManager.getToken()}"
                val response = RetrofitInstance.api.verifyQr(
                    token,
                    VerifyQrRequest(token = scannedToken)
                )

                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = QrUiState.Success(response.body()!!)
                } else {
                    _uiState.value = QrUiState.Error("Invalid or expired QR code")
                }
            } catch (e: Exception) {
                _uiState.value = QrUiState.Error("Network error: ${e.message}")
            }
        }
    }

    fun reset() {
        _uiState.value = QrUiState.Idle
    }

    fun finalizeBooking(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = QrUiState.Loading
            try {
                val token = "Bearer " + tokenManager.getToken().first()
                val response = apiService.finalizeBooking(token, bookingId)

                if (response.isSuccessful) {
                    _uiState.value = QrUiState.SuccessMessage("Booking finalized successfully")
                } else {
                    _uiState.value = QrUiState.Error("Failed to finalize booking")
                }
            } catch (e: Exception) {
                _uiState.value = QrUiState.Error(e.localizedMessage ?: "Error finalizing booking")
            }
        }
    }

    fun loadBookingDetails(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = QrUiState.Loading
            try {
                val token = "Bearer ${tokenManager.getToken().first()}"
                val response = apiService.getBookingDetails(token, bookingId)

                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = QrUiState.BookingDetails(response.body()!!)
                } else {
                    _uiState.value = QrUiState.Error("Failed to fetch booking details")
                }
            } catch (e: Exception) {
                _uiState.value = QrUiState.Error(e.localizedMessage ?: "Error loading booking details")
            }
        }
    }

    fun loadTodayBookings() {
        viewModelScope.launch {
            _uiState.value = QrUiState.Loading
            try {
                val token = "Bearer ${tokenManager.getToken().first()}"
                val response = apiService.getAllBookings(token)
                if (response.isSuccessful && response.body() != null) {
                    // Filter to today’s bookings (simple UTC check)
                    val today = java.time.LocalDate.now()
                    val todayBookings = response.body()!!.filter {
                        java.time.Instant.parse(it.startTimeUtc)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate() == today
                    }
                    _uiState.value = QrUiState.TodayBookings(todayBookings)
                } else {
                    _uiState.value = QrUiState.Error("Failed to load bookings")
                }
            } catch (e: Exception) {
                _uiState.value = QrUiState.Error(e.localizedMessage ?: "Error loading bookings")
            }
        }
    }

}
