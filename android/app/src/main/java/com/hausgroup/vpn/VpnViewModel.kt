package com.hausgroup.vpn

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class VpnUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val serverName: String = "New York, US",
    val message: String? = null,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val hasConfig: Boolean = false,
    val signedIn: Boolean = false,
    val email: String? = null,
    val busy: Boolean = false,
)

/**
 * Owns the tunnel lifecycle and exposes a single [uiState] the Compose screen
 * renders. Server configs are stored locally until the HausVPN backend is
 * wired in; the "Paste config" flow lets the app connect to any real
 * WireGuard endpoint today.
 */
class VpnViewModel(app: Application) : AndroidViewModel(app) {

    private val tunnels = TunnelManager(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        VpnUiState(
            hasConfig = prefs.getString(KEY_CONFIG, null) != null,
            signedIn = prefs.getString(KEY_TOKEN, null) != null,
            email = prefs.getString(KEY_EMAIL, null),
        )
    )
    val uiState: StateFlow<VpnUiState> = _uiState.asStateFlow()

    fun saveConfig(raw: String) {
        prefs.edit().putString(KEY_CONFIG, raw.trim()).apply()
        _uiState.value = _uiState.value.copy(hasConfig = true, message = "Server config saved")
    }

    fun hasConfig(): Boolean = prefs.getString(KEY_CONFIG, null) != null

    val defaultBaseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""

    /** Sign in against the control plane and remember the token. */
    fun signIn(baseUrl: String, email: String) {
        val url = baseUrl.trim()
        val mail = email.trim()
        if (url.isBlank() || mail.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Enter server URL and email")
            return
        }
        _uiState.value = _uiState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ApiClient(url).login(mail) }
            when (result) {
                is ApiResult.Ok -> {
                    prefs.edit()
                        .putString(KEY_BASE_URL, url)
                        .putString(KEY_TOKEN, result.value)
                        .putString(KEY_EMAIL, mail)
                        .apply()
                    _uiState.value = _uiState.value.copy(
                        busy = false, signedIn = true, email = mail,
                        message = "Signed in — tap “Get server”",
                    )
                }
                is ApiResult.Err -> _uiState.value =
                    _uiState.value.copy(busy = false, message = result.message)
            }
        }
    }

    /** Ask the gateway to provision this device and store the returned config. */
    fun provisionServer() {
        val url = prefs.getString(KEY_BASE_URL, null)
        val token = prefs.getString(KEY_TOKEN, null)
        if (url == null || token == null) {
            _uiState.value = _uiState.value.copy(message = "Sign in first")
            return
        }
        _uiState.value = _uiState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiClient(url).provisionDevice(token, android.os.Build.MODEL ?: "device")
            }
            when (result) {
                is ApiResult.Ok -> {
                    prefs.edit().putString(KEY_CONFIG, result.value.trim()).apply()
                    _uiState.value = _uiState.value.copy(
                        busy = false, hasConfig = true,
                        message = "Server ready — tap to connect",
                    )
                }
                is ApiResult.Err -> _uiState.value =
                    _uiState.value.copy(busy = false, message = result.message)
            }
        }
    }

    fun signOut() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_EMAIL).apply()
        _uiState.value = _uiState.value.copy(signedIn = false, email = null, message = "Signed out")
    }

    /** Called once the OS VPN consent has been granted. */
    fun toggle() {
        when (_uiState.value.status) {
            ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING -> disconnect()
            else -> connect()
        }
    }

    private fun connect() {
        val raw = prefs.getString(KEY_CONFIG, null)
        if (raw.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                status = ConnectionStatus.ERROR,
                message = "Add a server configuration first",
            )
            return
        }

        _uiState.value = _uiState.value.copy(status = ConnectionStatus.CONNECTING, message = null)

        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    Config.parse(BufferedReader(StringReader(raw)))
                }
                withContext(Dispatchers.IO) {
                    tunnels.connect(config) { state -> onTunnelState(state) }
                }
                _uiState.value = _uiState.value.copy(status = ConnectionStatus.CONNECTED)
                pollStatistics()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = ConnectionStatus.ERROR,
                    message = e.message ?: "Could not connect",
                )
            }
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { tunnels.disconnect() }
            } catch (_: Exception) {
            } finally {
                _uiState.value = _uiState.value.copy(
                    status = ConnectionStatus.DISCONNECTED,
                    rxBytes = 0,
                    txBytes = 0,
                    message = null,
                )
            }
        }
    }

    private fun onTunnelState(state: Tunnel.State) {
        val status = when (state) {
            Tunnel.State.UP -> ConnectionStatus.CONNECTED
            Tunnel.State.DOWN -> ConnectionStatus.DISCONNECTED
            else -> _uiState.value.status
        }
        _uiState.value = _uiState.value.copy(status = status)
    }

    private fun pollStatistics() {
        viewModelScope.launch {
            while (isActive && _uiState.value.status == ConnectionStatus.CONNECTED) {
                val stats = withContext(Dispatchers.IO) { runCatching { tunnels.statistics() }.getOrNull() }
                if (stats != null) {
                    _uiState.value = _uiState.value.copy(
                        rxBytes = stats.totalRx(),
                        txBytes = stats.totalTx(),
                    )
                }
                delay(1500)
            }
        }
    }

    companion object {
        private const val PREFS = "hausvpn"
        private const val KEY_CONFIG = "wg_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_EMAIL = "email"
    }
}
