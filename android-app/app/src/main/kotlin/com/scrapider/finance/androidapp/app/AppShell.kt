package com.scrapider.finance.androidapp.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import com.scrapider.finance.androidapp.feature.market.MarketRoute
import com.scrapider.finance.androidapp.feature.market.theme.MarketMiuixTheme
import com.scrapider.finance.androidapp.feature.profile.ProfileEvent
import com.scrapider.finance.androidapp.feature.profile.ProfileRoute
import com.scrapider.finance.androidapp.feature.profile.ProfileViewModel
import com.scrapider.finance.androidapp.feature.workbench.WorkbenchRoute
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@Composable
fun AppShell(
    session: UserSession,
    selectedDestination: AppDestination,
    apiClient: FinanceApiClient,
    onDestinationSelected: (AppDestination) -> Unit,
    onSessionUpdated: (UserSession) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var toolPageVisible by remember(session.accessToken) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val showUnavailableFeature: (String) -> Unit = { message ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
    val profileFactory = remember(apiClient) { ProfileViewModel.Factory(apiClient) }
    val profileViewModel: ProfileViewModel = viewModel(factory = profileFactory)
    val currentAccessToken by rememberUpdatedState(session.accessToken)
    val currentOnSessionUpdated by rememberUpdatedState(onSessionUpdated)
    val currentOnSignOut by rememberUpdatedState(onSignOut)

    LaunchedEffect(profileViewModel) {
        profileViewModel.events.collect { event ->
            if (event.accessToken != currentAccessToken) return@collect
            when (event) {
                is ProfileEvent.Notice -> showUnavailableFeature(event.message)
                is ProfileEvent.Saved -> showUnavailableFeature(event.message)
                is ProfileEvent.SessionExpired -> currentOnSignOut()
                is ProfileEvent.SessionUpdated -> currentOnSessionUpdated(event.session)
            }
        }
    }

    if (selectedDestination == AppDestination.Market) {
        MarketMiuixTheme {
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
        }
        return
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!toolPageVisible) {
                FinanceBottomNavigation(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = onDestinationSelected,
                )
            }
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
                    onToolVisibilityChanged = { toolPageVisible = it },
                    modifier = Modifier.padding(contentPadding).consumeWindowInsets(contentPadding),
                )
            }

            AppDestination.Profile -> {
                ProfileRoute(
                    session = session,
                    viewModel = profileViewModel,
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
    val colors = MaterialTheme.colorScheme
    val signals = rememberFinanceSignalColors()
    val dimensions = LocalFinanceDimensions.current
    // 三个目的地共用同一原生底栏；不依赖行情局部主题，也不叠加第二次系统栏边距。
    Column {
        HorizontalDivider(color = colors.outlineVariant)
        NavigationBar(
            containerColor = colors.surface,
            contentColor = colors.onSurface,
            tonalElevation = NavigationBarDefaults.Elevation,
        ) {
            AppDestination.entries.forEach { destination ->
                val selected = destination == selectedDestination
                NavigationBarItem(
                    selected = selected,
                    onClick = { onDestinationSelected(destination) },
                    icon = {
                        Icon(
                            painter = painterResource(destination.iconRes(selected)),
                            contentDescription = null,
                            modifier = Modifier.size(dimensions.iconSize),
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = signals.onNeutralContainer,
                        unselectedTextColor = signals.onNeutralContainer,
                    ),
                    alwaysShowLabel = true,
                )
            }
        }
    }
}

@DrawableRes
private fun AppDestination.iconRes(selected: Boolean): Int = when (this) {
    AppDestination.Workbench -> if (selected) R.drawable.ic_phosphor_squares_four_fill else R.drawable.ic_phosphor_squares_four
    AppDestination.Market -> if (selected) R.drawable.ic_phosphor_chart_line_up_fill else R.drawable.ic_phosphor_chart_line_up
    AppDestination.Profile -> if (selected) R.drawable.ic_phosphor_user_circle_fill else R.drawable.ic_phosphor_user_circle
}
