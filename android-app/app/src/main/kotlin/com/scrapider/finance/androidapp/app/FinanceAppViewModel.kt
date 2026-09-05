package com.scrapider.finance.androidapp.app

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import com.scrapider.finance.androidapp.core.network.FinanceApiClient
import com.scrapider.finance.androidapp.core.session.SessionStore
import com.scrapider.finance.androidapp.core.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class AppUiState(
    val session: UserSession? = null,
    val destination: AppDestination = AppDestination.Workbench,
    val rememberedUsername: String = "",
    val rememberAccount: Boolean = true,
)

class FinanceAppViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = SessionStore(application)
    private val initialSnapshot = sessionStore.load()
    internal val apiClient = FinanceApiClient()
    private val _uiState = MutableStateFlow(
        AppUiState(
            session = initialSnapshot.session,
            rememberedUsername = initialSnapshot.username,
            rememberAccount = initialSnapshot.rememberAccount,
        ),
    )
    val uiState = _uiState.asStateFlow()

    init {
        apiClient.setAccessToken(initialSnapshot.session?.accessToken.orEmpty())
    }

    fun authenticate(session: UserSession, rememberAccount: Boolean) {
        apiClient.setAccessToken(session.accessToken)
        sessionStore.save(session, rememberAccount)
        _uiState.value = _uiState.value.copy(
            session = session,
            destination = AppDestination.Workbench,
            rememberedUsername = if (rememberAccount) session.username else "",
            rememberAccount = rememberAccount,
        )
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.value = _uiState.value.copy(destination = destination)
    }

    fun signOut() {
        apiClient.setAccessToken("")
        sessionStore.clearSession()
        val snapshot = sessionStore.load()
        _uiState.value = AppUiState(
            rememberedUsername = snapshot.username,
            rememberAccount = snapshot.rememberAccount,
        )
    }

    override fun onCleared() {
        apiClient.close()
        super.onCleared()
    }
}
