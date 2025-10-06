package com.ead.evcharge.data.repository


import android.util.Log
import com.auth0.android.jwt.JWT
import com.ead.evcharge.data.local.dao.UserDao
import com.ead.evcharge.data.local.entity.UserEntity
import com.ead.evcharge.data.model.UserResponse
import com.ead.evcharge.data.remote.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.net.UnknownHostException
import java.net.SocketTimeoutException

class UserRepository(
    private val userDao: UserDao,
    private val apiService: ApiService
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
            val nic = jwt.getClaim("nic").asString() ?: ""
            val phone = jwt.getClaim("phone").asString() ?: ""
            val role = jwt.getClaim("role").asString() ?: ""

            val userEntity = UserEntity(
                userId = userId,
                name = name,
                email = email,
                nic = nic,
                phone = phone,
                role = role,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            saveUser(userEntity)
            Log.d(TAG, "User extracted from token and saved: $email")
            userEntity
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting user from token: ${e.message}", e)
            null
        }
    }

    // Sync user data from server (when online)
    suspend fun syncUserFromServer(nic: String): Result<UserEntity> {
        return try {
            Log.d(TAG, "Attempting to sync user from server: $nic")

            // Call API to fetch latest user details
            val response = apiService.getUserDetails(nic)

            if (response.isSuccessful) {
                response.body()?.let { userResponse ->
                    Log.d(TAG, "User data fetched from server: ${userResponse.email}")

                    // Convert API response to Room entity
                    val userEntity = userResponse.toEntity()

                    // Save to local database
                    userDao.insertUser(userEntity)

                    Log.d(TAG, "User synced successfully: ${userEntity.email}")
                    Result.success(userEntity)
                } ?: run {
                    Log.e(TAG, "Empty response body from server")

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
                Log.e(TAG, "API error: ${response.code()} - ${response.message()}")

                // Fallback to local data if API fails
                val localUser = userDao.getUserByNic(nic).first()
                if (localUser != null) {
                    Log.d(TAG, "Using cached user data: ${localUser.email}")
                    Result.success(localUser)
                } else {
                    Result.failure(Exception("API error: ${response.code()}"))
                }
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "No internet connection, using cached data")
            handleOfflineSync(nic)
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Request timeout, using cached data")
            handleOfflineSync(nic)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user from server: ${e.message}", e)
            handleOfflineSync(nic)
        }
    }


    // Handle offline sync - return cached data
    private suspend fun handleOfflineSync(nic: String): Result<UserEntity> {
        return try {
            val localUser = userDao.getUserByNic(nic).first()
            if (localUser != null) {
                Log.d(TAG, "Returning cached user data (offline): ${localUser.email}")
                Result.success(localUser)
            } else {
                Result.failure(Exception("No cached user data available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached user: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Clear all user data (for logout)
    suspend fun clearUserData() {
        try {
            userDao.deleteAllUsers()
            Log.d(TAG, "All user data cleared from local database")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing user data: ${e.message}", e)
        }
    }

    // Check if user exists in local database
    suspend fun hasLocalUser(): Boolean {
        return try {
            userDao.getUserCount() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking local user: ${e.message}", e)
            false
        }
    }
    private fun UserResponse.toEntity(): UserEntity {
        return UserEntity(
            userId = this.userId,
            name = this.name,
            email = this.email,
            nic = this.nic,
            phone = this.phone,
            role = this.role,
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }
}
