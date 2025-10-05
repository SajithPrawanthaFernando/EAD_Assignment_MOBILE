// LoginViewModel.kt
package com.ead.evcharge.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.model.LoginRequest
import com.ead.evcharge.data.remote.ApiService
import com.ead.evcharge.data.remote.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.auth0.android.jwt.JWT

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val token: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val apiService: ApiService = RetrofitInstance.api

    fun login(email: String, password: String) {
        // Validate input
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Email and password are required")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _loginState.value = LoginState.Error("Invalid email format")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            try {
                val response = apiService.login(
                    LoginRequest(email, password)
                )

                if (response.isSuccessful) {
                    response.body()?.let { loginResponse ->
                        val token = loginResponse.token
                        println("Token: ${loginResponse.token}")

                        try {
                            val jwt = JWT(token)

                            // Extract claims from JWT
                            val userId = jwt.getClaim("sub").asString()
                                ?: jwt.getClaim("id").asString()
                                ?: jwt.subject
                                ?: ""

                            val userEmail = jwt.getClaim("email").asString() ?: email

                            val userRole = jwt.getClaim("http://schemas.microsoft.com/ws/2008/06/identity/claims/role").asString()
                                ?: jwt.getClaim("roles").asString()
                                ?: ""
                            println("userRole: ${userRole}")
                            println("userEmail: ${userEmail}")
                            println("userId: ${userId}")
                            // Save all data to DataStore
                            tokenManager.saveToken(token)
                            tokenManager.saveUserId(userId)
                            tokenManager.saveUserEmail(userEmail)
                            tokenManager.saveUserRole(userRole)



                            _loginState.value = LoginState.Success(token)

                        } catch (e: com.auth0.android.jwt.DecodeException) {
                            _loginState.value = LoginState.Error("Failed to decode token")
                        }
                    } ?: run {
                        _loginState.value = LoginState.Error("Empty response from server")
                    }
                } else {
                    val errorMessage = when (response.code()) {

                        401 -> "Invalid email or password"
                        404 -> "User not found"
                        500 -> "Server error. Please try again later"
                        else -> "Login failed: ${response.message()}"
                    }
                    _loginState.value = LoginState.Error(errorMessage)
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(
                    e.localizedMessage ?: "Network error. Please check your connection"
                )
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }
}
