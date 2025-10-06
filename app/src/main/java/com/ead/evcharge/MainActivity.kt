// MainActivity.kt
package com.ead.evcharge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.navigation.MainNavGraph
import com.ead.evcharge.navigation.Screen
import com.ead.evcharge.navigation.getStartDestinationForRole
import com.ead.evcharge.ui.login.LoginScreen
import com.ead.evcharge.ui.signup.SignupScreen
import com.ead.evcharge.ui.theme.EVChargeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)

        setContent {
            EVChargeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(tokenManager)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(tokenManager: TokenManager) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // Check if user is already logged in
    var isLoading by remember { mutableStateOf(true) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf("login") }

    LaunchedEffect(Unit) {
        val token = tokenManager.getToken().first()
        isAuthenticated = !token.isNullOrEmpty()

        startDestination = if (isAuthenticated) {
            "main"
        } else {
            "login"
        }

        isLoading = false
    }

    // Show loading screen while checking authentication
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    // Navigate to main screen after login
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate("signup")
                }
            )
        }
        composable("signup") {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate("main") {
                        popUpTo("signup") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable("main") {
            // Get user role and show appropriate screens
            var userRole by remember { mutableStateOf("") }
            var roleBasedStartDest by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                userRole = tokenManager.getUserRole().first() ?: "owner"
                roleBasedStartDest = getStartDestinationForRole(userRole)
            }

            if (roleBasedStartDest.isNotEmpty()) {
                MainNavGraph(
                    tokenManager = tokenManager,
                    startDestination = roleBasedStartDest,
                    userRole = userRole,
                    onLogout = {
                        scope.launch {
                            tokenManager.clearToken()
                            navController.navigate("login") {
                                popUpTo("main") { inclusive = true }
                            }
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
