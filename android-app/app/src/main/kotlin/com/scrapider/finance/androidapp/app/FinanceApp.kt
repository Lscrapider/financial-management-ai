package com.scrapider.finance.androidapp.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.feature.auth.LoginRoute

@Composable
fun FinanceApp(
    state: AppUiState,
    apiClient: FinanceApiClient,
    onAuthenticated: (UserSession, Boolean) -> Unit,
    onDestinationSelected: (AppDestination) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        val session = state.session
        if (session == null) {
            LoginRoute(
                apiClient = apiClient,
                initialUsername = state.rememberedUsername,
                initialRememberAccount = state.rememberAccount,
                onAuthenticated = onAuthenticated,
            )
        } else {
            AppShell(
                session = session,
                selectedDestination = state.destination,
                apiClient = apiClient,
                onDestinationSelected = onDestinationSelected,
                onSignOut = onSignOut,
            )
        }
    }
}
