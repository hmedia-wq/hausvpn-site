package com.hausgroup.vpn

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

/**
 * A single named WireGuard tunnel. The WireGuard library calls back on
 * [onStateChange] whenever the tunnel goes up or down.
 */
class HausTunnel(
    private val tunnelName: String,
    private val onState: (Tunnel.State) -> Unit,
) : Tunnel {
    override fun getName(): String = tunnelName
    override fun onStateChange(newState: Tunnel.State) = onState(newState)
}

/**
 * Thin wrapper around WireGuard's [GoBackend] — the real userspace VPN engine.
 *
 * All of these calls (bringing the tunnel up/down, reading stats) can block or
 * throw, so callers must invoke them off the main thread.
 */
class TunnelManager(context: Context) {

    private val backend: Backend = GoBackend(context.applicationContext)
    private var tunnel: HausTunnel? = null

    /** Bring the tunnel up with the given parsed WireGuard [config]. */
    @Throws(Exception::class)
    fun connect(config: Config, onState: (Tunnel.State) -> Unit) {
        val t = tunnel ?: HausTunnel(TUNNEL_NAME, onState).also { tunnel = it }
        backend.setState(t, Tunnel.State.UP, config)
    }

    /** Tear the tunnel down. */
    @Throws(Exception::class)
    fun disconnect() {
        tunnel?.let { backend.setState(it, Tunnel.State.DOWN, null) }
    }

    fun currentState(): Tunnel.State =
        tunnel?.let { backend.getState(it) } ?: Tunnel.State.DOWN

    /** Live transfer counters (rx/tx bytes), or null if the tunnel is down. */
    fun statistics(): Statistics? =
        tunnel?.takeIf { backend.getState(it) == Tunnel.State.UP }
            ?.let { backend.getStatistics(it) }

    companion object {
        private const val TUNNEL_NAME = "hausvpn"
    }
}
