package com.scrapider.finance.androidapp.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.feature.market.MarketRoute
import com.scrapider.finance.androidapp.feature.profile.ProfileScreen
import com.scrapider.finance.androidapp.feature.workbench.WorkbenchRoute
import kotlinx.coroutines.launch

@Composable
fun AppShell(
    session: UserSession,
    selectedDestination: AppDestination,
    apiClient: FinanceApiClient,
    onDestinationSelected: (AppDestination) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedDestination == AppDestination.Market) {
        MarketRoute(
            session = session,
            apiClient = apiClient,
            onSessionExpired = onSignOut,
            bottomBar = {
                FinanceBottomNavigation(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = onDestinationSelected,
                )
            },
            modifier = modifier,
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val showUnavailableFeature: (String) -> Unit = { message ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FinanceBottomNavigation(
                selectedDestination = selectedDestination,
                onDestinationSelected = onDestinationSelected,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when (selectedDestination) {
            AppDestination.Workbench -> {
                WorkbenchRoute(
                    session = session,
                    apiClient = apiClient,
                    onMarketSelected = {
                        onDestinationSelected(AppDestination.Market)
                    },
                    onSessionExpired = onSignOut,
                    onUnavailableFeature = showUnavailableFeature,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            AppDestination.Profile -> {
                ProfileScreen(
                    session = session,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            AppDestination.Market -> Unit
        }
    }
}

@Composable
private fun FinanceBottomNavigation(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon(),
                        contentDescription = null,
                    )
                },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

private fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.Workbench -> Icons.Outlined.Home
    AppDestination.Market -> Icons.AutoMirrored.Outlined.ShowChart
    AppDestination.Profile -> Icons.Outlined.Person
}
