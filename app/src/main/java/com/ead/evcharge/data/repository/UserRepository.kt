// data/repository/UserRepository.kt
package com.ead.evcharge.data.repository

import android.util.Log
import com.auth0.android.jwt.JWT
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.local.dao.UserDao
import com.ead.evcharge.data.local.entity.UserEntity
import com.ead.evcharge.data.model.EvOwnerResponse
import com.ead.evcharge.data.model.UpdateEvOwnerRequest
import com.ead.evcharge.data.model.UserResponse
import com.ead.evcharge.data.remote.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UserRepository(
    private val userDao: UserDao,
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
) {

    companion object {
        private const val TAG = "UserRepository"
    }

    // Get user from local database (offline-first)
    fun getCurrentUser(): Flow<UserEntity?> {
        return userDao.getCurrentUser()
    }

    fun getUserByNic(nic: String): Flow<UserEntity?> {
        return userDao.getUserByNic(nic)
    }

    // Save user to local database
    suspend fun saveUser(user: UserEntity) {
        try {
            userDao.insertUser(user)
            Log.d(TAG, "User saved to local database: ${user.email}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user: ${e.message}", e)
        }
    }

    // Fetch and save EV Owner data from API
    suspend fun fetchAndSaveEvOwnerData(nic: String): Result<UserEntity> {
        return try {
            Log.d(TAG, "🔍 Fetching EV Owner data for NIC: $nic")

            val response = apiService.getEvOwnerByNic(nic)

            if (response.isSuccessful) {
                response.body()?.let { evOwnerData ->
                    Log.d(TAG, "✅ EV Owner data fetched: ${evOwnerData.name}")

                    // Get userId from token
                    val userId = tokenManager.getUserId().first() ?: ""

                    // Convert to UserEntity
                    val userEntity = UserEntity(
                        userId = userId,
                        name = evOwnerData.name,
                        email = evOwnerData.user.email,
                        nic = evOwnerData.nic,
                        phone = evOwnerData.phone,
                        role = evOwnerData.user.roles.firstOrNull() ?: "EVOwner",
                        status = evOwnerData.status,
                        isActive = evOwnerData.user.active,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )

                    // Save to database
                    userDao.insertUser(userEntity)

                    Log.d(TAG, "💾 EV Owner data saved to database")
                    Result.success(userEntity)
                } ?: run {
                    Log.e(TAG, "❌ Empty response body")
                    Result.failure(Exception("Empty response"))
                }
            } else {
                Log.e(TAG, "❌ API error: ${response.code()}")
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error fetching EV Owner data: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateEvOwner(
        nic: String,
        name: String,
        phone: String,
        email: String,
        password: String = ""
    ): Result<UserEntity> {
        return try {
            Log.d(TAG, "🔄 Updating EV Owner via API: $name")

            val request = UpdateEvOwnerRequest(
                nic = nic,
                name = name,
                phone = phone,
                email = email,
                password = password
            )

            // Call the update API
            val response = apiService.updateEvOwner(request)

            if (response.isSuccessful) {
                Log.d(TAG, "✅ EV Owner updated on server (${response.code()})")

                // Since API returns 204 No Content, fetch the latest data
                Log.d(TAG, "🔄 Fetching updated EV Owner data...")
                val fetchResult = fetchAndSaveEvOwnerData(nic)

                if (fetchResult.isSuccess) {
                    Log.d(TAG, "✅ Updated profile fetched and saved to database")
                    fetchResult
                } else {
                    Log.e(TAG, "⚠️ Update succeeded but fetch failed, using local data")
                    // Fallback: Update local data with what we have
                    val userId = tokenManager.getUserId().first() ?: ""
                    val userEntity = UserEntity(
                        userId = userId,
                        name = name,
                        email = email,
                        nic = nic,
                        phone = phone,
                        role = "EVOwner",
                        status = "active",
                        isActive = true,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    userDao.updateUser(userEntity)
                    Result.success(userEntity)
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ API error: ${response.code()} - $errorBody")
                Result.failure(Exception("Update failed: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error updating EV Owner: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Extract user data from JWT and save to Room
    suspend fun saveUserFromToken(token: String): UserEntity? {
        return try {
            val jwt = JWT(token)
            val userId = jwt.getClaim("userId").asString()
                ?: jwt.getClaim("id").asString()
                ?: jwt.subject
                ?: return null

            val email = jwt.getClaim("email").asString() ?: ""
            val name = jwt.getClaim("name").asString() ?: ""
            var nic = jwt.getClaim("nic").asString() ?: ""
            val phone = jwt.getClaim("phone").asString() ?: ""
            val role = jwt.getClaim("http://schemas.microsoft.com/ws/2008/06/identity/claims/role").asString() ?: ""

            Log.d(TAG, "🔑 Role from token: $role")

            // If role is EVOwner, fetch complete data from API
            if (role.equals("EVOwner", ignoreCase = true)) {
                Log.d(TAG, "👤 Role is EVOwner, fetching complete profile data")

                try {
                    // First get the user details to get NIC
                    val userResponse = apiService.getUserDetails(userId)

                    if (userResponse.isSuccessful) {
                        userResponse.body()?.let { userDetails ->
                            nic = userDetails.ownerNic
                            tokenManager.saveUserNic(nic)
                            Log.d(TAG, "🆔 NIC fetched from user details: $nic")

                            // Now fetch complete EV Owner data
                            if (nic.isNotEmpty()) {
                                val evOwnerResult = fetchAndSaveEvOwnerData(nic)
                                if (evOwnerResult.isSuccess) {
                                    Log.d(TAG, "✅ Complete EV Owner profile saved")
                                    return evOwnerResult.getOrNull()
                                } else {
                                    Log.w(TAG, "⚠️ Failed to fetch EV Owner data, using token data")
                                }
                            }
                        } ?: Log.w(TAG, "⚠️ Empty response body when fetching user details")
                    } else {
                        Log.w(TAG, "⚠️ Failed to fetch user details: ${userResponse.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "💥 Error fetching EV Owner profile: ${e.message}", e)
                }
            }

            // Fallback: Create basic user entity from token
            val userEntity = UserEntity(
                userId = userId,
                name = name,
                email = email,
                nic = nic,
                phone = phone,
                role = role,
                status = "active",
                isActive = true,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            saveUser(userEntity)
            Log.d(TAG, "💾 User saved from token: $email")
            userEntity
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error extracting user from token: ${e.message}", e)
            null
        }
    }

    // Sync user data from server (when online)
    suspend fun syncUserFromServer(nic: String): Result<UserEntity> {
        return try {
            Log.d(TAG, "🔄 Attempting to sync user from server: $nic")

            // Try to fetch EV Owner data first
            val evOwnerResult = fetchAndSaveEvOwnerData(nic)
            if (evOwnerResult.isSuccess) {
                return evOwnerResult
            }

            // Fallback to getUserDetails
            val response = apiService.getUserDetails(nic)

            if (response.isSuccessful) {
                response.body()?.let { userResponse ->
                    Log.d(TAG, "✅ User data fetched from server: ${userResponse.email}")

                    // Convert API response to Room entity
                    val userEntity = userResponse.toEntity()

                    // Save to local database
                    userDao.insertUser(userEntity)

                    Log.d(TAG, "💾 User synced successfully: ${userEntity.email}")
                    Result.success(userEntity)
                } ?: run {
                    Log.e(TAG, "❌ Empty response body from server")

                    // Fallback: Get from local DB and update timestamp
                    val localUser = userDao.getUserByNic(nic).first()
                    if (localUser != null) {
                        val updatedUser = localUser.copy(
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                        userDao.updateUser(updatedUser)
                        Result.success(updatedUser)
                    } else {
                        Result.failure(Exception("User not found locally"))
                    }
                }
            } else {
                Log.e(TAG, "❌ API error: ${response.code()} - ${response.message()}")

                // Fallback to local data if API fails
                val localUser = userDao.getUserByNic(nic).first()
                if (localUser != null) {
                    Log.d(TAG, "📦 Using cached user data: ${localUser.email}")
                    Result.success(localUser)
                } else {
                    Result.failure(Exception("API error: ${response.code()}"))
                }
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "📡 No internet connection, using cached data")
            handleOfflineSync(nic)
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "⏱️ Request timeout, using cached data")
            handleOfflineSync(nic)
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error syncing user from server: ${e.message}", e)
            handleOfflineSync(nic)
        }
    }

    // Handle offline sync - return cached data
    private suspend fun handleOfflineSync(nic: String): Result<UserEntity> {
        return try {
            val localUser = userDao.getUserByNic(nic).first()
            if (localUser != null) {
                Log.d(TAG, "📦 Returning cached user data (offline): ${localUser.email}")
                Result.success(localUser)
            } else {
                Result.failure(Exception("No cached user data available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error getting cached user: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Clear all user data (for logout)
    suspend fun clearUserData() {
        try {
            userDao.deleteAllUsers()
            Log.d(TAG, "🗑️ All user data cleared from local database")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error clearing user data: ${e.message}", e)
        }
    }

    // Check if user exists in local database
    suspend fun hasLocalUser(): Boolean {
        return try {
            userDao.getUserCount() > 0
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error checking local user: ${e.message}", e)
            false
        }
    }

    // Helper function to convert UserResponse to UserEntity
    private fun UserResponse.toEntity(): UserEntity {
        return UserEntity(
            userId = this.userId,
            name = this.name,
            email = this.email,
            nic = this.ownerNic,
            phone = this.phone,
            role = this.role,
            status = "active",
            isActive = true,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }
}
