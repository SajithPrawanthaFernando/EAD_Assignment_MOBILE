package com.ead.evcharge.ui.signup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auth0.android.jwt.JWT
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.model.LoginRequest
import com.ead.evcharge.data.model.SignupRequest
import com.ead.evcharge.data.remote.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SignupState {
    object Idle : SignupState()
    object Loading : SignupState()
    object Success : SignupState()
    data class Error(val message: String) : SignupState()
}

class SignupViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _signupState = MutableStateFlow<SignupState>(SignupState.Idle)
    val signupState: StateFlow<SignupState> = _signupState.asStateFlow()

    private val apiService = RetrofitInstance.api

    companion object {
        private const val TAG = "SignupViewModel"
    }

    fun signup(
        nic: String,
        name: String,
        phone: String,
        email: String,
        password: String,
        confirmPassword: String
    ) {
        // Validation
        if (nic.isBlank() || name.isBlank() || phone.isBlank() ||
            email.isBlank() || password.isBlank()) {
            _signupState.value = SignupState.Error("All fields are required")
            return
        }

        if (!isValidNIC(nic)) {
            _signupState.value = SignupState.Error("Invalid NIC format")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _signupState.value = SignupState.Error("Invalid email format")
            return
        }

        if (!isValidPhone(phone)) {
            _signupState.value = SignupState.Error("Invalid phone number")
            return
        }

        if (password.length < 8) {
            _signupState.value = SignupState.Error("Password must be at least 8 characters")
            return
        }

        if (!isValidPassword(password)) {
            _signupState.value = SignupState.Error(
                "Password must contain uppercase, lowercase, number and special character"
            )
            return
        }

        if (password != confirmPassword) {
            _signupState.value = SignupState.Error("Passwords do not match")
            return
        }

        viewModelScope.launch {
            _signupState.value = SignupState.Loading
            Log.d(TAG, "Attempting signup for email: $email")

            try {
                // Step 1: Create account
                val signupResponse = apiService.signup(
                    SignupRequest(
                        nic = nic,
                        name = name,
                        phone = phone,
                        email = email,
                        password = password
                    )
                )

                Log.d(TAG, "Signup response code: ${signupResponse.code()}")

                if (signupResponse.isSuccessful) {
                    Log.d(TAG, "Signup successful, attempting auto-login")

                    // Step 2: Auto login
                    val loginResponse = apiService.login(
                        LoginRequest(email, password)
                    )

                    if (loginResponse.isSuccessful) {
                        loginResponse.body()?.let { loginData ->
                            val token = loginData.token

                            // Decode JWT and save user data
                            try {
                                val jwt = JWT(token)
                                val userId = jwt.getClaim("userId").asString()
                                    ?: jwt.getClaim("id").asString()
                                    ?: jwt.subject
                                    ?: ""
                                val userName = jwt.getClaim("name").asString() ?: name
                                val userEmail = jwt.getClaim("email").asString() ?: email
                                val userRole = jwt.getClaim("role").asString() ?: ""

                                Log.d(TAG, "Auto-login successful")
                                Log.d(TAG, "Token: $token")
                                Log.d(TAG, "User ID: $userId")
                                Log.d(TAG, "Role: $userRole")

                                // Save to DataStore
                                tokenManager.saveToken(token)
                                tokenManager.saveUserId(userId)
                                tokenManager.saveUserEmail(userEmail)
                                tokenManager.saveUserRole(userRole)

                                _signupState.value = SignupState.Success
                            } catch (e: Exception) {
                                Log.e(TAG, "JWT decode error: ${e.message}", e)
                                _signupState.value = SignupState.Error("Signup successful but login failed")
                            }
                        }
                    } else {
                        Log.e(TAG, "Auto-login failed: ${loginResponse.code()}")
                        _signupState.value = SignupState.Error(
                            "Account created but auto-login failed. Please login manually."
                        )
                    }
                } else {
                    val errorMessage = when (signupResponse.code()) {
                        400 -> "Invalid data provided"
                        409 -> "Email or NIC already registered"
                        500 -> "Server error. Please try again later"
                        else -> "Signup failed: ${signupResponse.message()}"
                    }
                    Log.e(TAG, "Signup failed: $errorMessage")
                    _signupState.value = SignupState.Error(errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signup exception: ${e.message}", e)
                _signupState.value = SignupState.Error(
                    e.localizedMessage ?: "Network error. Please check your connection"
                )
            }
        }
    }

    private fun isValidNIC(nic: String): Boolean {
        // Sri Lankan NIC validation (old: 9 digits + V/X, new: 12 digits)
        val oldNICPattern = "^[0-9]{9}[VXvx]$".toRegex()
        val newNICPattern = "^[0-9]{12}$".toRegex()
        return nic.matches(oldNICPattern) || nic.matches(newNICPattern)
    }

    private fun isValidPhone(phone: String): Boolean {
        // Sri Lankan phone number validation (+94 format)
        val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
        return cleanPhone.matches("^\\+94[0-9]{9}$".toRegex()) ||
                cleanPhone.matches("^0[0-9]{9}$".toRegex())
    }

    private fun isValidPassword(password: String): Boolean {
        val hasUppercase = password.any { it.isUpperCase() }
        val hasLowercase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return hasUppercase && hasLowercase && hasDigit && hasSpecial
    }

    fun resetState() {
        _signupState.value = SignupState.Idle
    }
}