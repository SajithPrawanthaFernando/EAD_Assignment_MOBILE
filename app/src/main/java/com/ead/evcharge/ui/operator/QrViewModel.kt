package com.ead.evcharge.ui.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.model.BookingDetailsResponse
import com.ead.evcharge.data.remote.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// UI State definitions
sealed class QrUiState {
    object Idle : QrUiState()
    object Loading : QrUiState()
    data class SuccessMessage(val message: String) : QrUiState()
    data class Error(val message: String) : QrUiState()
    data class BookingDetails(val data: BookingDetailsResponse) : QrUiState()
}

// ViewModel
class QrViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<QrUiState>(QrUiState.Idle)
    val uiState: StateFlow<QrUiState> = _uiState

    private val apiService = RetrofitInstance.api


    // Fetch booking details by ID
    fun loadBookingDetails(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = QrUiState.Loading
            try {
                val token = "Bearer ${tokenManager.getToken().first()}"
                val response = apiService.getBookingDetails(bookingId)

                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = QrUiState.BookingDetails(response.body()!!)
                } else {
                    _uiState.value = QrUiState.Error(
                        "Failed to fetch booking details (${response.code()} ${response.message()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = QrUiState.Error(
                    e.localizedMessage ?: "Error loading booking details"
                )
            }
        }
    }


    // Start charging booking
    fun startCharging(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = QrUiState.Loading
            try {
                val token = "Bearer ${tokenManager.getToken().first()}"
                val response = apiService.startCharging(bookingId)
                if (response.isSuccessful) {
                    _uiState.value = QrUiState.SuccessMessage("Charging started successfully.")
                    loadBookingDetails(bookingId)
                } else {
                    _uiState.value = QrUiState.Error("Failed to start charging (${response.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = QrUiState.Error(e.localizedMessage ?: "Error starting charging")
            }
        }
    }


    // Finalize (complete) booking
    fun finalizeBooking(bookingId: String) {
        viewModelScope.launch {
            _uiState.value = QrUiState.Loading
            try {
                val token = "Bearer ${tokenManager.getToken().first()}"
                val response = apiService.completeBooking(bookingId)
                if (response.isSuccessful) {
                    _uiState.value = QrUiState.SuccessMessage("Booking finalized successfully.")
                    loadBookingDetails(bookingId)
                } else {
                    _uiState.value = QrUiState.Error("Failed to finalize booking (${response.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = QrUiState.Error(e.localizedMessage ?: "Error finalizing booking")
            }
        }
    }


    // Reset UI state
    fun reset() {
        _uiState.value = QrUiState.Idle
    }
}
