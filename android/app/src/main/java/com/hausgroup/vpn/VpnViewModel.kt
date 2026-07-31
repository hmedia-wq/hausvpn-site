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
        VpnUiState(hasConfig = prefs.getString(KEY_CONFIG, null) != null)
    )
    val uiState: StateFlow<VpnUiState> = _uiState.asStateFlow()

    fun saveConfig(raw: String) {
        prefs.edit().putString(KEY_CONFIG, raw.trim()).apply()
        _uiState.value = _uiState.value.copy(hasConfig = true, message = "Server config saved")
    }

    fun hasConfig(): Boolean = prefs.getString(KEY_CONFIG, null) != null

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
    }
}
