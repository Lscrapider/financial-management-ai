package com.scrapider.finance.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrapider.finance.androidapp.app.FinanceApp
import com.scrapider.finance.androidapp.app.FinanceAppViewModel
import com.scrapider.finance.androidapp.designsystem.FinanceTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: FinanceAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by appViewModel.uiState.collectAsStateWithLifecycle()
            FinanceTheme {
                FinanceApp(
                    state = state,
                    apiClient = appViewModel.apiClient,
                    onAuthenticated = appViewModel::authenticate,
                    onDestinationSelected = appViewModel::selectDestination,
                    onSignOut = appViewModel::signOut,
                )
            }
        }
    }
}
