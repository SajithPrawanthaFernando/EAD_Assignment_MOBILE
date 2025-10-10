package com.ead.evcharge.ui.operator

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.model.BookingDetailsResponse
import com.ead.evcharge.data.remote.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// UI State
sealed class BookingsUiState {
    object Loading : BookingsUiState()
    data class Success(val bookings: List<BookingDetailsResponse>) : BookingsUiState()
    data class Error(val message: String) : BookingsUiState()
}


// ViewModel
class OperatorBookingsViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookingsUiState>(BookingsUiState.Loading)
    val uiState: StateFlow<BookingsUiState> = _uiState

    private var allBookings: List<BookingDetailsResponse> = emptyList()

    init {
        loadBookings()
    }

    // Fetch all bookings from API
    fun loadBookings() {
        viewModelScope.launch {
            try {
                _uiState.value = BookingsUiState.Loading
                val response = RetrofitInstance.api.getAllBookings()

                if (response.isSuccessful && response.body() != null) {
                    allBookings = response.body()!!
                    _uiState.value = BookingsUiState.Success(allBookings)
                } else {
                    _uiState.value = BookingsUiState.Error(
                        "Failed to load bookings: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: IOException) {
                _uiState.value = BookingsUiState.Error("Network error: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = BookingsUiState.Error("HTTP error: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = BookingsUiState.Error("Unexpected error: ${e.message}")
            }
        }
    }

    // Filter by booking status
    fun filterByStatus(status: String) {
        val filtered = if (status == "All") allBookings
        else allBookings.filter { it.status.equals(status, ignoreCase = true) }
        _uiState.value = BookingsUiState.Success(filtered)
    }

    // Show only today’s bookings
    fun showTodaysBookings() {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_DATE
        val todayBookings = allBookings.filter {
            it.startTimeUtc?.startsWith(today.format(formatter)) == true
        }
        _uiState.value = BookingsUiState.Success(todayBookings)
    }
}
