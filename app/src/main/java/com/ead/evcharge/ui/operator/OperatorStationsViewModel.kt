package com.ead.evcharge.ui.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.model.StationResponse
import com.ead.evcharge.data.remote.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

sealed class StationsUiState {
    object Loading : StationsUiState()
    data class Success(val stations: List<StationResponse>) : StationsUiState()
    data class Error(val message: String) : StationsUiState()
}

class OperatorStationsViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<StationsUiState>(StationsUiState.Loading)
    val uiState: StateFlow<StationsUiState> = _uiState

    init {
        loadStations()
    }

    fun loadStations() {
        viewModelScope.launch {
            try {
                _uiState.value = StationsUiState.Loading
                val response = RetrofitInstance.api.getAllStations()
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = StationsUiState.Success(response.body()!!)
                } else {
                    _uiState.value =
                        StationsUiState.Error("Failed to load stations: ${response.code()} ${response.message()}")
                }
            } catch (e: IOException) {
                _uiState.value = StationsUiState.Error("Network error: ${e.message}")
            } catch (e: HttpException) {
                _uiState.value = StationsUiState.Error("HTTP error: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = StationsUiState.Error("Unexpected error: ${e.message}")
            }
        }
    }
}
